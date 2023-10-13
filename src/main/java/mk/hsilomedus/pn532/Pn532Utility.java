package mk.hsilomedus.pn532;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.pi4j.provider.exception.ProviderNotFoundException;

public final class Pn532Utility {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS");

	private static Logger logger = null;

	private Pn532Utility() {
		throw new UnsupportedOperationException("Utility class.");
	}

	public static void setLogger(Logger value) {
		logger = value;
	}

	public static void log(String message) {
		if (logger != null) {
			logger.log(Level.FINE, () -> LocalDateTime.now().format(DATE_FORMAT) + "   " + message + System.lineSeparator());
		}
	}

	public static void log(Supplier<String> message) {
		if (logger != null) {
			logger.log(Level.FINE, () -> LocalDateTime.now().format(DATE_FORMAT) + "   " + message.get() + System.lineSeparator());
		}
	}

	public static void log(String message, Supplier<String> arg1) {
		log(() -> String.format(message, arg1.get()));
	}

	public static void log(String message, Supplier<String> arg1, Supplier<String> arg2) {
		log(() -> String.format(message, arg1.get(), arg2.get()));
	}

	public static String getByteHexString(byte[] bytes, int startIndex, int length) {
		var output = new StringBuilder();
		output.append('[');

		if (bytes != null) {
			boolean first = true;
			for (int i = startIndex; i < startIndex + length; i++) {
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
		return getByteHexString(bytes, 0, bytes.length);
	}

	public static void wrapInitializationExceptions(Runnable runnable) throws IOException {
		try {
			runnable.run();
		} catch (IllegalArgumentException | ProviderNotFoundException e) { // Handle pi4j/pigpio nonsense
			throw Pn532Utility.getCheckedIoException(e);
		} catch (UndeclaredThrowableException e) { // Handle pigpio nonsense
			throw Pn532Utility.getCheckedIoException(e, true);
		}
	}

	public static void wrapIoException(Runnable runnable) throws IOException {
		try {
			runnable.run();
			// Pi4JException rather than the Pi4J IOException, encountered while writing to a non-existent I2C device
		} catch (com.pi4j.exception.Pi4JException e) {
			throw getCheckedIoException(e);
		}
	}

	public static <T> T wrapIoExceptionInterruptable(InterruptableIoRunnable<T> runnable) throws InterruptedException, IOException {
		try {
			return runnable.run();
			// Pi4JException rather than the Pi4J IOException, encountered while writing to a non-existent I2C device
		} catch (com.pi4j.exception.Pi4JException e) {
			throw getCheckedIoException(e);
		}
	}

	private static IOException getCheckedIoException(Throwable e, boolean getNestedMessage) {
		var message = getNestedMessage ? getNestedMessage(e) : e.getMessage();
		return new IOException(message, e);
	}

	private static IOException getCheckedIoException(Throwable e) {
		return getCheckedIoException(e, false);
	}

	private static String getNestedMessage(Throwable throwable) {
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

	@FunctionalInterface
	public interface InterruptableIoRunnable<T> {
		T run() throws InterruptedException, IOException;
	}
}
