import 'package:flutter_test/flutter_test.dart';
import 'package:parser/parser.dart';

void main() {
  group('Auditoría del Módulo Parser', () {
    test('Debe instanciar el servicio de parser mediante su fábrica pública', () {
      final parser = ParserModule.crearServicio();
      expect(parser, isNotNull);
    });
  });
}