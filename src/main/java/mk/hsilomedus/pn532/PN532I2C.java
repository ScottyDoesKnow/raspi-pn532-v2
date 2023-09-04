
package mk.hsilomedus.pn532;

import java.lang.reflect.UndeclaredThrowableException;
import java.nio.ByteBuffer;

import com.pi4j.io.exception.IOException;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CProvider;

public class PN532I2C extends PN532Interface<I2C> {

	public static final String PROVIDER_PIGPIO = "pigpio-i2c";
	public static final String PROVIDER_LINUXFS = "linuxfs-i2c";

	public static final String DEFAULT_PROVIDER = PROVIDER_PIGPIO;
	public static final int DEFAULT_BUS = 1;
	public static final int DEFAULT_DEVICE = 0x24;

	private int bus;
	private int device;

	/**
	 * Defaults to {@link PN532I2C#DEFAULT_PROVIDER}, {@link PN532I2C#DEFAULT_BUS}, and {@link PN532I2C#DEFAULT_DEVICE}.
	 */
	public PN532I2C() {
		this(DEFAULT_PROVIDER, DEFAULT_BUS, DEFAULT_DEVICE);
	}

	/**
	 * @param provider The provider to use. Options are {@link PN532I2C#PROVIDER_PIGPIO} or {@link PN532I2C#PROVIDER_LINUXFS}.
	 */
	public PN532I2C(String provider, int bus, int device) {
		super(provider, "i2c-" + bus + "-0x" + Integer.toHexString(device),
				"I2C " + bus + " 0x" + Integer.toHexString(device),
				"I2C Bus " + bus + ", Device 0x" + Integer.toHexString(device));

		this.bus = bus;
		this.device = device;
	}

	@Override
	protected I2C getInterface() throws IllegalArgumentException, UndeclaredThrowableException {
		var config = I2C.newConfigBuilder(pi4j)
				.id(id)
				.name(name)
				.bus(bus)
				.device(device)
				.build();
		I2CProvider i2CProvider = pi4j.provider(provider);
		return i2CProvider.create(config);
	}

	@Override
	protected void wakeupInternal() throws InterruptedException {
		Thread.sleep(500);
	}

	@Override
	protected void writeCommandInternal(byte[] header, byte[] body) throws IOException {
		var buffer = ByteBuffer.allocate(header.length + body.length + 8);

		lastCommand = header[0];

		buffer.put(PN532_PREAMBLE);
		buffer.put(PN532_STARTCODE1);
		buffer.put(PN532_STARTCODE2);

		byte length = (byte) (header.length + body.length + 1);
		buffer.put(length);
		buffer.put((byte) (~length + 1));

		buffer.put(PN532_HOSTTOPN532);
		buffer.put(header);
		buffer.put(body);

		byte sum = PN532_HOSTTOPN532;
		for (int i = 0; i < header.length; i++) {
			sum += header[i];
		}
		for (int i = 0; i < body.length; i++) {
			sum += body[i];
		}
		buffer.put((byte) (~sum + 1));

		buffer.put(PN532_POSTAMBLE);

		log("writeCommand() sending " + PN532Debug.getByteString(buffer.array()));
		io.write(buffer);
	}

	@Override
	protected PN532CommandStatus readAckFrame() throws InterruptedException, IOException {
		byte[] buffer = new byte[PN532_ACK.length + 1];

		if (!waitForData(buffer, ackTimeout)) {
			log("readAckFrame() timed out.");
			return PN532CommandStatus.TIMEOUT;
		}

		for (int i = 1; i < buffer.length; i++) {
			if (buffer[i] != PN532_ACK[i - 1]) {
				log("readAckFrame() was invalid.");
				return PN532CommandStatus.INVALID;
			}
		}

		log("readAckFrame() successful.");
		return PN532CommandStatus.OK;
	}

	@Override
	protected int readResponseInternal(byte[] buffer, int expectedLength, int timeout) throws InterruptedException, IOException {
		byte[] response = new byte[expectedLength + 2];

		if (!waitForData(response, timeout)) {
			logRead("readResponse() timed out.");
			return -1;
		}

		int i = 1;
		if (response[i++] != PN532_PREAMBLE || response[i++] != PN532_STARTCODE1 || response[i++] != PN532_STARTCODE2) {
			logRead("readResponse() received bad starting bytes.");
			return -1;
		}

		byte length = response[i++];

		byte lengthCheck = (byte) (length + response[i++]);
		if (lengthCheck != 0) {
			logRead("readResponse() received bad length checksum.");
			return -1;
		}

		byte command = (byte) (lastCommand + 1);
		if (response[i++] != PN532_PN532TOHOST || response[i++] != command) {
			logRead("readResponse() received bad command.");
			return -1;
		}

		length -= 2;
		if (length > expectedLength) {
			logRead("readResponse() received length greater than expectedLength.");
			return -1;
		}

		byte sum = PN532_PN532TOHOST;
		sum += command;

		for (int j = 0; j < length; j++) {
			buffer[j] = response[i++];
			sum += buffer[j];
		}

		byte check = (byte) (sum + response[i++]);
		if (check != 0) {
			logRead("readResponse() received bad checksum.");
			return -1;
		}

		if (response[i] != PN532_POSTAMBLE) {
			logRead("readResponse() received bad postamble.");
			return -1;
		}

		logRead("readResponse() returned " + length + " bytes: " + PN532Debug.getByteString(buffer));
		return length;
	}

	@Override
	void close() {
		log("close()");
		if (io != null && io.isOpen()) {
			io.close();
		}
		log("close() successful.");
	}

	private boolean waitForData(byte[] buffer, int timeout) throws InterruptedException, IOException {
		long end = System.currentTimeMillis() + timeout;
		while (true) {
			int readTotal = io.read(buffer);
			if (readTotal > 0 && (buffer[0] & 1) == 1) {
				logRead("waitForData() received " + readTotal + " bytes: " + PN532Debug.getByteString(buffer));

				while (readTotal < buffer.length) {
					int read = io.read(buffer, readTotal, buffer.length - readTotal);

					if (read > 0) {
						logRead("waitForData() received " + read + " bytes: " + PN532Debug.getByteString(buffer));

						readTotal += read;
						if (readTotal >= buffer.length) { // >= for safety
							break;
						}
					}

					Thread.sleep(10);
					if (System.currentTimeMillis() > end) {
						return false;
					}
				}

				return true;
			}

			Thread.sleep(10);
			if (System.currentTimeMillis() > end) {
				return false;
			}
		}
	}
}
