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

	public static String getByteHexString(byte[] bytes, int length) {
		var output = new StringBuilder();
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
		} catch (com.pi4j.io.exception.IOException e) {
			throw getCheckedIoException(e);
		}
	}

	public static <T> T wrapIoExceptionInterruptable(InterruptableRunnable<T> runnable) throws InterruptedException, IOException {
		try {
			return runnable.run();
		} catch (com.pi4j.io.exception.IOException e) {
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
	public interface InterruptableRunnable<T> {
		T run() throws InterruptedException;
	}
}
