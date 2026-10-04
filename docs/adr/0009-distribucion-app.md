# ADR-0009: Distribución de la Aplicación Móvil como APK Directo

* *Estatus:* Decidido
* *Fecha:* Septiembre 2026
* *Corte:* Segundo corte — despliegue del sistema

## Contexto y Problema

La Aplicación Móvil XALD corre en el dispositivo Android del usuario, no en infraestructura del equipo ([arc42 §7](../arc42/07-Deployment%20View.md)). Hay que decidir por qué canal llega el instalable, bajo RO-01 (un semestre), RO-02 ($0) y RO-03 (sin tarjeta). La app depende del permiso `RECEIVE_SMS` (RT-01, ADR-0003).

## Opciones Evaluadas

* *Google Play Store:* descartada; el registro en Play Console exige un pago y un medio de pago (RO-02, RO-03), y su política restringe los permisos de SMS a los gestores de SMS predeterminados, lo que comprometería `RECEIVE_SMS`.
* *Firebase App Distribution:* descartada; es gratuita, pero agrega una consola externa y un SDK para un grupo de prueba de pocas personas (RO-01).
* *APK directo vía GitHub Releases (Adoptada):* el APK de Gradle se publica como adjunto de un *Release* del repositorio público. Sin cuentas nuevas, pago ni tarjeta.

## Decisión Tomada

Distribuir la app como **APK directo** en **GitHub Releases** de `ISCOUTB/AS_202620_XALD`, instalado manualmente (*sideloading*).

## Consecuencias

* **Positivas:** distribución inmediata y gratuita; cada instalable queda ligado al commit que lo generó; se conserva `RECEIVE_SMS`.
* **Riesgo:** el usuario debe permitir instalar desde orígenes desconocidos, y no hay actualizaciones automáticas.
* **Mitigación:** aceptado para un grupo de prueba reducido (RO-01); si el proyecto pasara a producción, se revisaría esta decisión junto con las políticas de permisos de la tienda.

## Trazabilidad

RO-01 · RO-02 · RO-03 · RT-01 · [ADR-0003](0003-restriccion-os.md)
