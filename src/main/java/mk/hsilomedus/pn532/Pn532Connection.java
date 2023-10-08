package mk.hsilomedus.pn532;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Supplier;

import com.pi4j.context.Context;
import com.pi4j.io.IO;

// Don't know how to do this without the ?
public abstract class Pn532Connection<T extends IO<T, ?, ?>> {

	public static final int DEFAULT_ACK_TIMEOUT = 1000;
	public static final int DEFAULT_READ_TIMEOUT = 1000;

	private static final byte PREAMBLE = 0x00;
	private static final byte START_CODE_1 = 0x00;
	private static final byte START_CODE_2 = (byte) 0xFF;
	private static final byte POSTAMBLE = 0x00;

	private static final byte HOST_TO_PN532 = (byte) 0xD4;
	private static final byte PN532_TO_HOST = (byte) 0xD5;

	private static final byte[] PN532_ACK = { 0, 0, (byte) 0xFF, 0, (byte) 0xFF, 0 };

	private int ackTimeout = DEFAULT_ACK_TIMEOUT;
	private int readTimeout = DEFAULT_READ_TIMEOUT;

	protected final String provider;
	protected final String id;
	protected final String name;
	private final String displaySuffix;

	private String modelName = "PN5xx";
	private String firmwareVersion = "";

	protected Context pi4j = null;
	protected T io; // WARNING: If you use this to read or write directly, SPI won't work correctly since it reverses the bytes
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

	protected Pn532Connection(String provider, String id, String name, String displaySuffix) {
		this.provider = provider;
		this.id = "pn5xx-" + id;
		this.name = "PN5xx " + name;
		this.displaySuffix = displaySuffix;
	}

	public void begin() throws IOException {
		synchronized (Pn532ContextHelper.mutex) { // pigpio can crash the JRE without this
			log("begin()");

			if (pi4j != null) {
				throw new IllegalStateException(prefixMessage("begin() can only be called once."));
			}

			pi4j = Pn532ContextHelper.getContext();
			Pn532Utility.wrapInitializationExceptions(() -> io = getInterface());

			log("begin() successful.");
		}
	}

	public void wakeup() throws InterruptedException, IOException {
		log("wakeup()");

		if (io == null) {
			throw new IllegalStateException(prefixMessage("wakeup() called without calling begin()."));
		}

		Pn532Utility.wrapIoExceptionInterruptable(() -> {
			wakeupInternal();
			return null;
		});

		log("wakeup() successful.");
	}

	public Pn532TransferResult writeCommand(byte[] header, byte[] body) throws InterruptedException, IOException {
		log("writeCommand(header: %s, body: %s)", () -> Pn532Utility.getByteHexString(header), () -> Pn532Utility.getByteHexString(body));

		if (io == null) {
			throw new IllegalStateException(prefixMessage("writeCommand() called without calling begin()."));
		} else if (header == null) {
			throw new IllegalArgumentException(prefixMessage("writeCommand() called with null header."));
		} else if (body == null) {
			throw new IllegalArgumentException(prefixMessage("writeCommand() called with null body."));
		}

		return Pn532Utility.wrapIoExceptionInterruptable(() -> {
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
			for (byte element : header) {
				sum += element;
			}
			for (byte element : body) {
				sum += element;
			}
			buffer.put((byte) (~sum + 1));

			buffer.put(POSTAMBLE);

			log("writeCommand() sending %s", () -> Pn532Utility.getByteHexString(buffer.array()));
			ioWrite(buffer);
			lastCommand = header[0];

			postWrite();

			log("writeCommand() calling readAckFrame()");
			return readAckFrame();
		});
	}

	public Pn532TransferResult writeCommand(byte[] header) throws InterruptedException, IOException {
		return writeCommand(header, new byte[0]);
	}

	protected Pn532TransferResult readAckFrame() throws InterruptedException {
		var buffer = new byte[PN532_ACK.length];

		if (!readFully(buffer, ackTimeout)) {
			log("readAckFrame() timed out.");
			return Pn532TransferResult.TIMEOUT;
		}

		if (!Arrays.equals(buffer, PN532_ACK)) {
			log("readAckFrame() was invalid.");
			return Pn532TransferResult.INVALID_ACK;
		}

		log("readAckFrame() successful.");
		return Pn532TransferResult.OK;
	}

	public int readResponse(byte[] buffer, int expectedLength, int timeout) throws InterruptedException, IOException {
		log("readResponse(..., " + expectedLength + ", " + timeout + ")");

		if (io == null) {
			throw new IllegalStateException(prefixMessage("readResponse() called without calling begin()."));
		} else if (buffer == null) {
			throw new IllegalArgumentException(prefixMessage("readResponse() called with null buffer."));
		}

		return Pn532Utility.wrapIoExceptionInterruptable(() -> {
			var response = new byte[expectedLength + 2];

			if (!readFully(response, timeout)) {
				log("readResponse() timed out.");
				return Pn532TransferResult.TIMEOUT.getValue();
			}

			int i = 0;
			if (response[i++] != PREAMBLE || response[i++] != START_CODE_1 || response[i++] != START_CODE_2) {
				log("readResponse() received bad starting bytes.");
				return Pn532TransferResult.INVALID_FRAME.getValue();
			}

			byte length = response[i++];

			byte lengthCheck = (byte) (length + response[i++]);
			if (lengthCheck != 0) {
				log("readResponse() received bad length checksum.");
				return Pn532TransferResult.INVALID_FRAME.getValue();
			}

			byte command = (byte) (lastCommand + 1);
			if (response[i++] != PN532_TO_HOST || response[i++] != command) {
				log("readResponse() received bad command.");
				return Pn532TransferResult.INVALID_FRAME.getValue();
			}

			length -= 2;
			if (length > expectedLength) {
				log("readResponse() received length greater than expectedLength.");
				return Pn532TransferResult.INSUFFICIENT_SPACE.getValue();
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
				return Pn532TransferResult.INVALID_FRAME.getValue();
			}

			if (response[i] != POSTAMBLE) {
				log("readResponse() received bad postamble.");
				return Pn532TransferResult.INVALID_FRAME.getValue();
			}

			final int lengthFinal = length;
			log("readResponse() returned " + length + " bytes: %s", () -> Pn532Utility.getByteHexString(buffer, lengthFinal));
			return (int) length;
		});
	}

	public int readResponse(byte[] buffer, int expectedLength) throws InterruptedException, IOException {
		return readResponse(buffer, expectedLength, readTimeout);
	}

	public void close() {
		log("close()");
		if (io != null && ioIsOpen()) {
			ioClose();
		}
		log("close() successful.");
	}

	protected abstract T getInterface();

	protected abstract void wakeupInternal() throws InterruptedException;

	protected abstract boolean readFully(byte[] buffer, int timeout) throws InterruptedException;

	protected void preWrite() throws InterruptedException {
	}

	protected void postWrite() throws InterruptedException {
	}

	protected abstract void ioWrite(ByteBuffer buffer);

	protected abstract boolean ioIsOpen();

	protected abstract void ioClose();

	void log(String message) {
		Pn532Utility.log(prefixMessage(message));
	}

	void log(String message, Supplier<String> arg1) {
		Pn532Utility.log(prefixMessage(message), arg1);
	}

	void log(String message, Supplier<String> arg1, Supplier<String> arg2) {
		Pn532Utility.log(prefixMessage(message), arg1, arg2);
	}

	String prefixMessage(String message) {
		return getDisplayName() + ": " + message;
	}
}