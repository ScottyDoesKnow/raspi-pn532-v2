package mk.hsilomedus.pn532;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;

import com.pi4j.io.IO;
import com.pi4j.io.exception.IOException;

public class PN532SamThread<T extends IO<T, ?, ?>> extends Thread {

	private static final byte MIFARE_ISO14443A = 0x00;

	public interface PN532SamThreadListener {
		void println(String message);

		void uidReceived(String displayName, byte[] uid);

		static String getUidString(byte[] bytes) {
			StringBuilder uid = new StringBuilder();
			for (int i = 0; i < bytes.length; i++) {
				uid.append(String.format("%02X", bytes[i]));
			}
			return uid.toString();
		}
	}

	private final PN532SamThreadListener listener;
	private final PN532Interface<T> connection;

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
		if (version < 0) {
			println(pn532, "getFirmwareVersion() returned " + PN532TransferResult.fromValue((int)version));
			return;
		}
		println(pn532, "found.");

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

		byte[] buffer = new byte[8]; // TODO 8 is too small, isn't it? I think PN532.java will only go to 14 which also seems too small
		while (!closed) {
			int length;
			try {
				length = pn532.readPassiveTargetId(MIFARE_ISO14443A, buffer);
			} catch (IllegalStateException | InterruptedException | IOException e) {
				println(pn532, "readPassiveTargetId() error: " + e.getMessage());
				return;
			}

			if (length > 0) {
				byte[] uid = Arrays.copyOfRange(buffer, 0, length);
				listener.uidReceived(pn532.getDisplayName(), uid);
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
