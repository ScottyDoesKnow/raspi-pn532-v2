package mk.hsilomedus.pn532;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;

public final class Pn532ContextHelper {

	// Synchronization could be done more efficiently but I don't think it matters
	private static Context context;
	static final Object mutex = new Object();

	private Pn532ContextHelper() {
		throw new UnsupportedOperationException("Utility class.");
	}

	/**
	 * Initializes the Pi4J {@link Context}. Must be called and must be called only once.
	 */
	public static void initialize() {
		synchronized (mutex) {
			if (context != null) {
				throw new IllegalStateException("PN532ContextHelper.initialize() can only be called once.");
			}

			context = Pi4J.newAutoContext();
		}
	}

	public static Context getContext() {
		synchronized (mutex) {
			if (context == null) {
				throw new IllegalStateException("PN532ContextHelper.getContext() cannot be called before calling initialize() or after calling shutdown().");
			}

			return context;
		}
	}

	/**
	 * Shuts down the Pi4J {@link Context}. Must be called before exiting the application, but can be called multiple times.
	 */
	public static void shutdown() {
		synchronized (mutex) {
			if (context != null && !context.isShutdown()) {
				context.shutdown();
			}
		}
	}
}
