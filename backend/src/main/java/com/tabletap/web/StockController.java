package com.tabletap.web;

import com.tabletap.dto.StockDtos.*;
import com.tabletap.security.CurrentUser;
import com.tabletap.stock.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StockController {
    private final StockService stock;
    private final CurrentUser current;

    @GetMapping("/restaurants/{rid}/ingredients")
    public List<IngredientView> list(@PathVariable Long rid) { return stock.list(current.get(), rid); }

    @PostMapping("/restaurants/{rid}/ingredients")
    public IngredientView create(@PathVariable Long rid, @Valid @RequestBody IngredientRequest req) {
        return stock.create(current.get(), rid, req);
    }

    @PutMapping("/ingredients/{id}")
    public IngredientView update(@PathVariable Long id, @Valid @RequestBody IngredientRequest req) {
        return stock.update(current.get(), id, req);
    }

    @DeleteMapping("/ingredients/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { stock.delete(current.get(), id); }

    @PostMapping("/ingredients/{id}/restock")
    public IngredientView restock(@PathVariable Long id, @Valid @RequestBody RestockRequest req) {
        return stock.restock(current.get(), id, req.amount());
    }

    @GetMapping("/menu/items/{id}/recipe")
    public List<RecipeLineView> recipe(@PathVariable Long id) { return stock.recipe(current.get(), id); }

    @PutMapping("/menu/items/{id}/recipe")
    public List<RecipeLineView> setRecipe(@PathVariable Long id, @Valid @RequestBody RecipeRequest req) {
        return stock.setRecipe(current.get(), id, req);
    }

    @GetMapping("/restaurants/{rid}/stock-alerts")
    public List<AlertView> alerts(@PathVariable Long rid) { return stock.alerts(current.get(), rid); }

    @PostMapping("/stock-alerts/{id}/acknowledge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acknowledge(@PathVariable Long id) { stock.acknowledge(current.get(), id); }
}
