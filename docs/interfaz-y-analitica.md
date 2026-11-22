# Interfaz y analítica local (noviembre 2026)

Nueva interfaz de XALDAPP y la lógica financiera que la alimenta. Todo se calcula en el
teléfono a partir de la base local (Local-First); el backend no interviene en las pantallas.

## Pantallas

| Pestaña | Qué muestra |
|---|---|
| **Inicio** | Tarjeta flash · saldo disponible con semáforo · selector Semana/Mes/Año · entradas vs. salidas · gráfico de evolución · gastos por categoría · vista previa de presupuestos · estado de sincronización |
| **Movimientos** | Búsqueda (comercio, categoría o monto) · filtros Todos/Gastos/Ingresos/Transferencias · origen (SMS/manual) · orden (fecha o monto) · lista agrupada por día · tocar un movimiento abre su detalle y permite corregir la clasificación |
| **Presupuestos** | Topes por categoría o globales, mensuales o semanales, con barras que pasan de verde a rojo |

El botón **+** agrega un movimiento manual (o un presupuesto, en esa pestaña). El ojo de la barra
superior (o mantener presionado el saldo) activa el modo privacidad.

## Dónde quedó cada punto de la lista

| Requisito | Lógica | Interfaz |
|---|---|---|
| Gestión de balance base / ajuste manual | `establecerSaldoBase` y tabla `saldo_base` en `:corefinanciero` | Botón "Ajustar saldo" del inicio (`Dialogos.ajustarSaldo`) |
| Clasificador de transferencias | `ClasificadorMovimientos` y reglas nuevas en `ReglaBancaria.kt` (`:parser`) | Chip "Transferencias", íconos ↗️ ↙️ |
| Motor de consolidado | `AnaliticaFinanciera.consolidar` | Tarjeta del saldo disponible |
| Agregación por categorías | `AnaliticaFinanciera.gastosPorCategoria` | Tarjeta "Gastos por categoría" |
| Corrección y reclasificación manual | `reclasificarMovimiento` + `ReclasificarMovimientoUseCase` | Detalle del movimiento → "Corregir clasificación" |
| Filtros y búsqueda | `AnaliticaFinanciera.filtrar` | Pestaña Movimientos |
| Control de sincronización Local-First | Cola existente de `:syncqueue` | Insignia de pendientes, botón sincronizar, tarjeta de estado, ✓/⏳ por movimiento |
| Panel estadístico temporal | `Calendario` + `AnaliticaFinanciera.serie` | Selector Semana/Mes/Año + `GraficoBarrasView` |
| Presupuestos | Tabla `presupuestos`, `estadoPresupuestos` | Pestaña Presupuestos + `BarraProgresoView` (verde → ámbar → rojo) |
| Alertas de proximidad a topes | `alertasNuevas` (80 %, 90 %, 100 %, una vez por periodo) | `NotificadorPresupuestos` (notificación local, sin montos) |
| Proporción entradas vs. salidas | `AnaliticaFinanciera.flujo` | `BarraFlujoView` |
| Semáforo de salud financiera | `AnaliticaFinanciera.semaforo` (presupuestos, ritmo de gasto contra los 3 meses anteriores, flujo del mes) | `PuntoSemaforoView` en la tarjeta del saldo |
| Modo privacidad | `FormatoDinero` con `privado = true` → `$ ****` | Ojo de la barra superior / mantener presionado el saldo |
| Tarjeta flash | `AnaliticaFinanciera.tarjetaFlash` (semana contra semana, gastos sin categoría, categoría principal) | Píldora superior del inicio; si sugiere algo, al tocarla filtra Movimientos |

Extras que no estaban en la lista: movimientos manuales (gasto, ingreso, envío, recepción, con
fecha), detalle de cada movimiento, insignia de pendientes y hora del último envío.

## Cambios en los datos

- `TransaccionEntidad.tipoMovimiento` (GASTO, INGRESO, TRANSFERENCIA_ENVIADA, TRANSFERENCIA_RECIBIDA).
  Es nullable: las filas viejas quedan en `null` y se leen como GASTO.
- Tablas nuevas `presupuestos` y `saldo_base`.
- Base `xald_core.db` versión 1 → 2 con `MIGRACION_CORE_1_2`: **no se pierde ninguna transacción**.
  `MigracionCoreTest` abre una base v1 real y comprueba que Room acepte el esquema migrado.
- Solo los **gastos** cuentan para categorías y presupuestos; las transferencias mueven el saldo
  pero no son gasto.
- Se conservó `obtenerSaldoTotal()` (suma de todos los montos) por compatibilidad; el saldo
  disponible es `obtenerConsolidado()`.

## Pruebas nuevas

- `:corefinanciero` — `AnaliticaFinancieraTest` (31, JVM puras) y `MigracionCoreTest` (Robolectric).
- `:parser` — `ClasificadorMovimientosTest` y 7 casos nuevos en `ParseoSmsTest`.
- `:app` — `MovimientosManualesUseCasesTest`, `FormatoDineroTest`, `FormatoFechaTest` y 2 casos nuevos en `ProcesarNotificacionUseCaseTest`.

## Pendientes conocidos

1. **Formatos de SMS de ingresos y transferencias.** Las reglas nuevas siguen formatos típicos de
   Bancolombia, Davivienda y Nequi. Hay que confirmarlas con SMS reales y, si alguno no se
   reconoce, agregar su regla en `ReglaBancaria.kt` con un caso en `ParseoSmsTest`.
2. **Las correcciones no llegan al backend.** El backend toma un reenvío con la misma
   `fecha_transaccion` como duplicado (ADR-0015, punto abierto). La corrección sí queda en el
   teléfono, que es de donde salen todas las estadísticas.
3. **El tipo de movimiento no viaja al backend.** El contrato v1 no tiene ese campo; los ingresos
   y las transferencias llegan con categoría "Ingresos" o "Transferencias". Agregar
   `tipo_movimiento` al contrato sería un cambio compatible.
4. **Listas sin reciclar.** El historial se dibuja en un `LinearLayout` (máximo 300 filas a la vez).
   Si algún día hay miles de movimientos, conviene pasarlo a `RecyclerView`.
