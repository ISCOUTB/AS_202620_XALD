class TransaccionEntidad {
  final String id;
  final double monto;
  final String comercio;
  final String categoria;
  final DateTime fecha;
  final String? tarjetaUltimosDigitos;
  final bool sincronizada;

  const TransaccionEntidad({
    required this.id,
    required this.monto,
    required this.comercio,
    required this.categoria,
    required this.fecha,
    this.tarjetaUltimosDigitos,
    this.sincronizada = false,
  });
}