package mk.hsilomedus.pn532;

import java.lang.reflect.UndeclaredThrowableException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.pi4j.io.exception.IOException;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalOutputProvider;
import com.pi4j.io.spi.Spi;
import com.pi4j.io.spi.SpiProvider;

// TODO Don't know what's up with the whole reset pin thing
public class PN532Spi extends PN532Interface<Spi> {

	public static final String PROVIDER_DO_PIGPIO = "pigpio-digital-output";
	public static final String PROVIDER_DO_LINUXFS = "linuxfs-digital-output";

	public static final String DEFAULT_PROVIDER = "pigpio-spi";
	public static final String DEFAULT_PROVIDER_DO = PROVIDER_DO_PIGPIO;
	public static final int DEFAULT_CHANNEL = 0; // TODO was 1
	public static final int CS_PIN_CE0 = 8;
	public static final int CS_PIN_CE1 = 7;

	private static final byte SPI_READY = 0x01;
	private static final byte SPI_DATA_WRITE = 0x01;
	private static final byte SPI_STATUS_READ = 0x02;
	private static final byte SPI_DATA_READ = 0x03;

	private String providerDo;
	private int channel;
	private int csPin;

	private DigitalOutput csOutput;

	/**
	 * Defaults to {@link PN532Spi#DEFAULT_PROVIDER}, {@link PN532Spi#DEFAULT_PROVIDER_DO},
	 *  {@link PN532Spi#DEFAULT_CHANNEL}, and {@link PN532Spi#CS_PIN_CE0}.
	 */
	public PN532Spi() {
		this(DEFAULT_PROVIDER, DEFAULT_PROVIDER_DO, DEFAULT_CHANNEL, CS_PIN_CE0);
	}

	/**
	 * @param channel The SPI channel to use. Common values are 0 and 1.
	 * @param csPin The Chip Select Pin to use. Common values are {@link PN532Spi#CS_PIN_CE0}
	 *  and {@link PN532Spi#CS_PIN_CE1}, but any GPIO can be used.
	 */
	public PN532Spi(String provider, String providerDo, int channel, int csPin) {
		super(provider, "spi-" + channel + "-" + csPin, "SPI " + channel + " " + csPin,
				"SPI Channel " + channel + ", CS Pin " + csPin);

		this.providerDo = providerDo;
		this.channel = channel;
		this.csPin = csPin;
	}

	@Override
	protected Spi getInterface() throws IllegalArgumentException, UndeclaredThrowableException {
		DigitalOutputProvider doProvider = pi4j.provider(providerDo);
		csOutput = doProvider.create(DigitalOutput.newConfigBuilder(pi4j).address(csPin).build());

		var config = Spi.newConfigBuilder(pi4j)
				.id(id)
				.name(name)
				.address(channel)
				.baud(Spi.DEFAULT_BAUD)
				.build();
		SpiProvider spiProvider = pi4j.provider(provider);
		var spi = spiProvider.create(config);

		// TODO csOutput was created here in Java version

		return spi;
	}

	@Override
	protected void wakeupInternal() throws InterruptedException, IOException {
		// TODO Was high and then low in Java version
		csLow();
		csOutput.high();
	}

	@Override
	protected void writeCommandInternal(byte[] header, byte[] body) throws InterruptedException, IOException {
		csLow();

		var buffer = ByteBuffer.allocate(header.length + body.length + 8 + 1); // + 1 for DATAWRITE

		lastCommand = header[0];

		putReverse(buffer, SPI_DATA_WRITE);
		
		putReverse(buffer, PN532_PREAMBLE);
		putReverse(buffer, PN532_STARTCODE1);
		putReverse(buffer, PN532_STARTCODE2);

		byte length = (byte) (header.length + body.length + 1);
		putReverse(buffer, length);
		putReverse(buffer, (byte) (~length + 1));

		putReverse(buffer, PN532_HOSTTOPN532);
		putReverse(buffer, header);
		putReverse(buffer, body);

		byte sum = PN532_PREAMBLE + PN532_STARTCODE1 + PN532_STARTCODE2 + PN532_HOSTTOPN532;
		for (int i = 0; i < header.length; i++) {
			sum += header[i];
		}
		for (int i = 0; i < body.length; i++) {
			sum += body[i];
		}
		putReverse(buffer, (byte) (~sum + 1));

		putReverse(buffer, PN532_POSTAMBLE);

		log("writeCommand() sending " + PN532Debug.getByteString(buffer.array()));
		io.write(buffer); // Reversed in buffer

		csOutput.high();
	}

	@Override
	protected int readResponseInternal(byte[] buffer, int expectedLength, int timeout) throws InterruptedException, IOException {
		if (!waitForReady(timeout)) {
			logRead("readResponse() timed out.");
			return PN532_TIMEOUT;
		}

		csLow();

		writeByte(SPI_DATA_READ);

		if (readByte() != PN532_PREAMBLE || readByte() != PN532_STARTCODE1 || readByte() != PN532_STARTCODE2) {
			logRead("readResponse() received bad starting bytes.");
			return PN532_INVALID_FRAME;
		}

		byte length = readByte();

		byte lengthCheck = (byte) (length + readByte());
		if (lengthCheck != 0) {
			logRead("readResponse() received bad length checksum.");
			return PN532_INVALID_FRAME;
		}

		byte command = (byte) (lastCommand + 1);
		if (readByte() != PN532_PN532TOHOST || readByte() != command) {
			logRead("readResponse() received bad command.");
			return PN532_INVALID_FRAME;
		}

		length -= 2;
		if (length > expectedLength) {
			for (int i = 0; i < length + 2; i++) {
				readByte(); // Dump message
			}
			
			logRead("readResponse() received length greater than expectedLength.");
			return PN532_NO_SPACE;
		}

		byte sum = PN532_PN532TOHOST;
		sum += command;

		for (int i = 0; i < length; i++) {
			buffer[i] = readByte();
			sum += buffer[i];
		}

		byte check = (byte) (sum + readByte());
		if (check != 0) {
			logRead("readResponse() received bad checksum.");
			return PN532_INVALID_FRAME;
		}

		if (readByte() != PN532_POSTAMBLE) {
			logRead("readResponse() received bad postamble.");
			return PN532_INVALID_FRAME;
		}

		csOutput.high();

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

	@Override
	protected PN532CommandStatus readAckFrame() throws InterruptedException, IOException {
		if (!waitForReady(ackTimeout)) {
			log("readAckFrame() timed out.");
			return PN532CommandStatus.TIMEOUT;
		}

		byte[] buffer = new byte[PN532_ACK.length];
		for (int i = 0; i < buffer.length; i++) {
			buffer[i] = readByte();
		}

		csOutput.high();

		if (!Arrays.equals(buffer, PN532_ACK)) {
			log("readAckFrame() was invalid.");
			return PN532CommandStatus.INVALID;
		}

		log("readAckFrame() successful.");
		return PN532CommandStatus.OK;
	}

	// There was a 1-2ms delay in every place but one in the original C++ code, so why not
	private void csLow() throws InterruptedException, IOException {
		csOutput.low();
		Thread.sleep(2);
	}

	private boolean waitForReady(int timeout) throws InterruptedException, IOException {
		long end = System.currentTimeMillis() + timeout;
		while (true) {
			if (!isReady()) {
				Thread.sleep(10);
				if (System.currentTimeMillis() > end) {
					return false;
				}
			} else {
				return true;
			}
		}
	}

	private boolean isReady() throws InterruptedException, IOException {
		csLow();
		writeByte(SPI_STATUS_READ, true);
		boolean result = readByte() == SPI_READY;
		csOutput.high();

		return result;
	}

	private void writeByte(byte value, boolean logRead) throws IOException {
		if (logRead) {
			logRead("writeByte() wrote " + String.format("%02X", value));
		} else {
			log("writeByte() wrote " + String.format("%02X", value));
		}
		
		io.write(reverse(value)); // Reversed
	}
	
	private void writeByte(byte value) throws IOException {
		writeByte(value, false);
	}

	private byte readByte() throws IOException {
		//Thread.sleep(1); // Only in Java
		
		byte value = reverse(io.readByte()); // Reversed
		logRead("readByte() read " + String.format("%02X", value));
		return value;
	}

	private static void putReverse(ByteBuffer buffer, byte value) {
		buffer.put(reverse(value));
	}

	private static void putReverse(ByteBuffer buffer, byte[] values) {
		for (int i = 0; i < values.length; i++) {
			putReverse(buffer, values[i]);
		}
	}

	// TODO From the Java version, should try to understand and maybe optimize this
	private static byte reverse(byte input) {
		byte output = 0;

		for (int p = 0; p < 8; p++) {
			if ((input & 0x01) > 0) {
				output |= 1 << (7 - p);
			}
			input = (byte) (input >> 1);
		}

		return output;
	}
}