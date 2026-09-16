// Exportaciones públicas
export 'src/dtos/payload_sincronizacion_dto.dart';
export 'src/informacion_financiera.dart';

import 'src/gestor_core_financiero.dart';
import 'src/informacion_financiera.dart';

class CoreFinancieroModule {
  static InformacionFinanciera crearServicio() => GestorCoreFinanciero();
}
