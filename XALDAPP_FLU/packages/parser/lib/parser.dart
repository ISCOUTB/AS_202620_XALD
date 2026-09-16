// Exportaciones públicas permitidas para :app
export 'src/dtos/transaccion_procesada_dto.dart';
export 'src/servicio_parser.dart';

// Fábrica para permitir instanciar el contrato sin exponer la clase concreta
import 'src/parseo_sms.dart';
import 'src/servicio_parser.dart';

class ParserModule {
  static ServicioParser crearServicio() => ParseoSms();
}
