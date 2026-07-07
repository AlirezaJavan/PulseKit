import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.pulsekit.android.application)
    alias(libs.plugins.pulsekit.android.application.compose)
}

extensions.configure<ApplicationExtension> {
    namespace = "io.github.alirezajavan.pulsekit"

    defaultConfig {
        applicationId = "io.github.alirezajavan.pulsekit"
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":pulsekit-core"))
    implementation(project(":pulsekit-location"))
    implementation(project(":pulsekit-motion"))
    implementation(project(":pulsekit-bluetooth"))
    implementation(project(":pulsekit-sync"))
    implementation(project(":pulsekit-ui"))
    implementation(libs.ktor.client.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
