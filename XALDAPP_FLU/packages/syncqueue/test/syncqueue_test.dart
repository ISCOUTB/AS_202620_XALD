import 'package:flutter_test/flutter_test.dart';
import 'package:syncqueue/syncqueue.dart';

void main() {
  group('Auditoría del Módulo Sync Queue', () {
    test('Debe instanciar el servicio de cola de sincronización mediante su fábrica pública', () {
      final syncQueue = SyncQueueModule.crearServicio();
      expect(syncQueue, isNotNull);
    });
  });
}
