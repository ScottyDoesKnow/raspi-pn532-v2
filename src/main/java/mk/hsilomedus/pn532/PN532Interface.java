package mk.hsilomedus.pn532;

import java.lang.reflect.UndeclaredThrowableException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.pi4j.context.Context;
import com.pi4j.io.IO;
import com.pi4j.io.exception.IOException;

// Don't know how to do this without the ?
public abstract class PN532Interface<T extends IO<T, ?, ?>> {

	public static final int DEFAULT_ACK_TIMEOUT = 1000;
	public static final int DEFAULT_READ_TIMEOUT = 1000;

	protected static final byte PREAMBLE = 0x00;
	protected static final byte START_CODE_1 = 0x00;
	protected static final byte START_CODE_2 = (byte) 0xFF;
	protected static final byte POSTAMBLE = 0x00;

	protected static final byte HOST_TO_PN532 = (byte) 0xD4;
	protected static final byte PN532_TO_HOST = (byte) 0xD5;

	protected static final byte PN532_ACK[] = new byte[] { 0, 0, (byte) 0xFF, 0, (byte) 0xFF, 0 };

	protected int ackTimeout = DEFAULT_ACK_TIMEOUT;
	protected int readTimeout = DEFAULT_READ_TIMEOUT;

	protected final String provider;
	protected final String id;
	protected final String name;
	protected final String displaySuffix;

	protected String modelName = "PN5xx";
	protected String firmwareVersion = "";

	protected final Context pi4j = PN532ContextHelper.getContext();
	protected T io = null; // WARNING: If you use this to read or write directly, SPI won't work correctly since it reverses the bytes
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

	public String getDisplayName() {
		return modelName + (firmwareVersion.isEmpty() ? "" : "-" + firmwareVersion) + " " + displaySuffix;
	}

	void setModelName(String value) {
		modelName = value;
	}

	void setFirmwareVersion(String value) {
		firmwareVersion = value;
	}

	public PN532Interface(String provider, String id, String name, String displaySuffix) {
		this.provider = provider;
		this.id = "pn5xx-" + id;
		this.name = "PN5xx " + name;
		this.displaySuffix = displaySuffix;
	}

	public void begin() throws IllegalArgumentException, IllegalStateException, UndeclaredThrowableException {
		synchronized (PN532ContextHelper.mutex) { // pigpio can crash the JRE without this
			log("begin()");

			if (io != null) {
				throw new IllegalStateException(prefixMessage("begin() can only be called once."));
			}

			io = getInterface();

			log("begin() successful.");
		}
	}

	public void wakeup() throws IllegalStateException, InterruptedException, IOException {
		log("wakeup()");

		if (io == null) {
			throw new IllegalStateException(prefixMessage("wakeup() called without calling begin()."));
		}

		wakeupInternal();

		log("wakeup() successful.");
	}

	public PN532TransferResult writeCommand(byte[] header, byte[] body) throws IllegalArgumentException, IllegalStateException, InterruptedException, IOException {
		log("writeCommand(header: " + PN532Debug.getByteHexString(header) + ", body: " + PN532Debug.getByteHexString(body) + ")");

		if (io == null) {
			throw new IllegalStateException(prefixMessage("writeCommand() called without calling begin()."));
		} else if (header == null) {
			throw new IllegalArgumentException(prefixMessage("writeCommand() called with null header."));
		} else if (body == null) {
			throw new IllegalArgumentException(prefixMessage("writeCommand() called with null body."));
		}

		preWrite();

		var buffer = ByteBuffer.allocate(header.length + body.length + 8);

		buffer.put(PREAMBLE);
		buffer.put(START_CODE_1);
		buffer.put(START_CODE_2);

		byte length = (byte) (header.length + body.length + 1);
		buffer.put(length);
		buffer.put((byte) (~length + 1));

		buffer.put(HOST_TO_PN532);
		buffer.put(header);
		buffer.put(body);

		byte sum = HOST_TO_PN532;
		for (int i = 0; i < header.length; i++) {
			sum += header[i];
		}
		for (int i = 0; i < body.length; i++) {
			sum += body[i];
		}
		buffer.put((byte) (~sum + 1));

		buffer.put(POSTAMBLE);

		log("writeCommand() sending " + PN532Debug.getByteHexString(buffer.array()));
		ioWrite(buffer);
		lastCommand = header[0];

		postWrite();

		log("writeCommand() calling readAckFrame()");
		return readAckFrame();
	}

	public PN532TransferResult writeCommand(byte[] header) throws IllegalArgumentException, IllegalStateException, InterruptedException, IOException {
		return writeCommand(header, new byte[0]);
	}

	protected PN532TransferResult readAckFrame() throws InterruptedException, IOException {
		byte[] buffer = new byte[PN532_ACK.length];

		if (!readFully(buffer, ackTimeout)) {
			log("readAckFrame() timed out.");
			return PN532TransferResult.TIMEOUT;
		}

		if (!Arrays.equals(buffer, PN532_ACK)) {
			log("readAckFrame() was invalid.");
			return PN532TransferResult.INVALID_ACK;
		}

		log("readAckFrame() successful.");
		return PN532TransferResult.OK;
	}

	public int readResponse(byte[] buffer, int expectedLength, int timeout) throws IllegalArgumentException, IllegalStateException, InterruptedException, IOException {
		log("readResponse(..., " + expectedLength + ", " + timeout + ")");

		if (io == null) {
			throw new IllegalStateException(prefixMessage("readResponse() called without calling begin()."));
		} else if (buffer == null) {
			throw new IllegalArgumentException(prefixMessage("readResponse() called with null buffer."));
		}

		byte[] response = new byte[expectedLength + 2];

		if (!readFully(response, timeout)) {
			log("readResponse() timed out.");
			return PN532TransferResult.TIMEOUT.getValue();
		}

		int i = 0;
		if (response[i++] != PREAMBLE || response[i++] != START_CODE_1 || response[i++] != START_CODE_2) {
			log("readResponse() received bad starting bytes.");
			return PN532TransferResult.INVALID_FRAME.getValue();
		}

		byte length = response[i++];

		byte lengthCheck = (byte) (length + response[i++]);
		if (lengthCheck != 0) {
			log("readResponse() received bad length checksum.");
			return PN532TransferResult.INVALID_FRAME.getValue();
		}

		byte command = (byte) (lastCommand + 1);
		if (response[i++] != PN532_TO_HOST || response[i++] != command) {
			log("readResponse() received bad command.");
			return PN532TransferResult.INVALID_FRAME.getValue();
		}

		length -= 2;
		if (length > expectedLength) {
			log("readResponse() received length greater than expectedLength.");
			return PN532TransferResult.INSUFFICIENT_SPACE.getValue();
		}

		byte sum = PN532_TO_HOST;
		sum += command;

		for (int j = 0; j < length; j++) {
			buffer[j] = response[i++];
			sum += buffer[j];
		}

		byte check = (byte) (sum + response[i++]);
		if (check != 0) {
			log("readResponse() received bad checksum.");
			return PN532TransferResult.INVALID_FRAME.getValue();
		}

		if (response[i] != POSTAMBLE) {
			log("readResponse() received bad postamble.");
			return PN532TransferResult.INVALID_FRAME.getValue();
		}

		log("readResponse() returned " + length + " bytes: " + PN532Debug.getByteHexString(buffer, length));
		return length;
	}

	public int readResponse(byte[] buffer, int expectedLength) throws IllegalArgumentException, IllegalStateException, InterruptedException, IOException {
		return readResponse(buffer, expectedLength, readTimeout);
	}

	public void close() {
		log("close()");
		if (io != null && ioIsOpen()) {
			ioClose();
		}
		log("close() successful.");
	}

	protected abstract T getInterface() throws IllegalArgumentException, UndeclaredThrowableException;

	protected abstract void wakeupInternal() throws InterruptedException, IOException;

	protected abstract boolean readFully(byte[] buffer, int timeout) throws InterruptedException, IOException;

	protected void preWrite() throws InterruptedException, IOException {
	}

	protected void postWrite() throws InterruptedException, IOException {
	}

	protected abstract void ioWrite(ByteBuffer buffer) throws IOException;

	protected abstract boolean ioIsOpen();

	protected abstract void ioClose();

	void log(String message) {
		PN532Debug.log(prefixMessage(message));
	}

	String prefixMessage(String message) {
		return getDisplayName() + ": " + message;
	}
}