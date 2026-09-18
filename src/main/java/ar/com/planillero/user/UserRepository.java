package ar.com.planillero.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los usuarios persistidos. */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    /** Todos los usuarios con un rol dado (por ejemplo, los operadores). */
    List<User> findByRoles_Name(RoleName role);
}
