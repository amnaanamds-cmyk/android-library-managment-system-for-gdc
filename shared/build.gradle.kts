plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.multiplatform.settings)
                
                // Firebase KMP
                implementation("dev.gitlive:firebase-firestore:1.13.0")
                implementation("dev.gitlive:firebase-auth:1.13.0")
                implementation("dev.gitlive:firebase-common:1.13.0")
                implementation("dev.gitlive:firebase-app:1.13.0")
                
                // Ktor
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                
                // SQLDelight
                implementation(libs.sqldelight.coroutines)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.sqldelight.android)
                implementation(libs.ktor.client.cio)
            }
        }
    }
}

android {
    namespace = "com.college.library.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("LibraryDatabase") {
            packageName.set("com.college.library.data.db")
        }
        create("AppDatabase") {
            packageName.set("com.college.library.database")
            srcDirs.setFrom("src/commonMain/sqlsync")
        }
    }
}
