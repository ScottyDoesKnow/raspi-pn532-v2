package mk.hsilomedus.pn532;

enum Pn532TransferResult {
	OK(0),
	UNDEFINED(-1),
	TIMEOUT(-2),
	INVALID_ACK(-3),
	INVALID_FRAME(-4),
	INVALID_FW_VERSION(-5),
	INSUFFICIENT_SPACE(-6);

	private static final Pn532TransferResult[] VALUES = values();

	public static Pn532TransferResult fromValue(int value) {
		for (var element : VALUES) {
			if (element.getValue() == value) {
				return element;
			}
		}

		return UNDEFINED;
	}

	private final int value;

	Pn532TransferResult(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}
}