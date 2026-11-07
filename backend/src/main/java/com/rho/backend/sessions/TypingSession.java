package com.rho.backend.sessions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Maps to the V1 {@code sessions} table. The class carries the API-facing
 * "typing session" name; the table itself stays {@code sessions}.
 */
@Table("sessions")
public record TypingSession(
		@Id UUID id,
		@Column("user_id") UUID userId,
		@Column("client_session_id") UUID clientSessionId,
		int tier,
		@Column("drill_source") String drillSource,
		@Column("target_length") int targetLength,
		@Column("allowed_charset") String allowedCharset,
		String status,
		@Column("started_at") OffsetDateTime startedAt,
		@Column("ended_at") OffsetDateTime endedAt,
		@Column("keystroke_count") int keystrokeCount,
		@Column("correct_count") int correctCount,
		BigDecimal accuracy,
		@Column("median_inter_key_ms") Integer medianInterKeyMs,
		@Column("p95_inter_key_ms") Integer p95InterKeyMs,
		@Column("hesitation_count") int hesitationCount,
		@Column("created_at") @ReadOnlyProperty OffsetDateTime createdAt,
		@Column("updated_at") @ReadOnlyProperty OffsetDateTime updatedAt) {
}
