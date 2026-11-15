package com.universidad.reservaslabs.web;

import com.universidad.reservaslabs.model.EstadoReserva;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.model.Reserva;
import com.universidad.reservaslabs.repository.LaboratorioRepository;
import com.universidad.reservaslabs.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Vista Thymeleaf: lista, crea y cancela usando el mismo ReservaService que la API REST. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservaWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LaboratorioRepository laboratorios;

    @Autowired
    private ReservaRepository reservas;

    private Laboratorio lab;
    private final LocalDate dia = LocalDate.now().plusDays(12);

    @BeforeEach
    void preparar() {
        lab = laboratorios.save(new Laboratorio(null, "Lab. Web", "Bloque B, piso 2", 30, "COMPUTO"));
    }

    private MockHttpServletRequestBuilder formulario(String nombre, String inicio, String fin) {
        return post("/reservas")
                .param("laboratorio.id", String.valueOf(lab.getId()))
                .param("nombreSolicitante", nombre)
                .param("correoSolicitante", "ana@udes.edu.co")
                .param("inicio", dia + "T" + inicio)
                .param("fin", dia + "T" + fin)
                .param("motivo", "Práctica");
    }

    @Test
    void listarMuestraLaVistaDeReservas() throws Exception {
        mockMvc.perform(get("/reservas"))
                .andExpect(status().isOk())
                .andExpect(view().name("reservas/lista"))
                .andExpect(model().attributeExists("reservas"));
    }

    @Test
    void elFormularioCargaLosLaboratoriosDesdeLaBase() throws Exception {
        mockMvc.perform(get("/reservas/nueva"))
                .andExpect(status().isOk())
                .andExpect(view().name("reservas/nueva"))
                .andExpect(model().attributeExists("reserva", "laboratorios"))
                .andExpect(content().string(containsString("Lab. Web")));
    }

    @Test
    void crearReservaValidaRedirigeAlListadoConMensaje() throws Exception {
        mockMvc.perform(formulario("Ana Torres", "09:00", "11:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reservas"))
                .andExpect(flash().attribute("mensaje", "Reserva creada correctamente"));

        mockMvc.perform(get("/reservas"))
                .andExpect(content().string(containsString("Ana Torres")));
    }

    @Test
    void reservaSolapadaVuelveAlFormularioConElMensajeDeNegocio() throws Exception {
        mockMvc.perform(formulario("Ana Torres", "09:00", "11:00"));

        mockMvc.perform(formulario("Luis Gómez", "10:00", "12:00"))
                .andExpect(redirectedUrl("/reservas/nueva"))
                .andExpect(flash().attribute("error", "El laboratorio Lab. Web ya tiene una reserva en ese horario"));
    }

    @Test
    void reservaFueraDeHorarioVuelveAlFormularioConElMensaje() throws Exception {
        mockMvc.perform(formulario("Carlos Ruiz", "22:00", "23:00"))
                .andExpect(redirectedUrl("/reservas/nueva"))
                .andExpect(flash().attribute("error", "La reserva debe estar dentro del horario de atención (07:00 - 21:00)"));
    }

    @Test
    void camposInvalidosVuelvenAMostrarElFormularioConErrores() throws Exception {
        mockMvc.perform(formulario("", "09:00", "10:00"))
                .andExpect(status().isOk())
                .andExpect(view().name("reservas/nueva"))
                .andExpect(model().attributeHasFieldErrors("reserva", "nombreSolicitante"));
    }

    @Test
    void cancelarReservaFuturaRedirigeConMensaje() throws Exception {
        Reserva futura = reservas.save(new Reserva(null, lab, "Ana", "ana@udes.edu.co",
                dia.atTime(15, 0), dia.atTime(16, 0), "Práctica", EstadoReserva.CONFIRMADA));

        mockMvc.perform(post("/reservas/{id}/cancelar", futura.getId()))
                .andExpect(redirectedUrl("/reservas"))
                .andExpect(flash().attribute("mensaje", "Reserva cancelada correctamente"));
    }

    @Test
    void cancelarReservaQueYaEmpezoMuestraElErrorEnElListado() throws Exception {
        LocalDate ayer = LocalDate.now().minusDays(1);
        Reserva pasada = reservas.save(new Reserva(null, lab, "Ana", "ana@udes.edu.co",
                ayer.atTime(9, 0), ayer.atTime(10, 0), "Práctica", EstadoReserva.CONFIRMADA));

        mockMvc.perform(post("/reservas/{id}/cancelar", pasada.getId()))
                .andExpect(redirectedUrl("/reservas"))
                .andExpect(flash().attribute("error", "No se puede cancelar una reserva cuyo horario de inicio ya pasó"));
    }

    @Test
    void cancelarReservaInexistenteMuestraElErrorEnElListado() throws Exception {
        mockMvc.perform(post("/reservas/{id}/cancelar", 999_999L))
                .andExpect(redirectedUrl("/reservas"))
                .andExpect(flash().attribute("error", "Reserva no encontrada: 999999"));
    }
}
