import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "io.github.alirezajavan.pulsekit.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    implementation(libs.sqldelight.gradlePlugin)
    implementation(libs.binary.compatibility.validator.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "pulsekit.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "pulsekit.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "pulsekit.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("kotlinMultiplatform") {
            id = "pulsekit.kotlin.multiplatform"
            implementationClass = "KotlinMultiplatformConventionPlugin"
        }
        register("kotlinMultiplatformCompose") {
            id = "pulsekit.kotlin.multiplatform.compose"
            implementationClass = "KotlinMultiplatformComposeConventionPlugin"
        }
        register("kotlinMultiplatformSqlDelight") {
            id = "pulsekit.kotlin.multiplatform.sqldelight"
            implementationClass = "KotlinMultiplatformSqlDelightConventionPlugin"
        }
    }
}
