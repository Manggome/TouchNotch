import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---- 버전: version.properties + CI 빌드 번호 ----
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val baseVersionName: String = versionProps.getProperty("baseVersionName")
val baseVersionCode: Int = versionProps.getProperty("baseVersionCode").toInt()
// CI 는 커밋 수를 넘겨준다 (같은 커밋이면 항상 같은 값 → 재실행해도 같은 버전)
val buildNumber: Int = (System.getenv("TN_BUILD_NUMBER") ?: System.getenv("GITHUB_RUN_NUMBER"))
    ?.toIntOrNull() ?: 0
val fullVersionName = "$baseVersionName.$buildNumber"

// ---- 서명 ----
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "kr.manggome.touchnotch"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.manggome.touchnotch"
        minSdk = 30
        targetSdk = 35
        versionCode = baseVersionCode * 1000 + buildNumber
        versionName = fullVersionName
        buildConfigField("String", "GITHUB_REPO", "\"Manggome/TouchNotch\"")
    }

    signingConfigs {
        create("release") {
            val ksPath = keystoreProps.getProperty("storeFile")
            if (ksPath != null && rootProject.file(ksPath).exists()) {
                storeFile = rootProject.file(ksPath)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
    lint {
        // 릴리스 빌드에서 lint 를 강제하지 않는다 (CI 에서 별도로 확인)
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
}
