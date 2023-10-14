package mk.hsilomedus.pn532;

import java.nio.ByteBuffer;

import com.pi4j.io.exception.IOException;
import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialProvider;

public class Pn532Serial extends Pn532Connection<Serial> {

	public static final String DEFAULT_PROVIDER = "pigpio-serial";
	public static final String DEFAULT_DEVICE = "/dev/ttyAMA0";

	// The serial connection will not wake up without this, I have no idea why
	// Originally this was just the first 5 bytes. Now we're sending a lot more 0s and a COMMAND_SAM_CONFIG
	// Sending a different command without side effects like COMMAND_GET_FW_VERSION doesn't seem to work
	// https://stackoverflow.com/a/61220607/22663657
	private static final byte[] WAKEUP = { 0x55, 0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, (byte) 0xFF, 0x03, (byte) 0xFD, (byte) 0xD4, 0x14, 0x01, 0x17, 0x00 };

	private final String device;

	/**
	 * Defaults to {@link Pn532Serial#DEFAULT_PROVIDER} and {@link Pn532Serial#DEFAULT_DEVICE}.
	 */
	public Pn532Serial() {
		this(DEFAULT_PROVIDER, DEFAULT_DEVICE);
	}

	public Pn532Serial(String provider, String device) {
		super(provider, "serial-" + device, "Serial " + device, "Serial Device " + device);

		this.device = device;
	}

	@Override
	protected Serial getInterface() {
		var config = Serial.newConfigBuilder(pi4j)
				.id(id)
				.name(name)
				.device(device)
				.use_115200_N81()
				.build();
		SerialProvider serialProvider = pi4j.provider(provider);
		var serial = serialProvider.create(config);

		serial.open();

		return serial;
	}

	@Override
	protected void wakeupInternal() throws InterruptedException, IOException {
		log("wakeupInternal() sending %s", () -> Pn532Utility.getByteHexString(WAKEUP));
		io.write(WAKEUP);
		io.drain();
	}

	@Override
	protected boolean read(byte[] buffer, int startIndex, int length, long timeoutEnd) throws InterruptedException, IOException {
		int readTotal = 0;
		while (true) {
			int available = io.available();
			if (available > 0) {
				int toRead = Math.min(available, length - readTotal);
				int read = io.read(buffer, startIndex + readTotal, toRead);

				if (read > 0) {
					readTotal += read;
					final int readTotalFinal = readTotal;
					log("read() has so far received " + readTotal + " bytes: %s", () -> Pn532Utility.getByteHexString(buffer, startIndex, readTotalFinal));

					if (readTotal >= length) { // Shouldn't happen, but >= for safety
						return true;
					}
				}
			}

			Thread.sleep(10);
			if (System.currentTimeMillis() > timeoutEnd) {
				return false;
			}
		}
	}

	@Override
	protected void preWrite() throws IOException {
		io.drain();
	}

	@Override
	protected void ioWrite(ByteBuffer buffer) throws IOException {
		io.write(buffer);
	}

	@Override
	protected boolean ioIsOpen() {
		return io.isOpen();
	}

	@Override
	protected void ioClose() {
		io.close();
	}
}