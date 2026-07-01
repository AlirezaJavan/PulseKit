plugins {
    alias(libs.plugins.pulsekit.kotlin.multiplatform)
    alias(libs.plugins.pulsekit.kotlin.multiplatform.sqldelight)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

kotlin {
    android {
        namespace = "io.github.alirezajavan.pulsekit.core"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidHostTest.dependencies {
            // Robolectric gives SensorEventStore/TrackingEngine tests a real (in-memory)
            // AndroidSqliteDriver instead of needing a device/emulator -- SQLDelight's Android
            // driver wraps android.database.sqlite, which throws without either a real device or
            // Robolectric's shadow.
            implementation(libs.robolectric)
            implementation(libs.junit)
        }
    }
}

mavenPublishing {
    configure(
        com.vanniktech.maven.publish.KotlinMultiplatform(
            javadocJar = com.vanniktech.maven.publish.JavadocJar.Dokka("dokkaGenerateHtml"),
            sourcesJar = true,
        ),
    )
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    pom {
        name.set("PulseKit Core")
        description.set(
            "Core tracking engine, SQLDelight-backed batched persistence, and cross-platform " +
                "permission abstraction for PulseKit.",
        )
    }
}
