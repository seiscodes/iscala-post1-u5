package com.universidad.reservaslabs.exception;

/**
 * La reserva es inválida por sí misma, sin necesidad de compararla con otros datos:
 * rango de fechas incoherente, duración fuera de 30 min – 3 h, fuera del horario
 * de atención o sin laboratorio. → 400
 *
 * Se separa de {@link ReservaConflictException} porque el enunciado exige 400 para
 * estos casos y 409 para el solapamiento: son errores de naturaleza distinta.
 */
public class ReservaInvalidaException extends RuntimeException {
    public ReservaInvalidaException(String mensaje) { super(mensaje); }
}
