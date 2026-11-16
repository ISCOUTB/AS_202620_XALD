package com.proyecto.xald.aigemini

import java.text.Normalizer

/**
 * Categorizador por reglas de palabras clave. NO es inteligencia artificial: es el mock/respaldo
 * determinista de ADR-0012. Funciona sin red ni API key, así que la demo siempre categoriza.
 *
 * Las palabras se comparan completas (no como subcadena): "ara" coincide con "ARA CARIBE" pero
 * no con "PARA TI".
 */
internal class CategorizadorLocal : ServicioCategorizacion {

    override suspend fun categorizarComercio(comercio: String): CategoriaResultado {
        val texto = normalizar(comercio)
        if (texto.isBlank() || texto == "desconocido") {
            return CategoriaResultado.Fallo("Comercio sin información para clasificar")
        }

        val rodeado = " $texto "
        for ((categoria, palabras) in REGLAS) {
            if (palabras.any { rodeado.contains(" $it ") }) {
                return CategoriaResultado.Exitosa(categoria, confianza = CONFIANZA_REGLA_LOCAL)
            }
        }
        return CategoriaResultado.Fallo("Sin regla local para '$comercio'")
    }

    /** Minúsculas, sin tildes y solo letras/dígitos separados por un espacio. */
    private fun normalizar(texto: String): String {
        val sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return sinTildes.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private companion object {
        const val CONFIANZA_REGLA_LOCAL = 0.6

        // Las claves deben coincidir exactamente con CategoriasPermitidas.lista.
        val REGLAS: List<Pair<String, List<String>>> = listOf(
            "Supermercado" to listOf(
                "exito", "jumbo", "ara", "d1", "olimpica", "carulla", "makro", "metro",
                "surtimax", "supermercado", "tienda", "minimercado", "megatiendas"
            ),
            "Restaurante" to listOf(
                "restaurante", "kfc", "mcdonalds", "mcdonald", "burger", "crepes", "frisby",
                "presto", "pizza", "pizzeria", "sandwich", "cafe", "starbucks", "juan valdez",
                "corral", "rappi", "domicilios", "panaderia", "asadero"
            ),
            "Transporte" to listOf(
                "uber", "didi", "cabify", "indrive", "beat", "taxi", "terpel", "primax",
                "texaco", "mobil", "gasolina", "transcaribe", "transmilenio", "peaje",
                "parqueadero", "avianca", "latam", "viva air"
            ),
            "Servicios" to listOf(
                "claro", "movistar", "tigo", "wom", "etb", "epm", "vanti", "celsia", "enel",
                "codensa", "aguas", "acueducto", "triple a", "gas natural", "internet", "energia"
            ),
            "Entretenimiento" to listOf(
                "netflix", "spotify", "disney", "hbo", "youtube", "prime video", "cine",
                "cinemark", "procinal", "royal films", "steam", "playstation", "xbox", "teatro"
            ),
            "Salud" to listOf(
                "drogueria", "farmacia", "cruz verde", "farmatodo", "la rebaja", "clinica",
                "hospital", "eps", "medico", "laboratorio", "odontologia", "optica", "consultorio"
            ),
            "Ropa y Calzado" to listOf(
                "zara", "h m", "hm", "arturo calle", "studio f", "bosi", "adidas", "nike", "koaj",
                "gef", "offcorss", "americanino", "mango", "bershka", "calzado", "zapatos", "ropa"
            ),
            "Educación" to listOf(
                "universidad", "colegio", "sena", "platzi", "udemy", "coursera", "instituto",
                "libreria", "duolingo", "academia", "matricula", "icetex"
            ),
            "Tecnología" to listOf(
                "alkosto", "ktronix", "ishop", "apple", "samsung", "huawei", "xiaomi", "google",
                "microsoft", "openai", "github", "amazon", "mercadolibre", "computador", "celular"
            )
        )
    }
}
