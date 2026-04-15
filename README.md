# SubFlow

Proyecto Android listo para abrir en Android Studio y generar el APK.

## Qué hace
- Escucha audio ambiente con `SpeechRecognizer`.
- Muestra subtítulos en una burbuja flotante.
- Traduce usando ML Kit en el dispositivo.
- Guarda configuración inicial dentro de la app.

## Límite importante
En Android 12 sin root, esta app **no captura audio interno de Chrome**. Escucha por micrófono, así que el video debe sonar por el altavoz del teléfono o cerca del micrófono.

## Compilar en Android Studio
1. Abre Android Studio.
2. `File > Open` y selecciona la carpeta `SubFlowProject`.
3. Deja que sincronice Gradle.
4. En el menú: `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
5. Instala el APK generado en tu teléfono.

## Primer uso
1. Abre la app.
2. Elige idioma de entrada y salida.
3. Activa traducción si la quieres.
4. Toca **Iniciar SubFlow**.
5. Da permisos de micrófono y superposición.
6. Abre Chrome y reproduce el video.

## Mejoras pendientes
- Captura de audio interno solo con enfoques más complejos o root.
- Traducción parcial más fina.
- Auto-reintentos más robustos según el motor de voz del dispositivo.
