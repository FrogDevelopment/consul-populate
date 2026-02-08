plugins {
    id("io.micronaut.minimal.application") version "4.5.3"
//    alias(libs.plugins.jib)
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.frogdevelopment.*")
    }
}

dependencies {
    annotationProcessor(mn.lombok)

    implementation(projects.consulPopulateGit)
    implementation(mn.micronaut.management)

    compileOnly(mn.lombok)

    runtimeOnly(mn.logback.classic)
    runtimeOnly(mn.snakeyaml)

    testImplementation(mn.assertj.core)
}

application {
    mainClass.set("com.frogdevelopment.consul.populate.ConsulPopulateApplication")
}

//jib {
//    from {
//        image = "eclipse-temurin:21-jre-alpine"
//        platforms {
//            platform {
//                architecture = "amd64"
//                os = "linux"
//            }
//            platform {
//                architecture = "arm64"
//                os = "linux"
//            }
//        }
//    }
//
//    to {
//        image = "frogdevelopment/${name}:${rootProject.version}"
//        if (System.getenv("CI") == "true") {
//            auth {
//                username = System.getenv("DOCKER_USR")
//                password = System.getenv("DOCKER_PSW")
//            }
//        }
//    }
//
//    container {
//        setMainClass(application.mainClass)
//
//        jvmFlags = listOf("-Xmx128m")
//        volumes = listOf("/tmp")
//        creationTime = "USE_CURRENT_TIMESTAMP"
//        labels.put("frog.image_base", jib.from.image.toString())
//    }
//}
