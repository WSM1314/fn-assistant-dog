pluginManagement {
    repositories {
        // 本机实测（Clash 代理对 maven 主机间歇掐 TLS 握手，而国内镜像直连 0.04–0.3 秒）：
        // 镜像优先，repo1 / google / mavenCentral 作为兜底。
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
    }
}

rootProject.name = "fn-assistant-dog"
include(":app")
include(":core")
