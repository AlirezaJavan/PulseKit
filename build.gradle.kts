plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.ktlint) apply false
}

// Applied at the root rather than via a build-logic convention plugin: ktlint is a pure
// formatting/style check with no KMP-target-specific configuration, unlike the other convention
// plugins here which each wire up real target/toolchain setup. build-logic is a separate included
// build (settings.gradle.kts), not a subproject here, so it's untouched by this block.
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        // SQLDelight generates Kotlin into build/ as part of each KMP source set; it's not
        // hand-written code and shouldn't be linted/reformatted.
        filter {
            exclude("**/build/**")
        }
    }
}
