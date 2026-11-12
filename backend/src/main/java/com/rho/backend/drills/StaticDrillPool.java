package com.rho.backend.drills;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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

	public List<String> forTier(int tier) {
		return DRILLS.getOrDefault(tier, List.of());
	}

	/** Picks a drill, avoiding the previous text when the pool allows it. */
	public String pick(int tier, String exclude) {
		List<String> candidates = forTier(tier);
		if (candidates.isEmpty()) {
			throw new IllegalStateException("No static drill configured for tier " + tier);
		}

		List<String> filtered = candidates.stream()
				.filter(candidate -> exclude == null || !candidate.equals(exclude))
				.toList();
		List<String> pool = filtered.isEmpty() ? candidates : filtered;

		return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}
}
