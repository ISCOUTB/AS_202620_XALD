// Exportaciones públicas del paquete
export 'src/cola_sincronizacion_service.dart';
export 'src/dtos/payload_sincronizacion_dto.dart';

import 'src/cola_sincronizacion.dart';
import 'src/cola_sincronizacion_service.dart';

class SyncQueueModule {
  static ColaSincronizacionService crearServicio() => ColaSincronizacion();
}