package com.tabletap.live;

/** Close live streams for a user (and, if ownerScope, for all staff under that owner). */
public record SessionRevoked(Long userId, boolean ownerScope) {}
