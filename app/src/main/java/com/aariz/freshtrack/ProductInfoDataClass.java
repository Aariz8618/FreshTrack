package com.aariz.freshtrack;

import com.google.gson.annotations.SerializedName;
import java.util.Date;
import java.util.Map;

// ─────────────────────────────────────────────────────────────
//  OpenFoodFacts API Response Models
// ─────────────────────────────────────────────────────────────

class OpenFoodFactsResponse {
    @SerializedName("status")
    public int status;

    @SerializedName("product")
    public OpenFoodFactsProduct product;
}

class OpenFoodFactsProduct {
    @SerializedName("product_name")
    public String productName;

    @SerializedName("brands")
    public String brands;

    @SerializedName("categories")
    public String categories;

    @SerializedName("image_url")
    public String imageUrl;

    @SerializedName("image_front_small_url")
    public String imageFrontSmallUrl;

    @SerializedName("nutriments")
    public Map<String, Object> nutriments;

    @SerializedName("ingredients_text")
    public String ingredientsText;

    @SerializedName("allergens")
    public String allergens;
}

// ─────────────────────────────────────────────────────────────
//  Cached Product Info  (stored in Firestore)
// ─────────────────────────────────────────────────────────────
class CachedProductInfo {
    public String barcode = "";
    public String productName = "";
    public String brands = "";
    public String categories = "";
    public String imageUrl = "";
    public String suggestedCategory = "";
    public Date cachedAt = new Date();
    public String source = "openfoodfacts";

    /** Required no-arg constructor for Firestore deserialization */
    public CachedProductInfo() {}

    public CachedProductInfo(String barcode, String productName, String brands,
                             String categories, String imageUrl,
                             String suggestedCategory, Date cachedAt, String source) {
        this.barcode = barcode;
        this.productName = productName;
        this.brands = brands;
        this.categories = categories;
        this.imageUrl = imageUrl;
        this.suggestedCategory = suggestedCategory;
        this.cachedAt = cachedAt;
        this.source = source;
    }
}

//  Barcode Scan Result
class BarcodeScanResult {
    public final String barcode;
    public final String format; // EAN_13, UPC_A, etc.

    public BarcodeScanResult(String barcode, String format) {
        this.barcode = barcode;
        this.format = format;
    }
}