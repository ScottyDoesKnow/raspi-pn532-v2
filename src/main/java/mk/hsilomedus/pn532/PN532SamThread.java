package mk.hsilomedus.pn532;

import java.lang.reflect.UndeclaredThrowableException;

import com.pi4j.io.IO;
import com.pi4j.io.exception.IOException;

public class PN532SamThread<T extends IO<T, ?, ?>> extends Thread {

	private static final byte MIFARE_ISO14443A = 0x00;

	public interface PN532SamThreadListener {
		void println(String message);

		void uidReceived(String displayName, String uid); // TODO: return byte array?
	}

	private PN532SamThreadListener listener;
	private PN532Interface<T> connection;
	
	private boolean closed = false;

	public PN532SamThread(PN532SamThreadListener listener, PN532Interface<T> connection) throws IllegalArgumentException {
		if (listener == null) {
			throw new IllegalArgumentException("PN532SamThread constructed with null listener.");
		} else if (connection == null) {
			throw new IllegalArgumentException("PN532SamThread constructed with null connection.");
		}

		this.listener = listener;
		this.connection = connection;
	}

	@Override
	public void run() {
		PN532<T> pn532 = new PN532<T>(connection);

		try {
			pn532.initialize();
		} catch (IllegalArgumentException | IllegalStateException | InterruptedException | IOException e) {
			println(pn532, "begin() error: " + e.getMessage());
			return;
		} catch (UndeclaredThrowableException e) { // Handle pigpio permissions exception
			println(pn532, "begin() error: " + getNestedMessage(e));
			return;
		}
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			println(pn532, "initialization Thread.sleep() interrupted.");
			return;
		}

		long version;
		try {
			version = pn532.getFirmwareVersion();
		} catch (IllegalStateException | InterruptedException | IOException e) {
			println(pn532, "getFirmwareVersion() error: " + e.getMessage());
			return;
		}
		if (version == 0) {
			println(pn532, "getFirmwareVersion() returned 0.");
			return;
		}

		pn532.setModelName("PN5" + Long.toHexString((version >> 24) & 0xFF));
		println(pn532, "found with FW: " + Long.toHexString((version >> 16) & 0xFF)
				+ "." + Long.toHexString((version >> 8) & 0xFF));

		// Configure board to read RFID tags
		try {
			if (!pn532.samConfig()) {
				println(pn532, "samConfig() failed.");
				return;
			}
		} catch (IllegalStateException | InterruptedException | IOException e) {
			println(pn532, "samConfig() error: " + e.getMessage());
			return;
		}
		println(pn532, "running.");

		byte[] buffer = new byte[8];
		while (!closed) {
			int length;
			try {
				length = pn532.readPassiveTargetId(MIFARE_ISO14443A, buffer);
			} catch (IllegalStateException | InterruptedException | IOException e) {
				println(pn532, "readPassiveTargetId() error: " + e.getMessage());
				return;
			}

			if (length > 0) {
				StringBuilder uid = new StringBuilder();

				for (int i = 0; i < length; i++) {
					uid.append(Integer.toHexString(buffer[i]));
				}

				listener.uidReceived(pn532.getDisplayName(), uid.toString());
			}

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				println(pn532, "running Thread.sleep() interrupted.");
				return;
			}
		}
		
		pn532.close();
	}
	
	public void close() {
		closed = true;
	}

	private void println(PN532<T> pn532, String message) {
		listener.println(pn532.prefixMessage(message));
	}

	public static String getNestedMessage(Throwable throwable) {
		// Decided to stop at first throwable with a message rather than the deepest cause
		if (throwable.getMessage() == null && throwable.getCause() != null) {
			var cause = throwable.getCause();
			while (cause.getMessage() == null && cause.getCause() != null) {
				cause = cause.getCause();
			}
			return cause.getMessage();
		} else {
			return throwable.getMessage();
		}
	}
}
