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
- `consul.version`: Version of the current configuration. Will be used to generate the `configPath` (Required)
- `consul.type`: (FILES | GIT) Type of data used to be export into Consul (Required)

### About the KV name format

Read [Micronaut#HashiCorp Consul Support](https://docs.micronaut.io/4.3.14/guide/#distributedConfigurationConsul)

Note that the format of the KV in case of profile/environment is `{file_name},{profile}`

## Populate by files

Parameters:

- `consul.files.rootPath`: path to root directory
- `consul.files.target`: subdirectory used to override root configurations
- `consul.files.format`: (YAML | JSON | PROPERTIES) Supported format of the files

## Populate by git

**not yet implemented**
Alternative to [git2consul](https://github.com/breser/git2consul) which is not maintained anymore.
