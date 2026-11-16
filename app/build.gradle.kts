plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cl.jcleaner"
    compileSdk = 34

    defaultConfig {
        applicationId = "cl.jcleaner"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.4"

        // Identificador de la app registrada en Microsoft (para Outlook). Llega desde los secretos de GitHub.
        buildConfigField("String", "MS_CLIENT_ID", "\"${System.getenv("JCLEANER_MS_CLIENT_ID") ?: ""}\"")
        manifestPlaceholders["appAuthRedirectScheme"] = "cl.jcleaner"
    }

    // La clave permanente llega desde los secretos de GitHub (o variables de entorno locales).
    // Sin clave, la versión "release" se firma con la clave de prueba, solo para compilar.
    val rutaClave = System.getenv("JCLEANER_KEYSTORE")
    signingConfigs {
        if (rutaClave != null && file(rutaClave).exists()) {
            create("permanente") {
                storeFile = file(rutaClave)
                storePassword = System.getenv("JCLEANER_KEYSTORE_PASSWORD")
                keyAlias = "jcleaner"
                keyPassword = System.getenv("JCLEANER_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("permanente") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE.md", "META-INF/NOTICE.md", "META-INF/LICENSE", "META-INF/NOTICE", "META-INF/LICENSE.txt", "META-INF/NOTICE.txt")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Correo: IMAP (JavaMail para Android) e inicio de sesión de Microsoft (AppAuth)
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    implementation("net.openid:appauth:0.11.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
}
