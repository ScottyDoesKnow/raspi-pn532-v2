package mk.hsilomedus.pn532;

import java.io.IOException;

// Author: ScottyDoesKnow
// Built for I2C and UART because I couldn't get my SPI working, but it should be easy to modify as needed
public class Pn532Thread extends Thread {

	private static final byte PN532_MIFARE_ISO14443A = 0x00;

	public interface Pn532ThreadListener {
		void println(String message);

		void uidReceived(String channel, String uid);
	}

	private Pn532ThreadListener listener;
	private boolean uart;

	public Pn532Thread(Pn532ThreadListener listener, boolean uart) {
		this.listener = listener;
		this.uart = uart;
	}

	private String getChannelString() {
		if (uart) {
			return "UART";
		} else {
			return "I2C";
		}
	}

	@Override
	public void run() {
		IPN532Interface pn532Interface;
		if (uart) {
			pn532Interface = new PN532Serial();
		} else {
			pn532Interface = new PN532I2C();
		}
		PN532 pn532 = new PN532(pn532Interface);

		try {
			pn532.begin();
		} catch (IOException | InterruptedException e) {
			listener.println("PN5xx " + getChannelString() + " begin error: " + e.getMessage());
			return;
		}
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			listener.println("PN5xx " + getChannelString() + " initialization Thread.sleep interrupted.");
			return;
		}

		long version;
		try {
			version = pn532.getFirmwareVersion();
		} catch (InterruptedException | IllegalStateException | IOException e) {
			listener.println("PN5xx " + getChannelString() + " getFirmwareVersion error: " + e.getMessage());
			return;
		}
		if (version == 0) {
			listener.println("Couldn't find PN5xx " + getChannelString() + ".");
			return;
		}
		listener.println("Found PN5" + Long.toHexString((version >> 24) & 0xFF) + " " + getChannelString() + " - FW: "
				+ Long.toHexString((version >> 16) & 0xFF) + "." + Long.toHexString((version >> 8) & 0xFF));

		// Configure board to read RFID tags
		try {
			pn532.SAMConfig();
		} catch (InterruptedException | IllegalStateException | IOException e) {
			listener.println("PN5xx " + getChannelString() + " SAMConfig error: " + e.getMessage());
			return;
		}
		listener.println("PN5xx " + getChannelString() + " running.");

		byte[] buffer = new byte[8];
		while (true) {
			int length;
			try {
				length = pn532.readPassiveTargetID(PN532_MIFARE_ISO14443A, buffer);
			} catch (InterruptedException | IllegalStateException | IOException e) {
				listener.println("PN5xx " + getChannelString() + " readPassiveTargetID error: " + e.getMessage());
				return;
			}

			if (length > 0) {
				String uid = "";

				for (int i = 0; i < length; i++) {
					uid += Integer.toHexString(buffer[i]);
				}

				listener.uidReceived(getChannelString(), uid);
			}

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				listener.println("PN5xx " + getChannelString() + " running Thread.sleep interrupted.");
				return;
			}
		}
	}
}
