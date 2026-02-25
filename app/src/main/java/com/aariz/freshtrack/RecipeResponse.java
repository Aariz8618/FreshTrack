package com.aariz.freshtrack;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Data class for Recipe API response
 */
public class RecipeResponse {

    @SerializedName("title")
    private String title;

    @SerializedName("ingredients")
    private String ingredients;

    @SerializedName("servings")
    private String servings;

    @SerializedName("instructions")
    private String instructions;

    public RecipeResponse() {
        this.title = "";
        this.ingredients = "";
        this.servings = "";
        this.instructions = "";
    }

    public String getTitle()        { return title; }
    public String getIngredients()  { return ingredients; }
    public String getServings()     { return servings; }
    public String getInstructions() { return instructions; }

    public void setTitle(String title)               { this.title = title; }
    public void setIngredients(String ingredients)   { this.ingredients = ingredients; }
    public void setServings(String servings)         { this.servings = servings; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
}