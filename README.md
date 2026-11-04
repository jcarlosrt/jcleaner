# JCLEANER

**Limpiador honesto y de código abierto para Android.**
Encuentra archivos basura, archivos de WhatsApp y Telegram, fotos repetidas y caché de apps, y muestra la salud real de la batería.

La mayoría de las apps "limpiadoras" muestran números inventados, llenan el teléfono de anuncios y prometen cosas que Android no permite.
JCLEANER es lo contrario:

- 🔒 **Sin permiso de internet.** La app no puede enviar tus datos a ningún lado (puedes comprobarlo en el `AndroidManifest.xml`).
- 🚫 **Sin anuncios, sin cuentas, sin rastreo.**
- ✅ **Nada se borra sin tu confirmación.**
- 📏 **Números reales.** Lo que muestra es lo que de verdad ocupa cada cosa.
- 🗣️ **Honesta con los límites de Android.** Si algo no se puede hacer, te lo dice y te lleva al lugar donde sí se puede.

Autor: **jcarlosrt** · Licencia: **GPL-3.0**

## Descargar

Ve a **[Releases](../../releases)** y descarga el último `JCLEANER.apk`.

Para instalarlo: ábrelo en el teléfono, permite *"Instalar apps desconocidas"* y, si aparece Play Protect, toca *Más detalles → Instalar de todas formas* (pasa con cualquier app que no viene de Play Store).

Requiere Android 8.0 o más nuevo.

## Funciones

| Sección | Qué hace |
|---|---|
| **Inicio** | Espacio usado, RAM, batería y un puntaje de salud del teléfono. |
| **Limpiar** | APK ya instalados, miniaturas temporales, archivos .tmp/.log, carpetas vacías, descargas de más de 90 días y archivos de más de 100 MB. |
| **Chats** | WhatsApp, WhatsApp Business y Telegram separados en imágenes, videos, notas de voz, documentos, stickers, estados… Filtros por antigüedad y "solo enviados por mí". |
| **Repetidos** | Fotos idénticas o muy parecidas (reenviadas, redimensionadas, comprimidas) y archivos duplicados de cualquier tipo. Conserva automáticamente la de mejor calidad. |
| **Caché** | Cuánto caché tiene cada app, ordenado de mayor a menor, con acceso directo para borrarlo. |
| **Salud** | Estado, temperatura, voltaje, corriente, capacidad estimada vs. la de fábrica, ciclos de carga (Android 14+), almacenamiento, RAM y consejos. |

### Lo que Android no permite (y ninguna app puede hacer)
- **Borrar el caché de otras apps automáticamente** (bloqueado desde Android 11). JCLEANER lo mide y abre la pantalla de cada app para que lo borres con un toque.
- **Entrar a la carpeta privada de Telegram.** JCLEANER limpia lo que Telegram guardó en la galería y en Descargas, y te dice cuánto ocupa lo privado y cómo borrarlo desde Telegram.
- **Separar WhatsApp por grupo.** WhatsApp guarda los archivos de todos los chats juntos; se separan por tipo y antigüedad.
- **Capacidad exacta de la batería.** Es una estimación con los datos que entrega cada fabricante.

## Permisos

| Permiso | Para qué |
|---|---|
| Acceso a todos los archivos | Revisar y borrar basura, archivos de chats y fotos repetidas. |
| Acceso de uso | Solo para medir el caché de cada app. |
| Ver apps instaladas | Para listar las apps en la sección Caché. |

## Compilar desde el código

- **Android Studio:** *File → Open*, elige esta carpeta y luego *Build → Build APK(s)*.
- **Línea de comandos** (JDK 17): `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- **GitHub Actions:** cada cambio en `main` compila el APK y lo publica en Releases.

Hecho en Kotlin con Jetpack Compose. Estructura:

```
app/src/main/java/cl/jcleaner/
├── data/     lógica: escaneo, duplicados, caché, salud de batería
├── ui/       pantallas
└── util/     permisos y formatos
```

## Colaborar

¿Encontraste un error o tienes una idea? Abre un **[Issue](../../issues)**.
¿Quieres programar una mejora? Lee [CONTRIBUTING.md](CONTRIBUTING.md).

## Licencia

Copyright © 2026 jcarlosrt

Este programa es software libre: puedes redistribuirlo y/o modificarlo bajo los términos de la
**Licencia Pública General GNU versión 3** ([LICENSE](LICENSE)). Quien publique una versión modificada
debe compartir también su código bajo la misma licencia y mantener el crédito del autor original.

Se distribuye con la esperanza de que sea útil, pero **sin ninguna garantía**.
