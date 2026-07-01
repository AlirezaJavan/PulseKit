plugins {
    alias(libs.plugins.pulsekit.kotlin.multiplatform)
    alias(libs.plugins.pulsekit.kotlin.multiplatform.compose)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

kotlin {
    android {
        namespace = "io.github.alirezajavan.pulsekit.ui"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":pulsekit-core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.core)
            implementation(libs.androidx.activity)
        }
        androidHostTest.dependencies {
            implementation(libs.robolectric)
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(kotlin("test"))
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
        name.set("PulseKit UI")
        description.set(
            "Android infrastructure (Services, Receivers) and Jetpack Compose components " +
                "for PulseKit.",
        )
    }
}
