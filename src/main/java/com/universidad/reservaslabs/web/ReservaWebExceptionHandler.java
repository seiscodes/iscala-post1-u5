package com.universidad.reservaslabs.web;

import com.universidad.reservaslabs.exception.RecursoNoEncontradoException;
import com.universidad.reservaslabs.exception.ReservaConflictException;
import com.universidad.reservaslabs.exception.ReservaInvalidaException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Restringido con assignableTypes a ReservaWebController: no compite con
// GlobalRestExceptionHandler, que ya está restringido a los @RestController
// de la Parte 1. Mismas excepciones de dominio, presentación distinta:
// un mensaje legible tras una redirección, en lugar de JSON con código de estado.
@ControllerAdvice(assignableTypes = ReservaWebController.class)
public class ReservaWebExceptionHandler {

    @ExceptionHandler(ReservaConflictException.class)
    public String conflicto(ReservaConflictException ex, HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", ex.getMessage());
        // Un conflicto al cancelar se informa en el listado; uno al crear, en el formulario.
        return esCancelacion(request) ? "redirect:/reservas" : "redirect:/reservas/nueva";
    }

    @ExceptionHandler(ReservaInvalidaException.class)
    public String invalida(ReservaInvalidaException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", ex.getMessage());
        return "redirect:/reservas/nueva";
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public String noEncontrado(RecursoNoEncontradoException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", ex.getMessage());
        return "redirect:/reservas";
    }

    private boolean esCancelacion(HttpServletRequest request) {
        return request.getRequestURI().endsWith("/cancelar");
    }
}
