import 'dart:async';
import 'dtos/categoria_resultado.dart';
import 'servicio_categorizacion.dart';

class CategorizadorGemini implements ServicioCategorizacion {
  final String _apiKey;

  CategorizadorGemini({required String apiKey}) : _apiKey = apiKey;

  @override
  Future<CategoriaResultado> categorizarComercio(String comercio) async {
    // Simulador de integración REST / SDK de Gemini
    // Aquí se construirá el prompt estructurado enviando el comercio
    await Future.delayed(const Duration(milliseconds: 300));

    final comercioClean = comercio.toLowerCase();

    // Regla de fallback / mapeo previo a respuesta IA
    if (comercioClean.contains('uber') || comercioClean.contains('didi')) {
      return const CategoriaResultado(nombreCategoria: 'Transporte', confianza: 0.95);
    } else if (comercioClean.contains('exito') || comercioClean.contains('jumbo')) {
      return const CategoriaResultado(nombreCategoria: 'Mercado', confianza: 0.90);
    }

    return const CategoriaResultado(nombreCategoria: 'Varios', confianza: 0.70);
  }
}