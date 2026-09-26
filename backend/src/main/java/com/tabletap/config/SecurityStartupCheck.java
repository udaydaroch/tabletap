package com.tabletap.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Refuses to boot a non-demo (production) deployment that still uses development secrets. */
@Component
@RequiredArgsConstructor
public class SecurityStartupCheck {
    private final AppProperties props;

    @PostConstruct
    void verify() {
        if (props.demo().enabled()) return;
        String secret = props.jwt().secret();
        if (secret.contains("change-me") || secret.contains("local-dev") || secret.length() < 48)
            throw new IllegalStateException("Set JWT_SECRET to a random value of at least 48 characters for production");
        if ("ChangeMe123!".equals(props.admin().password()))
            throw new IllegalStateException("Set ADMIN_PASSWORD for production");
    }
}
