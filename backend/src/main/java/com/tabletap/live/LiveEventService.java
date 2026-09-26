package com.tabletap.live;

import com.tabletap.domain.AppUser;
import com.tabletap.domain.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-Sent Events hub. Events are broadcast only after the DB transaction commits,
 * and only to subscribers allowed to see that restaurant/owner.
 * Note: in-memory — with more than one replica, back this with Redis/Azure Web PubSub.
 */
@Slf4j
@Service
public class LiveEventService {
    private static final int MAX_STREAMS_PER_USER = 5;

    private record Sub(SseEmitter emitter, Long userId, Role role, Long restaurantId, Long ownerId) {}

    private final Set<Sub> subs = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe(AppUser u) {
        // cap streams per user so one account can't exhaust server threads
        var mine = subs.stream().filter(s -> s.userId().equals(u.getId())).toList();
        if (mine.size() >= MAX_STREAMS_PER_USER) close(mine.get(0));

        SseEmitter emitter = new SseEmitter(0L);
        Long restaurantId = u.getRestaurant() == null ? null : u.getRestaurant().getId();
        Long ownerId = switch (u.getRole()) {
            case OWNER -> u.getId();
            case WAITER, CHEF -> u.getRestaurant() == null ? null : u.getRestaurant().getOwner().getId();
            case ADMIN -> null;
        };
        Sub sub = new Sub(emitter, u.getId(), u.getRole(), restaurantId, ownerId);
        subs.add(sub);
        emitter.onCompletion(() -> subs.remove(sub));
        emitter.onTimeout(() -> subs.remove(sub));
        emitter.onError(t -> subs.remove(sub));
        send(sub, SseEmitter.event().name("ready").data("{}"));
        return emitter;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onChange(LiveEvent ev) {
        for (Sub s : subs) {
            if (canSee(s, ev)) send(s, SseEmitter.event().name("change").data(ev, MediaType.APPLICATION_JSON));
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onRevoked(SessionRevoked ev) {
        subs.stream()
            .filter(s -> s.userId().equals(ev.userId()) || (ev.ownerScope() && ev.userId().equals(s.ownerId())))
            .forEach(this::close);
    }

    private boolean canSee(Sub s, LiveEvent ev) {
        return switch (s.role()) {
            case ADMIN -> true;
            case OWNER -> Objects.equals(ev.ownerId(), s.userId());
            case WAITER, CHEF -> ev.restaurantId() != null && ev.restaurantId().equals(s.restaurantId());
        };
    }

    @Scheduled(fixedRate = 20_000)
    void heartbeat() {
        subs.forEach(s -> send(s, SseEmitter.event().comment("ping")));
    }

    private void send(Sub s, SseEmitter.SseEventBuilder event) {
        synchronized (s.emitter()) {
            try {
                s.emitter().send(event);
            } catch (IOException | IllegalStateException e) {
                subs.remove(s);
            }
        }
    }

    private void close(Sub s) {
        subs.remove(s);
        try { s.emitter().complete(); } catch (Exception ignored) { /* already closed */ }
    }
}
