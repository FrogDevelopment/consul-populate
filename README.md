## Consul Populate (WIP)

Concept: give a tool to easily push content in Consul KV to be used as distributed configurations

## Configuration

- `consul.http_address`: Address of the Consul to populate
- `consul.configPath`: The path where the configuration is stores. Default to *config*
- `consul.version`: Version of the current configuration. Will be added to the `configPath`
- `consul.type`: (FILES | GIT) Type of data used to be export into Consul

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
