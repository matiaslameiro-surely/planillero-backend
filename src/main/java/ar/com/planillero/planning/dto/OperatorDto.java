package ar.com.planillero.planning.dto;

import java.util.UUID;

/** Operador tal como lo necesita el backoffice para los selectores de la grilla. */
public record OperatorDto(UUID id, String username, String jurisdiction) {
}