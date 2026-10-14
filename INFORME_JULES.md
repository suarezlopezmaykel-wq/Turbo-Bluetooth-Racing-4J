# INFORME DE MIGRACIÓN Y ESTADO: TURBO BLUETOOTH RACING 4

## 1. Archivos del Juego Trasladados
Se han migrado única y exclusivamente los componentes necesarios para la ejecución y compilación de **Turbo Bluetooth Racing 4**:
- `app/src/main/assets/index.html`: Código principal del juego (HTML, CSS y JavaScript/Canvas) con lógica de renderizado, físicas, IA de rivales, interfaz de selección de modo/pistas y lógica del multijugador Bluetooth.
- `app/src/main/java/com/carrera/bluetooth3d/MainActivity.java`: Actividad Android de puente WebView con interfaz nativa `AndroidBluetooth` que gestiona servidores/clientes RFCOMM Bluetooth en hilos secundarios.

## 2. Archivos Excluidos de APK Doctor AI
Se han excluido intencionalmente todos los archivos y configuraciones pertenecientes a **APK Doctor AI**:
- `app/src/main/java/com/apkdoctor/ai/MainActivity.kt`: Código fuente de la app APK Doctor AI.
- `.github/workflows/build.yml` y flujos genéricos anteriores de APK Doctor AI.
- Ficheros duplicados o no utilizados (`*.kts`).

## 3. Dependencias Necesarias y Configuración Android
- **Paquete Android:** `com.carrera.bluetooth3d`
- **Build configuration (`app/build.gradle`):**
  - `compileSdk 33`, `targetSdk 33`, `minSdk 26`
  - Dependencias mínimas: `androidx.appcompat:appcompat:1.6.1` y `com.google.android.material:material:1.9.0`
- **Permisos Bluetooth (`AndroidManifest.xml`):**
  - Android 13+ (API 33): `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`
  - Compatibilidad previa (API <= 30): `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` con `maxSdkVersion="30"`.

## 4. Cambios en la Geometría de la Salida y Verificación
- **Problema corregido:** Los coches aparecían fuera de la carretera durante la cuenta regresiva debido a que la curvatura de la pista comenzaba demasiado cerca del punto de origen.
- **Solución implementada:** En `index.html`, se actualizó la función `getCurveOffset(y)` extendiendo la zona recta inicial de 2000 a **3500 unidades** antes de cualquier curvatura (`START_Y = 5000`, la recta se mantiene inalterada hasta `Y = 1500`). Se incrementó la longitud de transición a **2000 unidades** para garantizar una entrada suave a la primera curva.
- **Respeto a posiciones y vehículos:** Las posiciones relativas, orientaciones y carriles de los 4 vehículos permanecen idénticos (`ROAD_X ± 1.5 * LANE_WIDTH` y `ROAD_X ± 0.5 * LANE_WIDTH`).
- **Comprobación Visual:** Se ejecutó una prueba con Playwright cargando el juego y capturando una imagen en la cuenta regresiva. Se confirmó visualmente que todos los coches se encuentran sobre la superficie recta del circuito.

## 5. Estado Real de Bluetooth
- Se ha verificado e inspeccionado en código el flujo de conexión Bluetooth (Servidor RFCOMM en `AcceptThread` y Cliente en `ConnectThread`, callbacks JS `onBluetoothStatus` y `onBluetoothData`).
- **Aviso de verificación real:** De acuerdo con las directrices de seguridad y entorno, el funcionamiento real de la transmisión entre dispositivos requiere hardware móvil físico con dos smartphones Android emparejados.

## 6. Resultado de GitHub Actions y Generación de APK
- Se configuró el flujo exclusivo `.github/workflows/build-apk.yml` que ejecuta `./gradlew assembleDebug` en entornos Ubuntu con JDK 17 y publica el artefacto `TurboBluetoothRacing4-debug`.
- **Nota de compilación local en sandbox:** Durante la prueba local `./gradlew assembleDebug`, el servidor HTTP de Maven Central devolvió un código `429 Too Many Requests` (limitación de tasa de solicitudes en la red sandbox). Por ello, la generación oficial y descarga del paquete APK se procesa mediante el flujo automatizado de GitHub Actions al subir la rama.

## 7. Enlaces
- **Informe directo en la rama de trabajo:** [INFORME_JULES.md](https://github.com/suarezlopezmaykel-wq/Turbo-Bluetooth-Racing-4J/blob/jules-2585730824572745836-761d501d/INFORME_JULES.md)
- **Pull Request:** [Ver Pull Request en el Repositorio Destino](https://github.com/suarezlopezmaykel-wq/Turbo-Bluetooth-Racing-4J/pulls)
- **APK Artefacto:** Estará disponible en la pestaña "Actions" del repositorio una vez que se ejecute el flujo en GitHub.
