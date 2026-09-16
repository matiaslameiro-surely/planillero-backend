package ar.com.planillero.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ar.com.planillero.common.ApiException;
import ar.com.planillero.security.JwtProperties;
import ar.com.planillero.user.User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * Emite y lee los JWT propios.
 *
 * <p>El access token lleva los roles para que el RBAC se resuelva sin tocar la base en cada pedido.
 * El token de desafío del segundo factor es un JWT aparte, de vida corta y un propósito distinto:
 * no sirve como access token.
 */
@Service
public class TokenService {

    private static final String PURPOSE_CLAIM = "purpose";
    private static final String PURPOSE_ACCESS = "access";
    private static final String PURPOSE_TWO_FACTOR = "two_factor";
    private static final String ROLES_CLAIM = "roles";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder encoder, JwtDecoder decoder, JwtProperties properties) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.properties = properties;
    }

    /** Access token de un usuario ya autenticado. */
    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream().map(role -> role.getName().name()).toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .subject(user.getUsername())
                .claim(PURPOSE_CLAIM, PURPOSE_ACCESS)
                .claim(ROLES_CLAIM, roles)
                .build();
        return encode(claims);
    }

    /**
     * Token de desafío que prueba que la contraseña ya fue validada y falta el segundo factor.
     *
     * <p>Lleva un {@code jti} propio para que quien lo canjee pueda marcarlo como usado: el desafío
     * se consume una sola vez.
     */
    public String issueTwoFactorChallenge(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(properties.challengeTokenTtl()))
                .subject(user.getUsername())
                .id(UUID.randomUUID().toString())
                .claim(PURPOSE_CLAIM, PURPOSE_TWO_FACTOR)
                .build();
        return encode(claims);
    }

    /** Lee el desafío; rechaza cualquier token que no sea un desafío válido. */
    public TwoFactorChallenge readTwoFactorChallenge(String challengeToken) {
        try {
            Jwt jwt = decoder.decode(challengeToken);
            if (!PURPOSE_TWO_FACTOR.equals(jwt.getClaimAsString(PURPOSE_CLAIM)) || jwt.getId() == null) {
                throw ApiException.unauthorized("invalid_challenge", "El desafío de segundo factor no es válido.");
            }
            return new TwoFactorChallenge(jwt.getSubject(), jwt.getId());
        } catch (JwtException ex) {
            throw ApiException.unauthorized("invalid_challenge", "El desafío de segundo factor no es válido.");
        }
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
