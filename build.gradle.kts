plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// 本机 Clash 代理对 services.gradle.org 间歇不通（实测同一 URL 时 200 时 000），
// 该探测失败会让 wrapper 任务整体报错，但生成的 jar/脚本/属性文件本身有效。
// 网络恢复后首次执行 gradlew 会自行下载 distribution。
tasks.wrapper {
    gradleVersion = "8.14.3"
    distributionType = Wrapper.DistributionType.BIN
    validateDistributionUrl = false
}
