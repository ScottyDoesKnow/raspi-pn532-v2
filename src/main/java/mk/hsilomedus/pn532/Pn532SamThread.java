package mk.hsilomedus.pn532;

import java.io.IOException;
import java.util.Arrays;

import com.pi4j.io.IO;

public class Pn532SamThread<T extends IO<T, ?, ?>> extends Thread {

	private final Pn532SamThreadListener listener;
	private final Pn532Connection<T> connection;

	private boolean closed = false;

	public Pn532SamThread(Pn532SamThreadListener listener, Pn532Connection<T> connection) {
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
		try (var pn532 = new Pn532<>(connection)) {
			try {
				pn532.initialize();
			} catch (InterruptedException | IOException e) {
				println(pn532, "begin() error: " + e.getMessage());
				handleInterruptedException(e);
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
			} catch (InterruptedException | IOException e) {
				println(pn532, "getFirmwareVersion() error: " + e.getMessage());
				handleInterruptedException(e);
				return;
			}

			if (version < 0) {
				println(pn532, "getFirmwareVersion() returned " + Pn532TransferResult.fromValue((int) version));
				return;
			}
			println(pn532, "device found.");

			// Configure board to read RFID tags
			try {
				if (!pn532.samConfig()) {
					println(pn532, "samConfig() failed.");
					return;
				}
			} catch (InterruptedException | IOException e) {
				println(pn532, "samConfig() error: " + e.getMessage());
				handleInterruptedException(e);
				return;
			}
			println(pn532, "configured for SAM and running.");

			var buffer = new byte[10]; // UID should be max 10 bytes
			while (!closed) {
				int length;
				try {
					length = pn532.readPassiveTargetId(buffer);
				} catch (InterruptedException | IOException e) {
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

	private void println(Pn532<T> pn532, String message) {
		listener.receiveMessage(pn532.prefixMessage(message));
	}

	private void handleInterruptedException(Exception e) {
		if (e instanceof InterruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	public interface Pn532SamThreadListener {
		void receiveMessage(String message);

		void uidReceived(String displayName, byte[] uid);

		static String getUidString(byte[] bytes) {
			var uid = new StringBuilder();
			for (byte value : bytes) {
				uid.append(String.format("%02X", value));
			}
			return uid.toString();
		}
	}
}
