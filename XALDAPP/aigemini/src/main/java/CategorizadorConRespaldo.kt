package com.proyecto.xald.aigemini

/**
 * Prueba primero el categorizador [principal] (Gemini) y, si no logra categorizar, usa el
 * [respaldo] (reglas locales). Una transacción nunca se pierde por un fallo de la IA: en el peor
 * caso queda como "Sin Categorizar" (esquema.txt, ESC-05).
 */
internal class CategorizadorConRespaldo(
    private val principal: ServicioCategorizacion,
    private val respaldo: ServicioCategorizacion
) : ServicioCategorizacion {

    override suspend fun categorizarComercio(comercio: String): CategoriaResultado {
        val resultadoPrincipal = principal.categorizarComercio(comercio)
        // Gemini responde "Sin Categorizar" cuando no sabe, cuando su respuesta no está en la lista
        // permitida o cuando llega vacía. Eso no es una categoría: se intentan las reglas locales,
        // que sí reconocen comercios como "ARA CARIBE" o "UBER *TRIP".
        if (resultadoPrincipal is CategoriaResultado.Exitosa && !esSinCategoria(resultadoPrincipal)) {
            return resultadoPrincipal
        }

        val resultadoRespaldo = respaldo.categorizarComercio(comercio)
        if (resultadoRespaldo is CategoriaResultado.Exitosa) return resultadoRespaldo

        val motivoPrincipal = (resultadoPrincipal as? CategoriaResultado.Fallo)?.motivo
            ?: "respondió $SIN_CATEGORIA"
        val motivoRespaldo = (resultadoRespaldo as? CategoriaResultado.Fallo)?.motivo
        return CategoriaResultado.Fallo("IA: $motivoPrincipal; reglas locales: $motivoRespaldo")
    }

    private fun esSinCategoria(resultado: CategoriaResultado.Exitosa): Boolean =
        resultado.categoria.equals(SIN_CATEGORIA, ignoreCase = true)

    private companion object {
        const val SIN_CATEGORIA = "Sin Categorizar"
    }
}
