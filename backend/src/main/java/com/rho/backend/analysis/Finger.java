package com.rho.backend.analysis;

/** The nine typing fingers, in left-to-right order. */
public enum Finger {

	LEFT_PINKY("Left pinky"),
	LEFT_RING("Left ring"),
	LEFT_MIDDLE("Left middle"),
	LEFT_INDEX("Left index"),
	THUMBS("Thumbs"),
	RIGHT_INDEX("Right index"),
	RIGHT_MIDDLE("Right middle"),
	RIGHT_RING("Right ring"),
	RIGHT_PINKY("Right pinky");

	private final String label;

	Finger(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
