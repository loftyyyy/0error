package com.rho.backend.telemetry;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One raw keystroke. Rows are append-only and purged after the retention window;
 * the session row keeps the aggregates forever.
 */
@Table("keystroke_events")
public record KeystrokeEvent(
		@Id Long id,
		@Column("session_id") UUID sessionId,
		int seq,
		int position,
		String kind,
		@Column("expected_char") String expectedChar,
		@Column("actual_char") String actualChar,
		@Column("is_correct") boolean correct,
		@Column("t_ms") int tMs,
		@Column("display_ms") Integer displayMs,
		@Column("is_warmup") boolean warmup,
		@Column("created_at") @ReadOnlyProperty OffsetDateTime createdAt) {
}
