# ADR-0009: Distribución de la Aplicación Móvil como APK Directo

* *Estatus:* Decidido
* *Fecha:* Septiembre 2026
* *Proyecto:* XALDAPP — Aplicación de gestión financiera
* *Corte:* Segundo corte — despliegue del sistema

## Contexto y Problema

La Aplicación Móvil XALD es la única pieza del sistema que no corre en infraestructura del equipo: vive en el dispositivo Android del usuario (ver [arc42 §7](../arc42/07-Deployment%20View.md)). Hacía falta decidir por qué canal llega el instalable al celular, bajo las restricciones RO-01 (alcance de un semestre), RO-02 (presupuesto $0) y RO-03 (sin tarjeta de crédito).

Además, la app depende del permiso `RECEIVE_SMS` (RT-01, ADR-0003) para leer las notificaciones bancarias, un permiso que las tiendas de aplicaciones restringen a categorías específicas de apps.

## Opciones Evaluadas

* *Google Play Store:* descartada. El registro en Google Play Console exige un pago único y un medio de pago asociado, incompatible con RO-02 y RO-03. Además, la política de Google Play restringe los permisos de SMS a las apps que funcionan como gestor de SMS predeterminado, lo que obligaría a justificar o eliminar `RECEIVE_SMS`, del que depende el núcleo del producto.
* *Firebase App Distribution:* descartada. Es gratuita, pero agrega una cuenta y una consola externas, y un SDK adicional, para un grupo de prueba de pocas personas. El costo de configuración no se justifica en el alcance de RO-01.
* *APK directo vía GitHub Releases (Adoptada):* el APK generado por Gradle se publica como adjunto de un *Release* del propio repositorio, que ya es público. No requiere cuentas adicionales, pago ni tarjeta.

## Decisión Tomada

Distribuir la Aplicación Móvil XALD como **APK directo**, publicado en **GitHub Releases** del repositorio `ISCOUTB/AS_202620_XALD`, e instalado manualmente en el dispositivo (*sideloading*).

## Justificación Técnica

* **Cumple RO-02 y RO-03:** GitHub Releases es gratuito para repositorios públicos y no pide medio de pago.
* **No compromete `RECEIVE_SMS`:** al no pasar por la revisión de una tienda, la app conserva el permiso del que depende la ingesta automática (RT-01, RT-04).
* **Sin infraestructura nueva:** reutiliza el repositorio que el equipo ya mantiene; el repositorio ya cuenta con un Release publicado.

## Consecuencias

### Positivas:

* Distribución inmediata y sin costo a los usuarios de prueba.
* Trazabilidad directa entre cada versión instalable y el commit que la generó.

### Riesgos y Mitigación:

* **Riesgo:** el usuario debe habilitar la instalación desde orígenes desconocidos, lo que genera fricción y advertencias de seguridad del sistema operativo.
* **Mitigación:** aceptado para el alcance académico de pruebas (RO-01); se documenta el procedimiento de instalación junto a cada Release.
* **Riesgo:** no hay actualizaciones automáticas; cada nueva versión debe instalarse manualmente.
* **Mitigación:** aceptado mientras el grupo de usuarios sea reducido. Si el proyecto pasara a producción, esta decisión debe revisarse, incluyendo la adaptación a las políticas de permisos de SMS de la tienda.

## Trazabilidad

* Restricciones relacionadas: RO-01, RO-02, RO-03, RT-01.
* Decisión relacionada: [ADR-0003](0003-restriccion-os.md) (restricción a Android por el uso de `RECEIVE_SMS`).
* Vista de despliegue: [`docs/arc42/07-Deployment View.md`](../arc42/07-Deployment%20View.md).
