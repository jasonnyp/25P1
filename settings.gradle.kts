pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Add this line for JitPack
        maven(url = "https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Add this line for JitPack
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "ENHANCe"
include(":app")