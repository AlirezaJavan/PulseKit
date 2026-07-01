import com.android.build.api.dsl.LibraryExtension
import io.github.alirezajavan.pulsekit.buildlogic.configureKotlinAndroid
import io.github.alirezajavan.pulsekit.buildlogic.configureKotlinMultiplatform
import kotlinx.validation.ApiValidationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KotlinMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.kotlin.multiplatform.library")
                // Every module using this convention is a published library, not the demo `app`
                // -- keeping its public surface intentional matters here in a way it doesn't for
                // an application module, so binary-compatibility-validator applies to all of them.
                apply("org.jetbrains.kotlinx.binary-compatibility-validator")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                configureKotlinMultiplatform(this)
            }

            // klib (native/iOS) ABI validation is opt-in and off by default; the Android target
            // isn't validated by this plugin at all under com.android.kotlin.multiplatform.library
            // (no classfile-based check exists for it here) so that surface still needs manual
            // review, but klib validation catches accidental iOS-visible API breaks for free.
            extensions.configure<ApiValidationExtension> {
                klib {
                    enabled = true
                }
            }
        }
    }
}
