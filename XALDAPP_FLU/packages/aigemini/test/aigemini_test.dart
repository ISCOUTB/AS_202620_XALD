import 'package:flutter_test/flutter_test.dart';
import 'package:aigemini/aigemini.dart';

void main() {
  group('Auditoría del Módulo AI Gemini', () {
    test('Debe instanciar el servicio de categorización mediante su fábrica pública', () {
      final categorizador = AIGeminiModule.crearServicio(apiKey: 'TEST_API_KEY');
      expect(categorizador, isNotNull);
    });
  });
}
