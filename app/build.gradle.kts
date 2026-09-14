plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "br.com.bonamassa.driver"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.bonamassa.driver"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        val apiUrl = providers.gradleProperty("bonamassaApiUrl").orElse("").get()
        val storeSlug = providers.gradleProperty("bonamassaStoreSlug").orElse("bonamassa").get()
        require(apiUrl.matches(Regex("[A-Za-z0-9:/._-]*"))) { "bonamassaApiUrl inválida" }
        require(storeSlug.matches(Regex("[a-z0-9-]{1,60}"))) { "bonamassaStoreSlug inválida" }
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
        buildConfigField("String", "STORE_SLUG", "\"$storeSlug\"")
        buildConfigField("boolean", "DEMO_MODE", "false")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        debug { buildConfigField("boolean", "DEMO_MODE", providers.gradleProperty("bonamassaDemo").orElse("false").get().toBoolean().toString()) }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":client"))
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

val verifyReleaseConfiguration by tasks.registering {
    group = "verification"
    description = "Recusa release com HTTP, endpoint inválido ou flags de teste/demo."
    val apiUrl = providers.gradleProperty("bonamassaApiUrl").orElse("")
    val demo = providers.gradleProperty("bonamassaDemo").orElse("false")
    val integration = providers.gradleProperty("bonamassaIntegration").orElse("false")
    inputs.property("apiUrl", apiUrl)
    inputs.property("demo", demo)
    inputs.property("integration", integration)
    doLast {
        val endpoint = runCatching { java.net.URI(apiUrl.get()) }.getOrNull()
        require(endpoint != null && endpoint.scheme == "https" && !endpoint.host.isNullOrBlank() &&
            endpoint.rawUserInfo == null && endpoint.rawQuery == null && endpoint.rawFragment == null &&
            (endpoint.rawPath.isNullOrEmpty() || endpoint.rawPath == "/")) {
            "Release requer uma origem HTTPS válida, sem caminho, credenciais ou parâmetros."
        }
        require(demo.get() == "false" && integration.get() == "false") {
            "Release não permite bonamassaDemo ou bonamassaIntegration."
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(verifyReleaseConfiguration)
}
