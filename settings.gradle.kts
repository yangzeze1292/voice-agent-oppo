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
        // OPPO 开放平台 Maven 仓库（小布 SDK / ColorOS 能力）
        maven { url = uri("https://maven.heytapmuseum.com/repository/OPPO-open") }
    }
}

rootProject.name = "VoiceAgent"
include(":app")
