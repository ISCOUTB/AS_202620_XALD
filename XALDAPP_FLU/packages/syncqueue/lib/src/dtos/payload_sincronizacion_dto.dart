class PayloadSincronizacionDTO {
  final String idTransaccion;
  final double monto;
  final String comercio;
  final String categoria;
  final String fechaIso;

  const PayloadSincronizacionDTO({
    required this.idTransaccion,
    required this.monto,
    required this.comercio,
    required this.categoria,
    required this.fechaIso,
  });

  Map<String, dynamic> toJson() => {
    'id_transaccion': idTransaccion,
    'monto': monto,
    'comercio': comercio,
    'categoria': categoria,
    'fecha_iso': fechaIso,
  };
}