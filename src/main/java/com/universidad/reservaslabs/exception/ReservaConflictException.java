package com.universidad.reservaslabs.exception;

/** La reserva choca con el estado actual de los datos (horario ocupado, cancelación tardía). → 409 */
public class ReservaConflictException extends RuntimeException {
    public ReservaConflictException(String mensaje) { super(mensaje); }
}
