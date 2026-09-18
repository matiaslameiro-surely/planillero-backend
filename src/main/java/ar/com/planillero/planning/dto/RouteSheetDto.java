package ar.com.planillero.planning.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Hoja de ruta de un operador para una fecha, ordenada por posición. */
public record RouteSheetDto(
        UUID operatorId,
        String operatorUsername,
        LocalDate date,
        List<RouteSheetItemDto> items) {
}