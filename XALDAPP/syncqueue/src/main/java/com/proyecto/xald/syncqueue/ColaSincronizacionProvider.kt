package com.proyecto.xald.syncqueue

/**
 * Punto de acceso a la cola para el [SincronizacionWorker].
 *
 * WorkManager crea el Worker por su cuenta (incluso tras reiniciar el dispositivo), así que el
 * Worker no puede recibir la cola por constructor. `:app` la instala una sola vez al arrancar
 * (en `Application.onCreate`) y el Worker la recupera de aquí. Así toda la app comparte una única
 * instancia de la base de datos.
 *
 * Ejemplo en `:app`:
 * ```
 * val cola = crearColaSincronizacionService(this, transporte = ...)
 * ColaSincronizacionProvider.instalar(cola)
 * ProgramadorSincronizacion.programarPeriodica(this)
 * ```
 */
object ColaSincronizacionProvider {
    @Volatile
    private var servicio: ColaSincronizacionService? = null

    fun instalar(servicio: ColaSincronizacionService) {
        this.servicio = servicio
    }

    fun obtener(): ColaSincronizacionService? = servicio

    internal fun reiniciarParaPruebas() {
        servicio = null
    }
}
