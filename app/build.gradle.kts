plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.chaquo.python")
}

android {
    namespace = "com.Libertygi.dalo"
    compileSdk = 36
    signingConfigs {
        create("release") {
            // 경로 설정 시 file() 함수 사용
            storeFile = file("C:/dev/may-new-project/my-release-key.jks")

            // 환경 변수 읽기 (System.getenv 사용)
            storePassword = System.getenv("MY_KEY_STORE_PASSWORD")
            keyAlias = System.getenv("MY_KEY_ALIAS")
            keyPassword = System.getenv("MY_KEY_PASSWORD")
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    defaultConfig {
        applicationId = "com.Libertygi.dalo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 🔥 Chaquopy 필수 설정
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }


    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")

            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"

        pip {
            install("yt-dlp")
        }
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}