package com.rho.backend.drills;

import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Validates AI-generated drills before they can reach a client. The check is
 * deliberately strict: 40-60 printable ASCII characters, all inside the tier
 * charset. Static drills pass the same validator.
 */
@Component
public class DrillValidator {

	public Optional<String> validate(String text, TierDefinition tier) {
		if (text == null || text.isBlank()) {
			return Optional.of("Drill text is empty");
		}
		if (text.length() < tier.minLength() || text.length() > tier.maxLength()) {
			return Optional.of("Drill length %d is outside %d-%d"
					.formatted(text.length(), tier.minLength(), tier.maxLength()));
		}

		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (character < 0x20 || character > 0x7E) {
				return Optional.of("Drill contains a non-ASCII character at position " + index);
			}
			if (!tier.allows(character)) {
				return Optional.of("Drill contains a character outside tier %d charset at position %d"
						.formatted(tier.tier(), index));
			}
		}
		return Optional.empty();
	}

	public boolean isValid(String text, TierDefinition tier) {
		return validate(text, tier).isEmpty();
	}
}
