package com.cascade.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public class User {
    private String id;

    @NotBlank
    @Email(message = "must be a valid email address")
    private String email;

    @NotBlank
    @Size(min = 2, max = 80)
    private String name;

    private Role role = Role.MEMBER;

    /** Never serialized: responses are built by the view layer, not from this. */
    @JsonIgnore
    private String passwordHash;

    private String avatarColor;
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getAvatarColor() { return avatarColor; }
    public void setAvatarColor(String avatarColor) { this.avatarColor = avatarColor; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /** The {@code @handle} used when mentioning this user in markdown. */
    public String handle() {
        int at = email == null ? -1 : email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
