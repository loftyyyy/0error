package com.rho.backend.telemetry;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One raw keystroke exactly as the browser recorded it. Aggregates are never
 * accepted from the client; the server derives them from these rows.
 */
public record RawKeystroke(
		@Min(0) int seq,
		@Min(0) @Max(32_767) int position,
		@NotNull @Pattern(regexp = "KEY|BACKSPACE") String kind,
		@Size(max = 1) String expected,
		@Size(max = 1) String actual,
		boolean correct,
		@Min(0) @Max(Integer.MAX_VALUE) int t,
		@Min(0) Integer displayMs,
		boolean warmup) {

	public static final String KEY = "KEY";
	public static final String BACKSPACE = "BACKSPACE";

	public boolean isBackspace() {
		return BACKSPACE.equals(kind);
	}
}
