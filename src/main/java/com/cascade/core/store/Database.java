package com.cascade.core.store;

import com.cascade.core.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pooled datasource plus schema bootstrap.
 *
 * <p>The schema is created on first use rather than through a migration tool;
 * every statement is {@code IF NOT EXISTS} so start-up is idempotent.
 */
public final class Database implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Database.class);

    private static final String[] SCHEMA = {
        """
        CREATE TABLE IF NOT EXISTS users (
          id             VARCHAR(64) PRIMARY KEY,
          email          VARCHAR(320) NOT NULL UNIQUE,
          name           VARCHAR(120) NOT NULL,
          role           VARCHAR(16)  NOT NULL,
          password_hash  VARCHAR(256) NOT NULL,
          avatar_color   VARCHAR(16),
          created_at     TIMESTAMP    NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS projects (
          id           VARCHAR(64) PRIMARY KEY,
          project_key  VARCHAR(16) NOT NULL UNIQUE,
          name         VARCHAR(120) NOT NULL,
          description  CLOB,
          lead_id      VARCHAR(64),
          archived     BOOLEAN NOT NULL DEFAULT FALSE,
          created_at   TIMESTAMP NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS project_members (
          project_id VARCHAR(64) NOT NULL,
          user_id    VARCHAR(64) NOT NULL,
          PRIMARY KEY (project_id, user_id)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS issues (
          id           VARCHAR(64) PRIMARY KEY,
          issue_key    VARCHAR(32) NOT NULL UNIQUE,
          project_id   VARCHAR(64) NOT NULL,
          seq          BIGINT NOT NULL,
          title        VARCHAR(400) NOT NULL,
          description  CLOB,
          status       VARCHAR(24) NOT NULL,
          priority     VARCHAR(16) NOT NULL,
          type         VARCHAR(16) NOT NULL,
          reporter_id  VARCHAR(64),
          assignee_id  VARCHAR(64),
          labels       VARCHAR(512),
          story_points INT,
          due_date     TIMESTAMP,
          parent_id    VARCHAR(64),
          board_rank   DOUBLE NOT NULL,
          created_at   TIMESTAMP NOT NULL,
          updated_at   TIMESTAMP NOT NULL
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_issues_project ON issues (project_id, status)",
        "CREATE INDEX IF NOT EXISTS idx_issues_assignee ON issues (assignee_id)",
        """
        CREATE TABLE IF NOT EXISTS comments (
          id         VARCHAR(64) PRIMARY KEY,
          issue_id   VARCHAR(64) NOT NULL,
          author_id  VARCHAR(64) NOT NULL,
          body       CLOB NOT NULL,
          created_at TIMESTAMP NOT NULL,
          edited_at  TIMESTAMP
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_comments_issue ON comments (issue_id)",
        """
        CREATE TABLE IF NOT EXISTS activity (
          id           VARCHAR(64) PRIMARY KEY,
          actor_id     VARCHAR(64) NOT NULL,
          verb         VARCHAR(24) NOT NULL,
          subject_type VARCHAR(24) NOT NULL,
          subject_id   VARCHAR(64) NOT NULL,
          project_id   VARCHAR(64),
          meta         CLOB,
          created_at   TIMESTAMP NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS webhooks (
          id          VARCHAR(64) PRIMARY KEY,
          project_id  VARCHAR(64) NOT NULL,
          url         VARCHAR(1024) NOT NULL,
          events      VARCHAR(512) NOT NULL,
          secret      VARCHAR(128) NOT NULL,
          active      BOOLEAN NOT NULL DEFAULT TRUE,
          created_at  TIMESTAMP NOT NULL,
          last_status INT
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS counters (
          name  VARCHAR(64) PRIMARY KEY,
          value BIGINT NOT NULL
        )
        """
    };

    private final HikariDataSource dataSource;

    public Database(AppConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.getJdbcUrl());
        hikari.setUsername(config.getJdbcUser());
        hikari.setPassword(config.getJdbcPassword());
        hikari.setMaximumPoolSize(10);
        hikari.setPoolName("cascade");
        this.dataSource = new HikariDataSource(hikari);
        migrate();
    }

    private void migrate() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String ddl : SCHEMA) {
                statement.execute(ddl);
            }
            LOG.info("schema ready ({} statements applied)", SCHEMA.length);
        } catch (SQLException e) {
            throw new StoreException("could not initialise the schema", e);
        }
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
