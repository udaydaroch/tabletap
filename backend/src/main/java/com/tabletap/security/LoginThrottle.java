package com.tabletap.security;

import com.tabletap.service.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection: max 5 failed logins per account and 20 per IP per 15 minutes,
 * and max 5 sign-ups per IP per 15 minutes. In-memory (per replica).
 */
@Component
public class LoginThrottle {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_PER_ACCOUNT = 5, MAX_PER_IP = 20, MAX_SIGNUPS_PER_IP = 5;

    private record Bucket(int count, Instant start) {
        boolean expired() { return start.plus(WINDOW).isBefore(Instant.now()); }
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void checkLogin(String email, String ip) {
        if (over("acct:" + email, MAX_PER_ACCOUNT) || over("ip:" + ip, MAX_PER_IP)) throw tooMany();
    }

    public void loginFailed(String email, String ip) {
        hit("acct:" + email);
        hit("ip:" + ip);
    }

    public void loginSucceeded(String email) {
        buckets.remove("acct:" + email);
    }

    public void checkSignup(String ip) {
        if (over("signup:" + ip, MAX_SIGNUPS_PER_IP)) throw tooMany();
        hit("signup:" + ip);
    }

    private boolean over(String key, int max) {
        Bucket b = buckets.get(key);
        return b != null && !b.expired() && b.count() >= max;
    }

    private void hit(String key) {
        buckets.compute(key, (k, b) -> b == null || b.expired() ? new Bucket(1, Instant.now()) : new Bucket(b.count() + 1, b.start()));
    }

    private ApiException tooMany() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Try again in 15 minutes.");
    }

    @Scheduled(fixedRate = 300_000)
    void cleanup() {
        buckets.values().removeIf(Bucket::expired);
    }
}
