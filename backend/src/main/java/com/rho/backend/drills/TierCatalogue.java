package com.rho.backend.drills;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The locked curriculum: home row, top row, bottom row, punctuation, then full
 * sentences. Tier ids match the {@code sessions.tier} check constraint.
 */
@Component
public class TierCatalogue {

	private static final int DRILL_MIN_LENGTH = 40;
	private static final int DRILL_MAX_LENGTH = 60;

	private static final String ASCII_PRINTABLE = IntStream.rangeClosed(0x20, 0x7E)
			.mapToObj(value -> String.valueOf((char) value))
			.collect(Collectors.joining());

	private static final List<TierDefinition> TIERS = List.of(
			new TierDefinition(0, "Home row", "asdfjkl; ", DRILL_MIN_LENGTH, DRILL_MAX_LENGTH),
			new TierDefinition(1, "Top row", "qwertyuiopasdfghjkl; ", DRILL_MIN_LENGTH, DRILL_MAX_LENGTH),
			new TierDefinition(2, "Bottom row", "qwertyuiopasdfghjkl;zxcvbnm,. ", DRILL_MIN_LENGTH, DRILL_MAX_LENGTH),
			new TierDefinition(3, "Punctuation", "qwertyuiop[]asdfghjkl;'zxcvbnm,./-= ", DRILL_MIN_LENGTH, DRILL_MAX_LENGTH),
			new TierDefinition(4, "Full sentences", ASCII_PRINTABLE, DRILL_MIN_LENGTH, DRILL_MAX_LENGTH));

	public List<TierDefinition> all() {
		return TIERS;
	}

	public TierDefinition require(int tier) {
		return TIERS.stream()
				.filter(definition -> definition.tier() == tier)
				.findFirst()
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.BAD_REQUEST, "Unknown tier " + tier));
	}

	public TierDefinition next(int tier) {
		return require(tier + 1);
	}

	public int highestTier() {
		return TIERS.get(TIERS.size() - 1).tier();
	}
}
