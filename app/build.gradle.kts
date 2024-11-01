plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.nahian.filetransperftp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nahian.filetransperftp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes.add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
            excludes.add("META-INF/*.kotlin_module")
            excludes.add("META-INF/*.pro")
            excludes.add("META-INF/*.version")
            excludes.add("META-INF/*")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.barcode.scanning.common)
    implementation(libs.play.services.mlkit.barcode.scanning)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation (libs.commons.net.commons.net)
    implementation (libs.ktor.server.core)
    implementation (libs.ktor.server.netty)
    implementation (libs.ktor.server.html.builder)
    implementation (libs.ktor.server.call.logging)
    implementation (libs.ktor.server.auth)
    implementation (libs.ktor.server.sessions)
    implementation (libs.ktor.server.content.negotiation)
    implementation (libs.ktor.serialization.kotlinx.json)
    implementation (libs.zxing.android.embedded)
    implementation (libs.okhttp)
    implementation (libs.logging.interceptor)
    implementation (libs.ktor.network.tls.certificates)
    implementation (libs.bcpkix.jdk18on)
    implementation (libs.bcprov.jdk18on)
    implementation (libs.core)
    implementation (libs.zxing.android.embedded)
    implementation (libs.ktor.server.websockets)

    implementation(platform(libs.http4k.bom))
    implementation(libs.http4k.core)
    implementation (libs.http4k.client.okhttp)
    implementation (libs.http4k.server.netty)
    implementation(libs.nanohttpd)
    implementation (libs.java.websocket.java.websocket)
//    implementation(libs.okhttp)
}