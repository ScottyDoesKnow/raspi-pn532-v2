package mk.hsilomedus.pn532;

import java.lang.reflect.UndeclaredThrowableException;

import com.pi4j.io.IO;
import com.pi4j.io.exception.IOException;

public class PN532<T extends IO<T, ?, ?>> {

	private static final byte COMMAND_GET_FW_VERSION = 0x02;
	private static final byte COMMAND_SAM_CONFIG = 0x14;
	private static final byte COMMAND_IN_LIST_PASSIVE_TARGET = 0x4A;

	private PN532Interface<T> connection;
	private byte[] buffer = new byte[64];

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

	void setModelName(String value) {
		connection.setModelName(value);
	}

	public String getDisplayName() {
		return connection.getDisplayName();
	}

	PN532(PN532Interface<T> connection) throws IllegalArgumentException {
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

	// TODO comment methods, especially for when this returns 0
	public long getFirmwareVersion() throws IllegalStateException, InterruptedException, IOException {
		log("getFirmwareVersion()");

		long response;

		byte[] command = new byte[1];
		command[0] = COMMAND_GET_FW_VERSION;

		if (connection.writeCommand(command) != PN532CommandStatus.OK) {
			log("getFirmwareVersion() writeCommand failed.");
			return 0;
		}

		int status = connection.readResponse(buffer, 12);
		if (status < 0) {
			log("getFirmwareVersion() readResponse failed.");
			return 0;
		}

		response = buffer[0];
		response <<= 8;
		response |= buffer[1];
		response <<= 8;
		response |= buffer[2];
		response <<= 8;
		response |= buffer[3];

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

		if (connection.writeCommand(command) != PN532CommandStatus.OK) {
			log("samConfig() writeCommand failed.");
			return false;
		}

		int status = connection.readResponse(buffer, 12);
		if (status < 0) {
			log("samConfig() readResponse failed.");
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
		command[1] = 1; // TODO Max 1 card at a time (can set this to 2 later?)
		command[2] = (byte) cardBaudRate;

		if (connection.writeCommand(command) != PN532CommandStatus.OK) {
			log("readPassiveTargetId() writeCommand failed.");
			return -1;
		}

		if (connection.readResponse(buffer, 20) < 0) {
			log("readPassiveTargetId() readResponse failed.");
			return -1;
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
			return -1;
		}

		/* Card appears to be Mifare Classic */
		int uidLength = buffer[5];

		// TODO need to check if length is too long?
		// TODO create Mifare class object?

		for (int i = 0; i < uidLength; i++) {
			result[i] = buffer[6 + i];
		}

		log("readPassiveTargetId() returned " + PN532Debug.getByteString(result));
		return uidLength;
	}
	
	public void close() {
		connection.close();
	}

	void log(String message) {
		connection.log(message);
	}

	void logRead(String message) {
		connection.logRead(message);
	}

	String prefixMessage(String message) {
		return connection.prefixMessage(message);
	}
}