import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.freepdf.manager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.freepdf.manager"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getOrNull("debug") ?: signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
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
        viewBinding = true
    }
    packaging {
        resources.excludes += listOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt",
            "META-INF/LICENSE.md",
            "META-INF/NOTICE.md",
            "META-INF/DEPENDENCIES.txt",
            "META-INF/notice.txt",
            "META-INF/license.txt",
            "META-INF/INDEX.LIST",
            "META-INF/maven",
            "META-INF/*.rsa",
            "META-INF/*.dsa",
            "META-INF/*.sf",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
            "META-INF/*.EC",
            "META-INF/*.ec",
            "META-INF/*.dsa",
            "META-INF/*.rsa",
            "META-INF/*.sf",
            "debug/*",
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            "OSGI-INF/**",
            "module-info.class",
            "META-INF/versions/*/module-info.class",
            "**/module-info.class",
            "common/**",
            "kotlin/**",
            "annotations/**",
            "META-INF/kotlin/**"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.glide)
    kapt(libs.glide.compiler)

    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.text.recognition)

    // PDF engine (create / merge / split / compress / AES lock / watermark / page numbers)
    implementation(libs.pdfbox.android)

    // Offline document conversion: docx / pptx / xlsx -> PDF
    implementation(libs.poi.ooxml)

    implementation(libs.exifinterface)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

kapt {
    correctErrorTypes = true
}
