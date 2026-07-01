import app.cash.sqldelight.gradle.SqlDelightExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class KotlinMultiplatformSqlDelightConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("app.cash.sqldelight")

            extensions.configure<SqlDelightExtension> {
                databases.create("PulseKitDatabase") {
                    packageName.set("io.github.alirezajavan.pulsekit.core.db")
                }
            }
        }
    }
}
