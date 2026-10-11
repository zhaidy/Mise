plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // 读取 app/google-services.json（CI 从 Secrets 写入，不进仓库）
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.personal.mise"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.personal.mise"
        minSdk = 26
        targetSdk = 36
        // CI 每次构建递增，应用内更新靠它判断有没有新版本
        val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = buildNumber
        // 显示用的版本号带上 build 号，和 Release 的「Mise build N」对得上
        versionName = "0.3.$buildNumber"
    }

    // 固定签名：每次构建签名一致，新 APK 才能直接覆盖安装（数据保留）。
    // 密钥来自 CI 的 Secrets；本地没配置时退回 Android 默认调试签名。
    val keystorePath = System.getenv("MISE_KEYSTORE_FILE")
    if (keystorePath != null) {
        signingConfigs {
            create("mise") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("MISE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MISE_KEY_ALIAS")
                keyPassword = System.getenv("MISE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("mise")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("mise") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    compileOnly(libs.jspecify)
    implementation(libs.coil.compose)
    implementation(libs.reorderable)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testCompileOnly(libs.jspecify)
}
