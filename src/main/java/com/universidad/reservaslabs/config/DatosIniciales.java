package com.universidad.reservaslabs.config;

import com.universidad.reservaslabs.model.Laboratorio;
import com.universidad.reservaslabs.repository.LaboratorioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Carga un catálogo mínimo de laboratorios al arrancar (H2 en memoria empieza vacía),
 * para poder probar de inmediato la API y el formulario /reservas/nueva.
 * Usa existsByNombreIgnoreCase para no duplicar un laboratorio ya registrado.
 */
@Component
public class DatosIniciales implements CommandLineRunner {

    private final LaboratorioRepository laboratorios;

    public DatosIniciales(LaboratorioRepository laboratorios) {
        this.laboratorios = laboratorios;
    }

    @Override
    public void run(String... args) {
        List.of(new Laboratorio(null, "Lab. Cómputo 3", "Bloque B, piso 2", 30, "COMPUTO"),
                new Laboratorio(null, "Lab. Redes", "Bloque C, piso 1", 20, "REDES"),
                new Laboratorio(null, "Lab. Multimedia", "Bloque A, piso 3", 25, "MULTIMEDIA"))
            .forEach(lab -> {
                if (!laboratorios.existsByNombreIgnoreCase(lab.getNombre())) {
                    laboratorios.save(lab);
                }
            });
    }
}
