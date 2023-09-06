
package mk.hsilomedus.pn532;

import java.lang.reflect.UndeclaredThrowableException;
import java.nio.ByteBuffer;

import com.pi4j.io.exception.IOException;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CProvider;

public class PN532I2C extends PN532Interface<I2C> {

	public static final String PROVIDER_LINUXFS = "linuxfs-i2c";
	public static final String PROVIDER_PIGPIO = "pigpio-i2c";

	public static final String DEFAULT_PROVIDER = PROVIDER_LINUXFS;
	public static final int DEFAULT_BUS = 1;
	public static final int DEFAULT_DEVICE = 0x24;

	private final int bus;
	private final int device;

	/**
	 * Defaults to {@link PN532I2C#DEFAULT_PROVIDER}, {@link PN532I2C#DEFAULT_BUS}, and {@link PN532I2C#DEFAULT_DEVICE}.
	 */
	public PN532I2C() {
		this(DEFAULT_PROVIDER, DEFAULT_BUS, DEFAULT_DEVICE);
	}

	/**
	 * @param provider The provider to use. Options are {@link PN532I2C#PROVIDER_LINUXFS} or {@link PN532I2C#PROVIDER_PIGPIO}.
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
	protected boolean readFully(byte[] buffer, int timeout) throws InterruptedException, IOException {
		var bufferTemp = new byte[buffer.length + 1];

		long end = System.currentTimeMillis() + timeout;
		while (true) {
			if (io.read(bufferTemp, bufferTemp.length) == bufferTemp.length && (bufferTemp[0] & 1) != 0) {
				System.arraycopy(bufferTemp, 1, buffer, 0, buffer.length);
				log("readFully() received " + buffer.length + " bytes: %s", () -> PN532Utility.getByteHexString(buffer));
				return true;
			}

			Thread.sleep(10);
			if (System.currentTimeMillis() > end) {
				return false;
			}
		}
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
