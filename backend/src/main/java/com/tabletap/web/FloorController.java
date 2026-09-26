package com.tabletap.web;

import com.tabletap.dto.FloorDtos.*;
import com.tabletap.security.CurrentUser;
import com.tabletap.service.FloorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FloorController {
    private final FloorService floors;
    private final CurrentUser current;

    @GetMapping("/restaurants/{rid}/floor")
    public FloorPlan get(@PathVariable Long rid) { return floors.get(current.get(), rid); }

    @PutMapping("/restaurants/{rid}/floor")
    public FloorPlan save(@PathVariable Long rid, @Valid @RequestBody FloorSaveRequest req) {
        return floors.save(current.get(), rid, req);
    }

    @PostMapping("/restaurants/{rid}/floor/template")
    public FloorPlan applyTemplate(@PathVariable Long rid, @Valid @RequestBody TemplateRequest req) {
        return floors.applyTemplate(current.get(), rid, req.key());
    }

    @PostMapping("/restaurants/{rid}/floor/tier")
    public FloorPlan setTier(@PathVariable Long rid, @RequestBody TierRequest req) {
        return floors.setTier(current.get(), rid, req);
    }

    @GetMapping("/floor-templates")
    public List<TemplateView> templates() {
        current.get(); // any signed-in user
        return floors.templates();
    }
}
