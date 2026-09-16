import 'dart:async';
import 'dtos/payload_sincronizacion_dto.dart';
import 'cola_sincronizacion_service.dart';

class ColaSincronizacion implements ColaSincronizacionService {
  final List<PayloadSincronizacionDTO> _cola = [];

  @override
  Future<void> encolarPayload(PayloadSincronizacionDTO payload) async {
    _cola.add(payload);
  }

  @override
  Future<void> procesarCola() async {
    if (_cola.isEmpty) return;

    // Simulación de envío mediante cliente HTTP hacia FastAPI
    final elementosAProcesar = List<PayloadSincronizacionDTO>.from(_cola);

    for (final item in elementosAProcesar) {
      await Future.delayed(const Duration(milliseconds: 200));
      // Si la llamada HTTP a FastAPI es exitosa:
      _cola.remove(item);
    }
  }

  @override
  Future<int> obtenerTamanoCola() async {
    return _cola.length;
  }
}