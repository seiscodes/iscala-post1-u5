package com.universidad.reservaslabs.service;

import com.universidad.reservaslabs.exception.RecursoNoEncontradoException;
import com.universidad.reservaslabs.exception.ReservaConflictException;
import com.universidad.reservaslabs.exception.ReservaInvalidaException;
import com.universidad.reservaslabs.model.EstadoReserva;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.Reserva;
import com.universidad.reservaslabs.repository.LaboratorioRepository;
import com.universidad.reservaslabs.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Reglas de negocio de ReservaService, aisladas de la base de datos con dobles de los repositorios. */
@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock
    private ReservaRepository reservaRepo;

    @Mock
    private LaboratorioRepository laboratorioRepo;

    @InjectMocks
    private ReservaService service;

    private final Laboratorio laboratorio = new Laboratorio(1L, "Lab. Cómputo 3", "Bloque B, piso 2", 30, "COMPUTO");
    private final LocalDate dia = LocalDate.now().plusDays(7);

    private Reserva reserva(LocalDateTime inicio, LocalDateTime fin) {
        Laboratorio referencia = new Laboratorio();
        referencia.setId(1L);
        return new Reserva(null, referencia, "Ana Torres", "ana@udes.edu.co", inicio, fin, "Práctica", null);
    }

    @Test
    void crearReservaLibreLaConfirmaYLaGuarda() {
        when(laboratorioRepo.findById(1L)).thenReturn(Optional.of(laboratorio));
        when(reservaRepo.buscarSolapamientos(eq(1L), any(), any())).thenReturn(List.of());
        when(reservaRepo.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        Reserva creada = service.crear(reserva(dia.atTime(9, 0), dia.atTime(11, 0)));

        assertEquals(EstadoReserva.CONFIRMADA, creada.getEstado());
        assertEquals("Lab. Cómputo 3", creada.getLaboratorio().getNombre());
        verify(reservaRepo).save(creada);
    }

    @Test
    void reservaSolapadaLanzaConflictoYNoSeGuarda() {
        when(laboratorioRepo.findById(1L)).thenReturn(Optional.of(laboratorio));
        when(reservaRepo.buscarSolapamientos(eq(1L), any(), any())).thenReturn(List.of(new Reserva()));

        ReservaConflictException ex = assertThrows(ReservaConflictException.class,
                () -> service.crear(reserva(dia.atTime(10, 0), dia.atTime(12, 0))));

        assertEquals("El laboratorio Lab. Cómputo 3 ya tiene una reserva en ese horario", ex.getMessage());
        verify(reservaRepo, never()).save(any());
    }

    @Test
    void fueraDelHorarioDeAtencionEsInvalidaSinConsultarLaBase() {
        assertThrows(ReservaInvalidaException.class,
                () -> service.crear(reserva(dia.atTime(22, 0), dia.atTime(23, 0))));
        verifyNoInteractions(reservaRepo, laboratorioRepo);
    }

    @Test
    void duracionMenorA30MinutosEsInvalida() {
        assertThrows(ReservaInvalidaException.class,
                () -> service.crear(reserva(dia.atTime(9, 0), dia.atTime(9, 20))));
        verifyNoInteractions(reservaRepo, laboratorioRepo);
    }

    @Test
    void duracionMayorA3HorasEsInvalida() {
        assertThrows(ReservaInvalidaException.class,
                () -> service.crear(reserva(dia.atTime(9, 0), dia.atTime(12, 30))));
        verifyNoInteractions(reservaRepo, laboratorioRepo);
    }

    @Test
    void finAnteriorAlInicioEsInvalido() {
        assertThrows(ReservaInvalidaException.class,
                () -> service.crear(reserva(dia.atTime(11, 0), dia.atTime(9, 0))));
    }

    @Test
    void reservaSinLaboratorioEsInvalida() {
        Reserva sinLaboratorio = reserva(dia.atTime(9, 0), dia.atTime(10, 0));
        sinLaboratorio.setLaboratorio(null);
        assertThrows(ReservaInvalidaException.class, () -> service.crear(sinLaboratorio));
    }

    @Test
    void laboratorioInexistenteLanzaNoEncontradoSinBuscarSolapamientos() {
        when(laboratorioRepo.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.crear(reserva(dia.atTime(9, 0), dia.atTime(10, 0))));
        verify(reservaRepo, never()).buscarSolapamientos(any(), any(), any());
    }

    @Test
    void cancelarReservaFuturaLaMarcaCancelada() {
        Reserva existente = reserva(dia.atTime(9, 0), dia.atTime(10, 0));
        existente.setId(5L);
        existente.setEstado(EstadoReserva.CONFIRMADA);
        when(reservaRepo.findById(5L)).thenReturn(Optional.of(existente));

        service.cancelar(5L);

        assertEquals(EstadoReserva.CANCELADA, existente.getEstado());
        verify(reservaRepo).save(existente);
    }

    @Test
    void cancelarReservaQueYaEmpezoEsConflicto() {
        LocalDate ayer = LocalDate.now().minusDays(1);
        Reserva pasada = reserva(ayer.atTime(9, 0), ayer.atTime(10, 0));
        pasada.setEstado(EstadoReserva.CONFIRMADA);
        when(reservaRepo.findById(6L)).thenReturn(Optional.of(pasada));

        assertThrows(ReservaConflictException.class, () -> service.cancelar(6L));
        verify(reservaRepo, never()).save(any());
    }

    @Test
    void cancelarReservaInexistenteLanzaNoEncontrado() {
        when(reservaRepo.findById(7L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> service.cancelar(7L));
    }
}
