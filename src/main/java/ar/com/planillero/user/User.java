package ar.com.planillero.user;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * Usuario del sistema.
 *
 * <p>La contraseña nunca se guarda en texto plano: {@code passwordHash} contiene un hash bcrypt. El
 * secreto de TOTP, cuando existe, se guarda sólo una vez habilitado el segundo factor.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    @Column(name = "two_factor_secret")
    private String twoFactorSecret;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Zona en la que opera el usuario; base del control de acceso horizontal (OWASP A01). */
    @Column(nullable = false)
    private String jurisdiction;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected User() {
        // Requerido por JPA.
    }

    public User(String username, String passwordHash, Set<Role> roles) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = new HashSet<>(roles);
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public String getTwoFactorSecret() {
        return twoFactorSecret;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    /**
     * Guarda un secreto de TOTP pendiente de confirmación.
     *
     * <p>Queda almacenado pero sin efecto hasta que el usuario pruebe un código válido: recién
     * {@link #enableTwoFactor()} lo activa.
     */
    public void setPendingTwoFactorSecret(String secret) {
        this.twoFactorSecret = secret;
    }

    /** Enciende el segundo factor. Requiere que haya un secreto pendiente cargado. */
    public void enableTwoFactor() {
        if (this.twoFactorSecret == null) {
            throw new IllegalStateException("No hay secreto de TOTP pendiente para habilitar");
        }
        this.twoFactorEnabled = true;
    }

    /** Apaga el segundo factor y descarta el secreto. */
    public void disableTwoFactor() {
        this.twoFactorSecret = null;
        this.twoFactorEnabled = false;
    }
}
