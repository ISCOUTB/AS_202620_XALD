import 'dtos/transaccion_procesada_dto.dart';
import 'servicio_parser.dart';

class ParseoSms implements ServicioParser {
  // Reglas de expresiones regulares para parseo de SMS bancarios
  static final RegExp _regexMonto = RegExp(r'\$([\d,]+(?:\.\d{2})?)');
  static final RegExp _regexComercio = RegExp(r'en\s+([A-Z0-9\s]+?)(?=\s+por|\s+el|\s+\$|\.)', caseSensitive: false,);

  @override
  TransaccionProcesadaDTO? procesarTextoMensaje(String smsTexto) {
    if (smsTexto.isEmpty) return null;

    final matchMonto = _regexMonto.firstMatch(smsTexto);
    final matchComercio = _regexComercio.firstMatch(smsTexto);

    if (matchMonto == null) return null;

    final montoTexto = matchMonto.group(1)?.replaceAll(',', '') ?? '0';
    final monto = double.tryParse(montoTexto) ?? 0.0;
    final comercio = matchComercio?.group(1)?.trim() ?? 'Comercio Desconocido';

    return TransaccionProcesadaDTO(
      monto: monto,
      comercio: comercio,
      fecha: DateTime.now(),
      smsOriginal: smsTexto,
    );
  }
}