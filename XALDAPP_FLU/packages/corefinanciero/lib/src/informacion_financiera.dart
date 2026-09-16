import 'dtos/payload_sincronizacion_dto.dart';

abstract class InformacionFinanciera {
  /// Registra una nueva transacción procesada y asigna su UUID
  Future<String> registrarTransaccion({
    required double monto,
    required String comercio,
    required String categoria,
    required DateTime fecha,
    String? tarjetaUltimosDigitos,
  });

  /// Extrae los registros no sincronizados y los empaqueta como DTOs
  Future<List<PayloadSincronizacionDTO>> obtenerPendientesSincronizacion();

  /// Marca una transacción como enviada al Backend
  Future<void> marcarComoSincronizada(String idTransaccion);
}