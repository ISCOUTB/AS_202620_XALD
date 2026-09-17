import 'dtos/categoria_resultado.dart';
import 'servicio_categorizacion.dart';

class CategorizadorGemini implements ServicioCategorizacion {
  // 1. Lo hacemos público (sin el guion bajo)
  final String apiKey;

  // 2. Ahora sí usamos la sintaxis directa y limpia
  CategorizadorGemini({required this.apiKey});

  @override
  Future<CategoriaResultado> categorizarComercio(String comercio) async {
    await Future.delayed(const Duration(milliseconds: 300));

    final comercioClean = comercio.toLowerCase();

    if (comercioClean.contains('uber') || comercioClean.contains('didi')) {
      return const CategoriaResultado(nombreCategoria: 'Transporte', confianza: 0.95);
    } else if (comercioClean.contains('exito') || comercioClean.contains('jumbo')) {
      return const CategoriaResultado(nombreCategoria: 'Mercado', confianza: 0.90);
    }

    return const CategoriaResultado(nombreCategoria: 'Varios', confianza: 0.70);
  }
}