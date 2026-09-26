package com.tabletap.web;

import com.tabletap.live.LiveEventService;
import com.tabletap.security.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class LiveController {
    private final LiveEventService live;
    private final CurrentUser current;

    @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(HttpServletResponse res) {
        res.setHeader("X-Accel-Buffering", "no"); // don't let proxies buffer the stream
        res.setHeader("Cache-Control", "no-store");
        return live.subscribe(current.get());
    }
}
