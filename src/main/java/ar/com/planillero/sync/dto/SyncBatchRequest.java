package ar.com.planillero.sync.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Lo que el dispositivo vacía de su cola cuando vuelve la conexión.
 *
 * <p>El tope de operaciones por lote existe para que un dispositivo que estuvo una semana sin señal
 * no mande un pedido enorme que expire a mitad de camino: el cliente manda varios lotes y cada uno
 * confirma por separado.
 *
 * @param operations operaciones a aplicar, en el orden en que el operador las hizo
 */
public record SyncBatchRequest(
        @NotEmpty(message = "el lote no puede venir vacío")
        @Size(max = 100, message = "no puede traer más de 100 operaciones por lote")
        @Valid List<SyncOperationRequest> operations) {
}
