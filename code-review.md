# Code Review — consul-populate (full project)

Scope: every module (`core`, `git`, `cli`, `server`), `buildSrc`, the Gradle build, the CI workflow and the manifests.
Findings were checked against the code or files; those that rest on something not reproduced here are marked
**(reading only)** or **(reported)**. Findings 1–6 of the earlier per-module review ([core-review]) are already fixed
and are not repeated here.

ID prefixes: **C** core, **G** git, **L** CLI, **B** build, CI and delivery.

Status: **Fixed** findings keep their description (it records what was wrong) and replace the proposed fix with a note
on what was actually done. **Partial** means a related change mitigated part of the finding. **Open** means no change
yet.

## Summary

| ID  | Severity | Module | Finding                                                                       | Status  |
|-----|----------|--------|-------------------------------------------------------------------------------|---------|
| G1  | High     | git    | Webhook and force-pull never update Consul unless polling is enabled          | Fixed   |
| G2  | High     | git    | Index listener is never removed; populate runs N+1 times after N restarts     | Fixed   |
| G3  | High     | git    | HMAC mismatch log prints the valid signature                                  | Open    |
| G4  | High     | git    | `force-pull`, `toggle-poll` and `GET /git` are unauthenticated                | Open    |
| G5  | High     | git    | Anonymous clone of a public repo throws `NullPointerException`                | Open    |
| B1  | High     | CI     | Every branch push publishes the Docker image and can overwrite a released tag | Fixed   |
| C1  | Medium   | core   | No request timeout: a stalled Consul hangs the CLI forever                    | Open    |
| L1  | Medium   | cli    | `--consul.uri` and `--consul.secured` are rejected as arguments               | Open    |
| L2  | Medium   | cli    | `--dry-run` validates nothing and exits 0                                     | Open    |
| L4  | Medium   | cli    | CLI tests swallow exceptions and never assert the exit code                   | Open    |
| B2  | Medium   | CI     | One release can deploy to Maven Central more than once **(reported)**         | Partial |
| B3  | Medium   | CI     | `@master` action with a secret, unused write permissions, no action updates   | Open    |
| B4  | Medium   | deploy | Manifests reference an image tag that does not exist **(reported)**           | Open    |
| B5  | Medium   | deploy | Image runs as root; insecure defaults in samples likely to be copied          | Open    |
| B6  | Medium   | libs   | Library modules force logback onto consumers                                  | Open    |
| G6  | Medium   | git    | Missing or blank webhook secret silently disables signature checks            | Open    |
| G7  | Medium   | git    | Pull failures are reported as success (200/202)                               | Fixed   |
| G8  | Medium   | git    | A failed startup populate prevents polling from ever starting                 | Fixed   |
| G9  | Medium   | git    | No mutual exclusion between pull and populate                                 | Fixed   |
| G10 | Medium   | git    | `git.pull()` merges instead of resetting to the remote branch                 | Fixed   |
| G11 | Medium   | git    | Bitbucket handler only looks at the first changed branch                      | Open    |
| G12 | Medium   | git    | `webhook.type` default is never applied; handler match is case-sensitive      | Open    |
| C2  | Medium   | core   | Prune deletes every key under the prefix, with no dry run                     | Open    |
| C3  | Low      | core   | Chunking counts operations but not payload size                               | Open    |
| C4  | Low      | core   | Chunk membership depends on `HashMap` order                                   | Open    |
| C5  | Low      | core   | `PropertiesFilesImporter` strips trailing whitespace and loses key order      | Open    |
| C6  | Low      | core   | Malformed or colliding input files fail unhelpfully or silently               | Open    |
| C7  | Low      | core   | Documentation has drifted from the code                                       | Open    |
| G13 | Low      | git    | Ignored webhook events return 304, which providers flag as failed             | Open    |
| G14 | Low      | git    | Webhook handler pulls and populates inside the HTTP request                   | Open    |
| G15 | Low      | git    | Poll toggle is check-then-act; `stop()` interrupts in-flight work             | Open    |
| G16 | Low      | git    | Clone directories leak on every restart and on shutdown                       | Open    |
| G17 | Low      | git    | Credentials set JVM-wide; URI logged unmasked at DEBUG                        | Open    |
| G18 | Low      | git    | Symlinks in the repository are followed **(reading only)**                    | Open    |
| G19 | Low      | server | `ssl-verify` in `application-local.yaml` is read by nothing                   | Open    |
| L3  | Low      | cli    | `--help` prints a stack trace instead of usage                                | Open    |
| B7  | Low      | build  | Micronaut plugin version drift; signatures are never verified                 | Open    |

Done: B1, then G1+G2+G7+G8+G9+G10 as one change (they shared a cause).

Suggested order for the rest: G3, G5, G6, G4, C1, L1, L2, L4, then the others.

---

## consul-populate-git

### G1. Webhook and force-pull never update Consul unless polling is enabled

**Files:** `events/ServerEventsListener.java:53-57`, `pull/GitPullJob.java:60-63`

`populate()` has exactly two call sites: the startup hook, and an index-change listener that `GitPullJob.start()`
registers. `start()` is only called when `consul.git.poll-enabled=true`, and the default is `false`. With the default
config, a webhook or `POST /git/force-pull` updates the local clone and Consul stays stale until the next restart.
If polling was enabled and later toggled off, the listener stays registered, so behaviour depends on how the server
started.

**Status: fixed.** `GitPull` now owns the whole sequence for every trigger (poll, webhook, force-pull): fetch,
`reset --hard` to `origin/<branch>`, then populate Consul when HEAD differs from the last populated commit, all under
one lock. `GitPullJob` only schedules the poll, the JGit listener is gone, and the startup populate goes through
`GitPull.populate()`. A webhook or force-pull therefore updates Consul with `poll-enabled=false`. This also fixes G2,
G7, G8, G9 and G10; CLAUDE.md describes the new design.

Two details differ from the sketch originally proposed here. Change detection compares HEAD with the last *populated*
commit rather than with the pre-pull HEAD, so a populate that failed is retried on the next pull even when nothing
changed upstream. And force-pull on an unchanged repository does not repopulate (same as the old listener); add
`trigger == FORCED` to the check if you want it to act as a resync. Tests: `GitPullTest`.

### G2. Index listener is never removed

**File:** `pull/GitPullJob.java:60-63`

`addIndexChangedListener` is called on every `start()` and nothing removes it. Each `toggle-poll` off/on cycle adds
another listener, so one index change runs `populate()` N+1 times.

**Status: fixed** with G1: there is no listener anymore.

### G3. HMAC mismatch log prints the valid signature

**File:** `endpoint/handlers/WebhookHandler.java:143`

```java
log.warn("Signature mismatch - expected: {}, received: {}", calculatedSignature, givenSignature);
```

A caller who sends payload P with a wrong signature gets a WARN line containing the correct `sha256=...` value for P.
Anyone who can read the logs can replay that signature and forge a webhook for P.

**Fix:** never log the expected value:

```java
log.warn("Webhook signature mismatch (algorithm={})", algorithm);
```

### G4. Control endpoints are unauthenticated

**File:** `endpoint/GitEndpoint.java:47, 78-86`

`@Endpoint(value = "git", defaultSensitive = false)` exposes everything to anyone who can reach the port. `force-pull`,
`toggle-poll` and `webhook` share a single `@Write` method, so they cannot be secured separately. A caller can disable
polling (silently freezing sync) or spam pulls, and `GET /git` returns the local path and recent commit messages.

**Fix:** the webhook is the only action that must be reachable by the git provider, and it is already authenticated
by its HMAC signature. Split it out and lock down the rest:

- move `webhook` into a plain `@Controller("/git/webhook")` that does the signature check (see G6);
- keep `/git`, `force-pull` and `toggle-poll` as endpoints, set `endpoints.git.sensitive: true` (needs
  `micronaut-security`), and serve them on a private management port:

```yaml
endpoints:
  all:
    port: 8081   # not exposed outside the cluster / host
```

### G5. Anonymous clone of a public repo throws `NullPointerException`

**File:** `GitFactory.java:60`

With no token, username or password, the code builds `new UsernamePasswordCredentialsProvider(null, null)`. JGit's
`(String, String)` constructor calls `password.toCharArray()`, so startup fails with an NPE for any public repository.
`GitFactoryTest.shouldHandleEmptyToken` passes `"user"` and `"pass"`, so it never covers this.

**Fix:** only create a provider when there are credentials, and add a test with all three unset:

```java
private static Optional<CredentialsProvider> createCredentialsProvider(final GitProperties properties) {
    if (StringUtils.isNotEmpty(properties.getToken())) {
        return Optional.of(new UsernamePasswordCredentialsProvider(properties.getToken(), ""));
    }
    if (StringUtils.isNotEmpty(properties.getUsername())) {
        return Optional.of(new UsernamePasswordCredentialsProvider(
                properties.getUsername(), Objects.requireNonNullElse(properties.getPassword(), "")));
    }
    return Optional.empty();
}
```

### G6. Missing or blank webhook secret silently disables signature checks

**File:** `endpoint/handlers/WebhookHandler.java:101-111`

Verification only runs when `StringUtils.isNotEmpty(secret)`. A secret injected as `${WEBHOOK_SECRET:}` that resolves to
empty turns the webhook into an unauthenticated trigger, with no warning.

**Fix:** fail fast at startup unless the operator explicitly opts out. This is a breaking change for anyone running
unsigned webhooks today, so document it in the release notes:

```java
@PostConstruct
void validate() {
    final var webhook = gitProperties.getWebhook();
    if (webhook != null && StringUtils.isEmpty(webhook.getSecret()) && !webhook.isAllowUnsigned()) {
        throw new ConfigurationException(
                "consul.git.webhook.secret is required (or set consul.git.webhook.allow-unsigned=true)");
    }
}
```

### G7. Pull failures are reported as success

**Files:** `pull/GitPull.java:51-63`, `endpoint/handlers/ForcePullHandler.java:38-41`,
`endpoint/handlers/WebhookHandler.java` (`triggerPullingIfNeeded`)

`pull()` sets the trigger and time, then swallows every exception with a log line. Outcome and duration are not
updated, so `/git` shows the new trigger and time next to the previous `SUCCESS`. `ForcePullHandler` returns 200 and
the webhook returns 202 even when the fetch failed (bad credentials, network down).

**Status: fixed.** `GitPull.pull()` returns its `Status` and records `FAILURE` (with the duration) when the fetch, the
reset or the populate fails, so `/git` no longer shows a stale `SUCCESS`. `ForcePullHandler` and `WebhookHandler`
answer 500 on failure, so git providers flag the delivery as failed and can redeliver it. Tests: `GitPullTest`,
`ForcePullHandlerTest`, `WebhookHandlerTest`.

### G8. A failed startup populate prevents polling from starting

**File:** `events/ServerEventsListener.java:51-57`

`populate()` runs before `gitPullJob.start()`. If it throws (Consul down at boot, a bad YAML file), the `@Async`
listener dies and polling never starts, while `/git` reports it as scheduled. The first `toggle-poll` flips it to
`false` and calls `stop()`, so the operator needs two toggles.

**Status: fixed.** `ServerEventsListener` calls `GitPull.populate()` in a `try`/`catch`, logs the failure and starts the
job anyway. Because the repository is then still marked as not populated, the next pull (scheduled, webhook or
force-pull) retries even if nothing changed upstream. Tests: `ServerEventsListenerTest`, `GitPullTest`.

### G9. No mutual exclusion between pull and populate

**Files:** `pull/GitPull.java:57`, `PopulateServiceImpl.populate()` (core)

A poll tick, a webhook and a force-pull can run `git.pull()` concurrently, and JGit can throw `LockFailedException`.
Two populates can interleave, so an older snapshot can overwrite a newer one, and populate can read a half-checked-out
tree and emit DELETEs for files that merely have not been written yet. In the current design populate also runs inside
JGit's `DirCache.commit()` listener, i.e. before `MergeCommand` advances HEAD **(reading only)**: if populate throws,
the exception propagates through JGit and the retry may not fire the listener again.

**Status: fixed.** `GitPull` takes one `ReentrantLock` around "pull + populate", and the startup populate goes through
the same lock via `GitPull.populate()`. Populate no longer runs inside JGit's checkout, since it is called after the
reset completes. `PopulateServiceImpl` itself still has no lock, so any new caller must go through `GitPull` (the
one-shot CLI is not affected). Test: `GitPullTest.Concurrency`, which fails when the lock is turned into a no-op.

### G10. `git.pull()` merges instead of resetting to the remote branch

**File:** `pull/GitPull.java:57`

After a force-push or history rewrite of the tracked branch, `pull` creates a merge commit that resurrects removed
content, or leaves conflict markers in the working tree. The listener then publishes those files to Consul, and later
pulls keep failing because nothing resets the clone.

**Status: fixed.** `GitPull` runs `fetch` then `reset --hard refs/remotes/origin/<branch>`, so the clone always equals
the remote branch and local modifications are discarded. Tests: `GitPullTest` covers a rewritten history and a locally
modified file; both fail when `git.pull()` is put back.

### G11. Bitbucket handler only looks at the first changed branch

**File:** `endpoint/handlers/webhook/BitbucketWebhookPayloadHandler.java:66-74`

A single push can update several refs. The handler returns the first `new.name` it finds, so a push to
`[feature, main]` yields `feature`, the branch check fails, the webhook answers 304 and `main` is never pulled.

**Fix:** keep the public SPI (custom handlers implement it) and add a method with a backward-compatible default:

```java
// WebhookPayloadHandler
Optional<String> extractBranchName(JsonNode payload);

default Set<String> extractBranchNames(final JsonNode payload) {
    return extractBranchName(payload).stream().collect(Collectors.toSet());
}
```

Override `extractBranchNames` in the Bitbucket handler to collect every `change.new.name`, and make `WebhookHandler`
pull when the set contains the configured branch.

### G12. `webhook.type` default is never applied; handler match is case-sensitive

**Files:** `endpoint/handlers/webhook/GithubWebhookPayloadHandler.java:30`,
`BitbucketWebhookPayloadHandler.java:30`, `GitProperties.java` (`Webhook.type = GITHUB`)

`GitProperties.Webhook.type` defaults to `GITHUB`, but the handlers are gated by
`@Requires(property = "consul.git.webhook.type", pattern = "github|GITHUB")`, which matches the raw property and
ignores the Java default. With only `consul.git.webhook.secret` set, no handler bean exists and every webhook gets 501.
The pattern also rejects `GitHub`.

**Fix:**

```java
@Requires(property = "consul.git.webhook.type", pattern = "(?i)github", defaultValue = "GITHUB")
```

Use `(?i)bitbucket` without a default on the Bitbucket handler.

### G13. Ignored webhook events return 304

**File:** `endpoint/handlers/WebhookHandler.java` (`handle`, `triggerPullingIfNeeded`)

GitHub's `ping` event and pushes to other branches are answered with 304. Providers show non-2xx deliveries as failed
and may disable the hook after repeated failures.

**Fix:** return `HttpResponse.noContent()` (204) for events that are valid but intentionally ignored.

### G14. Webhook handler pulls and populates inside the HTTP request

**File:** `endpoint/handlers/WebhookHandler.java` (`triggerPullingIfNeeded`)

`gitPull.pull(WEBHOOK)` runs synchronously and, since the G1 fix, includes the populate. Providers time out after a few
seconds (GitHub: 10 s), mark the delivery failed, and may redeliver while the first pull is still running.

**Fix:** schedule the pull on the task scheduler and return 202 immediately. The lock in `GitPull` already serialises
runs. Note that returning 202 before the pull ends would lose the G7 behaviour (500 on failure), so the failure would
only be visible in `/git` and the logs.

### G15. Poll toggle is check-then-act; `stop()` interrupts in-flight work

**Files:** `endpoint/handlers/TogglePollHandler.java:46-56`, `pull/GitPullJob.java` (`start`, `stop`)

`start()` reads `scheduledFutureRef`, checks `isDone()`, then schedules and sets: two concurrent toggles can leave an
orphan scheduled future that `stop()` cannot cancel **(reading only)**. `stop()` uses `cancel(true)`, which interrupts a
pull or a multi-chunk populate halfway.

**Fix:** make `start()`/`stop()` `synchronized`, and use `cancel(false)` so a running pull finishes.

### G16. Clone directories leak

**Files:** `events/GitCleanUp.java:45-46`, `RepositoryDirectoryProvider.java:38-43, 54-58`

- `getRepository()` creates `localPath/consul-populate-XXXX/<repo>` but `GitCleanUp` deletes only `<repo>`, leaving
  an empty `consul-populate-XXXX` directory behind.
- `clean-up` defaults to `false` and every start creates a new temp directory, so each restart leaks a full clone
  under `/tmp`.
- The CAS guarantees one stored value, not one `createTempDirectory`: the losing thread leaves an empty directory.
- Cleanup runs in an unordered `ServerShutdownEvent` listener, so it can race the pull job and delete the tree while a
  pull is running.

**Fix:** delete `repositoryDirectory.getParent()`; call `gitPullJob.stop()` and close the `Git` handle before
deleting; delete the temp directory when `compareAndSet` fails. For the restart leak, either default `clean-up` to
`true` or use a deterministic directory (`localPath/<humanish-name>`) and reuse the existing clone with `Git.open`.

### G17. Credentials set JVM-wide; secrets reachable through logs

**Files:** `GitFactory.java:42, 45`, `GitProperties.java:27`

- `CredentialsProvider.setDefault(...)` is process-global state, so it affects any other JGit usage in the same JVM.
- The clone logs `gitProperties.getUri()` at DEBUG without masking, while `GitSummaryProvider` does mask it, which
  makes embedded `user:pass@` URIs look supported. The server's `logback.xml` enables DEBUG.
- `GitProperties` is `@Data` with no exclusions, so `toString()` contains `token`, `password` and the webhook secret.
  Nothing logs the object today, but `GlobalProperties` already excludes `aclToken` and the CLI logs its properties
  at startup, so the next person to add `log.info("{}", gitProperties)` leaks them.

**Fix:** pass the provider with `setCredentialsProvider(...)` on clone and fetch, log the masked URI via the helper
`GitSummaryProvider` already uses, and add `@ToString.Exclude` on `token`, `password` and `Webhook.secret`.

### G18. Symlinks in the repository are followed (reading only)

**File:** `consul-populate-core` `files/FilesImporter.java` (`filterFile`, `readFile`)

In git mode a contributor with push access can commit `application.yaml -> /path/on/server` and the file is parsed and
published to Consul if it is valid YAML/JSON/properties.

**Fix:** skip symbolic links in `filterFile`:

```java
if (Files.isSymbolicLink(file.toPath())) {
    log.warn("Ignoring symbolic link {}", file);
    return false;
}
```

### G19. `ssl-verify` in `application-local.yaml` is read by nothing

**File:** `consul-populate-server/src/main/resources/application-local.yaml:24`

The key is not bound to any property, so `ssl-verify: false` does nothing. TLS verification uses JGit defaults.
Anyone reading the file will believe verification is disabled.

**Fix:** delete the key, or implement it deliberately (and document the risk).

---

## consul-populate-core

### C1. No request timeout

**Files:** `config/ConsulFactory.java:51-53`, `config/GlobalProperties.java:62`, `VertxUtils.java:33`

`consul.timeout` is optional and unset by default. The Vert.x client only applies a timeout when it is above zero
(`ConsulClientImpl`: `if (timeoutMs > 0) rq.timeout(timeoutMs)`), and `toBlocking()` calls `Future.get()` with no
limit. A Consul that accepts the connection and then stalls (no leader, overloaded) hangs the CLI until the pipeline's
own timeout kills it.

**Fix:** default the timeout in the factory:

```java
static final long DEFAULT_TIMEOUT_MS = 30_000;

consulClientOptions.setTimeout(properties.getTimeout().orElse(DEFAULT_TIMEOUT_MS));
```

### C2. Prune deletes every key under the prefix, with no dry run

**File:** `PopulateServiceImpl.java:65-70`

Every key under `kvPath` that is not in the source is deleted. Without `consul.kv.version`, `kvPath` is `config/`, so a
run wipes `config/1.0/...` written by earlier versioned runs and anything else stored under that prefix. The only
guard is that an empty source throws.

**Fix:** log the number of deletions at WARN, and add two options: `consul.kv.prune` (default `true`, so existing
behaviour is kept) and a CLI `--dry-run` that prints the SET/DELETE plan and exits without calling `transaction()`.

### C3. Chunking counts operations but not payload size

**File:** `PopulateServiceImpl.java:75-79`

Consul also limits the request body: `txn_max_req_len` and `kv_max_value_size` both default to Raft's suggested
maximum, 512 KB ([consul-limits]). 64 operations with large YAML values can exceed that, and the transaction fails
mid-run after earlier chunks were committed.

**Fix:** start a new chunk when either 64 operations or a byte budget (for example 400 KiB of UTF-8 key + value bytes)
would be exceeded, and fail up front with a clear message when a single value is larger than the limit.

### C4. Chunk membership depends on `HashMap` order

**File:** `PopulateServiceImpl.java:53-56`

`Collectors.toMap` builds a `HashMap`, so which keys land in which chunk can differ between runs. When a chunk fails,
which keys were applied is not reproducible, which makes partial-failure debugging harder.

**Fix:** collect into a `TreeMap` (`Collectors.toMap(k, v, (a, b) -> b, TreeMap::new)`) and keep SETs before DELETEs.

### C5. `PropertiesFilesImporter` strips trailing whitespace and loses key order

**File:** `files/PropertiesFilesImporter.java:62-70`

- `.strip()` does nothing useful: with a `null` comment `store()` writes only the `#<date>` line and `key=value`
  lines (no blank line), and the join already has no trailing newline. It does trim trailing whitespace from the last
  value, which `store()` does not escape.
- `Properties` is hash-ordered, so the output no longer follows the source file order.

**Fix:** drop `.strip()`. Accept hash order and note it in the docs, since consumers parse the value as properties;
if a stable order matters, override `entrySet()` on a `Properties` subclass to return a `LinkedHashMap`'s entries
(works on JDK 21 but relies on `store0` iterating `entrySet()`).

### C6. Malformed or colliding input files

**Files:** `files/YamlFilesImporter.java:48`, `files/JsonFilesImporter.java:55`, `files/FilesImporter.java:65`

- A YAML file whose top level is a list or scalar throws a bare `ClassCastException` at the call site.
- An empty JSON file fails the whole run (`MismatchedInputException`), while an empty YAML file is skipped with a
  warning.
- `application.yaml` and `application.yml` in the same directory map to the same KV key and one silently wins.

**Fix:**

```java
// YamlFilesImporter.readFile
final Object loaded = yaml.get().load(reader);
if (loaded == null) {
    return null;
}
if (!(loaded instanceof Map<?, ?>)) {
    throw new IOException("Top-level of " + file + " must be a mapping");
}
return (SequencedMap<String, Object>) loaded;
```

In `JsonFilesImporter.readFile`, return `null` when `file.length() == 0` so both formats behave the same. In
`FilesImporter.readFiles`, throw when two files share a base name after `FilenameUtils.removeExtension`.

### C7. Documentation has drifted from the code

- `CLAUDE.md`: "`@Requires`-gated beans: `FilesImportFactory` creates the correct importer ... no runtime branching
  needed". `FilesImportFactory` now uses a `switch (properties.getFormat())`. Rewrite the bullet; the exhaustive
  `switch` over the enum is compiler-checked, which is a fair replacement.
- `GlobalProperties.aclToken` Javadoc says the token is sent as a `?token` query parameter. The Vert.x client sends it
  in the `X-Consul-Token` header, so it does not end up in URLs or access logs. Fix the Javadoc.

---

## consul-populate-cli

### L1. `--consul.uri` and `--consul.secured` are rejected as arguments

**File:** `ConsulPopulateCommand.java:22-43`

Picocli rejects any option it does not know, so every property passed as `--consul.x=y` must be declared. The command
declares only a subset: `consul.uri` and `consul.secured` are missing, and `--consul.acl-token` is accepted only in
kebab case (`--consul.files.rootPath` has both spellings). A Job that passes arguments, like the one in
`minikube-manifest.yaml:102-109`, cannot enable TLS or use a URI; only the `CONSUL_*` environment variables can.
**(reported)**: running the fat JAR with `--consul.secured=true` or `--consul.uri=...` gave "Unknown option" and exit
code 2.

**Fix:** declare the missing options and the camelCase aliases, and add a test that fails when a property in
`GlobalProperties` or `ImportFileProperties` has no matching option, so the two cannot drift again:

```java
@Option(names = {"--consul.uri"})
String consulUri;
@Option(names = {"--consul.secured"})
Boolean consulSecured;
@Option(names = {"--consul.acl-token", "--consul.aclToken"})
String aclToken;
```

### L2. `--dry-run` validates nothing

**File:** `ConsulPopulateCommand.java:64-66`

`--dry-run` logs "Nothing done" and returns before the importer runs or Consul is contacted. A pull-request
validation step using it is green for a malformed YAML file, a wrong `root-path` or an unreachable Consul
(`--dry-run --consul.files.root-path=/nonexistent` exits 0 **(reported)**).

**Fix:** make dry run do everything except the write: run `dataImporter.execute()`, read the existing keys, and print
the SET/DELETE plan (this is also the dry run proposed in C2):

```java
// PopulateService
void populate(boolean dryRun);   // dryRun: build the plan, log counts and keys, skip transaction()
```

### L3. `--help` prints a stack trace

**File:** `ConsulPopulateCommand.java:16-43`

The command has no `mixinStandardHelpOptions` and no option descriptions. Because the command's constructor requires
`PopulateService` and the properties beans, `--help` without `consul.files.*` set fails building the bean graph and
prints a `DependencyInjectionException` (exit 1) instead of usage **(reported)**.

**Fix:** enable the help mixin, describe each option, and inject the collaborators lazily so the command can be built
without a complete configuration:

```java
@Command(name = "consul-populate", mixinStandardHelpOptions = true,
         description = "Imports configuration files into Consul KV in one atomic transaction")
public class ConsulPopulateCommand implements Runnable {
    private final Provider<PopulateService> populateService;
    ...
}
```

### L4. CLI tests give false assurance

**File:** `src/test/java/.../ConsulPopulateCommandTest.java:74, 112, 124`

The tests wrap the call in `catch (Exception e)` and never assert the exit code, and the environment-variable test,
which covers the path `docker-compose.yaml` uses, is `@Disabled`. The `systemlambda` dependency and the
`java.security.manager` property exist only for it. There is no failure-path test, so a regression that returns exit
code 0 on error would pass. Exit codes were checked by hand: 0 on success, 1 on populate failure, 2 on usage error,
70 on an unexpected error **(reported)**.

**Fix:** assert the code returned by `PicocliRunner.execute(...)` and add the missing cases:

| Case                                  | Expected exit code |
|---------------------------------------|--------------------|
| Success                               | 0                  |
| Consul unreachable                    | 1                  |
| ACL-denied transaction                | 1                  |
| Unknown option                        | 2                  |
| `--dry-run` with a bad path (see L2)  | non-zero           |

For the environment-variable path, start the shadow JAR with `ProcessBuilder` and the `CONSUL_*` variables instead of
mutating the test JVM's environment.

---

## Build, CI and delivery

### B1. Every branch push publishes the Docker image and can overwrite a released tag

**Files:** `.github/workflows/ci_cd.yml:6-10, 64-87`, `consul-populate-cli/build.gradle.kts` (`jib.to.image`)

The workflow triggers on `push` to any branch. The `package` job has no `if`, so `./gradlew jib` runs on every branch
and pushes `frogdevelopment/consul-populate:${rootProject.version}`. Any feature branch overwrites
`:1.3.0-SNAPSHOT`, and after a release any push whose version is still `1.3.0` overwrites the released `:1.3.0` image
while the Maven Central artifact stays frozen. There is no `concurrency` group either, so two runs can race on the
same tag.

**Status: fixed** in `f0b31a5`: the workflow has a `concurrency` group per workflow and ref, and the `package` job only
runs for `main` and for tags. `main` carries a `-SNAPSHOT` version (`1.3.0-SNAPSHOT`), so it cannot overwrite a released
tag as long as the version is bumped right after a release. Feature branches no longer build the image at all; if you
want that check, use `./gradlew jibDockerBuild` (no push).

### B2. One release can deploy to Maven Central more than once (reported)

**File:** `.github/workflows/ci_cd.yml:6-10, 92-94`

`jreleaserDeploy` runs on a push to `release/*` and on `release: created` (ref `refs/tags/...`). JReleaser's
`dryrun` is `!version.matches("x.y.z")` (`deploy-convention:12`), so a `release/*` branch whose version is already
`1.3.0` deploys for real before any tag exists, and the `created` event then deploys the same version again, which
fails or publishes from the wrong ref. Per GitHub's event documentation, `created` also fires when a draft release is
saved. The image is pushed before the Central deploy, so a failed deploy leaves a released image with no artifacts.

**Status: partial.** The B1 fix (`f0b31a5`) skips the whole `package` job on `release/*` pushes, so the branch-push
deploy can no longer fire and only tags deploy. Still open: the `release: created` trigger also fires for drafts, the
image is still pushed before the Central deploy, and the `refs/heads/release/` part of the deploy step's condition
(`ci_cd.yml:97`) is now dead code.

**Fix:** deploy on `release: published` (or on a tag push) and remove the dead `release/*` condition. Move the image
push after the Central deploy so a failed release does not leave a half-published version.

### B3. `@master` action with a secret, unused write permissions, no action updates

**Files:** `.github/workflows/ci_cd.yml:18-19, 57`, `.github/dependabot.yml`

- `sonarsource/sonarqube-quality-gate-action@master` is a moving ref that receives `SONAR_TOKEN`; a compromise or
  breaking change upstream runs in your pipeline.
- The `build` job grants `checks: write` and `pull-requests: write`, and no step appears to need them.
- Dependabot only watches `gradle`, so the actions are never bumped.

No `pull_request_target` and no untrusted input in `run:` steps was found, and secrets are scoped to the steps that
use them.

**Fix:** pin the action to a release tag or a commit SHA, drop the two unused permissions, and add the ecosystem:

```yaml
  - package-ecosystem: github-actions
    directory: "/"
    schedule:
      interval: weekly
```

### B4. Manifests reference an image tag that does not exist (reported)

**Files:** `consul-populate-cli/docker-compose.yaml:10`, `consul-populate-cli/minikube-manifest.yaml:101`

Both use `frogdevelopment/consul-populate:1.0.0-SNAPSHOT`. The Docker Hub tag list (16 tags, `1.0.0` to
`1.3.0-SNAPSHOT`, plus `main-SNAPSHOT`) does not contain it, so the pull fails. They also pin Consul `1.18.1` while
the tests and the server compose file use `1.22.7`. `docker-compose.yaml` has no `depends_on`, healthcheck or
`restart`, so if Consul is not ready the populate container exits 1 once and never retries (the minikube Job is
cushioned by `backoffLimit: 3`).

**Fix:** point at a published release tag, align the Consul version with the tests, and make compose wait for Consul:

```yaml
services:
  consul:
    healthcheck:
      test: ["CMD", "consul", "members"]
      interval: 2s
      retries: 15
  populate:
    depends_on:
      consul:
        condition: service_healthy
    restart: on-failure
```

### B5. Image runs as root; insecure defaults in samples likely to be copied

**Files:** `consul-populate-cli/build.gradle.kts:91, 114-116`, `docker-compose.yaml:4-7`,
`minikube-manifest.yaml:57-58, 99-120`

- Jib sets no `container.user`; the published image runs as root **(reported)**.
- The base image `eclipse-temurin:21-jre-alpine` is a floating tag, so rebuilding the same version can change the
  image.
- `docker-compose.yaml` publishes a dev-mode Consul (ACLs off) on all host interfaces, so anyone on the LAN can read
  and write KV; `minikube-manifest.yaml` suggests exposing it with a NodePort. The Job has no `securityContext`.
- The samples only show the `args:` style and no `secretKeyRef`, so people will put `--consul.acl-token=...` in the pod
  spec or on the process list.

**Fix:**

```kotlin
container {
    setMainClass(application.mainClass)
    user = "10001"
}
```

Pin the base image to a minor tag or a digest, bind the dev Consul with `"127.0.0.1:8500:8500"`, add
`securityContext: { runAsNonRoot: true, readOnlyRootFilesystem: true }` to the Job, and show the token coming from a
secret (environment variables bind correctly and are overridden by CLI arguments **(reported)**):

```yaml
env:
  - name: CONSUL_ACL_TOKEN
    valueFrom:
      secretKeyRef: { name: consul-acl, key: token }
```

### B6. Library modules force logback onto consumers

**Files:** `consul-populate-core/build.gradle.kts:28`, `consul-populate-git/build.gradle.kts:32`

`runtimeOnly(mn.logback.classic)` with `from(components["java"])` puts logback into the runtime scope of the published
POMs, so anyone using the library gets it on the classpath and may clash with log4j2 or a Spring Boot logging setup.
Related: `core` declares `implementation(libs.vertx.consul)` while the public `VertxUtils.toBlocking(Future)` exposes
a Vert.x type, so consumers compile against a signature whose type is not on their compile classpath.

**Fix:** use `testRuntimeOnly(mn.logback.classic)` in the two libraries (the apps `cli` and `server` keep the runtime
dependency), and either make `VertxUtils` package-private or switch that dependency to `api(libs.vertx.consul)`.

### B7. Micronaut plugin version drift; signatures are never verified

**Files:** `consul-populate-server/build.gradle.kts:2`, `settings.gradle.kts:2`, the `cli`, `core` and `git` build
files, `buildSrc/.../deploy-convention.gradle.kts:22`

- The server module uses plugin `4.5.3`; the other four places use `4.6.2`. The version is repeated in five files, so
  Dependabot bumps them separately. The server is also commented out in `settings.gradle.kts`, so it cannot currently
  be built.
- `signing { verify = false }` means JReleaser does not check the signatures it just produced before uploading.

**Fix:** declare the plugin version once in `pluginManagement` in `settings.gradle.kts` and drop it from each module;
set `verify = true` so a bad signature fails the build instead of the Central validation.

---

## Reviewed and not reported

- **Chunking breaks atomicity:** inherent to Consul's 64-operation limit; documented in CLAUDE.md, and a failed chunk
  now aborts the run.
- **NPE on a `null` value in `PropertiesFilesImporter`:** unreachable, `Properties.load` never yields nulls.
- **`ThreadLocal<Yaml>` "leak":** negligible; the instance is small and thread-confined.
- **Properties escaping changes stored values:** that is the intended fix for the original escaping bug.
- **Webhook signature comparison:** uses `MessageDigest.isEqual`, so it is constant-time.
- **ACL token in the URL:** it is a header (see C7).
- **CLI exit codes, secrets and shutdown:** 0 on success, 1 on populate failure, 2 on usage error, 70 on an unexpected
  error. The ACL token is excluded from `toString` and absent from logs, and the context is closed before
  `System.exit`, so there is no JVM hang **(reported)**.
- **`shadowJar` service files:** merged by the Micronaut plugin.
- **CI secrets:** scoped to the steps that need them; no hardcoded secrets in the manifests.

[core-review]: consul-populate-core/code-review.md
[consul-limits]: https://developer.hashicorp.com/consul/docs/reference/agent/configuration-file/general
