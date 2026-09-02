package com.cascade.api.schedule;

import com.cascade.api.web.Services;
import com.cascade.core.model.Issue;
import com.cascade.core.model.IssueStatus;
import java.util.List;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Weekday digest of overdue and unassigned work.
 *
 * <p>Registered by {@link com.cascade.api.CascadeServer} when the
 * {@code digests} feature flag is on.
 */
public class DigestJob implements Job {

    public static final String SERVICES_KEY = "cascade.services";

    private static final Logger LOG = LoggerFactory.getLogger(DigestJob.class);

    @Override
    public void execute(JobExecutionContext context) {
        Object attached = context.getMergedJobDataMap().get(SERVICES_KEY);
        if (!(attached instanceof Services services)) {
            LOG.warn("digest job ran without services attached; skipping");
            return;
        }

        List<Issue> issues = services.issues().findAll();
        long overdue = issues.stream().filter(Issue::isOverdue).count();
        long unassigned = issues.stream()
                .filter(i -> i.getAssigneeId() == null && i.getStatus() != IssueStatus.DONE)
                .count();

        LOG.info("digest: {} overdue, {} unassigned, {} issues total",
                overdue, unassigned, issues.size());
    }
}
