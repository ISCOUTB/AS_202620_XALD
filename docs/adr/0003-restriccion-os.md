## ADR-0003: Restricción de Plataforma a Android y Exclusión de iOS
* *Estatus:* Aprobado
* *Proyecto:* XALDAPP — Aplicación de gestión financiera

## Contexto y Problema
La propuesta de valor de XALD se basa en la captura pasiva y automática de transacciones mediante la lectura de mensajes bancarios (SMS). Sin embargo, el sistema operativo iOS (Apple) impone políticas estrictas de privacidad en su *sandbox* que impiden a aplicaciones de terceros acceder o interceptar el buzón de SMS del dispositivo en segundo plano. Esta decisión responde directamente a la restricción RT-01 (exclusividad de sistema operativo) y condiciona que el escenario de calidad ESC-03 (incorporación de una nueva entidad bancaria) solo se cumpla dentro del ecosistema Android.

## Opciones Evaluadas
* *Desarrollo multiplataforma (Flutter / React Native):* descartado porque, incluso con un framework multiplataforma, la lectura pasiva de SMS en segundo plano seguiría dependiendo de una implementación nativa por sistema operativo, y esa implementación no existe en iOS.
* *Desarrollo nativo dual (Android + iOS), con captura manual en iOS:* descartado porque introduciría dos experiencias de usuario distintas (una automática, otra manual) y traicionaría la propuesta de valor central de captura sin fricción.
* *Desarrollo nativo exclusivo para Android (Adoptada):* permite usar `BroadcastReceiver` con el permiso `RECEIVE_SMS`, la única vía viable para la automatización completa de la ingesta.

## Decisión Tomada
Limitar el alcance arquitectónico del cliente exclusivamente al ecosistema **Android**, utilizando mecanismos nativos como `BroadcastReceiver` con el permiso `RECEIVE_SMS`.

## Consecuencias

### Positivas:
* Permite la automatización completa del flujo de ingesta sin requerir fricción o interacción manual por parte del usuario.

### Riesgos y Mitigación:
* *Riesgo:* incompatibilidad absoluta con dispositivos iOS, restringiendo el alcance de usuarios potenciales a solo aquellos con dispositivos Android.
* *Mitigación:* al tratarse de un proyecto académico de alcance acotado a un semestre (RO-01), esta restricción de mercado se acepta conscientemente; una futura expansión a iOS quedaría documentada como un nuevo ADR que replantee la estrategia de captura para ese sistema operativo (por ejemplo, con carga manual o notificaciones push del banco).

## Trazabilidad
* *Aspectos relacionados:* A-01 — Recepción y procesamiento de información vía SMS/Notificaciones bancarias (`docs/aspectos.md`).
* *Restricciones relacionadas:* RT-01 (Exclusividad de sistema operativo).
* *Escenarios de calidad relacionados:* ESC-03 (Incorporación de una nueva entidad bancaria).
* *Diagrama C4 relacionado:* C1 — Contexto (`docs/c4/c4.md`).
* *Módulos de código relacionados:* `:parser` (depende de `BroadcastReceiver`, exclusivo de Android).
* *ADR relacionado:* ADR-0002 (comparte el aspecto A-01, ya que ambos sustentan la captura pasiva por SMS).
