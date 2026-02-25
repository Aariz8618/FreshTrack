package com.aariz.freshtrack;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

public class RecipeDetailActivity extends AppCompatActivity {

    private boolean isIngredientsExpanded  = false;
    private boolean isInstructionsExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_recipe_detail);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsExtensions.applyHeaderInsets(findViewById(R.id.header_section));
        WindowInsetsExtensions.applyBottomNavInsets(findViewById(R.id.bottom_bar));

        setupBackButton();
        loadRecipeData();
        setupExpandableCards();
        setupActionButtons();
        setupStartCookingButton();
    }

    private void setupBackButton() {
        findViewById(R.id.button_back).setOnClickListener(v -> finish());
    }

    private void loadRecipeData() {
        Intent intent = getIntent();

        String title               = intent.getStringExtra("title");
        String servings            = intent.getStringExtra("servings");
        String ingredientsPreview  = intent.getStringExtra("ingredientsPreview");
        String instructionsPreview = intent.getStringExtra("instructionsPreview");

        if (title == null)               title = "Recipe";
        if (servings == null)            servings = "4 servings";
        if (ingredientsPreview == null)  ingredientsPreview = "";
        if (instructionsPreview == null) instructionsPreview = "";

        ArrayList<String> ingredients  = intent.getStringArrayListExtra("ingredients");
        ArrayList<String> instructions = intent.getStringArrayListExtra("instructions");
        if (ingredients == null)  ingredients  = new ArrayList<>();
        if (instructions == null) instructions = new ArrayList<>();

        ((TextView) findViewById(R.id.text_recipe_title)).setText(title);
        ((TextView) findViewById(R.id.text_servings)).setText(servings);
        ((TextView) findViewById(R.id.text_ingredients_preview)).setText(ingredientsPreview);
        ((TextView) findViewById(R.id.text_instructions_preview)).setText(instructionsPreview);

        populateIngredients(ingredients);
        populateInstructions(instructions);
    }

    private void populateIngredients(java.util.List<String> ingredients) {
        LinearLayout layout = findViewById(R.id.layout_ingredients_full);
        layout.removeAllViews();

        for (String ingredient : ingredients) {
            TextView tv = new TextView(this);
            tv.setText("• " + ingredient);
            tv.setTextSize(14f);
            layout.addView(tv);
        }
    }

    private void populateInstructions(java.util.List<String> instructions) {
        LinearLayout layout = findViewById(R.id.layout_instructions_full);
        layout.removeAllViews();

        for (int i = 0; i < instructions.size(); i++) {
            TextView tv = new TextView(this);
            tv.setText((i + 1) + ". " + instructions.get(i));
            tv.setTextSize(14f);
            layout.addView(tv);
        }
    }

    private void setupExpandableCards() {
        // --- Ingredients ---
        LinearLayout ingredientsButton     = findViewById(R.id.button_show_ingredients);
        TextView     ingredientsButtonText = (TextView) ingredientsButton.getChildAt(0);
        LinearLayout ingredientsFull       = findViewById(R.id.layout_ingredients_full);
        TextView     ingredientsPreview    = findViewById(R.id.text_ingredients_preview);

        ingredientsButton.setOnClickListener(v -> {
            isIngredientsExpanded = !isIngredientsExpanded;
            if (isIngredientsExpanded) {
                ingredientsFull.setVisibility(View.VISIBLE);
                ingredientsPreview.setVisibility(View.GONE);
                ingredientsButtonText.setText("Hide Ingredients");
            } else {
                ingredientsFull.setVisibility(View.GONE);
                ingredientsPreview.setVisibility(View.VISIBLE);
                ingredientsButtonText.setText("Show Full Ingredients");
            }
        });

        // --- Instructions ---
        LinearLayout instructionsButton     = findViewById(R.id.button_show_instructions);
        TextView     instructionsButtonText = (TextView) instructionsButton.getChildAt(0);
        LinearLayout instructionsFull       = findViewById(R.id.layout_instructions_full);
        TextView     instructionsPreview    = findViewById(R.id.text_instructions_preview);

        instructionsButton.setOnClickListener(v -> {
            isInstructionsExpanded = !isInstructionsExpanded;
            if (isInstructionsExpanded) {
                instructionsFull.setVisibility(View.VISIBLE);
                instructionsPreview.setVisibility(View.GONE);
                instructionsButtonText.setText("Hide Instructions");
            } else {
                instructionsFull.setVisibility(View.GONE);
                instructionsPreview.setVisibility(View.VISIBLE);
                instructionsButtonText.setText("Show Full Instructions");
            }
        });
    }

    private void setupActionButtons() {
        ((LinearLayout) findViewById(R.id.button_save)).setOnClickListener(v ->
                Toast.makeText(this, "Recipe saved!", Toast.LENGTH_SHORT).show());

        ((LinearLayout) findViewById(R.id.button_share)).setOnClickListener(v -> shareRecipe());
    }

    private void setupStartCookingButton() {
        ((LinearLayout) findViewById(R.id.button_start_cooking)).setOnClickListener(v -> {
            ArrayList<String> instructions = getIntent().getStringArrayListExtra("instructions");
            ArrayList<String> times        = getIntent().getStringArrayListExtra("times");
            if (instructions == null) instructions = new ArrayList<>();
            if (times == null)        times        = new ArrayList<>();

            // If times are not provided, create default times
            ArrayList<String> defaultTimes;
            if (times.isEmpty()) {
                defaultTimes = new ArrayList<>();
                for (int i = 0; i < instructions.size(); i++) {
                    defaultTimes.add("5 min");
                }
            } else {
                defaultTimes = times;
            }

            Intent cookingIntent = new Intent(this, CookingModeActivity.class);
            cookingIntent.putStringArrayListExtra("instructions", instructions);
            cookingIntent.putStringArrayListExtra("times", defaultTimes);
            startActivity(cookingIntent);
        });
    }

    private void shareRecipe() {
        String title = ((TextView) findViewById(R.id.text_recipe_title)).getText().toString();
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                "✨ Found something tasty! " + title + " – give it a try\n"
                        + "Check it out on FreshTrack - https://bit.ly/4nTf0Oo");
        shareIntent.setType("text/plain");
        startActivity(Intent.createChooser(shareIntent, "Share recipe via"));
    }
}