package com.tabletap.security;

import com.tabletap.config.AppProperties;
import com.tabletap.domain.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenService {
    private static final Duration IMPERSONATION_TTL = Duration.ofHours(1);

    private final JwtEncoder encoder;
    private final AppProperties props;

    /** @param impersonatorId admin id when an admin is acting as another user, otherwise null */
    public String issue(AppUser user, Long impersonatorId) {
        Instant now = Instant.now();
        Duration ttl = impersonatorId != null ? IMPERSONATION_TTL : Duration.ofHours(props.jwt().ttlHours());
        var claims = JwtClaimsSet.builder()
            .issuer("tabletap")
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .claim("role", user.getRole().name())
            .claim("ver", user.getTokenVersion());
        if (impersonatorId != null) claims.claim("imp", impersonatorId.toString());
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
