package ar.com.planillero.planning;

import java.time.Duration;
import java.time.Instant;

/**
 * Calcula el desfase entre el reloj del dispositivo y el del servidor.
 *
 * <p>Es una clase pura, sin estado ni dependencias, para poder probar los bordes sin levantar Spring.
 */
public final class DriftCalculator {

    private DriftCalculator() {
    }

    /**
     * Desfase en segundos: hora del servidor menos hora del dispositivo.
     *
     * <p>Es positivo cuando el dispositivo está atrasado y negativo cuando está adelantado. Se redondea
     * al segundo más cercano (y no se trunca) para que un desfase de -0.6 s y uno de -0.4 s no den el
     * mismo valor.
     *
     * @throws IllegalArgumentException si alguna de las horas está tan lejos que el cálculo no entra
     *                                  en un {@code long}; un reloj así de roto no es una hora real
     */
    public static long driftSeconds(Instant deviceTime, Instant serverTime) {
        try {
            long millis = Duration.between(deviceTime, serverTime).toMillis();
            return Math.round(millis / 1000.0);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("La hora del dispositivo está fuera de rango.", e);
        }
    }
}
