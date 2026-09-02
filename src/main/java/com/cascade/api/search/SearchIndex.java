package com.cascade.api.search;

import com.cascade.core.model.Issue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional full-text index.
 *
 * <p>CQL handles structured filters directly against the database; this covers
 * free-text across titles, descriptions, comments and extracted attachment
 * text, which SQL {@code LIKE} does badly at workspace scale. Every method
 * degrades to a no-op when Solr is unreachable — search is an enhancement, not
 * a dependency of the tracker working.
 */
public class SearchIndex implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SearchIndex.class);

    private final HttpSolrClient client;
    private final boolean enabled;

    public SearchIndex(String baseUrl) {
        this.enabled = baseUrl != null && !baseUrl.isBlank();
        this.client = enabled
                ? new HttpSolrClient.Builder(baseUrl)
                        .withConnectionTimeout(3_000)
                        .withSocketTimeout(5_000)
                        .build()
                : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void index(Issue issue, String attachmentText) {
        if (!enabled) {
            return;
        }
        SolrInputDocument document = new SolrInputDocument();
        document.addField("id", issue.getId());
        document.addField("key_s", issue.getKey());
        document.addField("project_s", issue.getProjectId());
        document.addField("title_t", issue.getTitle());
        document.addField("description_t", issue.getDescription());
        document.addField("attachments_t", attachmentText);
        document.addField("status_s", issue.getStatus().wire());
        document.addField("priority_s", issue.getPriority().wire());
        document.addField("labels_ss", issue.getLabels());

        try {
            client.add(document);
            client.commit();
        } catch (SolrServerException | IOException e) {
            LOG.warn("could not index {}: {}", issue.getKey(), e.getMessage());
        }
    }

    public void remove(String issueId) {
        if (!enabled) {
            return;
        }
        try {
            client.deleteById(issueId);
            client.commit();
        } catch (SolrServerException | IOException e) {
            LOG.warn("could not remove {} from the index: {}", issueId, e.getMessage());
        }
    }

    /** Returns matching issue ids, most relevant first. */
    public List<String> search(String text, int limit) {
        if (!enabled || text == null || text.isBlank()) {
            return List.of();
        }
        SolrQuery query = new SolrQuery(text);
        query.setRows(Math.max(1, Math.min(limit, 200)));
        query.setFields("id");

        try {
            QueryResponse response = client.query(query);
            List<String> ids = new ArrayList<>();
            for (SolrDocument document : response.getResults()) {
                ids.add(String.valueOf(document.getFieldValue("id")));
            }
            return ids;
        } catch (SolrServerException | IOException e) {
            LOG.warn("full-text search unavailable: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (IOException ignored) {
                // nothing useful to do while shutting down
            }
        }
    }
}
