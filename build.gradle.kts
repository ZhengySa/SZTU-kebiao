// AGP 9 起 Kotlin 编译内置，编译器版本由这里解析到的 KGP / KSP 决定。
// 默认内置的 KGP 版本较旧（读不了用 Kotlin 2.4 编译的 AndroidX 库），
// 所以显式提升到 2.3.21，并与 KSP 2.3.12 配套。
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
