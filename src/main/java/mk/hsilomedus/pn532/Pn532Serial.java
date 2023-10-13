package mk.hsilomedus.pn532;

import java.nio.ByteBuffer;

import com.pi4j.io.exception.IOException;
import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialProvider;

public class Pn532Serial extends Pn532Connection<Serial> {

	public static final String DEFAULT_PROVIDER = "pigpio-serial";
	public static final String DEFAULT_DEVICE = "/dev/ttyAMA0";

	private static final byte[] WAKUEP = { 0x55, 0x55, 0, 0, 0 };

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
	protected void wakeupInternal() throws IOException {
		io.write(WAKUEP);
		io.drain();
	}

	@Override
	protected boolean read(byte[] buffer, int startIndex, int length, int timeout) throws InterruptedException, IOException {
		int readTotal = 0;

		long end = System.currentTimeMillis() + timeout;
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
			if (System.currentTimeMillis() > end) {
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