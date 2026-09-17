import 'package:flutter_test/flutter_test.dart';
import 'package:corefinanciero/corefinanciero.dart';

void main() {
  group('Auditoría del Módulo CoreFinanciero', () {
    test('Debe instanciar el servicio financiero mediante su fábrica pública', () {
      final servicio = CoreFinancieroModule.crearServicio();
      expect(servicio, isNotNull);
    });
  });
}
