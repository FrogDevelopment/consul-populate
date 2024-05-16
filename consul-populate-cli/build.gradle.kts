plugins {
    id("io.micronaut.minimal.application") version "4.3.8"
    id("com.frogdevelopment.publish-conventions")
    alias(libs.plugins.jib)
    alias(libs.plugins.shadow)
}

micronaut {
    runtime("none")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.frogdevelopment.*")
    }
}

dependencies {
    annotationProcessor(mn.lombok)
    annotationProcessor(mn.picocli.codegen)

    implementation(mn.picocli.asProvider())
    implementation(mn.micronaut.picocli)
    implementation(projects.consulPopulateCore)

    compileOnly(mn.lombok)

    runtimeOnly(mn.logback.classic)
    runtimeOnly(mn.snakeyaml)

    testImplementation(mn.assertj.core)
    testImplementation(mn.mockito.junit.jupiter)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.consul)
    testImplementation(libs.vertx.consul)
    testImplementation(libs.systemlambda)
}

application {
    mainClass.set("com.frogdevelopment.consul.populate.ConsulPopulateCommand")
}

publishing {
    publications {
        named<MavenPublication>("mavenJava") {
            shadow.component(this)

            pom {
                name = "Consul Populate - CLI"
                description = "CLI executable for Consul Populate"
            }
        }
    }
}

tasks {
    jar {
        enabled = false
    }

    shadowJar {
//        minimize() waiting https://github.com/johnrengelman/shadow/pull/876
    }

    test {
        // https://github.com/stefanbirkner/system-lambda/issues/27
        systemProperty("java.security.manager", "allow")

        // https://junit-pioneer.org/docs/environment-variables/#warnings-for-reflective-access
//        jvmArgs = listOf("--add-opens java.base/java.util=ALL-UNNAMED","--add-opens java.base/java.lang=ALL-UNNAMED")

//        environment.put("CONSUL_HOST", "qwert")
//        environment.put("CONSUL_PORT", 12345)
//        environment.put("CONSUL_SECURED", true)
//        environment.put("CONSUL_KV_PREFIX", "frog")
//        environment.put("CONSUL_KV_VERSION", "1.2.3")
//        environment.put("CONSUL_FILES_FORMAT", "YAML")
//        environment.put("CONSUL_FILES_TARGET", "stg")
//        environment.put("CONSUL_FILES_ROOT_PATH", "somewhere")
    }

    jib {

        from {
            image = "eclipse-temurin:21-jre-alpine"
            platforms {
                platform {
                    architecture = "amd64"
                    os = "linux"
                }
                platform {
                    architecture = "arm64"
                    os = "linux"
                }
            }
        }

        to {
            image = "frogdevelopment/consul-populate:${rootProject.version}"
            if (System.getenv("CI") == "true") {
                auth {
                    username = System.getenv("DOCKER_USR")
                    password = System.getenv("DOCKER_PSW")
                }
            }
        }

        container {
            setMainClass(application.mainClass)
        }

    }
}
