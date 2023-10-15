# rasp-pn532-v2
Library using Java 11 and Pi4J v2 to interface with PN532 NFC modules from a Raspberry Pi.

Almost completely re-written and updated for Java 11 and Pi4J v2. I2C, Serial, and SPI are all working. Hopefully this library and set of instructions will be useful to someone, because it was a long and painful experience getting everything working. It's funny writing this all down and seeing how simple it is once you have the right information.

Forked from hsilomedus/raspi-pn532 which is based on elechouse/PN532, and using Pi4J/pi4j-v2.
* https://github.com/hsilomedus/raspi-pn532/
* https://github.com/elechouse/PN532
* https://github.com/Pi4J/pi4j-v2

Example output from running a Pn532SamThread thread for each connection type (only SPI is connected):
```
PN532-1.6 SPI Channel 0, CS Pin 8: device found.
PN5xx Serial Device /dev/ttyAMA0: getFirmwareVersion() returned TIMEOUT
PN5xx I2C Bus 1, Device 0x24: getFirmwareVersion() returned TIMEOUT
PN532-1.6 SPI Channel 0, CS Pin 8: configured for SAM and running.
PN532-1.6 SPI Channel 0, CS Pin 8: UID '<redacted>' received.
```

## Changes from hsilomedus/raspi-pn532
* Updated to Java 11
* Updated to PI4J 2.3.0
* Major refactoring to clean up the code and make it much easier to understand and use
* All connection methods are working, there was a bug in the Serial receive method in the raspi-pn532 library
* Added Pn532SamThread to make usage simple
* Added Pn532Utility for logging and exception handling
* Added Pn532ContextHelper
* Added Main/MainListener to provide example implementation

## Notes/issues/etc...
* sudo is required unless you're using I2C with the linuxfs-i2c provider.
* To get the Serial wakeup working, it sends a SAM config command immediately. I don't know why but I can't currently find a way around that.
* I've read in some places that you should not power your PN532 from an external 5V power supply and then connect it to the 3.3V GPIO pins. I honestly don't understand enough about it to know whether it's a real issue, but my setup does it anyway and we'll see if I blow up my Pi. More information below.

## Example Implementation
Releases include a runnable jar which will run a Pn532SamThread for each connection type with default parameters.
```
sudo java -jar raspi-pn532-v2-1.0.0.jar
```

## Library Usage
Note: I used eclipse 2022-06 for development to allow me to use Java 11 (jdk-11.10.16.8-hotspot specifically).

Your class will need to implement Pn532SamThreadListener and its methods.
```
@Override
public void receiveMessage(String message) {
    System.out.println(message);
}

@Override
public void uidReceived(String displayName, byte[] uid) {
    System.out.println(displayName + ": UID '" + Pn532SamThreadListener.getUidString(uid) + "' received.");
}
```

First initialize the Pi4J context:
```
Pn532ContextHelper.initialize();
```

Create and run a Pn532SamThread thread:
```
Pn532SamThread<I2C> i2cThread = new Pn532SamThread<>(this, new Pn532I2c());
i2cThread.start();
```

Stop the Pn532SamThread thread when you're done:
```
@SuppressWarnings("rawtypes")
private void closeThread(Pn532SamThread thread) {
    if (thread != null && thread.isAlive()) {
        thread.close();

        try {
            thread.join();
        } catch (InterruptedException e) {
            System.out.println("Error closing thread: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
```

Shut down the Pi4J context:
```
Pn532ContextHelper.shutdown();
```

You can also use the Pn532Utility methods in your own code to better handle the random RuntimeExceptions that come out of Pi4J and for logging.
```
public LockController() throws IOException {
    Pn532Utility.wrapInitializationExceptions(() -> {
        var pi4j = Pn532ContextHelper.getContext();
        DigitalOutputProvider provider = pi4j.provider("pigpio-digital-output");
        for (int i = 0; i < outputs.length; i++) {
            outputs[i] = provider.create(DigitalOutput.newConfigBuilder(pi4j).address(PIN_IDS[i]).build());
            outputs[i].state(DigitalState.LOW);
        }
    });
}
```
```
private void runMotor(boolean cw) throws IOException {
    Pn532Utility.wrapIoException(() -> {
        int stepIndex = 0;
        for (int i = 0; i < STEPS; i++) {
            for (int j = 0; j < outputs.length; j++) {
                outputs[j].state(STEP_SEQUENCE_FULL[stepIndex][j] ? DigitalState.HIGH : DigitalState.LOW);
            }
            stepIndex += cw ? -1 : 1;
            stepIndex = Math.floorMod(stepIndex, STEP_SEQUENCE_FULL.length);
            
            long target = System.nanoTime() + STEP_SLEEP;
            while (System.nanoTime() < target);
        }
        
        for (var output : outputs) {
            output.low();
        }
    });
}
```
```
Pn532Utility.setLogger(logger);
```

## Setting up your Raspberry Pi and PN532
If you're having issues, I recommend using libnfc to make sure your module is working and configured/connected correctly. i2cdetect is also useful.
* https://github.com/nfc-tools/libnfc
* https://learn.adafruit.com/adafruit-nfc-rfid-on-raspberry-pi/building-libnfc
* https://learn.adafruit.com/scanning-i2c-addresses/raspberry-pi

I used an Elechouse PN532 NFC RFID Module V3 and a Raspberry Pi Zero. **There may be differences in setup and/or configuration for other Raspberry Pi models.**
* http://www.elechouse.com/elechouse/images/product/PN532_module_V3/PN532_%20Manual_V3.pdf
* https://www.raspberrypi.com/products/raspberry-pi-zero/

### Software Setup - Raspbian 11 (bullseye)
1. Run raspi-config and in the Interface Options you can enable I2C, SPI, and the Serial port (while disabling shell messages on the serial connection)
1. Make sure Java 11 or higher is installed, I installed OpenJDK 17 (sudo apt install openjdk-17-jre)

### Powering your PN532
Raspberry Pi GPIO does not provide enough current (up to 150mA per PN532), so you'll need an external power supply. **As explained above, using a 5V power supply may not be safe for your Pi.**
* https://raspberrypi.stackexchange.com/a/82221
* https://raspberrypi.stackexchange.com/a/68174

I'm using a small USB hub with a power adapter to power the Raspberry Pi as well as my peripherals. For the peripherals I chopped up a USB cable and crimped on some connectors. One USB port can provide up to 500mA.

### Connections
[Pin Numbering](https://pi4j.com/documentation/pin-numbering/) - BCM numbering is used. Remember that you should be powering these externally, so I'll only list the data connections. SPI also has its own VCC/GND. These are the pins I used to get everything working, I'm pretty sure there are other pins you can use but I haven't tried them.

**I2C** (match labels)

Pi                 | PN532
------------------ | -----
SDA1 (I2C) - BCM 2 | SDA
SCL1 (I2C) - BCM 3 | SCL

**Serial** (Tx -> Rx, Rx -> Tx, labels on back of PN532 module)

Pi                | PN532
----------------- | -----
UART TxD - BCM 14 | RXD
UART RxD - BCM 15 | TXD

**SPI** (match labels, mostly)

Pi                  | PN532
------------------- | -----
MOSI (SPI) - BCM 10 | MOSI
MISO (SPI) - BCM 9  | MISO
SCLK (SPI) - BCM 11 | SCK
CE0 (SPI) - BCM 8   | SS