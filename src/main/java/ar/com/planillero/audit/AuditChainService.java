package ar.com.planillero.audit;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.planillero.evidence.crypto.CryptoService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/**
 * Arma y encadena las filas de {@code audit.audit_logs}, y verifica la integridad de la cadena.
 *
 * <p>La cadena es única y global: cada fila nueva encadena contra la última fila de la tabla, sin
 * importar a qué entidad pertenezca. Eso es lo que hace que borrar o insertar una fila en cualquier
 * punto de la historia sea detectable.
 */
@Service
public class AuditChainService {

    /** Primer eslabón: no hay fila anterior de la cual encadenar. */
    static final String GENESIS_HASH = "0".repeat(64);

    private final AuditLogRepository repository;
    private final CryptoService cryptoService;
    private final AuditMasker masker;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ObjectWriter canonicalWriter;

    public AuditChainService(AuditLogRepository repository, CryptoService cryptoService, AuditMasker masker,
            Clock clock, ObjectMapper objectMapper) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.masker = masker;
        this.clock = clock;
        this.objectMapper = objectMapper;
        // ORDER_MAP_ENTRIES_BY_KEYS ordena alfabéticamente (recursivamente) los Map que se
        // serializan; no reordena un árbol JsonNode ya armado. Por eso el payload pasa por un Map
        // antes de escribirse: es lo que garantiza que el mismo delta siempre produzca el mismo
        // texto, sin importar en qué orden Java haya insertado sus campos.
        this.canonicalWriter = objectMapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /**
     * Arma la fila del evento, la encadena contra la última existente y la persiste.
     *
     * @param rawPayload lo que se quiere auditar (un Map, un record, cualquier POJO serializable);
     *                    se enmascara y se canonicaliza antes de guardarse.
     */
    @Transactional
    public AuditLogEntry append(String eventType, String entityType, String entityId, String username, String ip,
            String deviceId, Object rawPayload) {
        JsonNode tree = objectMapper.valueToTree(rawPayload);
        JsonNode masked = masker.mask(tree);
        Object canonicalizable = objectMapper.treeToValue(masked, Object.class);
        String payloadJson = canonicalWriter.writeValueAsString(canonicalizable);

        // Bloquea la última fila hasta el commit: serializa el encadenamiento entre escrituras
        // concurrentes (ver AuditLogRepository.findFirstByOrderByCreatedAtDesc).
        String previousHash = repository.findFirstByOrderByCreatedAtDesc()
                .map(AuditLogEntry::getCurrentHash)
                .orElse(GENESIS_HASH);

        UUID id = UUID.randomUUID();
        // Truncado a microsegundos porque timestamptz de PostgreSQL no guarda más precisión que
        // esa: si se hashea con nanosegundos y se relee con microsegundos, la verificación
        // reportaría una alteración que nunca ocurrió.
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String currentHash = computeHash(id, createdAt, payloadJson, previousHash);

        AuditLogEntry entry = new AuditLogEntry(id, eventType, entityType, entityId, username, ip, deviceId,
                payloadJson, previousHash, currentHash, createdAt);
        return repository.save(entry);
    }

    /**
     * Verifica la cadena completa, o el segmento de una entidad puntual.
     *
     * <p>Con {@code entityId == null} se verifica también que cada fila encadene con la anterior
     * (la cadena es global). Con un {@code entityId}, sólo se puede verificar que cada fila de esa
     * entidad no fue alterada individualmente (su {@code hash_actual} recalcula igual a partir de
     * su propio {@code hash_previo} guardado); no se puede afirmar que no falte una fila intermedia
     * de otra entidad, porque el filtro por entidad rompe la adyacencia real de la cadena.
     */
    @Transactional(readOnly = true)
    public ChainVerificationResult verify(String entityId) {
        List<AuditLogEntry> chain = repository.findChain(entityId);
        boolean checkAdjacency = entityId == null;
        String expectedPrevious = GENESIS_HASH;

        for (AuditLogEntry row : chain) {
            String recomputed = computeHash(row.getId(), row.getCreatedAt(), row.getPayload(), row.getPreviousHash());
            if (!recomputed.equals(row.getCurrentHash())) {
                return ChainVerificationResult.broken(row.getId(),
                        "El hash almacenado no coincide con el recalculado: la fila fue alterada.");
            }
            if (checkAdjacency && !row.getPreviousHash().equals(expectedPrevious)) {
                return ChainVerificationResult.broken(row.getId(),
                        "El eslabón no encadena con el anterior: falta una fila o se insertó una fuera de "
                                + "orden.");
            }
            expectedPrevious = row.getCurrentHash();
        }
        return ChainVerificationResult.intact();
    }

    private String computeHash(UUID id, Instant createdAt, String payloadJson, String previousHash) {
        // Delimitado con '|' para que no haya ambigüedad de concatenación (por ejemplo, id="a" +
        // timestamp="bc" no puede confundirse con id="ab" + timestamp="c").
        String material = id + "|" + createdAt + "|" + payloadJson + "|" + previousHash;
        return cryptoService.calculateSha256(material.getBytes(StandardCharsets.UTF_8));
    }

    /** Resultado de {@link #verify}. El campo se llama {@code ok} para no chocar con la factory {@link #intact()}. */
    public record ChainVerificationResult(boolean ok, Optional<UUID> brokenAt, Optional<String> reason) {

        public static ChainVerificationResult intact() {
            return new ChainVerificationResult(true, Optional.empty(), Optional.empty());
        }

        public static ChainVerificationResult broken(UUID brokenAt, String reason) {
            return new ChainVerificationResult(false, Optional.of(brokenAt), Optional.of(reason));
        }
    }
}
