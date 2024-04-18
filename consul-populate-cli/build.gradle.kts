plugins {
    id("io.micronaut.minimal.application") version "4.3.6"
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
}

application {
    mainClass.set("com.frogdevelopment.consul.populate.ConsulPopulateCommand")
}

tasks {
    jib {

        from {
            image = "eclipse-temurin:21-jre-jammy"
        }

        to {
            image = "frogdevelopment/consul-populate:latest"
        }

        container {
            setMainClass(application.mainClass)
        }

    }
}
