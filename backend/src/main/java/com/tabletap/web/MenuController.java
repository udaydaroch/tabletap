package com.tabletap.web;

import com.tabletap.dto.MenuDtos.*;
import com.tabletap.security.CurrentUser;
import com.tabletap.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MenuController {
    private final MenuService menu;
    private final CurrentUser current;

    @GetMapping("/restaurants/{rid}/menu")
    public List<CategoryView> menu(@PathVariable Long rid) { return menu.menu(current.get(), rid); }

    @PostMapping("/restaurants/{rid}/menu/categories")
    public CategoryView addCategory(@PathVariable Long rid, @Valid @RequestBody CategoryRequest req) {
        return menu.createCategory(current.get(), rid, req);
    }

    @PutMapping("/menu/categories/{id}")
    public CategoryView updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        return menu.updateCategory(current.get(), id, req);
    }

    @DeleteMapping("/menu/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) { menu.deleteCategory(current.get(), id); }

    @PostMapping("/menu/items")
    public ItemView addItem(@Valid @RequestBody ItemRequest req) { return menu.createItem(current.get(), req); }

    @PutMapping("/menu/items/{id}")
    public ItemView updateItem(@PathVariable Long id, @Valid @RequestBody ItemRequest req) {
        return menu.updateItem(current.get(), id, req);
    }

    @PatchMapping("/menu/items/{id}/available")
    public ItemView setAvailable(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return menu.setAvailable(current.get(), id, Boolean.TRUE.equals(body.get("available")));
    }

    @DeleteMapping("/menu/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long id) { menu.deleteItem(current.get(), id); }
}
