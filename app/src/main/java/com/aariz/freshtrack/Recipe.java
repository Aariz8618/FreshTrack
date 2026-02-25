package com.aariz.freshtrack;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain model for Recipe (used in UI)
 */
public class Recipe {

    private final String title;
    private final List<String> ingredients;
    private final String servings;
    private final List<String> instructions;
    private final String ingredientsRaw;
    private final String instructionsRaw;

    public Recipe(String title,
                  List<String> ingredients,
                  String servings,
                  List<String> instructions,
                  String ingredientsRaw,
                  String instructionsRaw) {
        this.title           = title;
        this.ingredients     = ingredients;
        this.servings        = servings;
        this.instructions    = instructions;
        this.ingredientsRaw  = ingredientsRaw;
        this.instructionsRaw = instructionsRaw;
    }

    // ---- Getters ----
    public String getTitle()              { return title; }
    public List<String> getIngredients()  { return ingredients; }
    public String getServings()           { return servings; }
    public List<String> getInstructions() { return instructions; }
    public String getIngredientsRaw()     { return ingredientsRaw; }
    public String getInstructionsRaw()    { return instructionsRaw; }

    // ---- Factory ----

    /**
     * Convert API response to domain model
     */
    public static Recipe fromResponse(RecipeResponse response) {
        String servings = (response.getServings() == null || response.getServings().isEmpty())
                ? "Not specified"
                : response.getServings();

        return new Recipe(
                response.getTitle(),
                parseIngredients(response.getIngredients()),
                servings,
                parseInstructions(response.getInstructions()),
                response.getIngredients(),
                response.getInstructions()
        );
    }

    private static List<String> parseIngredients(String ingredientsString) {
        if (ingredientsString == null || ingredientsString.isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = ingredientsString.split("\\|");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static List<String> parseInstructions(String instructionsString) {
        if (instructionsString == null || instructionsString.isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = instructionsString.split("\\.");
        List<String> steps = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && trimmed.length() > 10) {
                steps.add(trimmed);
            }
        }
        if (!steps.isEmpty()) {
            return steps;
        }
        List<String> fallback = new ArrayList<>();
        fallback.add(instructionsString);
        return fallback;
    }

    // ---- Helpers ----

    public String getIngredientsPreview() {
        int count = Math.min(3, ingredients.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(", ");
            sb.append(ingredients.get(i));
        }
        if (ingredients.size() > 3) sb.append("...");
        return sb.toString();
    }

    public String getInstructionsPreview() {
        if (!instructions.isEmpty()) {
            String first = instructions.get(0);
            if (first.length() > 100) {
                return first.substring(0, 100) + "...";
            }
            return first;
        }
        return "See full instructions";
    }
}