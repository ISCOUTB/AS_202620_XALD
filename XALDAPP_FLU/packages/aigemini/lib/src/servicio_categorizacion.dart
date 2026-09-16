import 'dtos/categoria_resultado.dart';

abstract class ServicioCategorizacion {
  /// Recibe el nombre del comercio y devuelve la categoría sugerida
  Future<CategoriaResultado> categorizarComercio(String comercio);
}