package mk.hsilomedus.pn532;

import java.lang.reflect.UndeclaredThrowableException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.pi4j.io.exception.IOException;
import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialProvider;

// TODO not working yet, not enough power? 5v vs 3.3v?
public class PN532Serial extends PN532Interface<Serial> {

	public static final String DEFAULT_PROVIDER = "pigpio-serial";
	public static final String DEFAULT_DEVICE = "/dev/ttyAMA0"; // TODO failed: /dev/ttyAMA0 /dev/serial0, crashed: /dev/serial1 /dev/ttyS0

	private static final byte[] WAKUEP = new byte[] { 0x55, 0x55, 0, 0, 0 };

	private String device;

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
	protected Serial getInterface() throws IllegalArgumentException, UndeclaredThrowableException {
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
		//io.flush(); // TODO
		io.drain();
	}

	@Override
	protected void writeCommandInternal(byte[] header, byte[] body) throws IOException {
		io.drain();

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
		//io.flush(); // TODO
	}

	@Override
	protected PN532CommandStatus readAckFrame() throws InterruptedException, IOException {
		byte[] buffer = new byte[PN532_ACK.length];

		if (!waitForData(buffer, ackTimeout)) {
			log("readAckFrame() timed out.");
			return PN532CommandStatus.TIMEOUT;
		}

		if (!Arrays.equals(buffer, PN532_ACK)) {
			log("readAckFrame() was invalid.");
			return PN532CommandStatus.INVALID;
		}

		log("readAckFrame() successful.");
		return PN532CommandStatus.OK;
	}

	@Override
	public int readResponseInternal(byte[] buffer, int expectedLength, int timeout) throws InterruptedException, IOException {
		byte[] response = new byte[expectedLength + 2];

		if (!waitForData(response, timeout)) {
			logRead("readResponse() timed out.");
			return PN532_TIMEOUT;
		}

		int i = 0;
		if (response[i++] != PN532_PREAMBLE || response[i++] != PN532_STARTCODE1 || response[i++] != PN532_STARTCODE2) {
			logRead("readResponse() received bad starting bytes.");
			return PN532_INVALID_FRAME;
		}

		byte length = response[i++];

		byte lengthCheck = (byte) (length + response[i++]);
		if (lengthCheck != 0) {
			logRead("readResponse() received bad length checksum.");
			return PN532_INVALID_FRAME;
		}

		byte command = (byte) (lastCommand + 1);
		if (response[i++] != PN532_PN532TOHOST || response[i++] != command) {
			logRead("readResponse() received bad command.");
			return PN532_INVALID_FRAME;
		}

		length -= 2;
		if (length > expectedLength) {
			logRead("readResponse() received length greater than expectedLength.");
			return PN532_NO_SPACE;
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
			return PN532_INVALID_FRAME;
		}

		if (response[i] != PN532_POSTAMBLE) {
			logRead("readResponse() received bad postamble.");
			return PN532_INVALID_FRAME;
		}

		logRead("readResponse() returned " + length + " bytes: " + PN532Debug.getByteString(buffer));
		return length;
	}

	@Override
	public void close() {
		log("close()");
		if (io != null && io.isOpen()) {
			io.close();
		}
		log("close() successful.");
	}

	private boolean waitForData(byte[] buffer, int timeout) throws InterruptedException, IOException {
		int readTotal = 0;

		long end = System.currentTimeMillis() + timeout;
		while (true) {
			if (io.available() == 0) {
				Thread.sleep(10);
				if (System.currentTimeMillis() > end) {
					return false;
				}
			} else {
				int read = io.read(buffer, readTotal, buffer.length - readTotal);

				if (read > 0) {
					logRead("waitForData() received " + read + " bytes: " + PN532Debug.getByteString(buffer));

					readTotal += read;
					if (readTotal >= buffer.length) { // >= for safety
						break;
					}
				}
			}
		}

		return true;
	}
}