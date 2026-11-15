# Post-contenido — Unidad 5: Integración en Aplicaciones Web

**Estudiante:** Sebastián Iscala Jurado — 1152435
**Curso:** Patrones de Diseño de Software · Universidad Francisco de Paula Santander

## Descripción
Repositorio del post-contenido de la Unidad 5 de Patrones de Diseño de Software. Es un único
proyecto Spring Boot (`reservas-labs-api`) para reservar laboratorios de cómputo, con dos partes:

1. Una API REST en capas (Entity, Repository, Service y Controller) sobre una base H2 embebida.
2. Una vista Thymeleaf (MVC clásico) que reutiliza exactamente el mismo `ReservaService`.

```
           ┌───────────────────────┐      ┌──────────────────────────┐
 JSON ───▶ │ ReservaController     │      │ ReservaWebController     │ ◀─── navegador (HTML)
           │ @RestController       │      │ @Controller + Thymeleaf  │
           └──────────┬────────────┘      └────────────┬─────────────┘
                      │   misma instancia (bean)       │
                      └──────────────┬─────────────────┘
                                     ▼
                        ┌──────────────────────────┐
                        │ ReservaService           │  reglas de negocio
                        └────────────┬─────────────┘
                                     ▼
                 ┌────────────────────────────────────────┐
                 │ ReservaRepository / LaboratorioRepository │  Spring Data JPA → H2
                 └────────────────────────────────────────┘
```

## Parte 1 — Repository, Service y Controller REST
- `LaboratorioRepository` y `ReservaRepository` extienden `JpaRepository`. `ReservaRepository` agrega la consulta JPQL `buscarSolapamientos`, que detecta los cruces de horario.
- `ReservaService` concentra las reglas de negocio: solapamiento, horario de atención, duración, cancelación tardía y doble cancelación.
- `ReservaController` y `LaboratorioController` exponen `/api/reservas` y `/api/laboratorios`.
- `GlobalRestExceptionHandler` traduce las excepciones de dominio a JSON con su código de estado.

Al arrancar, `config/DatosIniciales` registra tres laboratorios de ejemplo (H2 empieza vacía). Usa
`existsByNombreIgnoreCase` para no duplicarlos.

| Método | Ruta | Respuesta |
|---|---|---|
| `GET` | `/api/laboratorios` · `/api/laboratorios/{id}` | 200 / 404 |
| `POST` | `/api/laboratorios` | 201 / 400 |
| `GET` | `/api/reservas` · `/api/reservas/{id}` · `/api/reservas/laboratorio/{id}` | 200 / 404 |
| `POST` | `/api/reservas` | 201 · **409** si el horario se solapa · **400** fuera del horario, duración inválida o datos inválidos · 404 si el laboratorio no existe |
| `DELETE` | `/api/reservas/{id}` | 204 · 409 si la reserva ya empezó o ya estaba cancelada · 404 |

## Parte 2 — Vista MVC con Thymeleaf
- `ReservaWebController` (`@Controller`) expone `/reservas` (listado) y `/reservas/nueva` (formulario), y permite cancelar desde el listado. Inyecta la **misma** instancia de `ReservaService` que usa la API REST, sin un Service duplicado.
- `ReservaWebExceptionHandler` maneja las mismas excepciones de dominio que `GlobalRestExceptionHandler`, pero con otra presentación: redirige con un mensaje legible en lugar de devolver JSON.
- Las plantillas están en `src/main/resources/templates/reservas/`.

## Cómo ejecutar
Requisitos: Java 17 o superior. Maven es opcional, porque el proyecto incluye el Maven Wrapper.
```
./mvnw clean package          # Windows: mvnw.cmd clean package
./mvnw spring-boot:run
./mvnw test
```
- API REST: http://localhost:8080/api/reservas
- Vista MVC: http://localhost:8080/reservas
- Consola H2: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:reservas_labs_db`, usuario `sa`, sin contraseña)

Pruebas rápidas con curl (el laboratorio 1 existe gracias a los datos iniciales):
```
# 201 — reserva válida
curl -i -X POST http://localhost:8080/api/reservas -H "Content-Type: application/json" \
  -d '{"laboratorio":{"id":1},"nombreSolicitante":"Ana Torres","correoSolicitante":"ana@udes.edu.co","inicio":"2026-12-10T09:00:00","fin":"2026-12-10T11:00:00","motivo":"Práctica de Bases de Datos"}'

# 409 — mismo laboratorio, horario solapado
curl -i -X POST http://localhost:8080/api/reservas -H "Content-Type: application/json" \
  -d '{"laboratorio":{"id":1},"nombreSolicitante":"Luis Gómez","correoSolicitante":"luis@udes.edu.co","inicio":"2026-12-10T10:00:00","fin":"2026-12-10T12:00:00","motivo":"Proyecto de Redes"}'

# 400 — fuera del horario de atención
curl -i -X POST http://localhost:8080/api/reservas -H "Content-Type: application/json" \
  -d '{"laboratorio":{"id":1},"nombreSolicitante":"Carlos Ruiz","correoSolicitante":"carlos@udes.edu.co","inicio":"2026-12-10T22:00:00","fin":"2026-12-10T23:00:00","motivo":"Estudio nocturno"}'
```

## Decisiones de diseño

### Punto de decisión 1 — Ubicación de la validación de solapamiento
La regla se reparte en dos preguntas distintas, y cada capa responde la suya:
- **Pregunta de datos: ¿qué reservas activas se cruzan con este rango?** La responde `ReservaRepository.buscarSolapamientos` en la base de datos, con la condición `inicio < :fin AND fin > :inicio` y excluyendo las canceladas. Filtrar en el motor evita traer a memoria todas las reservas históricas del laboratorio, que crecen sin límite. Los rangos contiguos (una termina 11:00 y otra empieza 11:00) no se consideran solapados (`ReservaRepositoryTest`).
- **Pregunta de negocio: ¿se permite crear esta reserva?** La responde exclusivamente `ReservaService.crear`, que lanza `ReservaConflictException` con un mensaje claro si la lista no está vacía. El Repository nunca lanza excepciones de negocio.

**Qué pasaría si el Controller llamara directamente a `buscarSolapamientos()`:** el controlador tendría que decidir qué hacer con el resultado. Esa decisión se duplicaría en `ReservaController` y en `ReservaWebController`, y el día que la regla cambie (por ejemplo, para dejar 10 minutos de limpieza entre reservas) habría que corregirla en cada controlador.

### Punto de decisión 2 — Reglas con y sin apoyo del Repository
El horario de atención (07:00–21:00, en el mismo día) y la duración (30 min–3 h) dependen solo de
los campos de la reserva que se está creando. Por eso `validarHorarioYDuracion` es Java puro dentro
del Service y se ejecuta **antes** de cualquier consulta: una reserva inválida se rechaza sin tocar
la base. `ReservaServiceTest` lo verifica con `verifyNoInteractions` sobre los dos repositorios.

**Criterio general:** si la regla necesita comparar contra datos que solo la base conoce (otras
reservas existentes), se apoya en una consulta del Repository. Si solo depende del propio objeto,
no hay razón para involucrar al Repository ni a la base.

**Ajuste sobre el código de la guía.** La guía lanzaba `ReservaConflictException` también para el
horario y la duración, lo que habría producido 409 donde el checkpoint exige 400. Se agregó
`ReservaInvalidaException` (400) para las reglas que dependen solo de la propia reserva, y
`ReservaConflictException` (409) quedó para los conflictos con datos existentes. Así, el tipo de
excepción expresa la diferencia entre ambos tipos de regla.

**Excepción intencional — `LaboratorioController`.** El catálogo de laboratorios es un CRUD sin
reglas propias. Un `LaboratorioService` que solo delegara en el Repository sería un Service
anémico, así que la capa de servicio se introduce solo donde hay una regla que la justifica
(`ReservaService`).

### Punto de decisión 3 — Cómo comparten Service el Controller MVC y el REST
Ambos controladores reciben por constructor la **misma clase** `ReservaService`, que Spring
gestiona como un único bean singleton:
- `controller/ReservaController.java`: `public ReservaController(ReservaService service)`
- `web/ReservaWebController.java`: `public ReservaWebController(ReservaService service, LaboratorioRepository laboratorioRepo)`

Ninguno de los dos reimplementa la validación de solapamiento ni la de horario.
`ConsistenciaMvcRestTest.ambosControladoresInyectanLaMismaInstanciaDeReservaService` comprueba
con `assertSame` que se trata del mismo objeto.

**Alternativa descartada:** copiar la validación en `ReservaWebController` o crear un
`ReservaWebService` casi idéntico. Corregir la regla de solapamiento en el futuro exigiría
cambiarla en dos lugares, y basta olvidar uno para que el navegador y la API acepten reservas
distintas: justo el problema que la capa de servicio existe para evitar.

El controlador MVC solo agrega lo que es propio de la presentación HTML: `@Valid` con
`BindingResult` para volver a mostrar el formulario con el error junto a cada campo cuando hay un
problema de formato.

### Punto de decisión 4 — Manejo de errores consistente entre MVC y REST
Se usan dos manejadores, cada uno restringido a su superficie:
- `GlobalRestExceptionHandler`, con `@RestControllerAdvice(annotations = RestController.class)`, responde JSON con código de estado: 404, 409 o 400.
- `ReservaWebExceptionHandler`, con `@ControllerAdvice(assignableTypes = ReservaWebController.class)`, redirige con un *flash attribute* `error`. Un conflicto al crear vuelve a `/reservas/nueva`; un conflicto al cancelar vuelve a `/reservas`.

**Por qué no un único `@RestControllerAdvice` global:** ese manejador siempre serializa a JSON, y
una página Thymeleaf necesita una redirección con un mensaje legible. Un manejador único que
detectara el origen de la petición (por ejemplo, inspeccionando el encabezado `Accept`) añadiría
un `if` por cada excepción y mezclaría dos presentaciones en una sola clase. Con dos manejadores
se mantiene la misma separación del resto del proyecto: una clase por superficie de presentación.

Ambos manejadores parten del mismo vocabulario de excepciones de dominio (`ReservaConflictException`,
`ReservaInvalidaException` y `RecursoNoEncontradoException`) y muestran exactamente el mismo
mensaje. `ConsistenciaMvcRestTest.unaReservaSolapadaProduceElMismoMensajeEnRestYEnMvc` lo verifica:
la API responde 409 con `{"error": "El laboratorio Lab. Consistencia ya tiene una reserva en ese horario"}`
y la vista redirige a `/reservas/nueva` con ese mismo texto.

## Evidencia (capturas de pantalla)
Las capturas están en `docs/capturas/`:

| Archivo | Qué muestra |
|---|---|
| `01-api-reserva-201.png` | `POST /api/reservas` válido → 201 Created |
| `02-api-solapamiento-409.png` | `POST /api/reservas` solapado → 409 con el mensaje de negocio |
| `03-api-horario-400.png` | `POST /api/reservas` fuera de horario → 400 |
| `04-web-lista.png` | `http://localhost:8080/reservas` con reservas creadas |
| `05-web-nueva.png` | `http://localhost:8080/reservas/nueva` con el combo de laboratorios |
| `06-web-solapamiento.png` | Reserva solapada desde el formulario: mismo mensaje que la API |
| `07-h2-console.png` | Consola H2 con las tablas `laboratorios` y `reservas` |

## Pruebas
`./mvnw test` ejecuta:

| Clase | Qué verifica |
|---|---|
| `ReservaServiceTest` | Reglas del Service con Mockito: confirma, conflicto sin guardar, horario y duración sin tocar la base, laboratorio inexistente, cancelaciones |
| `ReservaRepositoryTest` | `buscarSolapamientos` contra H2: solapamiento parcial, rango contenido, rangos contiguos, canceladas, otro laboratorio |
| `ReservaControllerTest` | API REST: 201, 409, 400 (horario, duración y validación), 404, 204 y catálogo inicial |
| `ReservaWebControllerTest` | Vista MVC: listado, formulario, creación, errores de negocio y de campos, cancelaciones |
| `ConsistenciaMvcRestTest` | Mismo bean `ReservaService` en ambos controladores y mismo mensaje de error en REST y MVC |
| `ReservasLabsApiApplicationTests` | El contexto de Spring arranca |

## Herramientas utilizadas
- Java 17, Spring Boot 3.2, Spring Data JPA, H2, Thymeleaf, Lombok, Bean Validation
- Apache Maven (Maven Wrapper), JUnit 5, Mockito, MockMvc, curl/Postman, Git, GitHub

## Conclusiones
Lo más difícil fue separar la regla de solapamiento en dos preguntas. Saber qué reservas se cruzan
es un problema de datos y su lugar natural es una consulta del Repository; decidir si eso impide
crear la reserva es un problema de negocio y pertenece al Service. Esa misma distinción explicó por
qué el horario y la duración no necesitan la base de datos, y llevó a separar sus excepciones (400
frente a 409). La Parte 2 mostró el valor real de la capa de servicio: agregar una interfaz
completamente distinta (HTML en lugar de JSON) no exigió copiar ni una sola regla, solo un
controlador y un manejador de errores propios de esa presentación. También quedó claro que la
arquitectura en capas no debe aplicarse de forma mecánica: `LaboratorioController` usa el
Repository directamente porque un Service sin reglas no habría aportado nada.
