package com.cascade.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Webhook {
    private String id;
    private String projectId;
    private String url;
    private List<String> events = new ArrayList<>();

    /** Returned once at creation; redacted by the view layer thereafter. */
    @JsonIgnore
    private String secret;

    private boolean active = true;
    private Instant createdAt = Instant.now();
    private Integer lastStatus;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public List<String> getEvents() { return events; }
    public void setEvents(List<String> events) { this.events = events; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Integer getLastStatus() { return lastStatus; }
    public void setLastStatus(Integer lastStatus) { this.lastStatus = lastStatus; }
}
