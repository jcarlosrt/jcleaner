# JCLEANER

Limpiador para Android: archivos basura, WhatsApp y Telegram, fotos repetidas, caché de apps y salud de la batería.
Funciona sin internet y no envía nada a ningún lado.

## Qué hace

| Pestaña | Qué hace |
|---|---|
| **Inicio** | Espacio usado, RAM, batería y un puntaje de salud del teléfono. Toca la tarjeta de salud para ver el detalle. |
| **Limpiar** | APK ya instalados, miniaturas temporales, archivos .tmp/.log, carpetas vacías, descargas de más de 90 días y archivos de más de 100 MB. |
| **Chats** | WhatsApp, WhatsApp Business y Telegram, separados en imágenes, videos, notas de voz, documentos, stickers, estados, etc. Filtra por antigüedad ("+3 meses") y por "solo enviados por mí". |
| **Repetidos** | Fotos idénticas o muy parecidas (reenviadas, redimensionadas, comprimidas) y archivos duplicados de cualquier tipo. Marca solas las copias y conserva la de mejor calidad. |
| **Caché** | Cuánto caché tiene cada app, de mayor a menor. El botón "Borrar" te lleva a la pantalla de esa app, donde tocas *Almacenamiento → Borrar caché*. |
| **Salud** | Nivel, estado, temperatura, voltaje, corriente, capacidad estimada vs. la de fábrica, ciclos de carga (Android 14+), almacenamiento, RAM y consejos. |

Nada se borra sin que lo confirmes.

### Límites de Android (aplican a cualquier app limpiadora)
- **Caché de otras apps:** desde Android 11 ninguna app puede borrarlo sola. JCLEANER lo mide y te lleva a la pantalla exacta para borrarlo.
- **Telegram:** guarda casi todo en una carpeta privada. JCLEANER limpia lo que Telegram guardó en la galería y Descargas, y te muestra cuánto pesa lo privado y cómo borrarlo desde Telegram.
- **WhatsApp por grupo:** WhatsApp guarda los archivos de todos los chats juntos, así que se separan por tipo y antigüedad, no por grupo.
- **Capacidad de la batería:** es una estimación. Algunos fabricantes no entregan todos los datos.

---

## Cómo obtener el instalable (APK)

Elige **una** de las dos opciones.

### Opción A: GitHub (sin instalar nada, recomendado)
1. Crea una cuenta gratis en https://github.com si no tienes.
2. Arriba a la derecha toca **+ → New repository**. Ponle de nombre `JCleaner`, márcalo **Private** y toca **Create repository**.
3. En la página del repositorio vacío toca **uploading an existing file**.
4. Descomprime `JCleaner.zip` en tu computador, abre la carpeta `JCleaner` y **arrastra todo su contenido** (incluida la carpeta `.github`) a la página. Toca **Commit changes**.
   - Si la carpeta `.github` no aparece (está oculta): en Windows activa *Vista → Elementos ocultos*; en Mac presiona `Cmd + Shift + .` en Finder.
5. Ve a la pestaña **Actions**. Verás "Compilar APK" ejecutándose (tarda unos 5 minutos).
6. Cuando tenga ✅, entra y descarga **JCLEANER-apk** al final de la página. Es un .zip con `JCLEANER.apk` adentro.

### Opción B: Android Studio
1. Instala Android Studio: https://developer.android.com/studio
2. **File → Open** y elige la carpeta `JCleaner`. Espera a que termine de sincronizar (la primera vez descarga varias cosas).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
   También puedes conectar el celular por USB (con *Depuración USB* activada) y tocar ▶ para instalarla directo.

## Instalar en el celular
1. Pasa el APK al celular (por WhatsApp a ti mismo, Drive o cable).
2. Ábrelo. Android pedirá permitir **"Instalar apps desconocidas"** para la app con que lo abriste: actívalo.
3. Si aparece Play Protect avisando que es una app desconocida, toca **Más detalles → Instalar de todas formas**
   (pasa con toda app que no viene de Play Store).

## Permisos que pide
- **Acceso a todos los archivos:** para revisar y borrar basura, archivos de WhatsApp y fotos repetidas.
- **Acceso de uso:** solo para medir el caché de cada app (pestaña Caché).
