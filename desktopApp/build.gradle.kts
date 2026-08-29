plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.materialIconsExtended)
                implementation(project(":shared"))
                
                implementation(libs.multiplatform.settings)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation(libs.sqldelight.sqlite)
                implementation(libs.sqldelight.coroutines)
                implementation(libs.kotlinx.datetime)
                
                // Ktor for SyncService
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                
                // JNA for Windows DPAPI
                implementation("net.java.dev.jna:jna:5.14.0")
                implementation("net.java.dev.jna:jna-platform:5.14.0")
                // ZXing for QR code generation (college linking)
                implementation(libs.zxing.core)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.college.library.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "GDC_Library50"
            packageVersion = "1.0.0"
            
            // Placeholder for custom .ico file. Just place an icon.ico inside src/jvmMain/resources/
            windows {
                iconFile.set(project.file("src/jvmMain/resources/icon.ico"))
            }
        }
    }
}
