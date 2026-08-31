package com.cascade.cli;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** {@code cascade} — a terminal client for the Cascade issue tracker. */
@Command(
    name = "cascade",
    version = "cascade 0.9.0",
    mixinStandardHelpOptions = true,
    description = "Query and update a Cascade issue tracker from the terminal.",
    subcommands = {
        CascadeCli.Login.class,
        CascadeCli.Projects.class,
        CascadeCli.Board.class,
        CascadeCli.Search.class,
        CascadeCli.Show.class,
        CascadeCli.Move.class,
        CascadeCli.CommentCmd.class,
        CascadeCli.Report.class
    })
public class CascadeCli implements Callable<Integer> {

    /** Options every subcommand inherits. */
    public static class Shared {
        @Option(names = "--url", defaultValue = "${env:CASCADE_URL:-http://127.0.0.1:4000}",
                description = "Base URL of the Cascade API.")
        String url;

        @Option(names = "--token", defaultValue = "${env:CASCADE_TOKEN}",
                description = "Session token from `cascade login`.")
        String token;

        @Option(names = "--json", description = "Print the raw API response.")
        boolean json;

        ApiClient client() {
            return new ApiClient(url, token);
        }
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    @Command(name = "login", description = "Exchange an email and password for a session token.")
    static class Login implements Callable<Integer> {
        @CommandLine.Mixin Shared shared;

        @Option(names = "--email", required = true) String email;
        @Option(names = "--password", required = true, defaultValue = "${env:CASCADE_PASSWORD}")
        String password;

        @Override
        public Integer call() {
            try (ApiClient api = shared.client()) {
                String token = api.login(email, password);
                if (shared.json) {
                    System.out.println("{\"token\":\"" + token + "\"}");
                } else {
                    System.out.println("Signed in. Use this token for later commands:\n");
                    System.out.println("  export CASCADE_TOKEN=" + token + "\n");
                }
                return 0;
            }
        }
    }

    @Command(name = "projects", description = "List projects in the workspace.")
    static class Projects implements Callable<Integer> {
        @CommandLine.Mixin Shared shared;

        @Override
        public Integer call() {
            try (ApiClient api = shared.client()) {
                JsonObject response = api.get("/projects", Map.of());
                if (shared.json) {
                    System.out.println(ApiClient.GSON.toJson(response));
                    return 0;
                }
                List<List<String>> rows = new ArrayList<>();
                for (JsonObject project : Tables.objects(response.getAsJsonArray("projects"))) {
                    rows.add(List.of(
                            Tables.text(project, "key"),
                            Tables.truncate(Tables.text(project, "name"), 34),
                            Tables.text(project, "openCount"),
                            Tables.text(project, "issueCount")));
                }
                System.out.print(Tables.render(List.of("key", "name", "open", "total"), rows));
                return 0;
            }
        }
    }

    @Command(name = "board", description = "Show a project's board, grouped by column.")
    static class Board implements Callable<Integer> {
        @CommandLine.Mixin Shared shared;

        @Parameters(index = "0", description = "Project id or key, e.g. PAY.")
        String project;

        @Override
        public Integer call() {
            try (ApiClient api = shared.client()) {
                String projectId = resolveProject(api, project);
                JsonObject response = api.get("/issues/board/" + projectId, Map.of());
                if (shared.json) {
                    System.out.println(ApiClient.GSON.toJson(response));
                    return 0;
                }
                for (JsonObject column : Tables.objects(response.getAsJsonArray("columns"))) {
                    List<JsonObject> issues = Tables.objects(column.getAsJsonArray("issues"));
                    String limit = Tables.text(column, "wipLimit");
                    String header = Tables.text(column, "status").replace('_', ' ').toUpperCase()
                            + "  (" + issues.size() + ("—".equals(limit) ? "" : "/" + limit) + ")";
                    System.out.println(header);
                    System.out.println("=".repeat(header.length()));
                    if (column.get("overLimit").getAsBoolean()) {
                        System.out.println("  ** over the WIP limit **");
                    }
                    if (issues.isEmpty()) {
                        System.out.println("  (empty)");
                    }
                    for (JsonObject issue : issues) {
                        System.out.printf("  %-10s %-52s %-9s %s%n",
                                Tables.text(issue, "key"),
                                Tables.truncate(Tables.text(issue, "title"), 52),
                                Tables.text(issue, "priority"),
                                Tables.nested(issue, "assignee", "name"));
                    }
                    System.out.println();
                }
                return 0;
            }
        }
    }

    @Command(name = "search", description = "Run a Cascade Query Language search.")
    static class Search implements Callable<Integer> {
        @CommandLine.Mixin Shared shared;

        @Parameters(index = "0", arity = "1",
                description = "The query, quoted. Example: \"priority in (critical, blocker)\".")
        String query;

        @Option(names = "--limit", defaultValue = "25") int limit;

        @Override
        public Integer call() {
            try (ApiClient api = shared.client()) {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("q", query);
                params.put("limit", String.valueOf(limit));
                JsonObject response = api.get("/search", params);

                if (shared.json) {
                    System.out.println(ApiClient.GSON.toJson(response));
                    return 0;
                }
                System.out.println(Tables.text(response, "total") + " matching issues for: "
                        + query + "\n");
                List<List<String>> rows = new ArrayList<>();
                for (JsonObject issue : Tables.objects(response.getAsJsonArray("results"))) {
                    rows.add(List.of(
                            Tables.text(issue, "key"),
                            Tables.truncate(Tables.text(issue, "title"), 46),
                            Tables.text(issue, "status"),
                            Tables.text(issue, "priority"),
                            Tables.nested(issue, "assignee", "name")));
                }
                System.out.print(Tables.render(
                        List.of("key", "title", "status", "priority", "assignee"), rows));
                return 0;
            }
        }
    }

    @Command(name = "show", description = "Show a single issue with its comments.")
    static class Show implements Callable<Integer> {
        @CommandLine.Mixin Shared shared;

        @Parameters(index = "0", description = "Issue key, e.g. PAY-3.") String key;

        @Override
        public Integer call() {
            try (ApiClient api = shared.client()) {
                JsonObject response = api.get("/issues/" + key, Map.of());
                if (shared.json) {
                    System.out.println(ApiClient.GSON.toJson(response));
                    return 0;
                }
                JsonObject issue = response.getAsJsonObject("issue");
                System.out.println(Tables.text(issue, "key") + "  " + Tables.text(issue, "title"));
                System.out.println(Tables.text(issue, "status") + " · "
                        + Tables.text(issue, "priority") + " · "
                        + Tables.text(issue, "type") + " · assignee "
                        + Tables.nested(issue, "assignee", "name"));
                System.out.println("transitions: " + response.get("allowedTransitions"));

                String description = Tables.text(issue, "description");
                if (!"—".equals(description) && !description.isBlank()) {
                    System.out.println("\n" + description);
                }

                JsonObject comments = api.get("/comments/" + key, Map.of());
                List<JsonObject> list = Tables.objects(comments.getAsJsonArray("comments"));
                System.out.println("\n--- " + list.size() + " comment(s) ---");
                for (JsonObject comment : list) {
                    System.out.println("\n" + Tables.nested(comment, "author", "name") + ":");
                    System.out.println(Tables.text(comment, "body"));
                }
                return 0;
            }
        }
    }

    @Command(name = "move", description = "Move an issue to a different status.")
    static class Move implements Callable<Integer> {
        @CommandLine.Mixin Shared shared;

        @Parameters(index = "0") String key;
        @Parameters(index = "1", description = "backlog, todo, in_progress, in_review or done.")
        String status;

        @Override
        public Integer call() {
            try (ApiClient api = shared.client()) {
                JsonObject response = api.patch("/issues/" + key, Map.of("status", status));
                JsonObject issue = response.getAsJsonObject("issue");
                System.out.println(Tables.text(issue, "key") + " is now "
                        + Tables.text(issue, "status"));
                return 0;
            }
        }
    }

    @Command(name = "comment", description = "Add a comment to an issue.")
    static class CommentCmd implements Callable<Integer> {
        @CommandLine.Mixin Shared shared;

        @Parameters(index = "0") String key;
        @Parameters(index = "1", description = "Comment body; quote it if it contains spaces.")
        String body;

        @Override
        public Integer call() {
            try (ApiClient api = shared.client()) {
                api.post("/comments/" + key, Map.of("body", body));
                System.out.println("Comment added to " + key + ".");
                return 0;
            }
        }
    }

    @Command(name = "report", description = "Print the report summary for a project.")
    static class Report implements Callable<Integer> {
        @CommandLine.Mixin Shared shared;

        @Option(names = "--project", description = "Project id or key.") String project;

        @Override
        public Integer call() {
            try (ApiClient api = shared.client()) {
                Map<String, String> params = new LinkedHashMap<>();
                if (project != null) {
                    params.put("projectId", resolveProject(api, project));
                }
                JsonObject response = api.get("/reports/summary", params);
                if (shared.json) {
                    System.out.println(ApiClient.GSON.toJson(response));
                    return 0;
                }
                JsonObject counts = response.getAsJsonObject("counts");
                System.out.println("Issues:      " + Tables.text(counts, "total"));
                System.out.println("Unassigned:  " + Tables.text(counts, "unassigned"));
                System.out.println("Overdue:     " + Tables.text(counts, "overdue"));
                System.out.println("Points:      " + Tables.text(counts, "pointsCompleted")
                        + " of " + Tables.text(counts, "pointsCommitted") + " done");

                JsonObject cycle = response.getAsJsonObject("cycleTime");
                System.out.println("Cycle time:  " + Tables.text(cycle, "medianDays")
                        + "d median, " + Tables.text(cycle, "p90Days") + "d p90");
                return 0;
            }
        }
    }

    /** Accepts either a project id or a key such as {@code PAY}. */
    static String resolveProject(ApiClient api, String needle) {
        JsonObject response = api.get("/projects", Map.of());
        for (JsonObject project : Tables.objects(response.getAsJsonArray("projects"))) {
            if (needle.equals(Tables.text(project, "id"))
                    || needle.equalsIgnoreCase(Tables.text(project, "key"))) {
                return Tables.text(project, "id");
            }
        }
        throw new ApiClient.ApiClientException("no project matching '" + needle + "'");
    }

    public static void main(String[] args) {
        int exit = new CommandLine(new CascadeCli()).execute(args);
        System.exit(exit);
    }
}
