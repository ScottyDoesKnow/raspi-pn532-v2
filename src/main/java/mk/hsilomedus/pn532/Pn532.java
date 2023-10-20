package mk.hsilomedus.pn532;

import java.io.IOException;
import java.util.function.Supplier;

import com.pi4j.io.IO;

public class Pn532<T extends IO<T, ?, ?>> implements AutoCloseable {

	private static final byte COMMAND_GET_FW_VERSION = 0x02;
	private static final byte COMMAND_SAM_CONFIG = 0x14;
	private static final byte COMMAND_IN_LIST_PASSIVE_TARGET = 0x4A;
	
	private static final byte MIFARE_ISO14443A_BAUD_RATE = 0x00;

	private final Pn532Connection<T> connection;
	private final byte[] buffer = new byte[64];

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

	public Pn532(Pn532Connection<T> connection) {
		if (connection == null) {
			throw new IllegalArgumentException("PN532 constructed with null connection.");
		}

		this.connection = connection;
	}

	public void initialize() throws InterruptedException, IOException {
		log("initialize()");
		connection.begin();
		connection.wakeup();
		log("initialize() successful.");
	}

	/**
	 * @return the firmware version of the device if successful, or a {@link Pn532TransferResult} value otherwise.
	 */
	public long getFirmwareVersion() throws InterruptedException, IOException {
		log("getFirmwareVersion()");

		var command = new byte[1];
		command[0] = COMMAND_GET_FW_VERSION;

		Pn532TransferResult writeStatus = connection.writeCommand(command);
		if (writeStatus != Pn532TransferResult.OK) {
			log("getFirmwareVersion() writeCommand returned " + writeStatus);
			return writeStatus.getValue();
		}

		int responseStatus = connection.readResponse(buffer, 4);
		if (responseStatus < 0) {
			log("getFirmwareVersion() readResponse returned " + Pn532TransferResult.fromValue(responseStatus));
			return responseStatus;
		}

		long response = buffer[0];
		response <<= 8;
		response |= (buffer[1] & 0xff); // '& 0xff' to deal with sign extension
		response <<= 8;
		response |= (buffer[2] & 0xff);
		response <<= 8;
		response |= (buffer[3] & 0xff);

		if (response == 0) {
			log("getFirmwareVersion() read 0.");
			return Pn532TransferResult.INVALID_FW_VERSION.getValue();
		}

		connection.setModelName("PN5" + String.format("%02X", buffer[0]));
		connection.setFirmwareVersion(buffer[1] + "." + buffer[2]);

		log("getFirmwareVersion() successful.");
		return response;
	}

	public boolean samConfig() throws InterruptedException, IOException {
		log("samConfig()");

		var command = new byte[4];
		command[0] = COMMAND_SAM_CONFIG;
		command[1] = 0x01; // Normal mode
		command[2] = 0x14; // Timeout (50ms * 20 = 1s)
		command[3] = 0x01; // Use IRQ pin

		Pn532TransferResult writeStatus = connection.writeCommand(command);
		if (writeStatus != Pn532TransferResult.OK) {
			log("samConfig() writeCommand returned " + writeStatus);
			return false;
		}

		int responseStatus = connection.readResponse(buffer, 0);
		if (responseStatus < 0) {
			log("samConfig() readResponse returned " + Pn532TransferResult.fromValue(responseStatus));
			return false;
		} else {
			log("samConfig() successful.");
			return true;
		}
	}

	public int readPassiveTargetId(byte[] result) throws InterruptedException, IOException {
		log("readPassiveTargetId()");

		var command = new byte[3];
		command[0] = COMMAND_IN_LIST_PASSIVE_TARGET;
		command[1] = 1; // Max 1 cards at once (we can set this to 2 later) - comment from C++ code
		command[2] = MIFARE_ISO14443A_BAUD_RATE;

		Pn532TransferResult writeStatus = connection.writeCommand(command);
		if (writeStatus != Pn532TransferResult.OK) {
			log("readPassiveTargetId() writeCommand returned " + writeStatus);
			return writeStatus.getValue();
		}

		// uidLength should be max 10 which means we only need 16 bytes, but an Android phone I tested with returned 33 bytes, so who knows
		int responseStatus = connection.readResponse(buffer, 64);
		if (responseStatus < 0) {
			log("readPassiveTargetId() readResponse returned " + Pn532TransferResult.fromValue(responseStatus));
			return responseStatus;
		}

		/*
		 * ISO14443A card response should be in the following format:
		 *
		 * byte    Description
		 * ------- ------------------------------------------
		 * b0      Tags Found
		 * b1      Tag Number (only one used in this example)
		 * b2..3   SENS_RES
		 * b4      SEL_RES
		 * b5      NFCID Length
		 * b6..    NFCIDLen NFCID
		 */

		if (buffer[0] != 1) {
			log("readPassiveTargetId() failed with " + buffer[0] + " tags found.");
			return Pn532TransferResult.UNDEFINED.getValue();
		}

		/* Card appears to be MIFARE Classic */
		int uidLength = buffer[5];
		if (6 + uidLength > buffer.length) {
			log("readPassiveTargetId() failed because uidLength of '" + uidLength + "' won't fit in buffer.");
			return Pn532TransferResult.UNDEFINED.getValue();
		}

		for (int i = 0; i < uidLength; i++) {
			result[i] = buffer[6 + i];
		}

		log("readPassiveTargetId() returned %s", () -> Pn532Utility.getByteHexString(result, 0, uidLength));
		return uidLength;
	}

	@Override
	public void close() {
		connection.close();
	}

	String prefixMessage(String message) {
		return connection.prefixMessage(message);
	}

	private void log(String message) {
		connection.log(message);
	}

	private void log(String message, Supplier<String> arg1) {
		connection.log(message, arg1);
	}
}