pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io/") }
        // Mozilla GeckoView（内嵌 Firefox/Gecko 内核，用于 Cloudflare 验证页）
        maven { url = uri("https://maven.mozilla.org/maven2/") }
    }
}
rootProject.name = "Han1meViewer"
include(":app")
