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

A **single** Maven artifact, `com.cascade:cascade`, organised by package:

| Package | What it is |
| --- | --- |
| `com.cascade.core` | Domain model, JDBC persistence, CQL engine, markdown, import, reporting |
| `com.cascade.api` | Embedded-Tomcat servlet API, Shiro/JWT auth, webhooks, Quartz digests |
| `com.cascade.cli` | `cascade` terminal client built on picocli |

It is deliberately *not* a multi-module reactor — see
[Why one module](#why-one-module).

> **Note on dependencies.** This repository intentionally pins older releases of
> its direct dependencies so SCA tooling has realistic work to do. Every pin
> below has a published advisory and an available fix. **Do not deploy this.**

## Build

```bash
mvn -q clean package
```

```bash
java -jar target/cascade.jar
```

The API listens on <http://127.0.0.1:4000> and creates an H2 database under
`data/` on first start. Point the CLI at it:

```bash
export CASCADE_URL=http://127.0.0.1:4000
```

```bash
java -cp target/cascade.jar com.cascade.cli.CascadeCli projects
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

Every version is declared **inline, as a literal, on the one and only POM** —
no modules, no `<dependencyManagement>`, no `${property}` indirection. Each
version string appears exactly once in the repository.

Verified against [OSV](https://osv.dev) and Maven Central: **65 direct
dependencies, 55 of them carrying advisories, 159 distinct CVEs, and every
pinned coordinate confirmed to exist on Maven Central.**

### Why one module

This started as a three-module reactor (`cascade-core`, `cascade-api`,
`cascade-cli`) and that structure defeated the tooling it was built to exercise.

An analyzer treats the aggregator POM as the project root. The modules become
*its* direct dependencies, and everything the modules declare is reported one
level further down — as **transitive**. Since tools that raise upgrade pull
requests generally only act on direct dependencies, every third-party pin in
this repository was invisible to them, even though all 65 were declared by hand.

Collapsing to a single artifact makes all 65 direct dependencies of the project
itself. The code is still separated by package, and the only thing lost is the
module boundary, which was never load-bearing here.

Two related traps, both also fixed:

- **Versions behind indirection.** With versions in the parent's
  `dependencyManagement` behind `${property}` references, a static POM read
  resolved none of them. Dependencies were recorded *without a version*, and an
  unversioned dependency matches no advisory — a scan found all 51 dependencies
  and raised zero issues, with `log4j-core 2.14.1` sitting right there.
- **Two SLF4J bindings.** `log4j-slf4j-impl` and `logback-classic` were fine in
  separate modules but conflict in one artifact, so logback is now test-scoped.

### What a static scan will and will not show

- **Direct dependencies only.** Maven has no lockfile, so a static POM read
  cannot compute the transitive closure; that needs a real `mvn dependency:tree`
  via the FOSSA CLI in CI. That is fine here — every dependency is direct by
  design, because an automated fix can only raise a version declared in a POM.
- **No more `com.cascade:*` entries.** The old modules used to appear as
  unanalyzable dependencies because they were unpublished first-party
  artifacts. With one module there is nothing first-party left to resolve.

### Advisories whose fix is a pre-release

These five artifacts are the reason this repository exists alongside the npm and
Cargo ports: the version OSV names as the fix is a milestone, alpha or
release-candidate build rather than a final release.

Only one of them is *strictly* pre-release-only. For the other three, OSV also
lists stable fixed versions — but on **older** branches, which is a downgrade
from the pin, not an upgrade. So on the branch the project is actually on, the
advisory's named fix is a pre-release in every case.

| Artifact | Pinned | Advisory-named fix | Stable fixes OSV lists | Lowest stable that also has the fix |
| --- | --- | --- | --- | --- |
| `org.hibernate.validator:hibernate-validator` | 7.0.0.Alpha1 | `7.0.0.CR1`, `6.2.0.CR1` | **none — pre-release only** | `7.0.0.Final` |
| `org.apache.tomcat.embed:tomcat-embed-core` | 10.0.0-M1 | `10.0.0-M5`, `10.0.0-M10` | 9.0.x / 8.5.x / 7.0.x (older branches) | `10.0.11` |
| `org.apache.shiro:shiro-core` | 2.0.0-alpha-2 | `2.0.0-alpha4` | 1.13.0 (older branch) | `2.0.0` |
| `org.apache.shiro:shiro-web` | 2.0.0-alpha-2 | `2.0.0-alpha-3`, `2.0.0-alpha-4` | 1.12.0, 1.13.0 (older branches) | `2.0.0` |
| `org.apache.tika:tika-parsers` | 1.27 | `2.0.0-BETA` | 1.28.4 | `1.28.4` |

The last column is the interesting part. In every case a later *stable* release
on the same branch also contains the fix — it simply sits above the boundary the
advisory records. So the behaviour worth testing is whether an automated upgrade

- proposes the literal advisory-named version (a pre-release), or
- computes the lowest **stable** release at or above that boundary, or
- refuses because the named fix is not a final release.

Two further wrinkles:

- `tomcat-embed-core` at 10.0.0-M1 carries six advisories with boundaries at
  `10.0.0-M5`, `10.0.0-M10`, `10.0.2` and `10.0.27`. No single pre-release
  clears them all; the lowest version that does is `10.0.27`. Useful for
  checking whether a bot picks the lowest sufficient fix or jumps to latest.
- `shiro-core` needs `2.2.1` to clear all four of its advisories, and
  `shiro-web` needs `2.2.0` — so "upgrade to the first stable" is not enough
  for either.

### Every direct dependency carrying an advisory

| Artifact | Pinned | Fixed in | CVEs | Pre-release fix |
| --- | --- | --- | --- | --- |
| `io.netty:netty-codec-http` | 4.1.68.Final | 4.2.17.Final | 21 | — |
| `com.fasterxml.jackson.core:jackson-databind` | 2.13.2 | 3.1.4 | 8 | — |
| `org.keycloak:keycloak-core` | 21.1.1 | 26.0.6 | 8 | — |
| `org.bouncycastle:bcprov-jdk18on` | 1.72 | 1.84 | 7 | — |
| `org.apache.logging.log4j:log4j-core` | 2.14.1 | 2.25.4 | 7 | — |
| `org.yaml:snakeyaml` | 1.30 | 2.0 | 7 | — |
| `org.apache.tomcat.embed:tomcat-embed-core` | 10.0.0-M1 | 10.0.0-M10 | 6 | **yes** |
| `com.hazelcast:hazelcast` | 5.1 | 5.3.5 | 5 | — |
| `org.postgresql:postgresql` | 42.3.2 | 42.7.11 | 5 | — |
| `org.apache.activemq:activemq-client` | 5.16.3 | 6.2.4 | 4 | — |
| `org.asynchttpclient:async-http-client` | 2.12.3 | 3.0.11 | 4 | — |
| `org.apache.commons:commons-configuration2` | 2.7 | 2.15.0 | 4 | — |
| `io.netty:netty-handler` | 4.1.68.Final | 4.2.15.Final | 4 | — |
| `org.apache.pdfbox:pdfbox` | 2.0.15 | 2.0.24 | 4 | — |
| `org.apache.shiro:shiro-core` | 2.0.0-alpha-2 | 3.0.0-alpha-2 | 4 | **yes** |
| `commons-beanutils:commons-beanutils` | 1.9.3 | 1.11.0 | 3 | — |
| `org.apache.kafka:kafka-clients` | 2.8.1 | 4.1.2 | 3 | — |
| `org.apache.shiro:shiro-web` | 2.0.0-alpha-2 | 3.0.0-alpha-2 | 3 | **yes** |
| `com.thoughtworks.xstream:xstream` | 1.4.19 | 1.4.21 | 3 | — |
| `commons-collections:commons-collections` | 3.2.1 | 3.2.2 | 2 | — |
| `org.apache.commons:commons-compress` | 1.21 | 1.26.0 | 2 | — |
| `commons-io:commons-io` | 2.6 | 2.14.0 | 2 | — |
| `org.owasp.esapi:esapi` | 2.2.0.0 | 2.6.0.0 | 2 | — |
| `com.google.guava:guava` | 30.1.1-jre | 32.0.0-android | 2 | — |
| `org.codehaus.jackson:jackson-mapper-asl` | 1.9.13 | — | 2 | — |
| `org.json:json` | 20220924 | 20231013 | 2 | — |
| `org.jsoup:jsoup` | 1.15.2 | 1.23.1 | 2 | — |
| `mysql:mysql-connector-java` | 8.0.27 | 8.0.28 | 2 | — |
| `com.nimbusds:nimbus-jose-jwt` | 9.35 | 10.0.2 | 2 | — |
| `org.eclipse.jgit:org.eclipse.jgit` | 5.13.0.202109080827-r | 7.2.1.202505142326-r | 2 | — |
| `org.apache.tika:tika-parsers` | 1.27 | 2.0.0-ALPHA | 2 | **yes** |
| `xerces:xercesImpl` | 2.12.0 | 2.12.2 | 2 | — |
| `net.lingala.zip4j:zip4j` | 2.9.0 | 2.11.3 | 2 | — |
| `org.apache.xmlgraphics:batik-transcoder` | 1.14 | 1.17 | 1 | — |
| `commons-httpclient:commons-httpclient` | 3.1 | — | 1 | — |
| `org.apache.commons:commons-lang3` | 3.12.0 | 3.18.0 | 1 | — |
| `org.apache.commons:commons-text` | 1.9 | 1.10.0 | 1 | — |
| `org.dom4j:dom4j` | 2.1.1 | 2.1.3 | 1 | — |
| `com.google.code.gson:gson` | 2.8.6 | 2.8.9 | 1 | — |
| `com.h2database:h2` | 2.1.210 | 2.2.220 | 1 | — |
| `org.hibernate.validator:hibernate-validator` | 7.0.0.Alpha1 | 7.0.0.CR1 | 1 | **yes** |
| `org.hsqldb:hsqldb` | 2.5.0 | 2.7.1 | 1 | — |
| `org.apache.httpcomponents.client5:httpclient5` | 5.1.3 | 5.6.3 | 1 | — |
| `com.sun.mail:jakarta.mail` | 1.6.7 | 2.0.2 | 1 | — |
| `org.jdom:jdom2` | 2.0.6 | 2.0.6.1 | 1 | — |
| `com.jayway.jsonpath:json-path` | 2.7.0 | 2.9.0 | 1 | — |
| `net.minidev:json-smart` | 2.4.7 | 2.4.9 | 1 | — |
| `ch.qos.logback:logback-classic` | 1.2.11 | 1.4.12 | 1 | — |
| `org.pac4j:pac4j-core` | 4.5.5 | 6.4.1 | 1 | — |
| `org.apache.poi:poi-ooxml` | 5.2.2 | 5.4.0 | 1 | — |
| `org.quartz-scheduler:quartz` | 2.3.0 | 2.3.2 | 1 | — |
| `org.apache.solr:solr-solrj` | 8.11.1 | 9.4.1 | 1 | — |
| `org.apache.tika:tika-core` | 2.4.0 | 3.2.2 | 1 | — |
| `org.apache.velocity:velocity` | 1.7 | — | 1 | — |
| `org.apache.santuario:xmlsec` | 2.2.3 | 3.0.3 | 1 | — |

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
pom.xml                     the whole project: 65 direct dependencies, all pinned inline
config/app.yml              board columns, WIP limits, datasource, SMTP, allowlist
src/main/java/com/cascade/
  core/                     model, store, query, markdown, importer, attachment, reports
  api/                      servlets, security, webhook, notify, schedule, search, cache, event, git
  cli/                      picocli client
src/main/resources/         log4j2.xml
```

## License

MIT
