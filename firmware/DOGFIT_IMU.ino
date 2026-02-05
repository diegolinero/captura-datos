#include <Arduino.h>
#include <Wire.h>
#include <MPU6050.h>
#include <NimBLEDevice.h>

static const int SDA_PIN = 4;  // ESP32-C3 Super Mini typical SDA
static const int SCL_PIN = 5;  // ESP32-C3 Super Mini typical SCL

static const uint32_t DEFAULT_RATE_HZ = 50;

static const char *BLE_DEVICE_NAME = "DOGFIT-IMU";
static const char *SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
static const char *RX_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E";
static const char *TX_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E";

MPU6050 mpu;

NimBLEServer *bleServer = nullptr;
NimBLECharacteristic *txCharacteristic = nullptr;
NimBLECharacteristic *rxCharacteristic = nullptr;

static bool bleConnected = false;
static bool streamingEnabled = false;
static uint32_t sampleRateHz = DEFAULT_RATE_HZ;
static uint32_t lastSampleMs = 0;
static bool mpuReady = false;
static uint32_t lastMpuRetryMs = 0;

void applySampleRate(uint32_t rateHz) {
  if (rateHz == 0) {
    rateHz = DEFAULT_RATE_HZ;
  }
  sampleRateHz = rateHz;
}

bool initMpu() {
  Wire.begin(SDA_PIN, SCL_PIN);
  mpu.initialize();
  if (!mpu.testConnection()) {
    Serial.println("MPU6050 not detected. Retrying...");
    return false;
  }
  mpu.setFullScaleAccelRange(MPU6050_ACCEL_FS_2);
  mpu.setFullScaleGyroRange(MPU6050_GYRO_FS_250);
  Serial.println("MPU6050 initialized.");
  return true;
}

void handleCommand(const std::string &command) {
  if (command == "START") {
    streamingEnabled = true;
    Serial.println("Streaming START");
    return;
  }
  if (command == "STOP") {
    streamingEnabled = false;
    Serial.println("Streaming STOP");
    return;
  }
  if (command.rfind("RATE=", 0) == 0) {
    uint32_t rate = static_cast<uint32_t>(atoi(command.substr(5).c_str()));
    if (rate == 25 || rate == 50 || rate == 100) {
      applySampleRate(rate);
      Serial.print("Rate set to ");
      Serial.println(rate);
    } else {
      Serial.println("Unsupported RATE. Use 25/50/100.");
    }
  }
}

class ServerCallbacks : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer *server) override {
    bleConnected = true;
    streamingEnabled = true;
    Serial.println("BLE connected");
  }

  void onDisconnect(NimBLEServer *server) override {
    bleConnected = false;
    streamingEnabled = false;
    Serial.println("BLE disconnected");
    NimBLEDevice::startAdvertising();
  }
};

class RxCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic *characteristic) override {
    std::string value = characteristic->getValue();
    if (!value.empty()) {
      value.erase(value.find_last_not_of("\r\n") + 1);
      handleCommand(value);
    }
  }
};

void setupBle() {
  NimBLEDevice::init(BLE_DEVICE_NAME);
  NimBLEDevice::setPower(ESP_PWR_LVL_P9);
  bleServer = NimBLEDevice::createServer();
  bleServer->setCallbacks(new ServerCallbacks());

  NimBLEService *service = bleServer->createService(SERVICE_UUID);
  txCharacteristic = service->createCharacteristic(
      TX_UUID, NIMBLE_PROPERTY::NOTIFY);
  rxCharacteristic = service->createCharacteristic(
      RX_UUID, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);

  rxCharacteristic->setCallbacks(new RxCallbacks());

  service->start();

  NimBLEAdvertising *advertising = NimBLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->start();
  Serial.println("BLE advertising started");
}

void setup() {
  Serial.begin(115200);
  delay(200);

  Wire.begin(SDA_PIN, SCL_PIN);
  mpuReady = initMpu();
  applySampleRate(DEFAULT_RATE_HZ);
  setupBle();
}

void loop() {
  const uint32_t nowMs = millis();

  if (!mpuReady && (nowMs - lastMpuRetryMs >= 2000)) {
    lastMpuRetryMs = nowMs;
    mpuReady = initMpu();
  }

  if (!bleConnected || !streamingEnabled || !mpuReady) {
    delay(5);
    return;
  }

  const uint32_t intervalMs = 1000 / sampleRateHz;
  if (nowMs - lastSampleMs < intervalMs) {
    delay(1);
    return;
  }
  lastSampleMs = nowMs;

  int16_t axRaw, ayRaw, azRaw;
  int16_t gxRaw, gyRaw, gzRaw;
  mpu.getMotion6(&axRaw, &ayRaw, &azRaw, &gxRaw, &gyRaw, &gzRaw);

  const float ax = axRaw / 16384.0f;
  const float ay = ayRaw / 16384.0f;
  const float az = azRaw / 16384.0f;
  const float gx = gxRaw / 131.0f;
  const float gy = gyRaw / 131.0f;
  const float gz = gzRaw / 131.0f;

  char payload[128];
  snprintf(payload, sizeof(payload), "%lu,%.4f,%.4f,%.4f,%.3f,%.3f,%.3f\n",
           static_cast<unsigned long>(nowMs), ax, ay, az, gx, gy, gz);

  txCharacteristic->setValue(reinterpret_cast<uint8_t *>(payload), strlen(payload));
  txCharacteristic->notify();
}
