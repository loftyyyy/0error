package com.rho.backend.sessions;

import java.util.List;

public record SessionHistoryResponse(List<SessionHistoryEntry> sessions) {
}
