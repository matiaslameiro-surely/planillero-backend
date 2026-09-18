package ar.com.planillero.planning.dto;

import java.util.List;

/** Hito de una hoja de ruta: una visita con su posición en el recorrido. */
public record RouteSheetItemDto(int position, VisitDto visit) {
}