package com.tabletap.web;

import com.tabletap.dto.BillingUsage;
import com.tabletap.dto.TreeNode;
import com.tabletap.security.CurrentUser;
import com.tabletap.service.BillingService;
import com.tabletap.service.TreeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrgController {
    private final TreeService tree;
    private final BillingService billing;
    private final CurrentUser current;

    @GetMapping("/tree")
    public TreeNode tree() { return tree.treeFor(current.get()); }

    @GetMapping("/billing/usage")
    public BillingUsage usage() { return billing.usage(current.get()); }
}
