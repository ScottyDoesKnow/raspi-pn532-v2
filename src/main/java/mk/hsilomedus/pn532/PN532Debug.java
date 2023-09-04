package mk.hsilomedus.pn532;

public class PN532Debug {

	private static boolean log = false;
	private static boolean logRead = false;

	public static boolean getLog() {
		return log;
	}

	public static void setLog(boolean value) {
		log = value;
	}

	public static boolean getLogRead() {
		return logRead;
	}

	public static void setLogRead(boolean value) {
		logRead = value;
	}

	static void log(String message) {
		if (log) {
			System.out.println(message);
		}
	}

	static void logRead(String message) {
		if (logRead) {
			System.out.println(message);
		}
	}

	public static String getByteString(byte[] bytes) {
		StringBuilder output = new StringBuilder();
		output.append('[');

		if (bytes != null) {
			boolean first = true;
			for (int i = 0; i < bytes.length; i++) {
				if (!first) {
					output.append(' ');
				}
				first = false;

				output.append(String.format("%02X", bytes[i]));
			}
		}

		output.append(']');
		return output.toString();
	}
}
