import 'package:flutter_test/flutter_test.dart';

// Importación de módulos de dominio
import 'package:parser/parser.dart';
import 'package:corefinanciero/corefinanciero.dart';
import 'package:aigemini/aigemini.dart';
import 'package:syncqueue/syncqueue.dart';

// Importación del orquestador
import 'package:xaldapp_flu/use_cases/procesar_notificacion_use_case.dart';

void main() {
  group('Prueba de Fuego - Corte Vertical XALDAPP', () {
    late ServicioParser parser;
    late ServicioCategorizacion categorizador;
    late InformacionFinanciera coreFinanciero;
    late ColaSincronizacionService colaSync;
    late ProcesarNotificacionUseCase orquestador;

    setUp(() {
      // 1. Inicialización mediante las fábricas públicas
      parser = ParserModule.crearServicio();
      categorizador = AIGeminiModule.crearServicio(apiKey: 'TEST_API_KEY');
      coreFinanciero = CoreFinancieroModule.crearServicio();
      colaSync = SyncQueueModule.crearServicio();

      // 2. Inyección en el orquestador
      orquestador = ProcesarNotificacionUseCase(
        parser: parser,
        categorizador: categorizador,
        coreFinanciero: coreFinanciero,
        colaSync: colaSync,
      );
    });

    test('Debe procesar un SMS bancario crudo y encolar el payload para FastAPI', () async {
      // GIVEN: SMS con escape en el símbolo de pesos (\$ para evitar interpolación)
      final smsTexto = 'Compra aprobada en Exito por \$150,000.00 el 15/09/2026.';

      // WHEN: El orquestador procesa la notificación
      await orquestador.ejecutar(smsTexto);

      // THEN 1: Registro en el Core Financiero
      final pendientesCore = await coreFinanciero.obtenerPendientesSincronizacion();
      expect(pendientesCore.length, equals(1));
      expect(pendientesCore.first.comercio, equals('Exito'));
      expect(pendientesCore.first.monto, equals(150000.0));
      expect(pendientesCore.first.categoria, equals('Mercado'));

      // THEN 2: Encolado y procesamiento en SyncQueue
      final tamanoColaRestante = await colaSync.obtenerTamanoCola();
      expect(tamanoColaRestante, equals(0));
    });
  });
}