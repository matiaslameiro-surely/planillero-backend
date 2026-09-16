package ar.com.planillero.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los roles persistidos. */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
