package mk.hsilomedus.pn532;

import java.io.IOException;

import com.pi4j.io.gpio.exception.UnsupportedBoardType;
import com.pi4j.io.i2c.I2CFactory.UnsupportedBusNumberException;

public class PN532 {
	
	public static final byte PN532_ACK[] = new byte[] { 0, 0, (byte) 0xFF, 0, (byte) 0xFF, 0 };

	private static final byte PN532_COMMAND_GETFIRMWAREVERSION = 0x02;
	private static final byte PN532_COMMAND_SAMCONFIGURATION = 0x14;
	private static final byte PN532_COMMAND_INLISTPASSIVETARGET = 0x4A;

	private IPN532Interface medium;
	private byte[] pn532_packetbuffer;

	public PN532(IPN532Interface medium) {
		this.medium = medium;
		this.pn532_packetbuffer = new byte[64];
	}

	public void begin() throws IOException, InterruptedException {
		try {
			medium.begin();
			medium.wakeup();
		} catch (UnsupportedBoardType | UnsupportedBusNumberException e) {
			throw new RuntimeException("Error beginning: " + e.getMessage());
		}
	}

	public long getFirmwareVersion() throws InterruptedException, IllegalStateException, IOException {
		long response;

		byte[] command = new byte[1];
		command[0] = PN532_COMMAND_GETFIRMWAREVERSION;

		if (medium.writeCommand(command) != CommandStatus.OK) {
			return 0;
		}

		// read data packet
		int status = medium.readResponse(pn532_packetbuffer, 12);
		if (status < 0) {
			return 0;
		}

		int offset = 0; // medium.getOffsetBytes();

		response = pn532_packetbuffer[offset + 0];
		response <<= 8;
		response |= pn532_packetbuffer[offset + 1];
		response <<= 8;
		response |= pn532_packetbuffer[offset + 2];
		response <<= 8;
		response |= pn532_packetbuffer[offset + 3];

		return response;
	}

	public boolean SAMConfig() throws InterruptedException, IllegalStateException, IOException {
		byte[] command = new byte[4];
		command[0] = PN532_COMMAND_SAMCONFIGURATION;
		command[1] = 0x01; // normal mode;
		command[2] = 0x14; // timeout 50ms * 20 = 1 second
		command[3] = 0x01; // use IRQ pin!

		if (medium.writeCommand(command) != CommandStatus.OK) {
			return false;
		}

		return medium.readResponse(pn532_packetbuffer, 8) > 0;
	}

	public int readPassiveTargetID(byte cardbaudrate, byte[] buffer) throws InterruptedException, IllegalStateException, IOException {
		byte[] command = new byte[3];
		command[0] = PN532_COMMAND_INLISTPASSIVETARGET;
		command[1] = 1; // max 1 cards at once (we can set this to 2 later)
		command[2] = (byte) cardbaudrate;

		if (medium.writeCommand(command) != CommandStatus.OK) {
			return -1; // command failed
		}

		// read data packet
		// if (medium.readResponse(pn532_packetbuffer, pn532_packetbuffer.length) < 0) {
		if (medium.readResponse(pn532_packetbuffer, 20) < 0) {
			return -1;
		}

		// check some basic stuff
		/*
		 * ISO14443A card response should be in the following format:
		 * 
		 * byte Description ------------- ------------------------------------------ b0
		 * Tags Found b1 Tag Number (only one used in this example) b2..3 SENS_RES b4
		 * SEL_RES b5 NFCID Length b6..NFCIDLen NFCID
		 */

		int offset = 0; // medium.getOffsetBytes();

		if (pn532_packetbuffer[offset + 0] != 1) {
			return -1;
		}
		// int sens_res = pn532_packetbuffer[2];
		// sens_res <<= 8;
		// sens_res |= pn532_packetbuffer[3];

		// DMSG("ATQA: 0x"); DMSG_HEX(sens_res);
		// DMSG("SAK: 0x"); DMSG_HEX(pn532_packetbuffer[4]);
		// DMSG("\n");

		/* Card appears to be Mifare Classic */
		int uidLength = pn532_packetbuffer[offset + 5];

		for (int i = 0; i < uidLength; i++) {
			buffer[i] = pn532_packetbuffer[offset + 6 + i];
		}

		return uidLength;
	}

	public static String getByteString(byte[] arr) {
		String output = "[";

		if (arr != null) {
			for (int i = 0; i < arr.length; i++) {
				output += Integer.toHexString(arr[i]) + " ";
			}
		}
		return output.trim() + "]";
	}
}