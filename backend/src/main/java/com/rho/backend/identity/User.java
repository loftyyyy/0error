package com.rho.backend.identity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
public record User(
		@Id UUID id,
		String kind,
		String email,
		@Column("display_name") String displayName,
		@Column("guest_token_hash") String guestTokenHash,
		@Column("claimed_by_user_id") UUID claimedByUserId,
		@Column("claimed_at") OffsetDateTime claimedAt,
		@Column("created_at") @ReadOnlyProperty OffsetDateTime createdAt,
		@Column("updated_at") @ReadOnlyProperty OffsetDateTime updatedAt) {
}
