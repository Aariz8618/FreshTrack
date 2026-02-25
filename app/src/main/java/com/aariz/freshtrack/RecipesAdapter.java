package com.aariz.freshtrack;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aariz.freshtrack.Recipe;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class RecipesAdapter extends RecyclerView.Adapter<RecipesAdapter.RecipeViewHolder> {

    public interface OnRecipeClickListener {
        void onRecipeClick(Recipe recipe);
    }

    private final List<Recipe> recipes;
    private final OnRecipeClickListener listener;

    public RecipesAdapter(List<Recipe> recipes, OnRecipeClickListener listener) {
        this.recipes  = recipes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecipeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recipe, parent, false);
        return new RecipeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecipeViewHolder holder, int position) {
        holder.bind(recipes.get(position));
    }

    @Override
    public int getItemCount() {
        return recipes.size();
    }

    class RecipeViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView cardView;
        private final TextView titleText;
        private final TextView servingsText;
        private final TextView ingredientsPreview;

        RecipeViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView          = itemView.findViewById(R.id.card_recipe);
            titleText         = itemView.findViewById(R.id.text_recipe_title);
            servingsText      = itemView.findViewById(R.id.text_servings);
            ingredientsPreview = itemView.findViewById(R.id.text_ingredients_preview);
        }

        void bind(Recipe recipe) {
            titleText.setText(recipe.getTitle());
            servingsText.setText("Servings: " + recipe.getServings());
            ingredientsPreview.setText(recipe.getIngredientsPreview());

            cardView.setOnClickListener(v -> listener.onRecipeClick(recipe));
        }
    }
}