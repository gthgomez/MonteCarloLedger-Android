pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// Prefer in-repo DesignSystem (CI + standalone clone). Fall back to monorepo sibling.
val inRepoDesign = file("DesignSystem")
val monorepoDesign = file("../DesignSystem")
when {
    inRepoDesign.resolve("build.gradle.kts").isFile -> includeBuild("DesignSystem")
    monorepoDesign.resolve("build.gradle.kts").isFile -> includeBuild("../DesignSystem")
    else -> throw GradleException(
        "DesignSystem composite not found. Expected ./DesignSystem or ../DesignSystem. " +
            "See docs/SHIP_STANDARD.md."
    )
}

rootProject.name = "MonteCarlo-Ledger-app"
include(":app")
