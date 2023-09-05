package mk.hsilomedus.pn532;

import java.lang.reflect.UndeclaredThrowableException;

import com.pi4j.io.IO;
import com.pi4j.io.exception.IOException;

public class PN532<T extends IO<T, ?, ?>> {

	private static final byte COMMAND_GET_FW_VERSION = 0x02;
	private static final byte COMMAND_SAM_CONFIG = 0x14;
	private static final byte COMMAND_IN_LIST_PASSIVE_TARGET = 0x4A;

	private final PN532Interface<T> connection;
	private final byte[] buffer = new byte[20];

	public int getAckTimeout() {
		return connection.getAckTimeout();
	}

	public void setAckTimeout(int value) {
		connection.setAckTimeout(value);
	}

	public int getReadTimeout() {
		return connection.getReadTimeout();
	}

	public void setReadTimeout(int value) {
		connection.setReadTimeout(value);
	}

	public String getDisplayName() {
		return connection.getDisplayName();
	}

	public PN532(PN532Interface<T> connection) throws IllegalArgumentException {
		if (connection == null) {
			throw new IllegalArgumentException("PN532 constructed with null connection.");
		}

		this.connection = connection;
	}

	public void initialize() throws IllegalArgumentException, IllegalStateException, InterruptedException, IOException, UndeclaredThrowableException {
		log("initialize()");
		connection.begin();
		connection.wakeup();
		log("initialize() successful.");
	}

	// TODO comment public methods, especially return values of this and readResponse
	public long getFirmwareVersion() throws IllegalStateException, InterruptedException, IOException {
		log("getFirmwareVersion()");

		long response;

		byte[] command = new byte[1];
		command[0] = COMMAND_GET_FW_VERSION;

		var writeStatus = connection.writeCommand(command);
		if (writeStatus != PN532TransferResult.OK) {
			log("getFirmwareVersion() writeCommand returned " + writeStatus);
			return writeStatus.getValue();
		}

		int responseStatus = connection.readResponse(buffer, 12);
		if (responseStatus < 0) {
			log("getFirmwareVersion() readResponse returned " + PN532TransferResult.fromValue(responseStatus));
			return responseStatus;
		}

		response = buffer[0];
		response <<= 8;
		response |= buffer[1];
		response <<= 8;
		response |= buffer[2];
		response <<= 8;
		response |= buffer[3];

		if (response == 0) {
			log("getFirmwareVersion() read 0.");
			return PN532TransferResult.INVALID_FW_VERSION.getValue();
		}

		connection.setModelName("PN5" + String.format("%02X", buffer[0]));
		connection.setFirmwareVersion(buffer[1] + "." + buffer[2]);

		log("getFirmwareVersion() successful.");
		return response;
	}

	public boolean samConfig() throws IllegalStateException, InterruptedException, IOException {
		log("samConfig()");

		byte[] command = new byte[4];
		command[0] = COMMAND_SAM_CONFIG;
		command[1] = 0x01; // normal mode
		command[2] = 0x14; // timeout (50ms * 20 = 1s)
		command[3] = 0x01; // use IRQ pin

		var writeStatus = connection.writeCommand(command);
		if (writeStatus != PN532TransferResult.OK) {
			log("samConfig() writeCommand returned " + writeStatus);
			return false;
		}

		int responseStatus = connection.readResponse(buffer, 12);
		if (responseStatus < 0) {
			log("samConfig() readResponse returned " + PN532TransferResult.fromValue(responseStatus));
			return false;
		} else {
			log("samConfig() successful.");
			return true;
		}
	}

	public int readPassiveTargetId(byte cardBaudRate, byte[] result) throws IllegalStateException, InterruptedException, IOException {
		log("readPassiveTargetId()");

		byte[] command = new byte[3];
		command[0] = COMMAND_IN_LIST_PASSIVE_TARGET;
		command[1] = 1; // max 1 cards at once (we can set this to 2 later) - comment from C++ code
		command[2] = cardBaudRate;

		var writeStatus = connection.writeCommand(command);
		if (writeStatus != PN532TransferResult.OK) {
			log("readPassiveTargetId() writeCommand returned " + writeStatus);
			return writeStatus.getValue();
		}

		int responseStatus = connection.readResponse(buffer, 20);
		if (responseStatus < 0) {
			log("readPassiveTargetId() readResponse returned " + PN532TransferResult.fromValue(responseStatus));
			return responseStatus;
		}

		/*
		 * ISO14443A card response should be in the following format:
		 *
		 * byte		Description
		 * -------- ------------------------------------------
		 * b0		Tags Found
		 * b1		Tag Number (only one used in this example)
		 * b2..3	SENS_RES
		 * b4		SEL_RES
		 * b5		NFCID Length
		 * b6..		NFCIDLen NFCID
		 */

		if (buffer[0] != 1) {
			log("readPassiveTargetId() failed with " + buffer[0] + " tags found.");
			return PN532TransferResult.UNDEFINED.getValue();
		}

		/* Card appears to be Mifare Classic */
		int uidLength = buffer[5];

		// TODO need to check if length is too long, and also if my buffer is too small?
		// TODO create Mifare class object?

		for (int i = 0; i < uidLength; i++) {
			result[i] = buffer[6 + i];
		}

		log("readPassiveTargetId() returned " + PN532Debug.getByteHexString(result));
		return uidLength;
	}

	public void close() {
		connection.close();
	}

	void log(String message) {
		connection.log(message);
	}

	String prefixMessage(String message) {
		return connection.prefixMessage(message);
	}
}