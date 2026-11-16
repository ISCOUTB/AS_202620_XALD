package com.proyecto.xald.aigemini

/**
 * Contrato público del módulo :aigemini (Anti-Corruption Layer).
 * Aísla al resto de la aplicación de la implementación concreta de IA.
 */
interface ServicioCategorizacion {
    /**
     * Clasifica un comercio en una categoría estandarizada.
     * Operación no bloqueante con suspend function y corrutinas.
     */
    suspend fun categorizarComercio(comercio: String): CategoriaResultado
}

/**
 * Fábrica del servicio que usa SOLO Gemini. Si no hay clave devuelve `Fallo` (la app queda con
 * "Sin Categorizar").
 */
fun crearServicioCategorizacion(
    apiKey: String = BuildConfig.GEMINI_API_KEY,
    timeoutMs: Long = 60_000L
): ServicioCategorizacion {
    return CategorizadorGemini(apiKey = apiKey, timeoutMs = timeoutMs)
}

/**
 * Fábrica de reglas locales instantáneas (0 ms de red). Ideal para persistencia inmediata
 * al recibir un SMS antes de consultar IA en segundo plano.
 */
fun crearCategorizadorLocal(): ServicioCategorizacion {
    return CategorizadorLocal()
}

/**
 * Fábrica recomendada para `:app` (ADR-0012): reglas locales siempre disponibles y, solo si hay
 * API key, Gemini como primera opción con las reglas locales de respaldo.
 */
fun crearServicioCategorizacionConRespaldo(
    apiKey: String = BuildConfig.GEMINI_API_KEY,
    timeoutMs: Long = 60_000L
): ServicioCategorizacion {
    val local = CategorizadorLocal()
    if (apiKey.isBlank()) return local
    return CategorizadorConRespaldo(
        principal = CategorizadorGemini(apiKey = apiKey, timeoutMs = timeoutMs),
        respaldo = local
    )
}