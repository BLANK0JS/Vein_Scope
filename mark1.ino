#include <Servo.h>
#include <SoftwareSerial.h>
#include <Wire.h>
#include <Adafruit_MLX90614.h>

#define TCA9548A_ADDR 0x70

SoftwareSerial hc06(8, 9);
Adafruit_MLX90614 mlx = Adafruit_MLX90614();
Servo laser_ang;

String receivedData_S = "";
String send_S = "";
int receivedData_i = "";

void selectTCA9548AChannel(uint8_t channel) {
  if (channel > 7) return;
  Wire.beginTransmission(TCA9548A_ADDR);
  Wire.write(1 << channel);
  Wire.endTransmission();
}

bool initializeMLX90614OnChannel(uint8_t channel) {
  selectTCA9548AChannel(channel);
  return mlx.begin();
}

void setup() {
  Serial.begin(9600);
  hc06.begin(9600);
  Wire.begin();
  mlx.begin();

  pinMode(5,OUTPUT);

  laser_ang.attach(6);
  laser_ang.write(90);

  for (uint8_t channel = 0; channel <= 4; channel++) {
    if (!initializeMLX90614OnChannel(channel)) {
      Serial.print("MLX90614 not found on channel ");
      Serial.println(channel);
    } else {
      Serial.print("MLX90614 initialized on channel ");
      Serial.println(channel);
    }
  }
}

void loop() {
  while (hc06.available()) {
    char c = hc06.read();
    receivedData_S += c;
    delay(5);
  }

  if (receivedData_S.length() > 0) {
  receivedData_i = receivedData_S.toInt();

    if (70 <= receivedData_i && receivedData_i <= 110)
        laser_ang.write(receivedData_i);

    if (receivedData_S == "on")
      digitalWrite(5,HIGH);

    if (receivedData_S == "off")
      digitalWrite(5,LOW);

    if (receivedData_S == "start") {
      for (int i=0;i<20;i++) {
        for (uint8_t channel = 0; channel <= 4; channel++) {
          selectTCA9548AChannel(channel);
          send_S = mlx.readObjectTempC();
          hc06.print(send_S + " ");
          delay(200);
        }
        hc06.print("\n");
        delay(500);
      }
      hc06.println("end");
    }

    if (receivedData_S == "now") {
      for (uint8_t channel = 0; channel <= 4; channel++) {
        selectTCA9548AChannel(channel);
        send_S = mlx.readObjectTempC();
        hc06.print(send_S + " ");
        delay(100);
      }
        hc06.print("\n");
    }

    receivedData_S = "";
  }
}














