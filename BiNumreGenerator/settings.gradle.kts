pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // Compose (brugerfladen i desktop/) bruger androidx-biblioteker herfra
        google()
    }
}

rootProject.name = "BiNumreGenerator"
include(":core", ":desktop")
