import 'dart:async';
import 'domain/transaccion_entidad.dart';
import 'dtos/payload_sincronizacion_dto.dart';
import 'informacion_financiera.dart';

class GestorCoreFinanciero implements InformacionFinanciera {
  // Simulación de almacenamiento en memoria / SQLite encriptado
  final List<TransaccionEntidad> _baseDatos = [];

  @override
  Future<String> registrarTransaccion({
    required double monto,
    required String comercio,
    required String categoria,
    required DateTime fecha,
    String? tarjetaUltimosDigitos,
  }) async {
    final idGenerado = DateTime.now().microsecondsSinceEpoch.toString();

    final nuevaTransaccion = TransaccionEntidad(
      id: idGenerado,
      monto: monto,
      comercio: comercio,
      categoria: categoria,
      fecha: fecha,
      tarjetaUltimosDigitos: tarjetaUltimosDigitos,
    );

    _baseDatos.add(nuevaTransaccion);
    return idGenerado;
  }

  @override
  Future<List<PayloadSincronizacionDTO>> obtenerPendientesSincronizacion() async {
    return _baseDatos
        .where((t) => !t.sincronizada)
        .map((t) => PayloadSincronizacionDTO(
      idTransaccion: t.id,
      monto: t.monto,
      comercio: t.comercio,
      categoria: t.categoria,
      fechaIso: t.fecha.toIso8601String(),
    ))
        .toList();
  }

  @override
  Future<void> marcarComoSincronizada(String idTransaccion) async {
    final index = _baseDatos.indexWhere((t) => t.id == idTransaccion);
    if (index != -1) {
      final t = _baseDatos[index];
      _baseDatos[index] = TransaccionEntidad(
        id: t.id,
        monto: t.monto,
        comercio: t.comercio,
        categoria: t.categoria,
        fecha: t.fecha,
        tarjetaUltimosDigitos: t.tarjetaUltimosDigitos,
        sincronizada: true,
      );
    }
  }
}