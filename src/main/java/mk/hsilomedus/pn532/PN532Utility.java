package mk.hsilomedus.pn532;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PN532Utility {

	private static final ThreadLocal<DateFormat> DATE_FORMAT = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS"));

	private static Logger logger = null;

	private PN532Utility() {
		throw new UnsupportedOperationException("Utility class.");
	}

	public static void setLogger(Logger value) {
		logger = value;
	}

	public static void log(String message) {
		if (logger != null) {
			logger.log(Level.INFO, () -> DATE_FORMAT.get().format(new Date()) + " " + message + System.lineSeparator());
		}
	}

	public static void log(Supplier<String> message) {
		if (logger != null) {
			logger.log(Level.INFO, () -> DATE_FORMAT.get().format(new Date()) + " " + message.get() + System.lineSeparator());
		}
	}

	public static void log(String message, Supplier<String> arg1) {
		log(() -> String.format(message, arg1.get()));
	}

	public static void log(String message, Supplier<String> arg1, Supplier<String> arg2) {
		log(() -> String.format(message, arg1.get(), arg2.get()));
	}

	public static String getByteHexString(byte[] bytes, int length) {
		var output = new StringBuilder();
		output.append('[');

		if (bytes != null) {
			var first = true;
			for (var i = 0; i < length; i++) {
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

	public static String getNestedMessage(Throwable throwable) {
		// Decided to stop at first throwable with a message rather than the deepest cause
		if (throwable.getMessage() == null && throwable.getCause() != null) {
			var cause = throwable.getCause();
			while (cause.getMessage() == null && cause.getCause() != null) {
				cause = cause.getCause();
			}
			return cause.getMessage();
		} else {
			return throwable.getMessage();
		}
	}
}
