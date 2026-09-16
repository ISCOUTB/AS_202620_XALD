// Exportaciones públicas permitidas
export 'src/dtos/categoria_resultado.dart';
export 'src/servicio_categorizacion.dart';

import 'src/categorizador_gemini.dart';
import 'src/servicio_categorizacion.dart';

class AIGeminiModule {
  static ServicioCategorizacion crearServicio({required String apiKey}) {
    return CategorizadorGemini(apiKey: apiKey);
  }
}
