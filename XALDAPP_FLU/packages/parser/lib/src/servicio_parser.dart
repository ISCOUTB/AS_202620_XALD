import 'dtos/transaccion_procesada_dto.dart';

abstract class ServicioParser {
  /// Recibe el texto crudo del SMS y devuelve el DTO interpretado.
  /// Devuelve `null` si el texto no coincide con los patrones de notificación bancaria.
  TransaccionProcesadaDTO? procesarTextoMensaje(String smsTexto);
}