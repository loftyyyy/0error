package com.rho.backend.identity;

import java.util.UUID;

/** Authenticated guest principal attached to the reactive security context. */
public record GuestPrincipal(UUID userId, String kind) {
}
