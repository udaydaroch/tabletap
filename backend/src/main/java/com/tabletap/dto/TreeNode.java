package com.tabletap.dto;

import java.util.List;

/**
 * Generic org-chart node. type is one of PLATFORM, OWNER, RESTAURANT, STAFF.
 * entityId is the user id (OWNER/STAFF) or restaurant id (RESTAURANT).
 */
public record TreeNode(String key, String type, String label, String subtitle, Long entityId,
                       boolean active, boolean onShift, List<TreeNode> children) {}
