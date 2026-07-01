package io.github.alirezajavan.pulsekit.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal fun Project.configureKotlinMultiplatform(
    extension: KotlinMultiplatformExtension,
) {
    extension.apply {
        (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
            configureKotlinMultiplatformAndroid(this)
        }

        listOf(
            iosArm64(),
            iosSimulatorArm64()
        ).forEach { target ->
            target.binaries.framework {
                baseName = path.substring(1).replace(":", "-")
                isStatic = true
            }
        }

        applyDefaultHierarchyTemplate()

        // Every module here declares expect/actual classes (DataSource implementations,
        // PermissionController); this flag applies to all targets' compilations (metadata,
        // Android, native) at once, unlike the JVM-only tasks.withType<KotlinCompile> hook used
        // for pure-Android modules in configureKotlinAndroid.
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
}
