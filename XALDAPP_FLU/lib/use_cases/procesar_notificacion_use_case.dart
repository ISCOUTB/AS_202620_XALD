import 'package:parser/parser.dart';
import 'package:aigemini/aigemini.dart';
import 'package:corefinanciero/corefinanciero.dart' as core;
import 'package:syncqueue/syncqueue.dart' as sync;

class ProcesarNotificacionUseCase {
  final ServicioParser parser;
  final ServicioCategorizacion categorizador;
  final core.InformacionFinanciera coreFinanciero;
  final sync.ColaSincronizacionService colaSync;

  ProcesarNotificacionUseCase({
    required this.parser,
    required this.categorizador,
    required this.coreFinanciero,
    required this.colaSync,
  });

  /// Ejecuta el flujo completo al recibir un SMS
  Future<void> ejecutar(String textoSms) async {
    // 1. Ingesta / Parseo del mensaje
    final txProcesada = parser.procesarTextoMensaje(textoSms);
    if (txProcesada == null) return; // No es un SMS bancario válido

    // 2. Categorización mediante Gemini (ACL)
    final categoria = await categorizador.categorizarComercio(txProcesada.comercio);

    // 3. Registro en el Core Financiero (Persistence & ID Generation)
    final idGenerado = await coreFinanciero.registrarTransaccion(
      monto: txProcesada.monto,
      comercio: txProcesada.comercio,
      categoria: categoria.nombreCategoria,
      fecha: txProcesada.fecha,
      tarjetaUltimosDigitos: txProcesada.tarjetaUltimosDigitos,
    );

    // 4. Obtención de pendientes de Core
    final pendientesCore = await coreFinanciero.obtenerPendientesSincronizacion();

    // 5. MAPEO/PASO DE INFORMACIÓN entre Core y SyncQueue
    for (final itemCore in pendientesCore) {
      if (itemCore.idTransaccion == idGenerado) {

        // Mapeo explícito: Convertimos Core Payload -> SyncQueue Payload
        final payloadParaSync = sync.PayloadSincronizacionDTO(
          idTransaccion: itemCore.idTransaccion,
          monto: itemCore.monto,
          comercio: itemCore.comercio,
          categoria: itemCore.categoria,
          fechaIso: itemCore.fechaIso,
        );

        // 6. Encolado para envío a FastAPI
        await colaSync.encolarPayload(payloadParaSync);
      }
    }

    // 7. Disparo del intento de procesamiento en cola
    await colaSync.procesarCola();
  }
}