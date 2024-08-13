import org.jreleaser.model.Active
import org.jreleaser.model.Signing.Mode

plugins {
    java
    id("org.ajoberstar.grgit") version "5.2.2"
    id("org.jreleaser")
}

description = "Give a tool to easily push content in Consul KV to be used as distributed configurations"

val versionProvider = Wrapper(computeProjectVersion())
allprojects {
    group = "com.frog-development.consul-populate"
    version = versionProvider
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

/**
 * temporary workaround waiting for project.version to be provider aware.
 * <ul>
 * <li><a href="https://github.com/gradle/gradle/issues/25971">workaround</a></li>
 * <li><a href="https://github.com/gradle/gradle/issues/13672">Project "coordinates" should use providers</a></li>
 * </ul>
 */
class Wrapper(private val version: Provider<String>) {
    override fun toString(): String {
        return version.get()
    }
}

fun computeProjectVersion(): Provider<String> {
    val branchName = grgit.branch.current().name

    println("Current branch: $branchName")

    val computedVersion = when (branchName) {
        "HEAD" -> handleHead()
        "main" -> handleMain()
        else -> handleBranch(branchName)
    }

    println("Computed version: $computedVersion")

    return provider { computedVersion }
}

fun handleHead(): String {
    val githubRefName = System.getenv("GITHUB_REF_NAME")
    if (githubRefName == null || githubRefName.isEmpty()) {
        throw GradleException("One does not simply build from HEAD. Checkout to matching local branch !!")
    }
    return githubRefName
}

fun handleMain(): String {
    return "main-SNAPSHOT"
}

fun handleBranch(branchName: String): String {
    val matchBranchResult = """^(?<type>\w+)/(?<details>.+)?$""".toRegex().find(branchName)
    val branchType = matchBranchResult!!.groups["type"]?.value!!
    val branchDetails = matchBranchResult.groups["details"]?.value!!

    if (branchType == "release" || branchType == "hotfix") {
        return "$branchDetails-SNAPSHOT"
    }

    return "$branchType-$branchDetails-SNAPSHOT"
}

fun isReleaseVersion(): Boolean {
    return """^\d+\.\d+\.\d+$""".toRegex().matches(version.toString())
}

jreleaser {
    gitRootSearch = true
    dependsOnAssemble = true
    dryrun = provider { !isReleaseVersion() }

    project {
        copyright.set("FrogDevelopment")
    }

    signing {
        active = Active.ALWAYS
        armored = true
        verify = false
        mode = Mode.MEMORY
    }

    deploy {
        maven {
            mavenCentral {
                childProjects.forEach { project ->
                    create(project.key) {
                        active = Active.ALWAYS
                        url = "https://central.sonatype.com/api/v1/publisher"
                        applyMavenCentralRules = true
                        stagingRepository(project.value.layout.buildDirectory.dir("staging-deploy").get().toString())
                        snapshotSupported = true
                    }
                }
            }
        }
    }
}

tasks {
    jreleaserDeploy {
        dependsOn(jreleaserSign)
    }

    jreleaserSign {
        childProjects.forEach { child -> dependsOn(child.value.tasks.named("publish")) }
    }

}
