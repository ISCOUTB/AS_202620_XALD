# ADR-0015: Autenticación del Cliente Móvil ante el Backend — Llaves Rotables Ahora, Token por Instalación Antes de Publicar

* *Estatus:* Decidido
* *Fecha:* Noviembre 2026
* *Proyecto:* XALDAPP — Aplicación de gestión financiera
* *Corte:* Tercer corte — Semana 13, modelado de amenazas
* *Amenazas que mitiga:* AM-01 (riesgo 9) y AM-17 ([`docs/seguridad/modelo-amenazas.md`](../seguridad/modelo-amenazas.md))

## Contexto y Problema

El backend autentica con una sola `X-API-Key` compartida por todos los clientes ([ADR-0004](0004-seguridad-y-cifrado.md)).
Hoy esa llave solo existe en Render y, para la prueba manual de extremo a extremo, en una variable de
entorno. Pero `TransporteHttpSincronizacion` ya la recibe como parámetro, y el siguiente paso natural sería
compilarla en el APK (`BuildConfig`).

El APK se publica en GitHub Releases ([ADR-0009](0009-distribucion-app.md)), un repositorio público.
Cualquier persona lo descarga, lo descompila con `apktool` y obtiene la llave. Con ella puede escribir en
`POST /api/v1/transacciones` como cualquier dispositivo y alterar el estado de Last-Write-Wins de
transacciones ajenas ([ADR-0011](0011-resolucion-conflictos-lww.md)). Es la amenaza de mayor riesgo del
modelo (P 3 × I 3).

Fuerzas:

* RO-01: dos personas y un semestre.
* RO-02: costo $0 (Render Free, sin servicios de identidad pagos).
* RL-01, Ley 1581: no recolectar datos personales que el sistema no necesita ([privacidad](../seguridad/privacidad.md)).
* ESC-01: la app debe registrar sin conexión, así que la autenticación no puede bloquear el registro local.

## Opciones Evaluadas

### A. Llave compartida compilada en el APK
Simple, sin cambios. **Descartada:** la llave queda pública en el primer Release. Rotarla exige
publicar otro APK y deja sin servicio a todos los que no actualicen.

### B. Llaves compartidas rotables en el backend, sin compilar en APKs publicados (adoptada para este corte)
El backend acepta una **lista** de llaves vigentes (`XALD_API_KEYS`). Para rotar se agrega la nueva, se
distribuye y se retira la vieja sin cortar el servicio. La llave solo se entrega a los dispositivos del
grupo de prueba, fuera del repositorio. No resuelve la extracción, pero acota el daño en el tiempo y
permite revocar.

### C. Token propio por instalación (adoptada como destino, antes de publicar un APK con sincronización)
En el primer arranque con red, la app llama `POST /api/v1/dispositivos` y recibe un token aleatorio. El
backend guarda solo su hash y lo asocia a un `id_dispositivo`. Cada sincronización manda ese token. Una
llave extraída de un APK ya no sirve para nada, porque el APK no contiene ninguna. Un token robado se
revoca uno por uno, y cada escritura queda atribuida a un dispositivo (cierra AM-17).

### D. Cuenta de usuario con un proveedor de identidad (Firebase Auth, Google Sign-In)
Resuelve la autenticación por persona. **Descartada:** obliga a recolectar un correo y a depender de una
cuenta de Google. Eso es un dato personal más que el sistema no necesita para categorizar gastos
(principio de finalidad de la Ley 1581) y es más trabajo del que cabe en el semestre.

## Decisión Tomada

1. **Este corte:** opción B. `verificar_llave` acepta cualquiera de las llaves de `XALD_API_KEYS` (más
   `XALD_API_KEY` por compatibilidad), con `hmac.compare_digest` contra todas para no filtrar por tiempo
   de respuesta cuál coincidió.
2. **Regla:** ninguna llave del backend se compila en un APK que se publique en GitHub Releases.
3. **Antes de publicar un APK con la sincronización activa:** opción C. Semana objetivo: 15.

## Cambio estructural

La opción C agrega al backend un endpoint de registro (`/api/v1/dispositivos`) y un almacén nuevo,
«tokens de dispositivo» (hash del token → `id_dispositivo`). Ese almacén se dibujará en el C2 cuando
exista, junto con la persistencia de `ultima_version` (P-04), porque ambos necesitan almacenamiento que
sobreviva al reinicio de Render. En el cliente, `TransporteHttpSincronizacion` cambia la cabecera
`X-API-Key` por `Authorization: Bearer <token>`.

## Consecuencias

### Positivas
* Una llave filtrada se revoca sin cortar el servicio (verificado por `test_am01_llave_revocada_deja_de_servir`).
* La regla 2 impide que AM-01 se materialice mientras llega la opción C.
* La opción C no recolecta datos personales nuevos.

### Negativas y riesgos
* Mientras rija la opción B, quien tenga una llave vigente escribe como cualquiera. Se acepta porque la
  llave solo la tiene el grupo de prueba (AM-17 en arc42 §11.4).
* La opción C depende de que el backend tenga persistencia (P-04). Sin ella, cada reinicio de Render
  invalidaría todos los tokens.

## Verificación
* `backend/tests/test_seguridad.py`: `test_am01_acepta_cualquiera_de_las_llaves_vigentes`,
  `test_am01_llave_revocada_deja_de_servir` y `test_am01_sin_llaves_configuradas_rechaza_todo`. Cada una
  falla si `llaves_vigentes()` deja de leer `XALD_API_KEYS`.

## Trazabilidad
* *Amenazas:* AM-01, AM-17.
* *ADR relacionados:* [ADR-0004](0004-seguridad-y-cifrado.md), [ADR-0009](0009-distribucion-app.md), [ADR-0011](0011-resolucion-conflictos-lww.md), [ADR-0014](0014-sincronizacion-asincrona-con-reintentos.md).
* *C4:* conector 4 del C2 (`docs/c4/c2.md`).
* *arc42:* §8.10 (arquitectura de seguridad), §11.4 (amenazas aceptadas).
