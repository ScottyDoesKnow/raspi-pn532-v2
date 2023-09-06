package mk.hsilomedus.pn532;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;

import com.pi4j.io.IO;
import com.pi4j.io.exception.IOException;

public class PN532SamThread<T extends IO<T, ?, ?>> extends Thread {

	private static final byte MIFARE_ISO14443A = 0x00;

	public interface PN532SamThreadListener {
		void receiveMessage(String message);

		void uidReceived(String displayName, byte[] uid);

		public static String getUidString(byte[] bytes) {
			var uid = new StringBuilder();
			for (var i = 0; i < bytes.length; i++) {
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
		try (var pn532 = new PN532<>(connection)) {
			try {
				pn532.initialize();
			} catch (IllegalArgumentException | IllegalStateException | InterruptedException | IOException e) {
				println(pn532, "begin() error: " + e.getMessage());
				handleInterruptedException(e);
				return;
			} catch (UndeclaredThrowableException e) { // Handle pigpio permissions exception
				println(pn532, "begin() error: " + PN532Utility.getNestedMessage(e));
				return;
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				println(pn532, "initialization Thread.sleep() interrupted.");
				handleInterruptedException(e);
				return;
			}

			long version;
			try {
				version = pn532.getFirmwareVersion();
			} catch (IllegalStateException | InterruptedException | IOException e) {
				println(pn532, "getFirmwareVersion() error: " + e.getMessage());
				handleInterruptedException(e);
				return;
			}

			if (version < 0) {
				println(pn532, "getFirmwareVersion() returned " + PN532TransferResult.fromValue((int) version));
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
				handleInterruptedException(e);
				return;
			}
			println(pn532, "running.");

			var buffer = new byte[8]; // TODO 8 is too small, isn't it? I think PN532.java will only go to 14 which also seems too small
			while (!closed) {
				int length;
				try {
					length = pn532.readPassiveTargetId(MIFARE_ISO14443A, buffer);
				} catch (IllegalStateException | InterruptedException | IOException e) {
					println(pn532, "readPassiveTargetId() error: " + e.getMessage());
					handleInterruptedException(e);
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
					handleInterruptedException(e);
					return;
				}
			}
		}
	}

	public void close() {
		closed = true;
	}

	private void println(PN532<T> pn532, String message) {
		listener.receiveMessage(pn532.prefixMessage(message));
	}

	private void handleInterruptedException(Exception e) {
		if (e instanceof InterruptedException) {
			Thread.currentThread().interrupt();
		}
	}
}
