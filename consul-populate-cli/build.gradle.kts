plugins {
    id("io.micronaut.minimal.application") version "4.3.7"
    alias(libs.plugins.jib)
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
    implementation(projects.consulPopulateApi)

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

tasks {
    test {
        // https://github.com/stefanbirkner/system-lambda/issues/27
        systemProperty("java.security.manager", "allow")

        // #https://junit-pioneer.org/docs/environment-variables/#warnings-for-reflective-access
//        jvmArgs = listOf("--add-opens java.base/java.util=ALL-UNNAMED","--add-opens java.base/java.lang=ALL-UNNAMED")

//        environment.put("CONSUL_HOST", "qwert")
//        environment.put("CONSUL_PORT", 12345)
//        environment.put("CONSUL_SECURED", true)
//        environment.put("CONSUL_CONFIG_VERSION", "toto")
//        environment.put("CONSUL_TYPE", "FILES")
//        environment.put("CONSUL_FILES_FORMAT", "YAML")
//        environment.put("CONSUL_FILES_TARGET", "stg")
//        environment.put("CONSUL_FILES_ROOT_PATH", "somewhere")
    }

    jib {

        from {
            image = "eclipse-temurin:21-jre-jammy"
        }

        to {
            image = "frogdevelopment/consul-populate:${rootProject.version}"
        }

        container {
            setMainClass(application.mainClass)
        }

    }
}
