package com.rho.backend.progress;

import java.util.List;

public record ProgressSummary(List<TierStatus> tiers) {
}
