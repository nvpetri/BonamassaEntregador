import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins { id("org.jetbrains.kotlin.jvm") }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Android supplies org.json. JVM tests use the matching public implementation.
    compileOnly("org.json:json:20240303")
    testImplementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
