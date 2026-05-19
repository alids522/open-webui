pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack for any additional dependencies
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "AdoetzGPTFlash"
include(":app")
