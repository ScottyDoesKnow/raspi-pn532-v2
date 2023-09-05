package mk.hsilomedus.pn532;

enum PN532TransferResult {
	OK(0),
	UNDEFINED(-1),
	TIMEOUT(-2),
	INVALID_ACK(-3),
	INVALID_FRAME(-4),
	INVALID_FW_VERSION(-5),
	INSUFFICIENT_SPACE(-6);
	
	private static final PN532TransferResult[] VALUES = values();
	
	public static PN532TransferResult fromValue(int value) {
		for (int i = 0; i < VALUES.length; i++) {
			if (VALUES[i].getValue() == value) {
				return VALUES[i];
			}
		}
		
		return UNDEFINED;
	}

	private final int value;

	PN532TransferResult(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}
}