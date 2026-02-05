# DOGFIT IMU Logger

## Checklist de implementación (alto nivel)
- Firmware ESP32-C3 con MPU6050 por I2C, streaming BLE UART y comandos START/STOP/RATE.
- App Android mínima para escanear, conectar, recibir CSV y guardar en Documents/DogfitLogs.
- Instrucciones de compilación y prueba para ambos lados.

## A) Firmware ESP32-C3 (Arduino IDE)

**Unidad de aceleración:** g (±2 g). 1 g ≈ 9.80665 m/s². Giroscopio en deg/s.

### Librerías (Arduino Library Manager)
- **MPU6050** by Electronic Cats
- **NimBLE-Arduino** (incluida con Arduino-ESP32 v2.x; si no, instálala desde Library Manager)

### Pines I2C (ESP32-C3 Super Mini)
- `SDA_PIN = 4`
- `SCL_PIN = 5`

Modifica estos `#define` al inicio del `.ino` si tu placa usa otros pines.

### Código
Archivo: `firmware/DOGFIT_IMU.ino`

### Compilación y carga
1. Abre `DOGFIT_IMU.ino` en Arduino IDE.
2. Selecciona la placa **ESP32C3 Dev Module** (o equivalente para Super Mini).
3. Selecciona el puerto serie.
4. Instala las librerías mencionadas.
5. Compila y sube.

## B) App Android (Kotlin)

### Requisitos
- Android Studio Giraffe o superior.
- SDK 34.
- Teléfono con Android 12+ (para permisos BLE modernos). Android 10/11 también funciona (con ubicación).

### Estructura
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/java/com/dogfit/logger/MainActivity.kt`
- `android/app/src/main/java/com/dogfit/logger/BleManager.kt`
- `android/app/src/main/java/com/dogfit/logger/Logger.kt`
- `android/app/src/main/res/layout/activity_main.xml`

### Compilación
1. Abre la carpeta `android/` en Android Studio.
2. Sincroniza Gradle.
3. Ejecuta en un dispositivo real (BLE requerido).

### Prueba rápida
1. Enciende el ESP32-C3 con el firmware cargado.
2. Abre la app. Otorga permisos de BLE.
3. Verifica el estado "Connected" cuando encuentre `DOGFIT-IMU`.
4. Pulsa **Start Logging** para crear el archivo y comenzar.
5. Pulsa **Stop Logging** para detener.
6. Revisa el archivo en: **Documents/DogfitLogs/imu_YYYYMMDD_HHMMSS.csv**.

### Notas
- La app acumula bytes hasta `\n` y procesa líneas completas.
- Si BLE se desconecta, la app reintenta escanear automáticamente.
