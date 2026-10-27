# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew build

# Run all tests
./gradlew test

# Run tests for a single module
./gradlew :consul-populate-core:test

# Run a single test class
./gradlew :consul-populate-core:test --tests "com.frogdevelopment.consul.populate.ConsulFactoryTest"

# Test coverage report
./gradlew jacocoTestReport

# Build fat JAR (CLI)
./gradlew :consul-populate-cli:shadowJar

# Build Docker image (requires Docker)
./gradlew jib -Djib.console='plain'
```

## Module Structure

Multi-module Gradle (Kotlin DSL) project. Java 21, Micronaut 4.x, Vertx Consul client.

- **`consul-populate-core`** — library; Consul connection, file parsing, atomic KV population
- **`consul-populate-git`** — library; JGit integration, polling, webhooks, management endpoints
- **`consul-populate-cli`** — runnable fat JAR (PicoCLI); one-shot import for CI/CD pipelines
- **`consul-populate-server`** — Micronaut Netty server; commented out in `settings.gradle.kts`, used for local dev only
- **`buildSrc`** — convention plugins for Maven publishing (JReleaser) and JaCoCo

## Architecture

### Data flow

```
ConsulPopulateCommand (CLI) / GitEndpoint (server)
  → PopulateService.populate()
    → DataImporter.execute()           # either FilesImporter or GitImporter
      → GitImporter pulls repo         # git mode only; delegates back to FilesImporter
      → FilesImporter reads root dir + merges target subdirectory (override strategy)
      → returns Map<String, String>    # key = KV path, value = serialized config
    → builds single TxnRequest         # SET new/updated, DELETE removed keys
    → ConsulClient.transaction()       # atomic commit
```

### Key design decisions

- **Atomic transactions:** Consul writes (SET + DELETE for removed keys) are batched into `TxnRequest`s of at most 64
  operations and of at most `consul.kv.max-txn-bytes` (default 524288, Consul's `txn_max_req_len`, above which Consul
  answers 413). The size is measured on the exact JSON the client sends, not estimated, and the keys are sorted so the
  chunks are the same on every run. Each transaction is atomic; across chunks atomicity does not hold, and the first
  failed chunk aborts the run with an exception. A value that can never fit fails the run, and the dry run, before
  anything is written.
- **Untrusted and empty input:** `FilesImporter.confineTo(dir)` ignores files that resolve outside `dir`; git mode sets
  it to the clone (anyone with push access could commit a symlink to a file of the server), files mode does not,
  because the files of a Kubernetes ConfigMap are symlinks. An import that yields no key (every file empty) fails
  instead of pruning everything under the path, and two files that map to the same key (`a.yaml`, `a.yml`) fail.
- **Sealed `FilesImporter`:** restricted to `YamlFilesImporter`, `JsonFilesImporter`, `PropertiesFilesImporter`; new
  formats require adding a permitted subclass.
- **Async → sync bridge:** `VertxUtils.toBlocking()` wraps Vertx `CompletionStage` for the CLI path. Don't introduce
  raw blocking elsewhere.
- **Importer selection:** `FilesImportFactory` creates the importer with an exhaustive `switch` over
  `consul.files.format` (the compiler flags a missing format); `GitImporter` does the same for git mode. Webhook
  handlers, by contrast, are chosen with `@Requires` on the raw `consul.git.webhook.type` property, which ignores the
  Java default: the GitHub handler declares `defaultValue = "GITHUB"` for that reason.
- **CLI options mirror the configuration:** picocli rejects unknown arguments, so every `consul.*` property must be
  declared as an option in `ConsulPopulateCommand`; `ConsulPopulateCommandOptionsTest` fails when one is missing.
- **`RepositoryDirectoryProvider`:** uses `AtomicReference` + `compareAndSet` for lock-free one-time git clone.
- **`GitPull` owns pull + populate:** every trigger (poll, webhook, force-pull) runs fetch, `reset --hard` to the remote
  branch (a force-push never leaves a merge commit), then populates Consul if HEAD differs from the last populated
  commit. One lock serializes pulls and populates; a failed populate is retried on the next pull even if nothing
  changed upstream. `GitPullJob` only schedules the poll, so webhooks work with `poll-enabled=false`.

### Configuration properties

Consul connection: `consul.host/port/uri/secured/acl-token/dc/timeout` (timeout defaults to 30 s, `0` disables it)  
KV path: `consul.kv.prefix` (default `config`), `consul.kv.version` (optional path segment), `consul.kv.prune`
(default `true`: delete the keys under the path that are not in the source), `consul.kv.max-txn-bytes`  
Files mode: `consul.files.format` (YAML|JSON|PROPERTIES), `consul.files.root-path`, `consul.files.target`  
Git mode: `consul.git.uri`, `consul.git.token` (preferred) or `username/password`, `consul.git.branch`,
`consul.git.ssl-verify` (default `true`; `false` accepts any certificate, applied by `GitTransportConfigCallback` to
the clone and the fetch because JGit only reads `http.sslVerify` from a repository config that does not exist yet),
`consul.git.clean-up` (default `true`: the clone is deleted at shutdown, after the running pull),
`consul.git.poll-enabled`, `consul.git.poll-interval`, `consul.git.webhook.type` (GITHUB|BITBUCKET|CUSTOM),
`consul.git.webhook.secret` (required when a webhook is configured, unless `consul.git.webhook.allow-unsigned=true`;
`WebhookSecretValidator` stops the startup otherwise), `consul.git.webhook.response-timeout` (default 8 s: how long a
webhook request waits for its pull before answering 202 and letting it finish). Credentials are given to each
transport by `GitTransportConfigCallback`, never as the JVM-wide `CredentialsProvider` default.

### Git server endpoints

| Endpoint           | Method | Purpose                       |
|--------------------|--------|-------------------------------|
| `/git`             | GET    | Current repo status/summary   |
| `/git/force-pull`  | POST   | Manually trigger pull         |
| `/git/webhook`     | POST   | Receive push events           |
| `/git/toggle-poll` | POST   | Enable/disable polling        |

Custom webhook providers implement `WebhookPayloadHandler` as a `@Singleton` and set
`consul.git.webhook.type=CUSTOM`. A provider whose push can update several branches overrides `extractBranchNames`
(the default wraps `extractBranchName`); the pull happens when the tracked branch is among them.

## Testing

JUnit 5 + Mockito + AssertJ. Integration tests use Testcontainers (Consul).  
Tests follow BDD given/when/then with `ArgumentCaptor` for Consul client verifications.
