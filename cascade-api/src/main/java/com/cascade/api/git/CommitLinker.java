package com.cascade.api.git;

import com.cascade.core.markdown.MarkdownRenderer;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Links commits to issues by scanning commit messages for issue keys, so an
 * issue page can show the work that references it.
 *
 * <p>Read-only: this never writes to the repository or runs network fetches.
 */
public class CommitLinker {

    private static final Logger LOG = LoggerFactory.getLogger(CommitLinker.class);

    /** One commit, reduced to what the issue page displays. */
    public record LinkedCommit(String sha, String shortSha, String author, String summary,
                               long timestamp) { }

    private final File gitDir;

    public CommitLinker(String repositoryPath) {
        this.gitDir = repositoryPath == null ? null : new File(repositoryPath);
    }

    public boolean isConfigured() {
        return gitDir != null && new File(gitDir, ".git").isDirectory();
    }

    /**
     * Walks up to {@code maxCommits} of history and groups commits by the issue
     * keys their messages mention.
     */
    public Map<String, List<LinkedCommit>> scan(int maxCommits) {
        Map<String, List<LinkedCommit>> byIssue = new LinkedHashMap<>();
        if (!isConfigured()) {
            return byIssue;
        }

        try (Repository repository = new FileRepositoryBuilder()
                     .setGitDir(new File(gitDir, ".git"))
                     .readEnvironment()
                     .findGitDir()
                     .build();
             Git git = new Git(repository)) {

            int seen = 0;
            for (RevCommit commit : git.log().setMaxCount(maxCommits).call()) {
                seen++;
                String message = commit.getFullMessage();
                for (String key : MarkdownRenderer.extractIssueRefs(message)) {
                    byIssue.computeIfAbsent(key, k -> new ArrayList<>())
                           .add(new LinkedCommit(
                                   commit.getName(),
                                   commit.getName().substring(0, 8),
                                   commit.getAuthorIdent().getName(),
                                   commit.getShortMessage(),
                                   commit.getCommitTime() * 1000L));
                }
            }
            LOG.info("scanned {} commits, matched {} issue keys", seen, byIssue.size());
        } catch (IOException | GitAPIException e) {
            LOG.warn("could not scan the repository: {}", e.getMessage());
        }
        return byIssue;
    }
}
