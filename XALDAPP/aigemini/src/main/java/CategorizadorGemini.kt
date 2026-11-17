package com.proyecto.xald.aigemini

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class CategorizadorGemini(
    private val apiKey: String,
    private val timeoutMs: Long = 60_000L,
    private val modelos: List<String> = MODELOS_POR_DEFECTO
) : ServicioCategorizacion {

    // Constructor secundario para mantener compatibilidad si algún test pasa un solo String
    constructor(apiKey: String, timeoutMs: Long, modelo: String) : this(
        apiKey = apiKey,
        timeoutMs = timeoutMs,
        modelos = listOf(modelo)
    )

    override suspend fun categorizarComercio(comercio: String): CategoriaResultado {
        if (apiKey.isBlank() || apiKey.contains("TU_API_KEY")) {
            Log.w(TAG, "Fallo: GEMINI_API_KEY vacía o no configurada en local.properties")
            return CategoriaResultado.Fallo("API Key no configurada")
        }

        if (comercio.isBlank() || comercio.trim().equals("DESCONOCIDO", ignoreCase = true)) {
            Log.w(TAG, "Fallo: comercio vacío o DESCONOCIDO")
            return CategoriaResultado.Fallo("Comercio sin información para clasificar")
        }

        val categoriasValidas = CategoriasPermitidas.lista.joinToString(", ")
        val prompt = """
            Clasifica el siguiente comercio en una de estas categorías financieras: 
            [$categoriasValidas].
            Responde ÚNICAMENTE con el nombre exacto de la categoría. Si no estás seguro, responde "Sin Categorizar".
            Comercio: $comercio
        """.trimIndent()

        var ultimoMotivo = "No se pudo contactar ningún modelo de Gemini"

        for (modelo in modelos) {
            try {
                val resultado = withTimeout(timeoutMs) {
                    Log.d(
                        TAG,
                        "Consultando $modelo (timeout=${timeoutMs}ms) para comercio: '$comercio'"
                    )

                    val generativeModel = GenerativeModel(
                        modelName = modelo,
                        apiKey = apiKey,
                        generationConfig = generationConfig {
                            temperature = 0.1f
                            maxOutputTokens = 1024
                        }
                    )

                    val response = generativeModel.generateContent(prompt)
                    val textoCrudo = response.text
                    Log.d(TAG, "Respuesta cruda de $modelo para '$comercio': '$textoCrudo'")

                    val categoriaExtraida =
                        textoCrudo?.trim()?.replace(".", "") ?: "Sin Categorizar"
                    val categoriaNormalizada = CategoriasPermitidas.normalizar(categoriaExtraida)

                    Log.i(
                        TAG,
                        "Categoría asignada por $modelo para '$comercio' -> $categoriaNormalizada"
                    )
                    CategoriaResultado.Exitosa(categoriaNormalizada)
                }
                return resultado
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.w(
                        TAG,
                        "Timeout de ${timeoutMs}ms con $modelo para '$comercio', probando siguiente..."
                    )
                    ultimoMotivo = "Timeout con $modelo"
                } else {
                    throw e
                }
            } catch (e: Exception) {
                if (e.cause is TimeoutCancellationException) {
                    Log.w(TAG, "Timeout de red con $modelo para '$comercio', probando siguiente...")
                    ultimoMotivo = "Timeout de red con $modelo"
                } else {
                    Log.w(
                        TAG,
                        "Fallo con $modelo (${e.javaClass.simpleName}), probando siguiente..."
                    )
                    ultimoMotivo = "Error IA ($modelo): ${e.localizedMessage}"
                }
            }
        }

        return CategoriaResultado.Fallo(ultimoMotivo)
    }

    companion object {
        private const val TAG = "XALD_IA"
        const val MODELO_POR_DEFECTO = "gemini-3.5-flash-lite"
        val MODELOS_POR_DEFECTO = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.8-flash"
        )
    }
}