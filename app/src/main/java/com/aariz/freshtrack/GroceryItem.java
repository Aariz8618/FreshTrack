package com.aariz.freshtrack;

import com.google.firebase.firestore.DocumentId;

import java.util.Date;

public class GroceryItem {

    @DocumentId
    private String id;
    private String name;
    private String category;
    private String expiryDate;
    private String purchaseDate;
    private int quantity;
    private String status;
    private int daysLeft;
    private String barcode;
    private String imageUrl;
    private boolean isGS1;
    private String batchLot;
    private String serialNumber;
    private String weight;
    private String weightUnit;
    private String amount;
    private String storageLocation;
    private String store;
    private String notes;
    private Date createdAt;
    private Date updatedAt;

    // No-argument constructor required for Firestore
    public GroceryItem() {
        this.id = "";
        this.name = "";
        this.category = "";
        this.expiryDate = "";
        this.purchaseDate = "";
        this.quantity = 1;
        this.status = "fresh";
        this.daysLeft = 0;
        this.barcode = "";
        this.imageUrl = "";
        this.isGS1 = false;
        this.batchLot = "";
        this.serialNumber = "";
        this.weight = "";
        this.weightUnit = "";
        this.amount = "";
        this.storageLocation = "";
        this.store = "";
        this.notes = "";
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }

    // Full constructor
    public GroceryItem(String name, String category, String expiryDate, String purchaseDate,
                       int quantity, String amount, String weight, String weightUnit,
                       String storageLocation, String notes, String status, int daysLeft,
                       String barcode, String imageUrl, boolean isGS1, Date createdAt, Date updatedAt) {
        this.id = "";
        this.name = name;
        this.category = category;
        this.expiryDate = expiryDate;
        this.purchaseDate = purchaseDate;
        this.quantity = quantity;
        this.amount = amount;
        this.weight = weight;
        this.weightUnit = weightUnit;
        this.storageLocation = storageLocation;
        this.notes = notes;
        this.status = status;
        this.daysLeft = daysLeft;
        this.barcode = barcode;
        this.imageUrl = imageUrl;
        this.isGS1 = isGS1;
        this.batchLot = "";
        this.serialNumber = "";
        this.store = "";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Creates a copy of this item with updated daysLeft and status.
     * Mirrors Kotlin data class .copy(daysLeft = ..., status = ...)
     */
    public GroceryItem copy(int daysLeft, String status) {
        GroceryItem copy = new GroceryItem();
        copy.id = this.id;
        copy.name = this.name;
        copy.category = this.category;
        copy.expiryDate = this.expiryDate;
        copy.purchaseDate = this.purchaseDate;
        copy.quantity = this.quantity;
        copy.status = status;
        copy.daysLeft = daysLeft;
        copy.barcode = this.barcode;
        copy.imageUrl = this.imageUrl;
        copy.isGS1 = this.isGS1;
        copy.batchLot = this.batchLot;
        copy.serialNumber = this.serialNumber;
        copy.weight = this.weight;
        copy.weightUnit = this.weightUnit;
        copy.amount = this.amount;
        copy.storageLocation = this.storageLocation;
        copy.store = this.store;
        copy.notes = this.notes;
        copy.createdAt = this.createdAt;
        copy.updatedAt = this.updatedAt;
        return copy;
    }

    // --- Getters ---

    public String getId() { return id != null ? id : ""; }
    public String getName() { return name != null ? name : ""; }
    public String getCategory() { return category != null ? category : ""; }
    public String getExpiryDate() { return expiryDate != null ? expiryDate : ""; }
    public String getPurchaseDate() { return purchaseDate != null ? purchaseDate : ""; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status != null ? status : "fresh"; }
    public int getDaysLeft() { return daysLeft; }
    public String getBarcode() { return barcode != null ? barcode : ""; }
    public String getImageUrl() { return imageUrl != null ? imageUrl : ""; }
    public boolean isGS1() { return isGS1; }
    public String getBatchLot() { return batchLot != null ? batchLot : ""; }
    public String getSerialNumber() { return serialNumber != null ? serialNumber : ""; }
    public String getWeight() { return weight != null ? weight : ""; }
    public String getWeightUnit() { return weightUnit != null ? weightUnit : ""; }
    public String getAmount() { return amount != null ? amount : ""; }
    public String getStorageLocation() { return storageLocation != null ? storageLocation : ""; }
    public String getStore() { return store != null ? store : ""; }
    public String getNotes() { return notes != null ? notes : ""; }
    public Date getCreatedAt() { return createdAt != null ? createdAt : new Date(); }
    public Date getUpdatedAt() { return updatedAt != null ? updatedAt : new Date(); }

    // --- Setters (required by Firestore deserialization) ---

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCategory(String category) { this.category = category; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }
    public void setPurchaseDate(String purchaseDate) { this.purchaseDate = purchaseDate; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setStatus(String status) { this.status = status; }
    public void setDaysLeft(int daysLeft) { this.daysLeft = daysLeft; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setGS1(boolean GS1) { isGS1 = GS1; }
    public void setBatchLot(String batchLot) { this.batchLot = batchLot; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public void setWeight(String weight) { this.weight = weight; }
    public void setWeightUnit(String weightUnit) { this.weightUnit = weightUnit; }
    public void setAmount(String amount) { this.amount = amount; }
    public void setStorageLocation(String storageLocation) { this.storageLocation = storageLocation; }
    public void setStore(String store) { this.store = store; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}