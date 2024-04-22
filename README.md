## Consul Populate (WIP)

Concept: give a tool to easily push content in Consul KV to be used as distributed configurations

## Configuration

- `consul.host`: Consul host. Defaults to *localhost* (Required)
- `consul.port`: Consul port. Defaults to *8500* (Required)
- `consul.is_secured`: Set whether Consul is secured. Defaults to *false* (Optional)
- `consul.acl_token`: ACL token needed to read and write in KV path. When present, will be added to requests
  using `?token` query parameter (Optional)
- `consul.dc`: Consul datacenter name. When present, will be added to requests using `?dc` query parameter (Optional)
- `consul.timeout`: Amount of time (in milliseconds) for requests (Optional)
- `consul.config_path_prefix`: The path where the configuration is stores. Will be used to generate the `configPath`.
  Defaults to *config* (Optional)
- `consul.configVersion`: Version of the current configuration. When present, will be used to generate
  the `configPath` (Optional)
- `consul.type`: (FILES | GIT) Type of data used to be export into Consul (Required)

### About the KV name format

Read [Micronaut#HashiCorp Consul Support](https://docs.micronaut.io/4.3.14/guide/#distributedConfigurationConsul)

Note that the format of the KV in case of profile/environment is `{file_name},{profile}`

## Populate by files

Parameters:

- `consul.files.rootPath`: path to root directory
- `consul.files.target`: subdirectory used to override root configurations
- `consul.files.format`: (YAML | JSON | PROPERTIES) Supported format of the files

### Usage

Using consul-populate-cli

```shell

```

### Directories structure

you will need to have:

- all bases configurations in the root of the configurations directory
- 1 subdirectory by environment, with **only** the configurations to override wit environment related values (usually
  url, credentials, ...)

#### Example:

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

#### Note:

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

## Populate by git

**not yet implemented**
Alternative to [git2consul](https://github.com/breser/git2consul) which is not maintained anymore.
