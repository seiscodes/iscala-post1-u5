package com.universidad.reservaslabs.web;

import com.jayway.jsonpath.JsonPath;
import com.universidad.reservaslabs.controller.ReservaController;
import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.repository.LaboratorioRepository;
import com.universidad.reservaslabs.service.ReservaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Puntos de decisión 3 y 4: un solo Service y el mismo mensaje de negocio en ambas superficies. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsistenciaMvcRestTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private ReservaController reservaController;

    @Autowired
    private ReservaWebController reservaWebController;

    @Autowired
    private LaboratorioRepository laboratorios;

    @Test
    void ambosControladoresInyectanLaMismaInstanciaDeReservaService() {
        Object deRest = ReflectionTestUtils.getField(reservaController, "service");
        Object deWeb = ReflectionTestUtils.getField(reservaWebController, "service");

        assertSame(reservaService, deRest);
        assertSame(deRest, deWeb);
    }

    @Test
    void unaReservaSolapadaProduceElMismoMensajeEnRestYEnMvc() throws Exception {
        Laboratorio lab = laboratorios.save(new Laboratorio(null, "Lab. Consistencia", "Bloque D", 20, "REDES"));
        LocalDate dia = LocalDate.now().plusDays(15);
        String json = """
            {"laboratorio":{"id":%d},"nombreSolicitante":"%s","correoSolicitante":"ana@udes.edu.co",
             "inicio":"%sT%s:00","fin":"%sT%s:00","motivo":"Práctica"}
            """;

        mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON)
                .content(json.formatted(lab.getId(), "Ana Torres", dia, "09:00", dia, "11:00")));

        MvcResult rest = mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON)
                .content(json.formatted(lab.getId(), "Luis Gómez", dia, "10:00", dia, "12:00"))).andReturn();

        MvcResult web = mockMvc.perform(post("/reservas")
                .param("laboratorio.id", String.valueOf(lab.getId()))
                .param("nombreSolicitante", "Luis Gómez")
                .param("correoSolicitante", "luis@udes.edu.co")
                .param("inicio", dia + "T10:00")
                .param("fin", dia + "T12:00")).andReturn();

        String mensajeRest = JsonPath.read(rest.getResponse().getContentAsString(), "$.error");
        Object mensajeWeb = web.getFlashMap().get("error");

        assertEquals(409, rest.getResponse().getStatus());
        assertEquals("/reservas/nueva", web.getResponse().getRedirectedUrl());
        assertEquals(mensajeRest, mensajeWeb);
    }
}
