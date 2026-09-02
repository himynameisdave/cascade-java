package com.cascade.core.importer;

import com.cascade.core.model.IssuePriority;
import com.cascade.core.model.IssueStatus;
import com.cascade.core.model.IssueType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One prospective issue produced by an import, before it is persisted. */
public class ImportRow {
    private String title = "";
    private String description = "";
    private IssueStatus status = IssueStatus.BACKLOG;
    private IssuePriority priority = IssuePriority.MAJOR;
    private IssueType type = IssueType.TASK;
    private List<String> labels = new ArrayList<>();
    private String assigneeEmail;
    private Integer storyPoints;
    private Instant dueDate;

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

    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }

    public String getAssigneeEmail() { return assigneeEmail; }
    public void setAssigneeEmail(String assigneeEmail) { this.assigneeEmail = assigneeEmail; }

    public Integer getStoryPoints() { return storyPoints; }
    public void setStoryPoints(Integer storyPoints) { this.storyPoints = storyPoints; }

    public Instant getDueDate() { return dueDate; }
    public void setDueDate(Instant dueDate) { this.dueDate = dueDate; }
}
