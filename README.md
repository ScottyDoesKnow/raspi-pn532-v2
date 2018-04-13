Updated for personal use over I2C and UART. I wasn't able to get SPI to work, but I think it might be my hardware. Credit for the conversion to Java of the elechouse PN532 library goes to hsilomedus

I highly recommend using libnfc to test your hardware before coding your own stuff. It would've saved me a ton of time trying to get SPI working.
* https://github.com/nfc-tools/libnfc

Used device: Elechouse PN532 NFC RFID module V3 kits (Note that I didn't buy from their site, so mine could be a knock-off)
* https://www.elechouse.com/elechouse/index.php?main_page=product_info&cPath=90_93&products_id=2242
* http://www.elechouse.com/elechouse/images/product/PN532_module_V3/PN532_%20Manual_V3.pdf

Changes
* Updated to PI4J 1.2-SNAPSHOT
* Serial is now working, there was a bug in the receive method
* Added the Pn532Thread class
* Bunch of stuff is refactored, but honestly it's all a little half-assed

With the latest version of Raspbian Stretch I think you can just use sudo-config to enable I2C and UART without doing anything else. You might need to install WiringPi or PI4J separately but I don't know for sure since I installed a ton of things while debugging.

Original README included below.

-------------------------------------------------------------------------------

pn532 NFC with Java8, pi4j and Raspberry Pi

Used device: ITEAD PN532 NFC Module: http://imall.iteadstudio.com/prototyping/basic-module/im130625002.html
Code Based on: 
 * http://blog.iteadstudio.com/to-drive-itead-pn532-nfc-module-with-raspberry-pi/
 * https://github.com/elechouse/PN532

For SPI enabling, follow the instructions on the ITEAD blog post:

"
First, before installing the library offered by us, we need to modify some of the configurations of Raspberry Pie to make SPI module automatically activated when powering on:

cd / etc / modprobe.d /

Enter the Configuration folder

sudo nano raspi-blacklist.conf

Open the configuration file as super user

blachlist sip-bcm2708

Comment out that line, to achieve SPI module loading when powering on.
" 
