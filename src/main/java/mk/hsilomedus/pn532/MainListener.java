package mk.hsilomedus.pn532;

import mk.hsilomedus.pn532.PN532SamThread.PN532SamThreadListener;

class MainListener implements PN532SamThreadListener {

	public void run() {
		(new PN532SamThread<>(this, new PN532I2C())).run();
		//(new PN532SamThread<>(this, new PN532Serial())).run();
		//(new PN532SamThread<>(this, new PN532Spi())).run();
	}

	@Override
	public void receiveMessage(String message) {
		System.out.println(message);
	}

	@Override
	public void uidReceived(String displayName, byte[] uid) {
		System.out.println(displayName + ": UID '" + PN532SamThreadListener.getUidString(uid) + "' received.");
	}
}