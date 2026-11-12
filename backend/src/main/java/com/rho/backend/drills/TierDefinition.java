package com.rho.backend.drills;

/**
 * One tier of the curriculum. Charset includes every character a drill for
 * this tier may contain, including spaces where allowed.
 */
public record TierDefinition(
		int tier,
		String name,
		String charset,
		int minLength,
		int maxLength) {

	public boolean allows(char character) {
		return charset.indexOf(character) >= 0;
	}
}
