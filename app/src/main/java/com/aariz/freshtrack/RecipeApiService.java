package com.aariz.freshtrack;

import com.aariz.freshtrack.RecipeResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

/**
 * Retrofit API interface for Recipe API
 */
public interface RecipeApiService {

    String BASE_URL = "https://api.api-ninjas.com/v1/";
    String API_KEY  = "DFjO/ZiJRuFrtUyJUpgA/w==DFB0MmIijzEgzsrv";

    @GET("recipe")
    Call<List<RecipeResponse>> searchRecipes(
            @Query("query") String query,
            @Header("X-Api-Key") String apiKey
    );
}