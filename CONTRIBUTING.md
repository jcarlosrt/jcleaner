# Cómo colaborar con JCLEANER

¡Gracias por querer mejorar JCLEANER! Toda ayuda es bienvenida: reportar errores, proponer ideas, traducir o programar.

## Reportar un problema
Abre un [Issue](../../issues) e incluye:
- Marca y modelo del teléfono, y versión de Android.
- Qué hiciste, qué esperabas y qué pasó.
- Una captura de pantalla si ayuda.

## Proponer un cambio en el código
1. Haz un *fork* del repositorio y crea una rama: `git checkout -b mi-mejora`.
2. Haz tus cambios y comprueba que compila: `./gradlew assembleDebug`.
3. Pruébalo en un teléfono real.
4. Abre un *Pull Request* explicando qué cambia y por qué.

## Principios que todo cambio debe respetar
Estos principios son la razón de ser de la app; los cambios que no los cumplan no se aceptarán:

1. **Sin permiso de internet**, sin anuncios, sin analíticas ni rastreo.
2. **Nunca borrar nada sin confirmación** del usuario, y mostrar siempre cuánto se va a liberar.
3. **Números reales.** Nada de cifras infladas, animaciones falsas de "optimización" ni "aceleradores de RAM".
4. **Honestidad.** Si Android no permite algo, la app lo dice en vez de fingir que lo hace.
5. **Español claro** en la interfaz, pensado para personas no técnicas.

## Estilo
- Kotlin + Jetpack Compose. Sigue el estilo del código existente (nombres en español).
- La lógica va en `data/`, las pantallas en `ui/`.
- Las tareas largas corren en `Dispatchers.IO` a través de `Motor`, para que sigan aunque cambies de pestaña.

Al enviar una contribución, aceptas que se publique bajo la licencia [GPL-3.0](LICENSE) del proyecto.
