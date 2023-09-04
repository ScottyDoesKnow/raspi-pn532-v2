package mk.hsilomedus.pn532;

import com.pi4j.io.i2c.I2C;
//import com.pi4j.io.serial.Serial;
//import com.pi4j.io.spi.Spi;

import mk.hsilomedus.pn532.PN532SamThread.PN532SamThreadListener;

class MainListener implements PN532SamThreadListener {

	public void run() {
		(new PN532SamThread<I2C>(this, new PN532I2C())).run();
		//(new PN532SamThread<Serial>(this, new PN532Serial())).run();
		//(new PN532SamThread<Spi>(this, new PN532Spi())).run();
	}

	@Override
	public void println(String message) {
		System.out.println(message);
	}

	@Override
	public void uidReceived(String displayName, String uid) {
		System.out.println(displayName + ": UID '" + uid + "' received.");
	}
}