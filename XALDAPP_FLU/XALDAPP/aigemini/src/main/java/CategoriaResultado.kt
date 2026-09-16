package com.proyecto.xald.aigemini

/**
 * Representa el resultado traducido por la Capa Anticorrupción (ACL).
 */
data class CategoriaResultado(
    val nombreCategoria: String,
    val nivelConfianza: Float
)