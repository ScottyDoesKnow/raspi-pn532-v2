package mk.hsilomedus.pn532;

import java.io.IOException;

import com.pi4j.io.gpio.exception.UnsupportedBoardType;
import com.pi4j.io.serial.Baud;
import com.pi4j.io.serial.DataBits;
import com.pi4j.io.serial.FlowControl;
import com.pi4j.io.serial.Parity;
import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialConfig;
import com.pi4j.io.serial.SerialFactory;
import com.pi4j.io.serial.SerialPort;
import com.pi4j.io.serial.StopBits;

public class PN532Serial implements IPN532Interface {
	
	private static final int READ_TIMEOUT = 1000; //ms

	private static final int PN532_TIMEOUT = -2;
	private static final int PN532_INVALID_FRAME = -3;
	private static final int PN532_NO_SPACE = -4;

	private boolean debug = false;
	private boolean debugReads = false;

	Serial serial;
	private byte command;

	public PN532Serial() {
		serial = SerialFactory.createInstance();
	}

	@Override
	public void begin() throws IOException, UnsupportedBoardType, InterruptedException {
		log("PN532Serial.begin()");

		// ScottyDoesKnow: everything here but speed should be defaults, but might as well not assume
		SerialConfig config = new SerialConfig();
		config.device(SerialPort.getDefaultPort())
			.baud(Baud._115200)
			.dataBits(DataBits._8)
			.parity(Parity.NONE)
			.stopBits(StopBits._1)
			.flowControl(FlowControl.NONE);
		
		serial.open(config);
	}

	@Override
	public void wakeup() throws IllegalStateException, IOException {
		log("PN532Serial.wakeup()");

		write((byte) 0x55);
		write((byte) 0x55);
		write((byte) 0x00);
		write((byte) 0x00);
		write((byte) 0x00);
		
		serial.flush();
		
		dumpSerialBuffer();
	}

	@Override
	public CommandStatus writeCommand(byte[] header, byte[] body) throws InterruptedException, IllegalStateException, IOException {
		log("PN532Serial.writeCommand(" + header + " " + (body != null ? body : "") + ")");

		dumpSerialBuffer();

		command = header[0];

		write(PN532_PREAMBLE);
		write(PN532_STARTCODE1);
		write(PN532_STARTCODE2);

		int length = header.length + (body != null ? body.length : 0) + 1;
		write((byte) length);
		write((byte) (~length + 1));

		write(PN532_HOSTTOPN532);
		byte sum = PN532_HOSTTOPN532;

		write(header);
		for (int i = 0; i < header.length; i++) {
			sum += header[i];
		}

		if (body != null) {
			write(body);
			for (int i = 0; i < body.length; i++) {
				sum += body[i];
			}
		}

		byte checksum = (byte) (~sum + 1);
		write(checksum);
		write(PN532_POSTAMBLE);
		
		serial.flush();
		
		return readAckFrame();
	}

	@Override
	public CommandStatus writeCommand(byte header[]) throws InterruptedException, IllegalStateException, IOException {
		return writeCommand(header, null);
	}

	@Override
	public int readResponse(byte[] buffer, int expectedLength, int timeout) throws InterruptedException, IllegalStateException, IOException {
		log("PN532Serial.readResponse(..., " + expectedLength + ", " + timeout + ")");
		
		byte[] tmp = new byte[3];
		if (receive(tmp, 3, timeout) <= 0) {
			return PN532_TIMEOUT;
		}
		if ((byte) 0 != tmp[0] || (byte) 0 != tmp[1] || (byte) 0xFF != tmp[2]) {
			return PN532_INVALID_FRAME;
		}

		byte[] length = new byte[2];
		if (receive(length, 2, timeout) <= 0) {
			return PN532_TIMEOUT;
		}
		if (0 != length[0] + length[1]) {
			return PN532_INVALID_FRAME;
		}
		length[0] -= 2;
		if (length[0] > expectedLength) {
			return PN532_NO_SPACE;
		}

		byte cmd = (byte) (command + 1); // response command
		if (receive(tmp, 2, timeout) <= 0) {
			return PN532_TIMEOUT;
		}
		if (PN532_PN532TOHOST != tmp[0] || cmd != tmp[1]) {
			return PN532_INVALID_FRAME;
		}

		if (receive(buffer, length[0], timeout) != length[0]) {
			return PN532_TIMEOUT;
		}
		byte sum = (byte) (PN532_PN532TOHOST + cmd);
		for (int i = 0; i < length[0]; i++) {
			sum += buffer[i];
		}

		if (receive(tmp, 2, timeout) <= 0) {
			return PN532_TIMEOUT;
		}
		if (0 != (sum + tmp[0]) || 0 != tmp[1]) {
			return PN532_INVALID_FRAME;
		}

		return length[0];
	}

	@Override
	public int readResponse(byte[] buffer, int expectedLength) throws InterruptedException, IllegalStateException, IOException {
		return readResponse(buffer, expectedLength, READ_TIMEOUT);
	}

	private CommandStatus readAckFrame() throws InterruptedException, IllegalStateException, IOException {
		log("PN532Serial.readAckFrame()");
		
		byte ackBuf[] = new byte[PN532.PN532_ACK.length];

		if (receive(ackBuf, PN532.PN532_ACK.length) <= 0) {
			log("PN532Serial.readAckFrame() Timeout");
			return CommandStatus.TIMEOUT;
		}

		for (int i = 0; i < ackBuf.length; i++) {
			if (ackBuf[i] != PN532.PN532_ACK[i]) {
				log("PN532Serial.readAckFrame() Invalid");
				return CommandStatus.INVALID_ACK;
			}
		}

		log("PN532Serial.readAckFrame() Success");
		return CommandStatus.OK;
	}

	int receive(byte[] buffer, int expectedLength, int timeout) throws InterruptedException, IllegalStateException, IOException {
		log("PN532Serial.receive(..., " + expectedLength + ", " + timeout + ")");

		int bufferIndex = 0;
		boolean receivedData;
		long startMs;

		while (bufferIndex < expectedLength) {
			startMs = System.currentTimeMillis();
			receivedData = false;
			do {
				if (serial.available() == 0) {
					Thread.sleep(10);
				} else {
					buffer[bufferIndex++] = read();
					receivedData = true;
					break;
				}
			} while (timeout == 0 || (System.currentTimeMillis() - startMs) < timeout);

			if (!receivedData) {
				if (bufferIndex > 0) {
					log("Read total of " + bufferIndex + " bytes.");
					return bufferIndex;
				} else {
					log("Timeout while reading.");
					return PN532_TIMEOUT;
				}
			}
		}
		
		return bufferIndex;
	}

	int receive(byte[] buffer, int expectedLength) throws InterruptedException, IllegalStateException, IOException {
		return receive(buffer, expectedLength, READ_TIMEOUT);
	}

	private void write(byte toSend) throws IllegalStateException, IOException {
		log("PN532Serial.write() " + Integer.toHexString(toSend));

		serial.write(toSend);
	}

	private void write(byte[] toSend) throws IllegalStateException, IOException {
		log("PN532Serial.write() " + PN532.getByteString(toSend));

		serial.write(toSend);
	}
	
	// Because I'm too lazy to refactor the code
	private byte read() throws IllegalStateException, IOException {
		byte result = serial.read(1)[0];
		if (debugReads) {
			log("PN532Serial.read() " + Integer.toHexString(result));
		}
		return result;
	}

	private void dumpSerialBuffer() throws IllegalStateException, IOException {
		log("PN532Serial.dumpSerialBuffer()");

		while (serial.available() > 0) {
			log("\tDumping byte");
			read();
		}
	}

	private void log(String message) {
		if (debug) {
			System.out.println(message);
		}
	}
}