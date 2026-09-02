package com.cascade.core.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Project {
    private String id;

    @NotBlank
    @Size(min = 2, max = 10)
    @Pattern(regexp = "[A-Z][A-Z0-9]*", message = "must be uppercase letters and digits")
    private String key;

    @NotBlank
    @Size(min = 2, max = 80)
    private String name;

    @Size(max = 2000)
    private String description = "";

    private String leadId;
    private List<String> memberIds = new ArrayList<>();
    private boolean archived;
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLeadId() { return leadId; }
    public void setLeadId(String leadId) { this.leadId = leadId; }

    public List<String> getMemberIds() { return memberIds; }
    public void setMemberIds(List<String> memberIds) { this.memberIds = memberIds; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
