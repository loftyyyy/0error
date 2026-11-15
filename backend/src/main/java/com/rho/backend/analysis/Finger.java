package com.rho.backend.analysis;

/** The nine typing fingers, in left-to-right order. */
public enum Finger {

	LEFT_PINKY("Left pinky", "`1qaz"),
	LEFT_RING("Left ring", "2wsx"),
	LEFT_MIDDLE("Left middle", "3edc"),
	LEFT_INDEX("Left index", "45rtfgvb"),
	THUMBS("Thumbs", " "),
	RIGHT_INDEX("Right index", "67yuhjnm"),
	RIGHT_MIDDLE("Right middle", "8ik,"),
	RIGHT_RING("Right ring", "9ol."),
	RIGHT_PINKY("Right pinky", "0p;/-=[]\\'");

	private final String label;
	private final String baseKeys;

	Finger(String label, String baseKeys) {
		this.label = label;
		this.baseKeys = baseKeys;
	}

	public String label() {
		return label;
	}

	/** The unshifted keys this finger reaches on a US keyboard. */
	public String baseKeys() {
		return baseKeys;
	}
}
