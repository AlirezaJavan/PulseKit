plugins {
    alias(libs.plugins.pulsekit.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

kotlin {
    android {
        namespace = "io.github.alirezajavan.pulsekit.geofence"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":pulsekit-core"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
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
        name.set("PulseKit Geofence")
        description.set("Pure logic geofencing trigger plugin for PulseKit.")
    }
}
