package com.aariz.freshtrack;
public class User {

    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String dateOfBirth;
    private long createdAt;

    // No-argument constructor required for Firestore deserialization
    public User() {
        this.id = "";
        this.firstName = "";
        this.lastName = "";
        this.email = "";
        this.dateOfBirth = "";
        this.createdAt = 0;
    }

    // Constructor matching usage in AddItemActivity (id, firstName, email)
    public User(String id, String firstName, String email) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = "";
        this.email = email;
        this.dateOfBirth = "";
        this.createdAt = 0;
    }

    // Full constructor
    public User(String id, String firstName, String lastName, String email,
                String dateOfBirth, long createdAt) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.dateOfBirth = dateOfBirth;
        this.createdAt = createdAt;
    }

    /**
     * Returns a copy of this User with the given id.
     * Mirrors Kotlin data class .copy(id = ...) used in FirestoreRepository.
     */
    public User withId(String newId) {
        return new User(newId, this.firstName, this.lastName,
                this.email, this.dateOfBirth, this.createdAt);
    }

    // --- Getters ---

    public String getId() { return id != null ? id : ""; }
    public String getFirstName() { return firstName != null ? firstName : ""; }
    public String getLastName() { return lastName != null ? lastName : ""; }
    public String getEmail() { return email != null ? email : ""; }
    public String getDateOfBirth() { return dateOfBirth != null ? dateOfBirth : ""; }
    public long getCreatedAt() { return createdAt; }

    // --- Setters (required for Firestore deserialization) ---

    public void setId(String id) { this.id = id; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setEmail(String email) { this.email = email; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}