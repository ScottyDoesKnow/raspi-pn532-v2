package mk.hsilomedus.pn532;

import java.nio.ByteBuffer;

import com.pi4j.io.exception.IOException;
import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialProvider;

// TODO not working yet, not enough power? 5v vs 3.3v?
public class PN532Serial extends PN532Interface<Serial> {

	public static final String DEFAULT_PROVIDER = "pigpio-serial";
	public static final String DEFAULT_DEVICE = "/dev/ttyAMA0"; // TODO /dev/serial0 https://raspberrypi.stackexchange.com/a/45571

	private static final byte[] WAKUEP = { 0x55, 0x55, 0, 0, 0 };

	private final String device;

	/**
	 * Defaults to {@link PN532Serial#DEFAULT_PROVIDER} and {@link PN532Serial#DEFAULT_DEVICE}.
	 */
	public PN532Serial() {
		this(DEFAULT_PROVIDER, DEFAULT_DEVICE);
	}

	public PN532Serial(String provider, String device) {
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
		//io.flush(); // TODO from Java code
		io.drain();
	}

	@Override
	protected boolean readFully(byte[] buffer, int timeout) throws InterruptedException, IOException {
		var readTotal = 0;

		var end = System.currentTimeMillis() + timeout;
		while (true) {
			var available = io.available();
			if (available > 0) {
				var toRead = Math.min(available, buffer.length - readTotal);
				var read = io.read(buffer, readTotal, toRead);

				if (read > 0) {
					final var readTotalFinal = readTotal;
					log("readFully() has so far received " + readTotal + " bytes: %s", () -> PN532Utility.getByteHexString(buffer, readTotalFinal));

					readTotal += read;
					if (readTotal >= buffer.length) { // Shouldn't happen, but >= for safety
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
	protected void postWrite() throws IOException {
		//io.flush(); // TODO from Java code
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