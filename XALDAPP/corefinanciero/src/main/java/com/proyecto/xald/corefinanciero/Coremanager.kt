package com.proyecto.xald.corefinanciero

import com.proyecto.xald.parser.TransaccionDto

class Coremanager {
    private val baseDeDatosLocal = mutableListOf<TransaccionDto>()

    /**
     * Recibe el TransaccionDto estructurado, valida la información
     * y simula el registro o persistencia local en el core financiero.
     */
    fun guardar(transaccion: TransaccionDto): Boolean {
        // Validación básica de que la transacción tenga un monto válido
        if (transaccion.monto.toIntOrNull() == 0) {
            return false
        }

        // Almacena la transacción en la lista simulando la base de datos local
        return baseDeDatosLocal.add(transaccion)
    }

    fun obtenerUltimaTransaccion(): TransaccionDto? {
        return baseDeDatosLocal.lastOrNull()
    }
}