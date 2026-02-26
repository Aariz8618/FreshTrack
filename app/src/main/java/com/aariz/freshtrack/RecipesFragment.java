package com.aariz.freshtrack;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecipesFragment extends Fragment {

    private SearchView searchView;
    private RecyclerView recyclerView;
    private RecipesAdapter adapter;
    private ProgressBar progressBar;
    private LinearLayout emptyState;
    private SwipeRefreshLayout swipeRefreshLayout;

    private final RecipeRepository recipeRepository = new RecipeRepository();
    private final FirestoreRepository firestoreRepository = new FirestoreRepository();
    private final List<Recipe> recipes = new ArrayList<>();
    private List<String> userIngredients = new ArrayList<>();
    private boolean isSearchMode = false;
    private String currentSearchQuery = "";

    private final List<Recipe> suggestedRecipesCache = new ArrayList<>();

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recipes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View headerSection = view.findViewById(R.id.header_section);
        if (headerSection != null) {
            WindowInsetsExtensions.applyHeaderInsets(headerSection);
        }

        initViews(view);
        setupRecyclerView();
        setupSearchView();
        setupSwipeRefresh();
        loadAllRecipes(false);
    }

    private void initViews(View view) {
        searchView         = view.findViewById(R.id.search_view);
        recyclerView       = view.findViewById(R.id.recycler_recipes);
        progressBar        = view.findViewById(R.id.progress_bar);
        emptyState         = view.findViewById(R.id.empty_state);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecipesAdapter(recipes, recipe -> openRecipeDetail(recipe));
        recyclerView.setAdapter(adapter);
    }

    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query != null && !query.trim().isEmpty()) {
                    isSearchMode       = true;
                    currentSearchQuery = query;
                    searchRecipes(query);
                } else {
                    isSearchMode       = false;
                    currentSearchQuery = "";
                    loadAllRecipes(false);
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if ((newText == null || newText.trim().isEmpty()) && isSearchMode) {
                    isSearchMode       = false;
                    currentSearchQuery = "";
                    loadAllRecipes(false);
                }
                return true;
            }
        });
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (isSearchMode && !currentSearchQuery.isEmpty()) {
                searchRecipes(currentSearchQuery);
            } else {
                loadAllRecipes(false);
            }
        });

        swipeRefreshLayout.setColorSchemeResources(
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light
        );
    }

    // ------------------------------------------------------------------ //
    //  Load all recipes
    // ------------------------------------------------------------------ //

    private void loadAllRecipes(boolean forceRefresh) {
        showLoading(true);

        // Use callback — getUserGroceryItemsAsync requires a Callback<List<GroceryItem>>
        firestoreRepository.getUserGroceryItemsAsync(groceryItems -> {
            // This callback runs on the Firestore thread; post work to executor
            executor.execute(() -> {
                try {
                    List<GroceryItem> items = (groceryItems != null) ? groceryItems : new ArrayList<>();

                    List<String> newIngredients = new ArrayList<>();
                    for (GroceryItem item : items) {
                        if (!"expired".equals(item.getStatus()) && !"used".equals(item.getStatus())) {
                            String name = item.getName().toLowerCase();
                            if (!newIngredients.contains(name)) {
                                newIngredients.add(name);
                            }
                        }
                    }
                    userIngredients = newIngredients;

                    if (suggestedRecipesCache.isEmpty() || forceRefresh) {
                        RecipeRepository.Result<List<Recipe>> suggestedResult =
                                recipeRepository.getSuggestedRecipes(userIngredients);
                        List<Recipe> suggested = suggestedResult.getOrNull();
                        suggestedRecipesCache.clear();
                        if (suggested != null) suggestedRecipesCache.addAll(suggested);
                    }

                    List<String> popularQueries = getRandomPopularQueries();
                    List<Recipe> allPopular = new ArrayList<>();
                    for (String query : popularQueries) {
                        RecipeRepository.Result<List<Recipe>> r = recipeRepository.searchRecipes(query);
                        if (r.isSuccess() && r.getOrNull() != null) {
                            allPopular.addAll(r.getOrNull());
                        }
                    }

                    if (!isAdded()) return;

                    List<Recipe> allRecipes = new ArrayList<>(suggestedRecipesCache);
                    Set<String> suggestedTitles = new HashSet<>();
                    for (Recipe rec : suggestedRecipesCache) {
                        suggestedTitles.add(rec.getTitle().toLowerCase());
                    }
                    for (Recipe rec : allPopular) {
                        if (!suggestedTitles.contains(rec.getTitle().toLowerCase())) {
                            allRecipes.add(rec);
                        }
                    }

                    List<Recipe> unique = distinctByTitle(allRecipes);
                    List<Recipe> sorted = sortRecipesByRelevance(unique);

                    postToMain(() -> {
                        if (!isAdded()) return;
                        recipes.clear();
                        recipes.addAll(sorted);
                        adapter.notifyDataSetChanged();
                        updateEmptyState();
                        showLoading(false);
                        swipeRefreshLayout.setRefreshing(false);
                    });

                } catch (Exception e) {
                    postToMain(() -> {
                        if (!isAdded()) return;
                        showLoading(false);
                        swipeRefreshLayout.setRefreshing(false);
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Error: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                        updateEmptyState();
                    });
                }
            });
        });
    }

    private void loadPopularRecipesOnly() {
        executor.execute(() -> {
            try {
                List<Recipe> allRecipes = new ArrayList<>();
                List<String> queries    = getRandomPopularQueries();

                for (String query : queries) {
                    RecipeRepository.Result<List<Recipe>> r = recipeRepository.searchRecipes(query);
                    if (r.isSuccess() && r.getOrNull() != null) {
                        allRecipes.addAll(r.getOrNull());
                    }
                }

                if (!isAdded()) return;

                List<Recipe> unique = distinctByTitle(allRecipes);

                postToMain(() -> {
                    if (!isAdded()) return;
                    recipes.clear();
                    recipes.addAll(unique);
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                });

            } catch (Exception e) {
                postToMain(() -> {
                    if (!isAdded()) return;
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                    updateEmptyState();
                });
            }
        });
    }

    // ------------------------------------------------------------------ //
    //  Search
    // ------------------------------------------------------------------ //

    private void searchRecipes(String query) {
        showLoading(true);

        executor.execute(() -> {
            try {
                List<String> searchQueries = generateSearchVariations(query);
                List<Recipe> allResults    = new ArrayList<>();

                for (String sq : searchQueries) {
                    RecipeRepository.Result<List<Recipe>> r = recipeRepository.searchRecipes(sq);
                    if (r.isSuccess() && r.getOrNull() != null) {
                        allResults.addAll(r.getOrNull());
                    }
                }

                if (!isAdded()) return;

                List<Recipe> unique = distinctByTitle(allResults);

                postToMain(() -> {
                    if (!isAdded()) return;
                    handleRecipeResult(RecipeRepository.Result.success(unique));
                    swipeRefreshLayout.setRefreshing(false);
                });

            } catch (Exception e) {
                postToMain(() -> {
                    if (!isAdded()) return;
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                    updateEmptyState();
                });
            }
        });
    }

    private List<String> generateSearchVariations(String query) {
        List<String> variations = new ArrayList<>();
        variations.add(query);
        variations.add(query + " recipe");
        variations.add(query + " recipes");
        variations.add(query + " dish");
        variations.add("easy " + query);
        variations.add("best " + query);
        variations.add("simple " + query);

        String[] words = query.split(" ");
        for (String word : words) {
            if (word.length() > 3 && !word.equals(query)) {
                variations.add(word);
            }
        }

        List<String> distinct = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String v : variations) {
            if (seen.add(v)) distinct.add(v);
        }
        return distinct;
    }

    private void handleRecipeResult(RecipeRepository.Result<List<Recipe>> result) {
        if (!isAdded()) return;
        showLoading(false);

        if (result.isSuccess()) {
            List<Recipe> fetched = result.getOrNull();
            if (fetched == null) fetched = new ArrayList<>();

            List<Recipe> sorted = (!userIngredients.isEmpty() && isSearchMode)
                    ? sortRecipesByRelevance(fetched)
                    : fetched;

            recipes.clear();
            recipes.addAll(sorted);
            adapter.notifyDataSetChanged();
            updateEmptyState();

            if (fetched.isEmpty() && getContext() != null) {
                Toast.makeText(getContext(), "No recipes found. Try a different search.",
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            Exception error = result.exceptionOrNull();
            if (getContext() != null) {
                Toast.makeText(getContext(),
                        "Failed to load recipes: " + (error != null ? error.getMessage() : ""),
                        Toast.LENGTH_LONG).show();
            }
            updateEmptyState();
        }
    }

    // ------------------------------------------------------------------ //
    //  Open detail
    // ------------------------------------------------------------------ //

    private void openRecipeDetail(Recipe recipe) {
        if (!isAdded()) return;

        ArrayList<String> times = extractTimesFromInstructions(recipe.getInstructions());

        Intent intent = new Intent(requireContext(), RecipeDetailActivity.class);
        intent.putExtra("title",               recipe.getTitle());
        intent.putExtra("servings",            recipe.getServings());
        intent.putExtra("prepTime",            "Varies");
        intent.putExtra("difficulty",          "Medium");
        intent.putExtra("ingredientsPreview",  recipe.getIngredientsPreview());
        intent.putExtra("instructionsPreview", recipe.getInstructionsPreview());
        intent.putStringArrayListExtra("ingredients",  new ArrayList<>(recipe.getIngredients()));
        intent.putStringArrayListExtra("instructions", new ArrayList<>(recipe.getInstructions()));
        intent.putStringArrayListExtra("times",        times);
        intent.putExtra("notes", "Recipe from API Ninjas");
        startActivity(intent);
    }

    private ArrayList<String> extractTimesFromInstructions(List<String> instructions) {
        ArrayList<String> times = new ArrayList<>();
        Pattern pattern = Pattern.compile(
                "(\\d+)\\s*(min|minute|minutes|hour|hours|seconds?)",
                Pattern.CASE_INSENSITIVE);

        for (String instruction : instructions) {
            Matcher matcher = pattern.matcher(instruction);
            if (matcher.find()) {
                int value   = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();
                int minutes;
                if (unit.startsWith("hour"))       minutes = value * 60;
                else if (unit.startsWith("sec"))   minutes = 1;
                else                               minutes = value;
                times.add(minutes + " min");
            } else {
                times.add("5 min");
            }
        }
        return times;
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    private List<String> getRandomPopularQueries() {
        List<String> all = Arrays.asList(
                "chicken", "pasta", "salad", "soup", "beef", "fish", "vegetarian",
                "rice", "noodles", "curry", "steak", "sandwich", "burger", "pizza",
                "dessert", "cake", "cookies", "seafood", "lamb", "pork", "tacos",
                "stir fry", "casserole", "breakfast", "lunch", "dinner", "healthy",
                "quick", "easy", "mexican", "italian", "chinese", "indian", "thai"
        );
        List<String> shuffled = new ArrayList<>(all);
        Collections.shuffle(shuffled, new Random());
        int count = 10 + new Random().nextInt(3);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    private List<Recipe> sortRecipesByRelevance(List<Recipe> recipeList) {
        List<Recipe> sorted = new ArrayList<>(recipeList);
        sorted.sort((a, b) -> Integer.compare(computeScore(b), computeScore(a)));
        return sorted;
    }

    private int computeScore(Recipe recipe) {
        int score = 0;
        for (String ingredient : recipe.getIngredients()) {
            String ingLower = ingredient.toLowerCase();
            for (String userIng : userIngredients) {
                if (ingLower.contains(userIng) || userIng.contains(ingLower)) {
                    score += 10;
                }
            }
        }
        if (recipe.getIngredients().size() <= 5) score += 5;
        return score;
    }

    private List<Recipe> distinctByTitle(List<Recipe> input) {
        List<Recipe> result = new ArrayList<>();
        Set<String>  seen   = new HashSet<>();
        for (Recipe r : input) {
            if (seen.add(r.getTitle().toLowerCase())) {
                result.add(r);
            }
        }
        return result;
    }

    private void showLoading(boolean show) {
        if (!isAdded()) return;
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE   : View.VISIBLE);
    }

    private void updateEmptyState() {
        if (!isAdded()) return;
        if (recipes.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
    }

    private void postToMain(Runnable r) {
        View root = getView();
        if (root != null) root.post(r);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdownNow();
    }
}