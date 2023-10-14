package mk.hsilomedus.pn532;

import mk.hsilomedus.pn532.Pn532SamThread.Pn532SamThreadListener;

class MainListener implements Pn532SamThreadListener {

	public void run() {
		(new Pn532SamThread<>(this, new Pn532I2c())).run();
		//(new Pn532SamThread<>(this, new Pn532Serial())).run();
		//(new Pn532SamThread<>(this, new Pn532Spi())).run();
	}

	@Override
	public void receiveMessage(String message) {
		System.out.println(message);
	}

	@Override
	public void uidReceived(String displayName, byte[] uid) {
		System.out.println(displayName + ": UID '" + Pn532SamThreadListener.getUidString(uid) + "' received.");
	}
}