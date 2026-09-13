// Share the Android and Kotlin plugin classpath; versions live in settings.gradle.kts.
// apply false defers Android configuration to app, which -PcoreOnly does not include.
plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}
