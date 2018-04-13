package mk.hsilomedus.pn532;

import java.io.IOException;

import com.pi4j.io.gpio.exception.UnsupportedBoardType;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;

public interface IPN532Interface {
	
	static final byte PN532_PREAMBLE = 0x00;
	static final byte PN532_STARTCODE1 = 0x00;
	static final byte PN532_STARTCODE2 = (byte) 0xFF;
	static final byte PN532_POSTAMBLE = 0x00;

	static final byte PN532_HOSTTOPN532 = (byte) 0xD4;
	static final byte PN532_PN532TOHOST = (byte) 0xD5;

	public abstract void begin() throws IOException, UnsupportedBoardType, InterruptedException, UnsupportedBusNumberException;

	public abstract void wakeup() throws IllegalStateException, IOException;

	public abstract CommandStatus writeCommand(byte[] header, byte[] body) throws InterruptedException, IllegalStateException, IOException;

	public abstract CommandStatus writeCommand(byte header[]) throws InterruptedException, IllegalStateException, IOException;

	public abstract int readResponse(byte[] buffer, int expectedLength, int timeout) throws InterruptedException, IllegalStateException, IOException;

	public abstract int readResponse(byte[] buffer, int expectedLength) throws InterruptedException, IllegalStateException, IOException;
}