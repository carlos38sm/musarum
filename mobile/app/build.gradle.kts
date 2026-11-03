import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val musarumBaseUrl: String =
    (localProps.getProperty("musarum.baseUrl")?.takeIf { it.isNotBlank() }
        ?: System.getenv("MUSARUM_BASE_URL")?.takeIf { it.isNotBlank() }
        ?: "http://10.0.2.2:8080/api/v1/")

// El workflow de GitHub Actions inyecta MUSARUM_VERSION_NAME derivado del tag
// (android-v1.2.3 → "1.2.3"). En local se queda en el fallback.
val musarumVersionName: String =
    System.getenv("MUSARUM_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0.0"

// versionCode derivado del versionName: "1.2.3" → 1*10000 + 2*100 + 3 = 10203.
// Garantiza monotonía siempre que el versionName suba, que es lo que Android pide.
val musarumVersionCode: Int = run {
    val parts = musarumVersionName.split(".").mapNotNull { it.toIntOrNull() }
    val code = when (parts.size) {
        3 -> parts[0] * 10000 + parts[1] * 100 + parts[2]
        2 -> parts[0] * 10000 + parts[1] * 100
        1 -> parts[0] * 10000
        else -> 1
    }
    code.coerceAtLeast(1)
}

android {
    namespace = "com.musarum.mobile"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.musarum.mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = musarumVersionCode
        versionName = musarumVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MUSARUM_BASE_URL", "\"$musarumBaseUrl\"")
    }

    // Firma de release. En local lee de keystore.properties (fuera de git);
    // en CI (GitHub Actions) lee de env vars que el workflow decodifica a
    // partir de los secrets. Si no hay keystore, el task :assembleRelease
    // falla con un mensaje claro — nunca firmamos con la debug key.
    signingConfigs {
        val keystoreFile = rootProject.file("keystore.properties")
        val fromEnv = System.getenv("MUSARUM_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
        if (keystoreFile.exists() || fromEnv != null) {
            create("release") {
                if (keystoreFile.exists()) {
                    val props = Properties().apply {
                        keystoreFile.inputStream().use { load(it) }
                    }
                    storeFile = rootProject.file(props.getProperty("storeFile"))
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                } else {
                    storeFile = file(fromEnv!!)
                    storePassword = System.getenv("MUSARUM_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("MUSARUM_KEY_ALIAS")
                    keyPassword = System.getenv("MUSARUM_KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Solo asignamos si existe el config — así los dev que no tengan
            // keystore pueden seguir compilando debug sin pelearse con esto.
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.session)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.palette.ktx)
    // Reorderable LazyColumn (drag-and-drop) para reordenar canciones en
    // playlists. Librería ligera FOSS mantenida activamente, cero deps extra.
    implementation("sh.calvin.reorderable:reorderable:2.5.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
