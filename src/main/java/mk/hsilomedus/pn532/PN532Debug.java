package mk.hsilomedus.pn532;

public class PN532Debug {

	private static boolean loggingEnabled = false;

	public static boolean getLoggingEnabled() {
		return loggingEnabled;
	}

	public static void setLoggingEnabled(boolean value) {
		loggingEnabled = value;
	}

	public static void log(String message) {
		if (loggingEnabled) {
			System.out.println(message);
		}
	}

	public static String getByteHexString(byte[] bytes, int length) {
		StringBuilder output = new StringBuilder();
		output.append('[');

		if (bytes != null) {
			boolean first = true;
			for (int i = 0; i < length; i++) {
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

	public static String getByteHexString(byte[] bytes) {
		return getByteHexString(bytes, bytes.length);
	}
}
