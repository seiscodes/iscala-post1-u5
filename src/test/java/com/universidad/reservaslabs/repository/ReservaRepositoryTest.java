package com.universidad.reservaslabs.repository;

import com.universidad.reservaslabs.model.EstadoReserva;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.Reserva;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** La consulta JPQL buscarSolapamientos, ejecutada contra H2 real. */
@DataJpaTest
class ReservaRepositoryTest {

    @Autowired
    private ReservaRepository reservas;

    @Autowired
    private LaboratorioRepository laboratorios;

    private Laboratorio lab;
    private Laboratorio otroLab;
    private final LocalDate dia = LocalDate.now().plusDays(3);

    @BeforeEach
    void preparar() {
        lab = laboratorios.save(new Laboratorio(null, "Lab. Pruebas", "Bloque Z", 10, "COMPUTO"));
        otroLab = laboratorios.save(new Laboratorio(null, "Lab. Otro", "Bloque Y", 10, "REDES"));
        // Reserva existente: 09:00 – 11:00
        reservas.save(new Reserva(null, lab, "Ana", "ana@udes.edu.co",
                dia.atTime(9, 0), dia.atTime(11, 0), "Práctica", EstadoReserva.CONFIRMADA));
    }

    @Test
    void detectaSolapamientoParcial() {
        assertEquals(1, reservas.buscarSolapamientos(lab.getId(), dia.atTime(10, 0), dia.atTime(12, 0)).size());
    }

    @Test
    void detectaRangoContenidoDentroDeOtro() {
        assertEquals(1, reservas.buscarSolapamientos(lab.getId(), dia.atTime(9, 30), dia.atTime(10, 30)).size());
    }

    @Test
    void rangosContiguosNoSeSolapan() {
        assertTrue(reservas.buscarSolapamientos(lab.getId(), dia.atTime(11, 0), dia.atTime(12, 0)).isEmpty());
        assertTrue(reservas.buscarSolapamientos(lab.getId(), dia.atTime(8, 0), dia.atTime(9, 0)).isEmpty());
    }

    @Test
    void lasReservasCanceladasNoBloqueanElHorario() {
        reservas.save(new Reserva(null, lab, "Luis", "luis@udes.edu.co",
                dia.atTime(14, 0), dia.atTime(15, 0), "Proyecto", EstadoReserva.CANCELADA));
        assertTrue(reservas.buscarSolapamientos(lab.getId(), dia.atTime(14, 0), dia.atTime(15, 0)).isEmpty());
    }

    @Test
    void otroLaboratorioNoGeneraSolapamiento() {
        assertTrue(reservas.buscarSolapamientos(otroLab.getId(), dia.atTime(9, 0), dia.atTime(11, 0)).isEmpty());
    }
}
