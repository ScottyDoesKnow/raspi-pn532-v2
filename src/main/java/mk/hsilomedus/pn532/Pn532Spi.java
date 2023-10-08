package mk.hsilomedus.pn532;

import java.nio.ByteBuffer;

import com.pi4j.io.exception.IOException;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalOutputProvider;
import com.pi4j.io.spi.Spi;
import com.pi4j.io.spi.SpiProvider;

// TODO Don't know what's up with the whole reset pin thing from the Java code
public class Pn532Spi extends Pn532Connection<Spi> {

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

	private final String providerDo;
	private final int channel;
	private final int csPin;

	private DigitalOutput csOutput;

	/**
	 * Defaults to {@link Pn532Spi#DEFAULT_PROVIDER}, {@link Pn532Spi#DEFAULT_PROVIDER_DO},
	 *  {@link Pn532Spi#DEFAULT_CHANNEL}, and {@link Pn532Spi#CS_PIN_CE0}.
	 */
	public Pn532Spi() {
		this(DEFAULT_PROVIDER, DEFAULT_PROVIDER_DO, DEFAULT_CHANNEL, CS_PIN_CE0);
	}

	/**
	 * @param channel The SPI channel to use. Common values are 0 and 1.
	 * @param csPin The Chip Select Pin to use. Common values are {@link Pn532Spi#CS_PIN_CE0}
	 *     and {@link Pn532Spi#CS_PIN_CE1}, but any GPIO can be used.
	 */
	public Pn532Spi(String provider, String providerDo, int channel, int csPin) {
		super(provider, "spi-" + channel + "-" + csPin, "SPI " + channel + " " + csPin,
				"SPI Channel " + channel + ", CS Pin " + csPin);

		this.providerDo = providerDo;
		this.channel = channel;
		this.csPin = csPin;
	}

	@Override
	protected Spi getInterface() {
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

		// TODO csOutput was created here in Java code

		return spi;
	}

	@Override
	protected void wakeupInternal() throws InterruptedException, IOException {
		// TODO Was high and then low in Java code
		csLow();
		csOutput.high();
	}

	@Override
	protected boolean readFully(byte[] buffer, int timeout) throws InterruptedException, IOException {
		int readTotal = 0;

		long end = System.currentTimeMillis() + timeout;
		while (true) {
			if (!isReady()) {
				Thread.sleep(10);
				if (System.currentTimeMillis() > end) {
					return false;
				}
			} else {
				csLow();
				writeByte(SPI_DATA_READ, true);

				try {
					while (true) {
						int read = io.read(buffer, readTotal, buffer.length - readTotal); // Processed in finally

						if (read > 0) {
							final int readTotalFinal = readTotal;
							log("readFully() has so far received " + readTotal + " (reversed) bytes: %s", () -> Pn532Utility.getByteHexString(buffer, readTotalFinal));

							readTotal += read;
							if (readTotal >= buffer.length) { // Shouldn't happen, but >= for safety
								return true;
							}
						}

						Thread.sleep(10);
						if (System.currentTimeMillis() > end) {
							return false;
						}
					}
				} finally {
					csOutput.high();
					reverseBytes(buffer);
				}
			}
		}
	}

	@Override
	protected void preWrite() throws InterruptedException, IOException {
		csLow();
		writeByte(SPI_DATA_WRITE, true);
	}

	@Override
	protected void postWrite() throws IOException {
		csOutput.high();
	}

	@Override
	protected void ioWrite(ByteBuffer buffer) throws IOException {
		var bufferArray = buffer.array();
		reverseBytes(bufferArray);
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

	private boolean isReady() throws IOException {
		csOutput.low(); // No delay in C++ code, so not calling csLow()
		writeByte(SPI_STATUS_READ, false); // Not logged because isReady() spams too much

		boolean result = reverseByte(io.readByte()) == SPI_READY;
		csOutput.high();

		return result;
	}

	// There was a 1-2ms delay in every place but one in the C++ code
	private void csLow() throws InterruptedException, IOException {
		csOutput.low();
		Thread.sleep(2);
	}

	private void writeByte(byte value, boolean log) throws IOException {
		if (log) {
			log("writeByte() wrote " + String.format("%02X", value));
		}

		io.write(reverseByte(value));
	}

	// TODO From the Java code, should try to understand and maybe optimize this
	private byte reverseByte(byte value) {
		byte input = value;
		byte output = 0;

		for (int i = 0; i < 8; i++) {
			if ((input & 0x01) > 0) {
				output |= 1 << (7 - i);
			}
			input = (byte) (input >> 1);
		}

		return output;
	}

	private void reverseBytes(byte[] values) {
		for (int i = 0; i < values.length; i++) {
			values[i] = reverseByte(values[i]);
		}
	}
}