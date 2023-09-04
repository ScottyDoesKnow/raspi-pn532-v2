package mk.hsilomedus.pn532;

import java.lang.reflect.UndeclaredThrowableException;

import com.pi4j.context.Context;
import com.pi4j.io.IO;
import com.pi4j.io.exception.IOException;

// Don't know how to do this without the ?
public abstract class PN532Interface<T extends IO<T, ?, ?>> {

	public static final int DEFAULT_ACK_TIMEOUT = 1000; // TODO: lower? Was 10 in original library
	public static final int DEFAULT_READ_TIMEOUT = 1000;

	protected static final byte PN532_PREAMBLE = 0x00; // TODO rename all these
	protected static final byte PN532_STARTCODE1 = 0x00;
	protected static final byte PN532_STARTCODE2 = (byte) 0xFF;
	protected static final byte PN532_POSTAMBLE = 0x00;

	protected static final byte PN532_HOSTTOPN532 = (byte) 0xD4;
	protected static final byte PN532_PN532TOHOST = (byte) 0xD5;

	protected static final int PN532_TIMEOUT = -2; // TODO normalize
	protected static final int PN532_INVALID_FRAME = -3; // TODO
	protected static final int PN532_NO_SPACE = -4; // TODO

	protected static final byte PN532_ACK[] = new byte[] { 0, 0, (byte) 0xFF, 0, (byte) 0xFF, 0 };

	protected int ackTimeout = DEFAULT_ACK_TIMEOUT;
	protected int readTimeout = DEFAULT_READ_TIMEOUT;

	protected String provider;
	protected String id;
	protected String name;
	protected String modelName = "PN5xx";
	protected String displaySuffix;

	protected Context pi4j = PN532ContextHelper.getContext();
	protected T io = null;
	protected byte lastCommand = 0;

	public int getAckTimeout() {
		return ackTimeout;
	}

	public void setAckTimeout(int value) {
		ackTimeout = value;
	}

	public int getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(int value) {
		readTimeout = value;
	}

	void setModelName(String value) {
		modelName = value;
	}

	public String getDisplayName() {
		return modelName + " " + displaySuffix;
	}

	public PN532Interface(String provider, String id, String name, String displaySuffix) {
		this.provider = provider;
		this.id = "pn5xx-" + id;
		this.name = "PN5xx " + name;
		this.displaySuffix = displaySuffix;
	}

	void begin() throws IllegalArgumentException, IllegalStateException, UndeclaredThrowableException {
		synchronized (PN532ContextHelper.mutex) { // pigpio crashes the JRE without this
			log("begin()");

			if (io != null) {
				throw new IllegalStateException(prefixMessage("begin() can only be called once."));
			}

			io = getInterface();

			log("begin() successful.");
		}
	}

	protected abstract T getInterface() throws IllegalArgumentException, UndeclaredThrowableException;

	void wakeup() throws IllegalStateException, InterruptedException, IOException {
		log("wakeup()");

		if (io == null) {
			throw new IllegalStateException(prefixMessage("wakeup() called without calling begin()."));
		}

		wakeupInternal();

		log("wakeup() successful.");
	}

	protected abstract void wakeupInternal() throws InterruptedException, IOException;

	PN532CommandStatus writeCommand(byte[] header, byte[] body) throws IllegalArgumentException, IllegalStateException, InterruptedException, IOException {
		log("writeCommand(header: " + PN532Debug.getByteString(header) + ", body: " + PN532Debug.getByteString(body) + ")");

		if (io == null) {
			throw new IllegalStateException(prefixMessage("writeCommand() called without calling begin()."));
		} else if (header == null) {
			throw new IllegalArgumentException(prefixMessage("writeCommand() called with null header."));
		} else if (body == null) {
			throw new IllegalArgumentException(prefixMessage("writeCommand() called with null body."));
		}

		writeCommandInternal(header, body);

		log("writeCommand() calling readAckFrame()");
		return readAckFrame();
	}

	protected abstract void writeCommandInternal(byte[] header, byte[] body) throws InterruptedException, IOException;

	protected abstract PN532CommandStatus readAckFrame() throws InterruptedException, IOException;

	PN532CommandStatus writeCommand(byte[] header) throws IllegalArgumentException, IllegalStateException, InterruptedException, IOException {
		return writeCommand(header, new byte[0]);
	}

	int readResponse(byte[] buffer, int expectedLength, int timeout) throws IllegalArgumentException, IllegalStateException, InterruptedException, IOException {
		logRead("readResponse()"); // Make it same as original library?

		if (io == null) {
			throw new IllegalStateException(prefixMessage("readResponse() called without calling begin()."));
		} else if (buffer == null) {
			throw new IllegalArgumentException(prefixMessage("readResponse() called with null buffer."));
		}

		return readResponseInternal(buffer, expectedLength, timeout);
	}

	protected abstract int readResponseInternal(byte[] buffer, int expectedLength, int timeout) throws InterruptedException, IOException;

	int readResponse(byte[] buffer, int expectedLength) throws IllegalArgumentException, IllegalStateException, InterruptedException, IOException {
		return readResponse(buffer, expectedLength, readTimeout);
	}

	abstract void close();

	void log(String message) {
		PN532Debug.log(prefixMessage(message));
	}

	void logRead(String message) {
		PN532Debug.logRead(prefixMessage(message));
	}

	String prefixMessage(String message) {
		return getDisplayName() + ": " + message;
	}
}