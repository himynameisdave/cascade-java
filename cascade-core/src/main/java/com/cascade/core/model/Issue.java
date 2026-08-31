package com.cascade.core.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Issue {
    private String id;
    private String key;
    private String projectId;
    private long seq;

    @NotBlank
    @Size(min = 3, max = 200)
    private String title;

    /** Markdown source. Rendered and sanitized by {@code MarkdownRenderer}. */
    @Size(max = 20000)
    private String description = "";

    private IssueStatus status = IssueStatus.BACKLOG;
    private IssuePriority priority = IssuePriority.MAJOR;
    private IssueType type = IssueType.TASK;

    private String reporterId;
    private String assigneeId;
    private List<String> labels = new ArrayList<>();

    @Min(0)
    @Max(100)
    private Integer storyPoints;

    private Instant dueDate;
    private String parentId;

    /**
     * Fractional rank within a board column, so a drag-and-drop reorder only
     * rewrites the row that moved rather than renumbering the whole column.
     */
    private double boardRank;

    private List<Attachment> attachments = new ArrayList<>();
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public IssueStatus getStatus() { return status; }
    public void setStatus(IssueStatus status) { this.status = status; }

    public IssuePriority getPriority() { return priority; }
    public void setPriority(IssuePriority priority) { this.priority = priority; }

    public IssueType getType() { return type; }
    public void setType(IssueType type) { this.type = type; }

    public String getReporterId() { return reporterId; }
    public void setReporterId(String reporterId) { this.reporterId = reporterId; }

    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }

    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }

    public Integer getStoryPoints() { return storyPoints; }
    public void setStoryPoints(Integer storyPoints) { this.storyPoints = storyPoints; }

    public Instant getDueDate() { return dueDate; }
    public void setDueDate(Instant dueDate) { this.dueDate = dueDate; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public double getBoardRank() { return boardRank; }
    public void setBoardRank(double boardRank) { this.boardRank = boardRank; }

    public List<Attachment> getAttachments() { return attachments; }
    public void setAttachments(List<Attachment> attachments) { this.attachments = attachments; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isOverdue() {
        return dueDate != null && status != IssueStatus.DONE && dueDate.isBefore(Instant.now());
    }
}
