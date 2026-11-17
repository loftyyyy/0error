package com.rho.backend.drills;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One drill text the server served to one guest. A session may only be created
 * from unexpired, unconsumed issuances, which keeps the tier and the expected
 * characters under server control.
 */
@Table("drill_issuances")
public record DrillIssuance(
		@Id UUID id,
		@Column("user_id") UUID userId,
		int tier,
		String source,
		String charset,
		@Column("drill_text") String drillText,
		@Column("created_at") @ReadOnlyProperty OffsetDateTime createdAt,
		@Column("expires_at") OffsetDateTime expiresAt,
		@Column("consumed_at") OffsetDateTime consumedAt) {
}
