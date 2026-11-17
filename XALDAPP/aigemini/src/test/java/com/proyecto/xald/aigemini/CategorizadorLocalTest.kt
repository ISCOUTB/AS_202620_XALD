package com.proyecto.xald.aigemini

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorizadorLocalTest {

    private val local = CategorizadorLocal()

    private suspend fun categoria(comercio: String): String? =
        (local.categorizarComercio(comercio) as? CategoriaResultado.Exitosa)?.categoria

    @Test
    fun comerciosConocidos_seClasificanEnSuCategoria() = runTest {
        assertEquals("Supermercado", categoria("Exito Cartagena"))
        assertEquals("Supermercado", categoria("ARA CARIBE"))
        assertEquals("Transporte", categoria("UBER *TRIP"))
        assertEquals("Entretenimiento", categoria("NETFLIX.COM"))
        assertEquals("Salud", categoria("Farmatodo Bocagrande"))
        assertEquals("Servicios", categoria("CLARO COLOMBIA"))
    }

    @Test
    fun tildesYMayusculas_noImportan() = runTest {
        assertEquals("Supermercado", categoria("ÉXITO"))
        assertEquals("Educación", categoria("UNIVERSIDAD DE CARTAGENA"))
        assertEquals("Salud", categoria("DROGUERÍA LA ECONOMÍA"))
    }

    @Test
    fun laPalabraDebeCoincidirCompleta_noComoSubcadena() = runTest {
        // "ara" está en "para" y en "cara", pero esos comercios no son un Ara.
        assertEquals(null, categoria("PARA TI SAS"))
        assertEquals(null, categoria("CARA LINDA"))
    }

    @Test
    fun comercioSinRegla_retornaFalloConFallbackSinCategorizar() = runTest {
        val resultado = local.categorizarComercio("Comercio Raro XYZ")
        assertTrue(resultado is CategoriaResultado.Fallo)
        assertEquals("Sin Categorizar", (resultado as CategoriaResultado.Fallo).categoriaFallback)
    }

    @Test
    fun comercioDesconocidoOEnBlanco_retornaFallo() = runTest {
        assertTrue(local.categorizarComercio("DESCONOCIDO") is CategoriaResultado.Fallo)
        assertTrue(local.categorizarComercio("   ") is CategoriaResultado.Fallo)
    }

    @Test
    fun todasLasCategoriasDeLasReglas_estanEnLaListaPermitida() = runTest {
        // Si alguien escribe mal una clave de REGLAS, normalizar() la degradaría a "Sin Categorizar".
        val resultados = listOf(
            "exito", "kfc", "uber", "claro", "netflix", "farmatodo", "zara", "platzi", "alkosto"
        ).mapNotNull { categoria(it) }
        assertEquals(9, resultados.size)
        resultados.forEach { assertEquals(it, CategoriasPermitidas.normalizar(it)) }
    }

    @Test
    fun respaldo_siLaIaFalla_usaLasReglasLocales() = runTest {
        val iaCaida = object : ServicioCategorizacion {
            override suspend fun categorizarComercio(comercio: String): CategoriaResultado =
                CategoriaResultado.Fallo("sin red")
        }
        val servicio = CategorizadorConRespaldo(principal = iaCaida, respaldo = local)

        val resultado = servicio.categorizarComercio("Exito Cartagena")

        assertEquals("Supermercado", (resultado as CategoriaResultado.Exitosa).categoria)
    }

    @Test
    fun respaldo_siLaIaResponde_noConsultaLasReglas() = runTest {
        val iaOk = object : ServicioCategorizacion {
            override suspend fun categorizarComercio(comercio: String): CategoriaResultado =
                CategoriaResultado.Exitosa("Salud")
        }
        val servicio = CategorizadorConRespaldo(principal = iaOk, respaldo = local)

        val resultado = servicio.categorizarComercio("Exito Cartagena")

        assertEquals("Salud", (resultado as CategoriaResultado.Exitosa).categoria)
    }

    @Test
    fun respaldo_siTodoFalla_retornaFalloConSinCategorizar() = runTest {
        val iaCaida = object : ServicioCategorizacion {
            override suspend fun categorizarComercio(comercio: String): CategoriaResultado =
                CategoriaResultado.Fallo("sin red")
        }
        val servicio = CategorizadorConRespaldo(principal = iaCaida, respaldo = local)

        val resultado = servicio.categorizarComercio("Comercio Raro XYZ")

        assertTrue(resultado is CategoriaResultado.Fallo)
        assertEquals("Sin Categorizar", (resultado as CategoriaResultado.Fallo).categoriaFallback)
    }
}
