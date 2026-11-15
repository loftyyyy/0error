package com.rho.backend.drills;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.rho.backend.analysis.Finger;

import org.springframework.stereotype.Component;

/**
 * Offline drill pool. The API always has a valid drill to serve, so AI is never
 * on the critical path.
 */
@Component
public class StaticDrillPool {

	private static final Map<Integer, List<String>> DRILLS = Map.of(
			0, List.of(
					"asdf jkl; asdf jkl; fj fj dk dk sl sl a; a;",
					"a; sl dk fj a; sl dk fj asdf jkl; asdf jkl; fj",
					"fj dk sl a; fj dk sl a; asdf jkl; asdf jkl; fj dk"),
			1, List.of(
					"qwer asdf jkl; uiop qwer asdf g h jkl; uiop qwer",
					"qwerty uiop asdfg hjkl; qwerty uiop asdfg hjkl;",
					"qq ww ee rr tt yy uu ii oo pp aa ss dd ff gg hh jj kk ll"),
			2, List.of(
					"qwer asdf zxcv jkl; qwer asdf zxcv jkl; mn mn ,. ,.",
					"zxcv bn m, . zxcv bn m, . asdf jkl; qwer uiop zxcv",
					"az sx dc fv gb hn jm k, l. az sx dc fv gb hn jm k, l."),
			3, List.of(
					"asdf-jkl; qwer=uiop zxcv/m,. asdf-jkl; qwer=uiop zxcv/m,.",
					"a[s]d;f'g/h j[k]l;q'w=e r/t y,u.i o[p] a's d;f g'h",
					"qwer[] asdf;' zxcv,./ qwer[] asdf;' zxcv,./ qwer[]"),
			4, List.of(
					"The calm fox types slowly, then finds a steady rhythm.",
					"Small steps build speed; accuracy comes before velocity.",
					"Keep your fingers on the home row and let the words flow."));

	private final TierCatalogue catalogue;

	public StaticDrillPool(TierCatalogue catalogue) {
		this.catalogue = catalogue;
	}

	public List<String> forTier(int tier) {
		return DRILLS.getOrDefault(tier, List.of());
	}

	/** Picks a drill, avoiding the previous text when the pool allows it. */
	public String pick(int tier, String exclude) {
		return pick(tier, DrillFocus.none(), exclude);
	}

	public String pick(int tier, List<String> focusPairs, String exclude) {
		return pick(tier, DrillFocus.of(focusPairs), exclude);
	}

	/**
	 * Picks a drill, preferring one that covers a focus pair. Without a pair
	 * focus, the drill that exercises the weak finger most wins. When the
	 * curated pool cannot cover the weakness, a small drill is composed from
	 * the target itself.
	 */
	public String pick(int tier, DrillFocus focus, String exclude) {
		DrillFocus target = focus == null ? DrillFocus.none() : focus;
		List<String> candidates = forTier(tier);
		if (candidates.isEmpty()) {
			throw new IllegalStateException("No static drill configured for tier " + tier);
		}

		List<String> filtered = candidates.stream()
				.filter(candidate -> exclude == null || !candidate.equals(exclude))
				.toList();
		List<String> pool = filtered.isEmpty() ? candidates : filtered;

		if (target.hasPairs()) {
			List<String> targeted = pool.stream()
					.filter(candidate -> containsAny(candidate, target.pairs()))
					.toList();
			if (!targeted.isEmpty()) {
				return random(targeted);
			}
			String built = FocusedDrillBuilder.build(
					catalogue.require(tier), target.pairs(), exclude);
			return built != null ? built : random(pool);
		}

		if (target.finger() != null) {
			TierDefinition definition = catalogue.require(tier);
			List<String> keys = FocusedDrillBuilder.allowedKeys(definition, target.finger());
			if (!keys.isEmpty()) {
				int best = pool.stream()
						.mapToInt(candidate -> countKeys(candidate, keys))
						.max()
						.orElse(0);
				if (best > 0) {
					return random(pool.stream()
							.filter(candidate -> countKeys(candidate, keys) == best)
							.toList());
				}
				String built = FocusedDrillBuilder.buildForFinger(
						definition, target.finger(), exclude);
				if (built != null) {
					return built;
				}
			}
		}

		return random(pool);
	}

	private static String random(List<String> pool) {
		return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}

	private static int countKeys(String text, List<String> keys) {
		int count = 0;
		for (String key : keys) {
			for (int index = 0; index < text.length(); index++) {
				if (text.charAt(index) == key.charAt(0)) {
					count++;
				}
			}
		}
		return count;
	}

	private static boolean containsAny(String text, List<String> focusPairs) {
		if (focusPairs == null) {
			return false;
		}
		for (String pair : focusPairs) {
			if (pair != null && pair.length() == 2 && text.contains(pair)) {
				return true;
			}
		}
		return false;
	}
}
