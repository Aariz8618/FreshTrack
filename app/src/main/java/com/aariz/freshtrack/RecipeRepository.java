package com.aariz.freshtrack;

import com.aariz.freshtrack.RecipeApiService;
import com.aariz.freshtrack.RetrofitClient;
import com.aariz.freshtrack.Recipe;
import com.aariz.freshtrack.RecipeResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Response;

/**
 * Repository for Recipe API operations.
 * All methods perform network I/O — call them from a background thread
 * (e.g. via ExecutorService or AsyncTask).
 */
public class RecipeRepository {

    private final RecipeApiService apiService;
    private final String apiKey;

    public RecipeRepository() {
        apiService = RetrofitClient.getRecipeApiService();
        apiKey     = RecipeApiService.API_KEY;
    }

    public static class Result<T> {
        private final T value;
        private final Exception error;

        private Result(T value, Exception error) {
            this.value = value;
            this.error = error;
        }

        public static <T> Result<T> success(T value)      { return new Result<>(value, null); }
        public static <T> Result<T> failure(Exception e)  { return new Result<>(null, e); }

        public boolean isSuccess()       { return error == null; }
        public T getOrNull()             { return value; }
        public Exception exceptionOrNull() { return error; }
    }

    public Result<List<Recipe>> searchRecipes(String query) {
        try {
            Response<List<RecipeResponse>> response =
                    apiService.searchRecipes(query, apiKey).execute();

            if (response.isSuccessful()) {
                List<RecipeResponse> body = response.body();
                if (body == null) body = new ArrayList<>();

                List<Recipe> recipes = new ArrayList<>();
                for (RecipeResponse r : body) {
                    recipes.add(Recipe.fromResponse(r));
                }
                return Result.success(recipes);
            } else {
                return Result.failure(
                        new Exception("Error: " + response.code() + " - " + response.message()));
            }
        } catch (IOException e) {
            return Result.failure(new Exception("Network error: " + e.getMessage(), e));
        }
    }

    public Result<List<Recipe>> getRecipesByIngredient(String ingredient) {
        return searchRecipes(ingredient);
    }

    public Result<List<Recipe>> getSuggestedRecipes(List<String> userIngredients) {
        try {
            if (userIngredients == null || userIngredients.isEmpty()) {
                return searchRecipes("popular");
            }

            List<Recipe> allRecipes  = new ArrayList<>();
            Set<String>  uniqueTitles = new HashSet<>();

            // Use top 3 ingredients to avoid too many API calls
            int limit = Math.min(3, userIngredients.size());
            for (int i = 0; i < limit; i++) {
                String ingredient = userIngredients.get(i);
                Result<List<Recipe>> result = searchRecipes(ingredient);

                if (result.isSuccess()) {
                    List<Recipe> recipes = result.getOrNull();
                    if (recipes != null) {
                        for (Recipe recipe : recipes) {
                            if (uniqueTitles.add(recipe.getTitle().toLowerCase())) {
                                allRecipes.add(recipe);
                            }
                        }
                    }
                }

                if (allRecipes.size() >= 15) break;
            }

            if (allRecipes.isEmpty()) {
                return searchRecipes("dinner");
            }

            return Result.success(allRecipes);

        } catch (Exception e) {
            return Result.failure(
                    new Exception("Error fetching suggested recipes: " + e.getMessage(), e));
        }
    }

    public Result<List<Recipe>> getPopularRecipes() {
        return searchRecipes("popular");
    }

    public Result<List<Recipe>> getRecipesByCategory(String category) {
        return searchRecipes(category);
    }
}