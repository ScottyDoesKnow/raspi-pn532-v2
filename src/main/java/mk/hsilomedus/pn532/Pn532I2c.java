
package mk.hsilomedus.pn532;

import java.nio.ByteBuffer;

import com.pi4j.io.exception.IOException;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CProvider;

public class Pn532I2c extends Pn532Connection<I2C> {

	public static final String PROVIDER_PIGPIO = "pigpio-i2c";
	public static final String PROVIDER_LINUXFS = "linuxfs-i2c";

	public static final String DEFAULT_PROVIDER = PROVIDER_PIGPIO;
	public static final int DEFAULT_BUS = 1;
	public static final int DEFAULT_DEVICE = 0x24;

	private static final byte[] PN532_NACK = { 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00 };

	private final int bus;
	private final int device;

	/**
	 * Defaults to {@link Pn532I2c#DEFAULT_PROVIDER}, {@link Pn532I2c#DEFAULT_BUS}, and {@link Pn532I2c#DEFAULT_DEVICE}.
	 */
	public Pn532I2c() {
		this(DEFAULT_PROVIDER, DEFAULT_BUS, DEFAULT_DEVICE);
	}

	/**
	 * @param provider the provider to use. Options are {@link Pn532I2c#PROVIDER_LINUXFS} or {@link Pn532I2c#PROVIDER_PIGPIO}.
	 */
	public Pn532I2c(String provider, int bus, int device) {
		super(provider, "i2c-" + bus + "-0x" + Integer.toHexString(device),
				"I2C " + bus + " 0x" + Integer.toHexString(device),
				"I2C Bus " + bus + ", Device 0x" + Integer.toHexString(device));

		this.bus = bus;
		this.device = device;
	}

	@Override
	protected I2C getInterface() {
		var config = I2C.newConfigBuilder(pi4j)
				.id(id)
				.name(name)
				.bus(bus)
				.device(device)
				.build();
		I2CProvider i2cProvider = pi4j.provider(provider);
		return i2cProvider.create(config);
	}

	@Override
	protected void wakeupInternal() throws InterruptedException {
		Thread.sleep(500);
	}

	@Override
	protected boolean read(byte[] buffer, int startIndex, int length, long timeoutEnd) throws InterruptedException, IOException {
		// I2C ignores startIndex and re-reads everything
		var bufferTemp = new byte[startIndex + length + 1];
		while (true) {
			if (io.read(bufferTemp, bufferTemp.length) == bufferTemp.length && (bufferTemp[0] & 1) != 0) {
				System.arraycopy(bufferTemp, 1 + startIndex, buffer, startIndex, length);
				log("read() received " + length + " bytes: %s", () -> Pn532Utility.getByteHexString(buffer, startIndex, length));
				return true;
			}

			Thread.sleep(10);
			if (System.currentTimeMillis() > timeoutEnd) {
				return false;
			}
		}
	}

	@Override
	protected void preSubsequentRead() throws IOException {
		log("preSubsequentRead() sending %s", () -> Pn532Utility.getByteHexString(PN532_NACK));
		io.write(PN532_NACK);
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
