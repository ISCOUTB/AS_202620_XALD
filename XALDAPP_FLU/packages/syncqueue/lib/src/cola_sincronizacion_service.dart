import 'dtos/payload_sincronizacion_dto.dart';

abstract class ColaSincronizacionService {
  /// Encola un DTO para su posterior envío al backend
  Future<void> encolarPayload(PayloadSincronizacionDTO payload);

  /// Procesa la cola enviando los elementos pendientes al servidor
  Future<void> procesarCola();

  /// Devuelve el número de elementos pendientes por sincronizar
  Future<int> obtenerTamanoCola();
}