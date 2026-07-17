// Root settings: declares all modules and shared repositories.
@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // MapLibre Native releases live in Maven Central; kept explicit for clarity.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "twocircle"

// Module tree — kept flat for IDE ergonomics; group prefixes are naming only.
include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:domain")
include(":core:data")
include(":feature:map")
include(":feature:search")
include(":feature:routing")
include(":feature:tracking")
include(":feature:tracks")
include(":feature:regions")
