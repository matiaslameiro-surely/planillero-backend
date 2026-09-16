package ar.com.planillero.user;

/**
 * Roles de la aplicación.
 *
 * <p>Este enum es la fuente de los nombres que se guardan en la tabla {@code roles} y de las
 * authorities que viajan en el JWT (con el prefijo {@code ROLE_} que espera Spring Security).
 */
public enum RoleName {

    /** Operario de campo: carga planillas y registra visitas. */
    OPERATOR,

    /** Supervisa el trabajo de los operarios. */
    SUPERVISOR,

    /** Administra usuarios y configuración del sistema. */
    ADMINISTRATOR
}
