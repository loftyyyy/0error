package com.rho.backend.sessions;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSessionRequest(
		@NotNull UUID clientSessionId,
		@Min(0) @Max(4) int tier,
		@NotBlank @Size(max = 128) @Pattern(regexp = "^[\\x20-\\x7E]+$") String targetCharset,
		@Min(1) @Max(500) int targetLength,
		@Pattern(regexp = "STATIC|AI") String drillSource) {

	public CreateSessionRequest {
		if (drillSource == null || drillSource.isBlank()) {
			drillSource = "STATIC";
		}
	}
}
