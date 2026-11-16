![Build](https://github.com/FrogDevelopment/consul-populate/actions/workflows/ci_cd.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=FrogDevelopment_consul-populate&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=FrogDevelopment_consul-populate)
![GitHub Release](https://img.shields.io/github/v/release/FrogDevelopment/consul-populate)
[![Release date](https://img.shields.io/github/release-date/FrogDevelopment/consul-populate)](https://packagist.org/packages/FrogDevelopment/consul-populate)

# Consul Populate

Concept: give a tool to easily push content in Consul KV to be used as distributed configurations.

Alternative to [git2consul](https://github.com/breser/git2consul) which is not maintained anymore.

## Contents

- [Features](#features)
- [Import methods](#import-methods)
- [Upgrading from 1.x](#upgrading-from-1x)
- [Global configurations](#global-configurations)
- [Populate by files](#populate-by-files)
- [Populate by git](#populate-by-git)

## Features

- Easy to configure
- Rapid execution
- Atomic transactions for data consistency (all-or-nothing updates, see
  [Consul > Transactions HTTP API](https://developer.hashicorp.com/consul/api-docs/txn)). Consul limits a transaction
  to 64 operations and to a request body of 512 KiB, so a larger import is split in several transactions: each one is
  atomic, and the import stops at the first one that fails
- Multiple import sources: local files or git repository
- Server mode with polling and webhook support for continuous synchronization

## Import Methods

| Method                                 | Description                  | Use Case                             |
|----------------------------------------|------------------------------|--------------------------------------|
| [Populate by files](#populate-by-files) | Import from local filesystem | CI/CD pipelines, one-shot updates    |
| [Populate by git](#populate-by-git)     | Import from git repository   | Server mode with auto-sync, webhooks |

## Upgrading from 1.x

2.0.0 is the first release since 1.2.7: the versions in between (1.3.x, 1.4.x) were never published to Maven Central
or Docker Hub. What can break an existing setup:

- The Docker image runs as user `10001` instead of root. The files it reads must be readable by that user (for a
  Kubernetes volume, set `fsGroup` or make the files world-readable).
- An import where every file is empty now fails. Before, it deleted every key under the path.
- Two files that map to the same key, such as `a.yaml` and `a.yml`, now fail. Before, one silently replaced the other.
- A single value too large for a Consul transaction now fails the run before anything is written.
- Requests to Consul time out after 30 seconds. Before, there was no timeout. Set `consul.timeout=0` to keep it that
  way.

The keys under the path that are not in the source are still deleted by default; `consul.kv.prune=false` keeps them.

## Global Configurations

- `consul.uri`: Consul URI. (Will be taken over `host` and `port`)
- `consul.host`: Consul host. Defaults to *localhost*; ignored when `uri` is set (Optional)
- `consul.port`: Consul port. Defaults to *8500*; ignored when `uri` is set (Optional)
- `consul.secured`: Set whether Consul is secured. Defaults to *false* (Optional)
- `consul.acl-token`: ACL token needed to read and write in KV path. When present, will be sent in the
  `X-Consul-Token` header of the requests (Optional)
- `consul.dc`: Consul datacenter name. When present, will be added to requests using `?dc` query parameter (Optional)
- `consul.timeout`: Amount of time (in milliseconds) for requests. Defaults to *30000*; `0` disables the timeout
  (Optional). _Before 2.0.0 there was no timeout, so a Consul that stopped answering made the import hang_
- `consul.kv.prefix`: Prefix for the KV path where the configuration is stored. Defaults to *config* (Optional)
- `consul.kv.version`: Version of the configuration. When present, will be used in the KV path (Optional)
- `consul.kv.prune`: Whether to delete the keys under the KV path that are not in the imported data. Defaults to
  *true*; set it to *false* when other writers share the same prefix and only the imported keys must be touched
  (Optional). A run that deletes keys logs a warning with their number, and `--dry-run` lists them first. _Available
  since 2.0.0 (deleting was the only behaviour before)_
- `consul.kv.max-txn-bytes`: Maximum size, in bytes, of the request body of one Consul transaction. Defaults to
  *524288* (512 KiB), the `txn_max_req_len` of Consul, above which Consul answers 413 (Optional). Change it only
  together with the setting of your Consul agents. The size is measured on what is really sent. A single value that
  does not fit makes the run (and the `--dry-run`) fail before anything is written. _Available since 2.0.0: before, only
  the number of operations was counted, so a few large values failed in the middle of the import_

### When running the CLI jar

1. All arguments have to be passed in the format `--{property}` (for instance `--consul.acl-token`). Every property
   listed above can be passed this way; `--consul.uri` and `--consul.secured` are accepted since 2.0.0, and an unknown
   argument is rejected
2. All properties but sensitive are logged in INFO at start of execution
3. There is an additional argument `--dry-run` that validates the import without writing anything: Consul must be
   reachable, the files are read and parsed, the keys already in Consul are listed, and the resulting `SET` and
   `DELETE` operations are logged. A wrong path, an invalid file or an unreachable Consul makes it fail, so it can be
   used as a validation step in a pipeline. _Before 2.0.0 it returned without checking anything_
4. `--help` (or `-h`) lists every option with its default value, and works without any configuration. _Before 2.0.0
   it ended with a stack trace about a missing bean_

Points 2 and 3 can be used to debug.

#### Exit codes

| Code | Meaning                                                                                         |
|------|-------------------------------------------------------------------------------------------------|
| 0    | The import succeeded (or the dry run found nothing wrong)                                       |
| 1    | The import failed: Consul unreachable or refusing access, missing directory, invalid file, ...  |
| 2    | Invalid command line, for example an unknown argument                                           |

A pipeline step fails on any non-zero code.

## About the KV name

If you need to use environment (Micronaut) or profile (Spring), you will have to name your KV according to the
format: `{key},{environment}` like explained in the following table

| Key path                       | Description                                                              |
|--------------------------------|--------------------------------------------------------------------------|
| /config/application            | Configuration shared by all applications                                 |
| /config/application,XXX        | Configuration shared by all applications for the XXX Environment/Profile |
| /config/[APPLICATION_NAME]     | Application-specific configuration, example /config/hello-world          |
| /config/[APPLICATION_NAME],XXX | Application-specific configuration for the XXX Environment/Profile       |

For more details,
read [Micronaut#HashiCorp Consul Support - Storing Configuration as Key/Value Pairs](https://docs.micronaut.io/latest/guide/#distributedConfigurationConsul)

## Populate by files

### Configuration

- `consul.files.root-path`: path to root directory (**Required**)
- `consul.files.target`: subdirectory used to override root configurations (**Required**)
- `consul.files.format`: (YAML | JSON | PROPERTIES) Supported format of the files. Defaults to *YAML* (Optional)

### Rules for the files

- A YAML or JSON file must be a mapping (an object) at its top level. Otherwise the import fails and names the file; so
  does a syntax error.
- An empty file is skipped with a warning, for every format. If **every** file is empty the import fails, instead of
  deleting everything under the KV path.
- The extension is not part of the key: `application.yaml` and `application.yml` in the same directory both give
  `application`, which fails the import and names both files.
- Symbolic links are followed, so a Kubernetes ConfigMap or Secret mounted as the configuration directory works. (In
  [git mode](#populate-by-git) the files must really be inside the repository.)
- A value is sent to Consul once encoded (base64, a third larger): a single value of more than about 390 000 bytes
  (UTF-8) does not fit in a Consul transaction, and fails the import before anything is written. See
  `consul.kv.max-txn-bytes`.

_Since 2.0.0. Before, a top-level list gave a bare `ClassCastException`, an empty JSON file failed the whole run while
an empty YAML file was skipped, and two files with the same name apart from the extension silently replaced each other._

### Directories structure

You need to have:

- all base configurations in the root of the configurations directory
- 1 subdirectory by environment, with **only** the configurations to override with environment related values (usually url, table name, ... all those values that change between environments)

#### Example

Given we have a very classic platform with

- orders-services => need configurations to postgres and rabbitmq
- products-services => need configurations to couchbase and rabbitmq
- users-services => need configurations to postgres

you will have this structure:

```
.
└── path_to_configuration
    ├── dev
    │   ├── application,couchbase.yaml 
    │   ├── application,postgres.yaml 
    │   ├── application,rabbitmq.yaml 
    │   └── orders-services.yaml
    ├── stg
    │   ├── application,couchbase.yaml 
    │   ├── application,postgres.yaml 
    │   ├── application,rabbitmq.yaml 
    │   └── products-services.yaml    
    ├── qa
    │   ├── application,couchbase.yaml 
    │   ├── application,postgres.yaml 
    │   └── application,rabbitmq.yaml 
    ├── prod
    │   ├── application,couchbase.yaml 
    │   ├── application,postgres.yaml 
    │   ├── application,rabbitmq.yaml 
    │   └── users-services.yaml
    ├── application.yaml
    ├── application,couchbase.yaml
    ├── application,postgres.yaml
    ├── application,rabbitmq.yaml
    ├── orders-services.yaml
    ├── products-services.yaml
    └── users-services.yaml
```

##### Note

You will need to define the `postgres`, `rabbitmq` and `couchbase` as environment (Micronaut) or profile (Spring)

```java
public class MyApplication {

    public static void main(final String[] args) {
        Micronaut.build(args)
                .mainClass(MyApplication.class)
                .environments("rabbitmq", "postgres")
                .start();
    }
}
```

### Usage

**Artifacts:**

- [consul-populate-cli jar](https://mvnrepository.com/artifact/com.frog-development.consul-populate/consul-populate-cli)
- [consul-populate Docker image](https://hub.docker.com/r/frogdevelopment/consul-populate/tags)

Replace `{version}` in the examples below with the desired version from the links above.

#### with Command Line

```shell
java -jar {path_to_archive}/consul-populate-cli-{version}.jar \
    --consul.host=localhost \
    --consul.port=8500 \
    --consul.kv.prefix=config/frog/my_app \
    --consul.kv.version=1.2.3 \
    --consul.files.format=YAML \
    --consul.files.target=dev \
    --consul.files.root-path={path_to_configuration}
```

##### Validate without writing

Add `--dry-run` to check a configuration directory, for instance in a pull request pipeline. Nothing is written to
Consul, but everything else runs: Consul must be reachable and the files are read and parsed. The planned operations
are logged:

```
Dry run, nothing is written to Consul: 3 key(s) to set, 1 key(s) to delete, in 1 transaction(s)
  SET    config/frog/my_app/1.2.3/application
  SET    config/frog/my_app/1.2.3/application,postgres
  SET    config/frog/my_app/1.2.3/orders-services
  DELETE config/frog/my_app/1.2.3/users-services
```

The command exits with a non-zero code when something is wrong (see the [exit codes](#exit-codes)).

#### with Docker Compose

```yaml
services:
  consul-populate:
    image: frogdevelopment/consul-populate:{version}
    environment:
      CONSUL_HOST: consul
      CONSUL_PORT: 8500
      CONSUL_KV_PREFIX: config/frog/my_app
      CONSUL_KV_VERSION: 1.2.3
      CONSUL_FILES_FORMAT: YAML
      CONSUL_FILES_TARGET: dev
      CONSUL_FILES_ROOT_PATH: /configurations
    volumes:
      - {path_to_configuration}:/configurations:ro
```
See full docker-compose file in [consul-populate/consul-populate-cli/docker-compose.yaml](https://github.com/FrogDevelopment/consul-populate/blob/main/consul-populate-cli/docker-compose.yaml)

#### with Kubernetes Job
```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: consul-populate
spec:
  template:
    spec:
      automountServiceAccountToken: false
      containers:
        - name: consul-populate
          image: frogdevelopment/consul-populate:{version}
          args:
            - "--consul.host=consul-svc"
            - "--consul.port=8500"
            - "--consul.kv.prefix=frog"
            - "--consul.kv.version=1.2.3"
            - "--consul.files.format=YAML"
            - "--consul.files.target=dev"
            - "--consul.files.root-path=/configurations"
          imagePullPolicy: Always
          volumeMounts:
          - mountPath: /configurations
            name: configurations
      restartPolicy: Never
      volumes:
        - name: configurations
          persistentVolumeClaim:
            claimName: configurations-pvc
            readOnly: true
  backoffLimit: 3
```
See full manifest file in [consul-populate/consul-populate-cli/minikube-manifest.yaml](https://github.com/FrogDevelopment/consul-populate/blob/main/consul-populate-cli/minikube-manifest.yaml)

## Populate by git

_Available since version 2.0.0_

### Configuration

#### Repository Settings

- `consul.git.uri`: Git repository URI (e.g., `https://github.com/user/repo.git`) (**Required**)
- `consul.git.branch`: Branch to clone and track. Defaults to *main* (Optional)
- `consul.git.local-path`: Local directory path where the repository will be cloned. Defaults to */tmp* (Optional)
- `consul.git.ssl-verify`: Whether to verify the TLS certificate of the git server. Defaults to *true*. Set to *false*
  for a server with a self-signed certificate (Optional). The connection can then be intercepted, so prefer adding
  the certificate to the JVM trust store; a warning is logged at startup when the check is off.

#### Authentication

A public repository needs none: leave all of the following unset and it is cloned anonymously.

For a private repository, use **one** of the following authentication methods:

##### Token Authentication (Recommended)

- `consul.git.token`: Personal access token for authentication

##### Basic Authentication

- `consul.git.username`: Username for basic authentication
- `consul.git.password`: Password for basic authentication

#### File Import Settings

- `consul.git.files.root-path`: Path of the configuration directory within the repository. Defaults to the root of the
  repository (Optional)
- `consul.git.files.target`: Subdirectory of the root path used to override root configurations. Defaults to the root
  path itself, which means no override (Optional)
- `consul.git.files.format`: (YAML | JSON | PROPERTIES) Supported format of the files. Defaults to *YAML* (Optional)

See [Populate by files](#populate-by-files) for directory structure details. A file that is a symbolic link to something
outside of the cloned repository is ignored with a warning, since anyone who can push could otherwise publish a file of
the server (links that stay inside the repository are followed).

#### Polling (Server Mode)

Polling allows the server to periodically check for repository changes and automatically update Consul.

- `consul.git.poll-enabled`: Whether to enable polling. Defaults to *false* (Optional)
- `consul.git.poll-interval`: Interval between polling attempts. Defaults to *5m* (Optional)

Polling is optional: a webhook or a call to `/git/force-pull` updates Consul whether or not polling is enabled.

#### Webhook

Webhooks allow git providers to notify the server when changes are pushed, triggering an immediate update.

- `consul.git.webhook.type`: Git provider type (GITHUB | BITBUCKET | CUSTOM), in upper or lower case. Defaults to
  *GITHUB* (Optional; set it for `BITBUCKET` or `CUSTOM`)
- `consul.git.webhook.secret`: Secret key used to verify the signature of incoming webhooks (**Required** if using
  webhooks, unless `allow-unsigned` is set)
- `consul.git.webhook.allow-unsigned`: Accept webhooks without verifying their signature when no secret is configured.
  Defaults to *false*. Anyone who can reach the endpoint can then trigger a pull, so only use it on a trusted network
  (Optional)
- `consul.git.webhook.response-timeout`: How long a webhook request waits for the pull it triggers. Defaults to *8s*
  (Optional). See [Response codes](#response-codes)

The server **refuses to start** when a webhook is configured without a secret (a blank value, for instance from
`${WEBHOOK_SECRET:}`, counts as missing) and `allow-unsigned` is not `true`.

##### Supported Providers

| Provider  | Type        | Event Header   | Signature Header    |
|-----------|-------------|----------------|---------------------|
| GitHub    | `GITHUB`    | X-GitHub-Event | X-Hub-Signature-256 |
| Bitbucket | `BITBUCKET` | X-Event-Key    | X-Hub-Signature     |
| Custom    | `CUSTOM`    | User-defined   | User-defined        |

A Bitbucket push can update several branches at once. The server pulls when the tracked branch is one of them.

##### Custom Webhook Handler

For providers other than GitHub or Bitbucket, set `consul.git.webhook.type=CUSTOM` and provide your own
`WebhookPayloadHandler` bean:

```java
import com.frogdevelopment.consul.populate.git.endpoint.handlers.webhook.WebhookPayloadHandler;

@Singleton
public class GitLabWebhookPayloadHandler implements WebhookPayloadHandler {

    @Override
    public String getEventHeaderKey() {
        return "X-Gitlab-Event";
    }

    @Override
    public String getExpectedEventValue() {
        return "Push Hook";
    }

    @Override
    public String getSignatureHeaderKey() {
        return "X-Gitlab-Token";
    }

    @Override
    public Optional<String> extractBranchName(JsonNode payload) {
        var refNode = payload.get("ref");
        if (refNode == null || refNode.isNull()) {
            return Optional.empty();
        }
        return Optional.of(refNode.asText());
    }
}
```

If a push of your provider can carry several branches, also override `Set<String> extractBranchNames(JsonNode payload)`.
Its default wraps `extractBranchName`, so a handler that does not override it keeps working. The server pulls when the
tracked branch is among the returned names.

If `type=CUSTOM` is configured but no handler bean is provided, the webhook endpoint returns **501 Not Implemented**.

#### Cleanup

- `consul.git.clean-up`: Whether to delete the cloned repository on shutdown. Defaults to *true* (Optional). The server
  stops polling and waits for the pull that is running before it deletes.

### Management Endpoints

The server exposes management endpoints under `/git`:

| Endpoint           | Method | Description                              |
|--------------------|--------|------------------------------------------|
| `/git/force-pull`  | POST   | Manually trigger a pull and update       |
| `/git/webhook`     | POST   | Receive webhook events from git provider |
| `/git/toggle-poll` | POST   | Toggle polling on/off                    |
| `/git`             | GET    | Get current git status and summary       |

#### How an update is applied

A pull is triggered by the polling schedule, a webhook or `/git/force-pull`. All of them behave the same way:

1. the remote is fetched and the local clone is reset (`git reset --hard`) to the configured branch of the remote, so
   a force-push or a rewritten history is followed cleanly and local changes in the clone are discarded;
2. if the checked-out commit is not the one last sent to Consul, Consul is populated;
3. pulls and populations run one at a time, so two triggers arriving together are never interleaved.

A population that failed (Consul down, an invalid file) is retried by the next pull, even if nothing changed in the
repository. The outcome of the last pull is shown by `GET /git`. A failure at startup is logged and does not prevent
polling from starting.

##### Response codes

| Endpoint          | Code | Meaning                                                                        |
|-------------------|------|--------------------------------------------------------------------------------|
| `/git/force-pull` | 200  | The pull succeeded                                                             |
| `/git/force-pull` | 500  | The pull or the population failed (see the logs)                               |
| `/git/webhook`    | 202  | The event was accepted and the pull succeeded, or is still running (see below) |
| `/git/webhook`    | 500  | The pull or the population failed: the provider flags the delivery as failed   |
| `/git/webhook`    | 401  | The signature is missing or invalid                                            |
| `/git/webhook`    | 400  | The body is missing or is not valid JSON                                       |
| `/git/webhook`    | 204  | The event (such as GitHub's `ping`) or the branch is ignored on purpose        |
| `/git/webhook`    | 304  | No webhook is configured                                                       |
| `/git/webhook`    | 501  | No payload handler for the configured webhook type                             |

A webhook request waits at most `consul.git.webhook.response-timeout` for its pull, because git providers give up after
about ten seconds and flag the delivery as failed. A pull that takes longer carries on and the request is answered
`202`; its outcome is then shown by `GET /git`. Within the delay a failed pull is answered `500`, so that the provider
can redeliver. Events that are ignored on purpose are answered `204` and not `304`, which providers also show as failed
deliveries.

### Usage

#### Configuring the GitHub webhook

Configure your GitHub repository webhook to:

- **Payload URL**: `http://your-server:8080/git/webhook`
- **Content type**: `application/json`
- **Secret**: Same as `consul.git.webhook.secret`
- **Events**: Just the push event

#### CLI Mode (One-shot)

The CLI has no git mode: it reads files from a directory, and rejects every `--consul.git.*` option ("Unknown options").
For a CI/CD pipeline, check the repository out and import its directory with [Populate by files](#populate-by-files), for
instance:

```shell
git clone --depth 1 --branch main https://github.com/myorg/config-repo.git config-repo
java -jar consul-populate-cli-{version}.jar \
    --consul.host=localhost \
    --consul.port=8500 \
    --consul.kv.prefix=config/frog/my_app \
    --consul.files.format=YAML \
    --consul.files.target=dev \
    --consul.files.root-path=config-repo/configurations
```
