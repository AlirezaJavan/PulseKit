plugins {
    alias(libs.plugins.pulsekit.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

kotlin {
    android {
        namespace = "io.github.alirezajavan.pulsekit.location"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":pulsekit-core"))
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core)
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
        name.set("PulseKit Location")
        description.set("GPS/network location DataSource plugin for PulseKit.")
    }
}
