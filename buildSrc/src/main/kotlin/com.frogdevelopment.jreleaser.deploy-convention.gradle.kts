import org.jreleaser.model.Active
import org.jreleaser.model.Signing.Mode

plugins {
    id("org.jreleaser")
}

val releaseRegex = """^\d+\.\d+\.\d+$""".toRegex()

jreleaser {
    gitRootSearch = true
    dependsOnAssemble = true
    dryrun = provider { !releaseRegex.matches(version.toString()) }

    project {
        copyright.set("FrogDevelopment")
    }

    signing {
        active = Active.ALWAYS
        armored = true
        verify = false
        mode = Mode.MEMORY
        files = false
        artifacts = true
    }

    deploy {
        maven {
            mavenCentral {
                create(name) {
                    active = Active.ALWAYS
                    url = "https://central.sonatype.com/api/v1/publisher"
                    applyMavenCentralRules = true
                    snapshotSupported = true
                    childProjects.forEach { stagingRepository(getStagingRepository(it.value)) }
                }
            }
        }
    }
}

fun getStagingRepository(project: Project): String {
    return project.layout.buildDirectory.dir("staging-deploy").get().toString()
}

tasks {
    jreleaserDeploy {
        dependsOn(jreleaserSign)
    }

    jreleaserSign {
        childProjects.forEach { child -> dependsOn(child.value.tasks.named("publish")) }
    }
}
