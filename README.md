# Post-contenido — Unidad 5: Integración en Aplicaciones Web

Sistema de reserva de laboratorios de cómputo (`reservas-labs-api`).

> Parte 1 completada: API REST en capas (Entity, Repository, Service, Controller) sobre H2.
> La Parte 2 (vista Thymeleaf) se documentará al finalizar.

## Cómo ejecutar
```
./mvnw clean package      # o: mvn clean package
./mvnw spring-boot:run
./mvnw test
```
API REST: http://localhost:8080/api/reservas · Consola H2: http://localhost:8080/h2-console
(JDBC URL `jdbc:h2:mem:reservas_labs_db`, usuario `sa`, sin contraseña).

## Decisiones de diseño

### Punto de decisión 1 — Ubicación de la validación de solapamiento
`ReservaRepository.buscarSolapamientos` filtra en la base de datos las reservas activas que se
cruzan con el rango pedido. El Repository responde la pregunta de datos; `ReservaService.crear`
toma la decisión de negocio de lanzar `ReservaConflictException` si el resultado no está vacío.
Si un Controller llamara directamente a `buscarSolapamientos()`, tendría que decidir él mismo qué
hacer con el resultado, y la regla terminaría copiada en cada controlador que cree reservas.

### Punto de decisión 2 — Reglas con y sin apoyo del Repository
El horario de atención (07:00–21:00) y la duración (30 min–3 h) dependen solo de la reserva que se
está creando. Por eso `validarHorarioYDuracion` es Java puro dentro del Service, se ejecuta
**antes** de cualquier consulta y lanza `ReservaInvalidaException` (400). El criterio: si la regla
compara contra datos que solo la base conoce, se apoya en una consulta del Repository; si solo
depende del propio objeto, no se involucra a la base.
