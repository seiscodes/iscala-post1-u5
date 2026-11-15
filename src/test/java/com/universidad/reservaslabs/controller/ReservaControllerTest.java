package com.universidad.reservaslabs.controller;

import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.repository.LaboratorioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Endpoints REST de la Parte 1 contra el contexto completo y H2 (cada prueba hace rollback). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LaboratorioRepository laboratorios;

    private Laboratorio lab;
    private final LocalDate dia = LocalDate.now().plusDays(10);

    @BeforeEach
    void preparar() {
        lab = laboratorios.save(new Laboratorio(null, "Lab. API", "Bloque B, piso 2", 30, "COMPUTO"));
    }

    private String json(long laboratorioId, String nombre, String inicio, String fin) {
        return """
            {"laboratorio":{"id":%d},"nombreSolicitante":"%s","correoSolicitante":"ana@udes.edu.co",
             "inicio":"%sT%s:00","fin":"%sT%s:00","motivo":"Práctica"}
            """.formatted(laboratorioId, nombre, dia, inicio, dia, fin);
    }

    private MvcResult crear(String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andReturn();
    }

    @Test
    void reservaEnHorarioLibreResponde201YQuedaConfirmada() throws Exception {
        mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(json(lab.getId(), "Ana Torres", "09:00", "11:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.laboratorio.nombre").value("Lab. API"));
    }

    @Test
    void reservaSolapadaResponde409ConMensajeDeNegocio() throws Exception {
        crear(json(lab.getId(), "Ana Torres", "09:00", "11:00"));

        mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(json(lab.getId(), "Luis Gómez", "10:00", "12:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("El laboratorio Lab. API ya tiene una reserva en ese horario"));
    }

    @Test
    void reservaFueraDelHorarioDeAtencionResponde400() throws Exception {
        mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(json(lab.getId(), "Carlos Ruiz", "22:00", "23:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La reserva debe estar dentro del horario de atención (07:00 - 21:00)"));
    }

    @Test
    void reservaConDuracionInvalidaResponde400() throws Exception {
        mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(json(lab.getId(), "Carlos Ruiz", "09:00", "13:00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void datosDeEntradaInvalidosResponden400ConLaListaDeErrores() throws Exception {
        mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(json(lab.getId(), "", "09:00", "10:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores").isArray());
    }

    @Test
    void laboratorioInexistenteResponde404() throws Exception {
        mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(json(999_999L, "Ana Torres", "09:00", "10:00")))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelarReservaFuturaResponde204() throws Exception {
        MvcResult creada = crear(json(lab.getId(), "Ana Torres", "15:00", "16:00"));
        Number id = com.jayway.jsonpath.JsonPath.read(creada.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/api/reservas/{id}", id.longValue()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/reservas/{id}", id.longValue()))
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void elCatalogoDeLaboratoriosIncluyeLosDatosIniciales() throws Exception {
        mockMvc.perform(get("/api/laboratorios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(4)));
    }
}
