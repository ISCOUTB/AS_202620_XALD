# Especificación y Evidencia del Contrato de API — Proyecto XALD

Este documento centraliza la especificación técnica, la trazabilidad de la arquitectura y la validación continua (CI/CD) para la API de Sincronización Financiera del proyecto **XALD**.

---

## 1. Descripción General del Servicio

La API de Sincronización Financiera actúa como el canal receptor de las transacciones capturadas en el cliente móvil (vía SMS bancarios, modelo de Inteligencia Artificial o entrada manual). 

- **Tecnología del Backend:** Python 3.11+ con FastAPI.
- **Validación de Datos:** Pydantic (DTOs con validación estricta de tipos).
- **Protocolo y Formato:** HTTP/REST con payload en JSON (UTF-8).
- **Estándar del Contrato:** OpenAPI 3.1.0 (`docs/api/openapi.yaml`).

---

## 2. Definición del Endpoint y Manejo de Respuestas

### Endpoint Principal
`POST /api/v1/transacciones`

### Modelo de Petición (`TransaccionDTO`)
La petición exige la estructura definida en el contrato OpenAPI con los siguientes campos obligatorios:

- `id_transaccion`: Cadena en formato UUIDv4.
- `monto`: Número flotante estrictamente mayor a 0.
- `moneda`: Código ISO 4217 de 3 caracteres (ej. `COP`).
- `comercio`: Nombre del establecimiento comercial.
- `categoria`: Categorización asignada a la transacción.
- `fecha_transaccion`: Timestamp en formato ISO 8601 UTC.
- `origen_datos`: Valor del Enum (`SMS_REGEX`, `AI_GEMINI`, `MANUAL`).

### Respuestas HTTP y Excepciones

| Código HTTP | Significado | Estructura de Respuesta / Causa |
|---|---|---|
| `202 Accepted` | **Éxito:** Transacción recibida y encolada. | Retorna `RespuestaSincronizacion` con `estado: "ACEPTADO"` y mensaje de confirmación. |
| `400 Bad Request` | **Error de Cliente:** Incompatibilidad de contrato. | Formato JSON inválido o incumplimiento de tipos del contrato OpenAPI. |
| `422 Unprocessable Entity` | **Error de Validación Pydantic:** Payload incompleto. | Fallo en las restricciones del DTO (monto <= 0, campo faltante, etc.). |
| `500 Internal Server Error` | **Error de Servidor:** Fallo no controlado. | Excepción no capturada en el pipeline del backend. |

---

## 3. Matriz de Trazabilidad (Contrato ↔ Código)

| Elemento OpenAPI (`docs/api/openapi.yaml`) | Implementación en Código (`backend/app/`) | Estado |
|---|---|---|
| `POST /api/v1/transacciones` | `@app.post("/api/v1/transacciones")` en `main.py` | Align 1:1 |
| `$ref: '#/components/schemas/TransaccionDTO'` | Class `TransaccionDTO(BaseModel)` en `dtos.py` | Align 1:1 |
| `$ref: '#/components/schemas/RespuestaSincronizacion'` | Class `RespuestaSincronizacion(BaseModel)` en `dtos.py` | Align 1:1 |
| Enum `OrigenDatosEnum` | Class `OrigenDatosEnum(str, Enum)` en `dtos.py` | Align 1:1 |

---

## 4. Historial de Versionado del Contrato

- **v1.0.0 (2026-09):** Creación e implementación inicial del contrato OpenAPI 3.1.0, migración del backend a FastAPI y acoplamiento con la suite de integración en GitHub Actions mediante `@redocly/cli`.

---

## 5. Instrucciones de Ejecución Local

Para ejecutar y probar la API localmente con la documentación interactiva Swagger:

1. Ubicarse en el directorio del backend:
  ```powershell
  cd backend
  ```
2. Instalar las dependencias requeridas:
  ```PowerShell
  pip install -r requirements.txt
  ```
3. Levantar el servidor con Uvicorn:
 ```PowerShell
 uvicorn app.main:app --reload --port 8000
 ```
4. Acceder a la documentación Swagger UI en: http://localhost:8000/docs
