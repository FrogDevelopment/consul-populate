plugins {
    java
    id("org.ajoberstar.grgit") version "5.2.2"
}

group = "com.frog-development.consul-populate"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
    targetCompatibility = JavaVersion.toVersion("21")
}

afterEvaluate {
    computeProjectVersion()
}

fun computeProjectVersion() {
    val branchName = grgit.branch.current().name

    println("Current branch: $branchName")

    val computedVersion = when (branchName) {
        "HEAD" -> handleHead()
        "main" -> handleMain()
        else -> handleBranch(branchName)
    }

    allprojects {
        group = rootProject.group
        version = computedVersion
    }

    println("Computed version: $version")
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
