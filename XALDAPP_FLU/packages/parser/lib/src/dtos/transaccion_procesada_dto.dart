class TransaccionProcesadaDTO {
  final double monto;
  final String comercio;
  final DateTime fecha;
  final String? tarjetaUltimosDigitos;
  final String smsOriginal;

  const TransaccionProcesadaDTO({
    required this.monto,
    required this.comercio,
    required this.fecha,
    this.tarjetaUltimosDigitos,
    required this.smsOriginal,
  });
}