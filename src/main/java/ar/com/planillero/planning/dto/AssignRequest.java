package ar.com.planillero.planning.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** Pedido de asignación de visitas a un operador para una fecha. */
public record AssignRequest(
        @NotNull UUID operatorId,
        @NotNull LocalDate date,
        @NotEmpty Set<UUID> visitIds) {
}