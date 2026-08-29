import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.googleServices)
}

android {
    namespace = "com.college.library"
    compileSdk = 34

    // Shorten build directory to avoid Windows MAX_PATH (260 chars) issues
    // layout.buildDirectory.set(file("${rootDir.absolutePath}/../build-gdc/${project.name}"))

    defaultConfig {
        applicationId = "com.college.library"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }
        val geminiApiKey: String = localProperties.getProperty("GEMINI_API_KEY") ?: "\"\""
        val formattedApiKey = if (geminiApiKey.startsWith("\"")) geminiApiKey else "\"$geminiApiKey\""
        buildConfigField("String", "GEMINI_API_KEY", formattedApiKey)
    }

    signingConfigs {
        create("release") {
            // Using debug keystore for easy sharing, in a real production app you'd use a real release keystore.
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    splits {
        abi {
            isEnable = false // Ensures a universal APK is built, avoiding the "invalid package" error when sharing standalone APKs
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/MANIFEST.MF"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "org/apache/xmlbeans/xml/stream/**"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("hilt.enableAggregatingTask", "true")
    arg("hilt.correctErrorTypes", "true")
}

hilt {
    enableAggregatingTask = true
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.sqldelight.android)
    implementation(libs.sqldelight.coroutines)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.ui.text.google.fonts)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    implementation(libs.androidx.hilt.navigation.compose)

    // Vico Charts
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)

    // ML Kit Barcode
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.play.services.code.scanner)
    implementation(libs.zxing.core)

    // Accompanist Permissions
    implementation(libs.accompanist.permissions)

    // Biometric Authentication
    implementation(libs.androidx.biometric)

    // WorkManager (background overdue checks + notifications)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    // Excel Import (Apache POI)
    implementation(libs.poi)
    implementation(libs.poi.ooxml) {
        exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
        exclude(group = "stax", module = "stax-api")
    }
    implementation(libs.xmlbeans) {
        exclude(group = "stax", module = "stax-api")
    }

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.guava)

    // Coil for Image Loading (Compose)
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Gemini AI
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Firebase (Auth + Firestore for multi-tenant onboarding & sync)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)


    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit)
}
