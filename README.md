# Cascade (Java / Maven)

A lightweight issue tracker and project board — a small Jira replacement, on the
Jakarta EE 9 stack. Kanban board with WIP limits and fractional-rank reordering,
markdown issue bodies sanitized server-side, a JQL-style query language, sprint
reports, CSV and Jira-XML import, HMAC-signed outbound webhooks behind an SSRF
guard, scheduled digests, and a terminal client.

A sibling of the [TypeScript](https://github.com/JoeTot/cascade-issue-tracker)
and Rust ports, built to give software-composition-analysis tooling a third
ecosystem — and specifically to exercise **advisories whose only fix is a
pre-release version**.

A Maven reactor of three modules:

| Module | What it is |
| --- | --- |
| `cascade-core` | Domain model, JDBC persistence, CQL engine, markdown, import, reporting |
| `cascade-api` | Embedded-Tomcat servlet API, Shiro/JWT auth, webhooks, Quartz digests |
| `cascade-cli` | `cascade` terminal client built on picocli |

> **Note on dependencies.** This repository intentionally pins older releases of
> its direct dependencies so SCA tooling has realistic work to do. Every pin
> below has a published advisory and an available fix. **Do not deploy this.**

## Build

```bash
mvn -q clean package
```

```bash
java -jar cascade-api/target/cascade-api-0.9.0.jar
```

The API listens on <http://127.0.0.1:4000> and creates an H2 database under
`data/` on first start. Point the CLI at it:

```bash
export CASCADE_URL=http://127.0.0.1:4000
```

```bash
java -jar cascade-cli/target/cascade-cli-0.9.0.jar projects
```

The first account to register becomes the workspace admin.

## Features

**Board.** Columns and WIP limits come from `config/app.yml`. Reordering assigns
a fractional `board_rank` between the two neighbours, so a drag rewrites only
the row that moved.

**Workflow.** Transitions are validated server-side — forward, or one step back.
`backlog → done` is rejected with a 422.

**Markdown.** Bodies are rendered with commonmark (raw HTML escaped, not passed
through) and then sanitized with a jsoup allowlist. `PROJ-12` becomes a link and
`@handle` a mention chip, applied *after* sanitization so the linkifier can only
emit markup the server constructs.

**Cascade Query Language.** A small JQL-like grammar over issues:

```text
status = in_progress AND priority in (critical, blocker)
assignee = me AND updated > -7d
labels ~ payments ORDER BY priority DESC
```

Field aliases (`assignee` → `assigneeId`, `updated` → `updatedAt`), `me`
resolution, and relative dates (`-7d`, `+2w`, `now`). Unknown fields are ignored
rather than rejected, so a partly-typed query still returns something.

**Import.** Preview then commit a CSV export or a Jira XML issue export. Status
and priority aliases are normalized (`Highest` → `blocker`), several date
formats are accepted including day-first, and unusable rows are reported with
line numbers instead of failing the batch. The XML reader disables DTDs and
external entities, since import files are user-supplied.

**Webhooks.** Per-project subscriptions signed with HMAC-SHA256. Targets must be
on an allowlist *and* must not resolve to a loopback, RFC1918, link-local,
CGNAT or cloud-metadata address — the allowlist alone is not enough, since an
allowlisted name can still point at `169.254.169.254`. The signing secret is
returned exactly once, at creation.

## API

Authentication is a JWT sent as a bearer token or the `cascade_session` cookie.

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/api/auth/register`, `/api/auth/login`, `/api/auth/logout` | First account becomes admin |
| `GET` | `/api/auth/me`, `/api/auth/users` | |
| `GET` `POST` | `/api/projects` | |
| `GET` `DELETE` | `/api/projects/{idOrKey}` | Detail includes stats; delete is admin-only |
| `GET` `POST` | `/api/issues` | Filter by project, status, assignee, label |
| `GET` | `/api/issues/board/{projectId}` | Columns with WIP-limit annotations |
| `GET` `PATCH` `DELETE` | `/api/issues/{idOrKey}` | Accepts an id or a key like `PAY-3` |
| `POST` | `/api/issues/{idOrKey}/rank` | Drag-and-drop reordering |
| `GET` `POST` | `/api/comments/{issueIdOrKey}` | |
| `DELETE` | `/api/comments/{issueIdOrKey}/{commentId}` | Authors and admins only |
| `GET` | `/api/search` | Cascade Query Language |
| `GET` | `/api/search/suggest`, `/api/search/activity` | |
| `GET` | `/api/reports/summary`, `/api/reports/export.csv` | |
| `GET` `POST` `DELETE` | `/api/integrations/webhooks` | Admin-only |
| `POST` | `/api/integrations/import/preview`, `/api/integrations/import/commit` | |
| `GET` | `/api/health` | Version, uptime, record counts |

## Configuration

`config/app.yml` holds board columns, WIP limits, feature flags, the datasource,
the webhook allowlist and SMTP settings. Environment variables override it:
`PORT`, `JWT_SECRET`, `JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD`, `SMTP_HOST`,
`SMTP_PORT`, `WEBHOOK_ALLOWLIST`, `CASCADE_CONFIG`. Set `JWT_SECRET` before
running this anywhere real — the server warns when it falls back to the
development secret, and the JWT service refuses a key shorter than 32 bytes.

## Dependency upgrade backlog

Every version is pinned in the parent POM's `<dependencyManagement>`, so the
module POMs carry no inline versions and an upgrade is a one-line change.

Verified against [OSV](https://osv.dev) and Maven Central: **46 declared direct
dependencies, 36 of them carrying advisories, 119 distinct CVEs, and every
pinned coordinate confirmed to exist on Maven Central.**

### Advisories whose fix is a pre-release

These four are the reason this repository exists alongside the npm and Cargo
ports. The first fixed version is a milestone, alpha or release-candidate build,
so an automated upgrade has to be willing to move to a non-final release — or
to recognise that no stable fix exists on that branch and say so.

| Artifact | Pinned | First fixed in | Kind | Example advisory |
| --- | --- | --- | --- | --- |
| `org.apache.tomcat.embed:tomcat-embed-core` | 10.0.0-M1 | `10.0.0-M5` | milestone | CVE-2020-9484, CVE-2020-11996 |
| `org.apache.shiro:shiro-core` | 2.0.0-alpha-2 | `2.0.0-alpha4` | alpha | CVE-2023-46749 |
| `org.apache.shiro:shiro-web` | 2.0.0-alpha-2 | `2.0.0-alpha-3` | alpha | CVE-2023-34478, CVE-2023-46750 |
| `org.hibernate.validator:hibernate-validator` | 7.0.0.Alpha1 | `7.0.0.CR1` | release candidate | CVE-2025-35036 |

Note that `tomcat-embed-core 10.0.0-M1` also carries advisories fixed at
`10.0.0-M10`, so a single artifact needs more than one pre-release step to
become clean — useful for testing whether a bot picks the *lowest* sufficient
fix or jumps to latest.

### `cascade-core`

| Artifact | Pinned | Fixed in | CVEs | Pre-release fix | Used for |
| --- | --- | --- | --- | --- | --- |
| `commons-beanutils:commons-beanutils` | 1.9.3 | 1.11.0 | 3 | — | dynamic property access |
| `commons-collections:commons-collections` | 3.2.1 | 3.2.2 | 2 | — | legacy collection helpers |
| `org.apache.commons:commons-compress` | 1.21 | 1.26.0 | 2 | — | attachment archives |
| `org.apache.commons:commons-configuration2` | 2.7 | 2.15.0 | 4 | — | layered configuration |
| `commons-io:commons-io` | 2.6 | 2.14.0 | 2 | — | file and stream helpers |
| `org.apache.commons:commons-lang3` | 3.12.0 | 3.18.0 | 1 | — | string and object utilities |
| `org.apache.commons:commons-text` | 1.9 | 1.10.0 | 1 | — | text similarity and escaping |
| `org.dom4j:dom4j` | 2.1.1 | 2.1.3 | 1 | — | Jira XML import |
| `com.google.guava:guava` | 30.1.1-jre | 32.0.0-android | 2 | — | collections and caching |
| `com.h2database:h2` | 2.1.210 | 2.2.220 | 1 | — | embedded database |
| `org.hibernate.validator:hibernate-validator` | 7.0.0.Alpha1 | 7.0.0.CR1 | 1 | **yes** | bean validation |
| `com.fasterxml.jackson.core:jackson-databind` | 2.13.2 | 3.1.4 | 8 | — | JSON serialization |
| `org.jsoup:jsoup` | 1.15.2 | 1.23.1 | 2 | — | HTML sanitization |
| `org.yaml:snakeyaml` | 1.30 | 2.0 | 7 | — | YAML configuration |
| `xerces:xercesImpl` | 2.12.0 | 2.12.2 | 2 | — | XML parsing |
| `com.thoughtworks.xstream:xstream` | 1.4.19 | 1.4.21 | 3 | — | XML object mapping |

### `cascade-api`

| Artifact | Pinned | Fixed in | CVEs | Pre-release fix | Used for |
| --- | --- | --- | --- | --- | --- |
| `org.asynchttpclient:async-http-client` | 2.12.3 | 3.0.11 | 4 | — | webhook delivery |
| `org.bouncycastle:bcprov-jdk18on` | 1.72 | 1.84 | 7 | — | cryptography provider |
| `com.sun.mail:jakarta.mail` | 1.6.7 | 2.0.2 | 1 | — | notification email |
| `org.json:json` | 20220924 | 20231013 | 2 | — | webhook payload building |
| `net.minidev:json-smart` | 2.4.7 | 2.4.9 | 1 | — | JOSE JSON parsing |
| `org.apache.logging.log4j:log4j-core` | 2.14.1 | 2.25.4 | 7 | — | server logging |
| `io.netty:netty-codec-http` | 4.1.68.Final | 4.2.17.Final | 21 | — | webhook HTTP codec |
| `io.netty:netty-handler` | 4.1.68.Final | 4.2.15.Final | 4 | — | webhook transport |
| `com.nimbusds:nimbus-jose-jwt` | 9.35 | 10.0.2 | 2 | — | JWT session tokens |
| `org.apache.pdfbox:pdfbox` | 2.0.15 | 2.0.24 | 4 | — | PDF report export |
| `org.apache.poi:poi-ooxml` | 5.2.2 | 5.4.0 | 1 | — | XLSX export |
| `org.postgresql:postgresql` | 42.3.2 | 42.7.11 | 5 | — | production JDBC driver |
| `org.quartz-scheduler:quartz` | 2.3.0 | 2.3.2 | 1 | — | scheduled digests |
| `org.apache.shiro:shiro-core` | 2.0.0-alpha-2 | 3.0.0-alpha-2 | 4 | **yes** | password hashing |
| `org.apache.shiro:shiro-web` | 2.0.0-alpha-2 | 3.0.0-alpha-2 | 3 | **yes** | servlet security filters |
| `org.apache.tika:tika-core` | 2.4.0 | 3.2.2 | 1 | — | attachment type detection |
| `org.apache.tomcat.embed:tomcat-embed-core` | 10.0.0-M1 | 10.0.0-M10 | 6 | **yes** | embedded servlet container |

### `cascade-cli`

| Artifact | Pinned | Fixed in | CVEs | Pre-release fix | Used for |
| --- | --- | --- | --- | --- | --- |
| `com.google.code.gson:gson` | 2.8.6 | 2.8.9 | 1 | — | CLI JSON parsing |
| `org.apache.httpcomponents.client5:httpclient5` | 5.1.3 | 5.6.3 | 1 | — | CLI HTTP client |
| `ch.qos.logback:logback-classic` | 1.2.11 | 1.4.12 | 1 | — | CLI logging |
### Clean by design

`commonmark`, `opencsv`, `HikariCP`, `picocli`, `slf4j-api`, `jakarta.servlet-api`,
`jakarta.el`, `tomcat-embed-el`, `log4j-slf4j-impl` and `jackson-datatype-jsr310`
carry no advisories at their pinned versions. They are here because the code
uses them, and they give the scanner some negatives to get right too.

### Upgrades that are not just a version bump

- `org.apache.shiro` 2.0.0-alpha-2 → 2.0.0 reorganised the `HashService` and
  `ByteSource` APIs used by `PasswordService`.
- `org.hibernate.validator` 7.0 → 8.0 moves to Jakarta Bean Validation 3.1.
- `org.apache.tomcat.embed` 10.0 → 10.1 raises the servlet baseline to 6.0, and
  → 11.0 to 6.1; both change the `jakarta.servlet` API surface.
- `commons-beanutils` 1.9.x → 1.11 changed the artifact coordinates upstream.

## Project layout

```
pom.xml                     parent: modules, dependencyManagement, all version pins
config/app.yml              board columns, WIP limits, datasource, SMTP, allowlist
cascade-core/               model, store, query, markdown, importer, reports
cascade-api/                servlets, security, webhook, notify, schedule
cascade-cli/                picocli client
```

## License

MIT
