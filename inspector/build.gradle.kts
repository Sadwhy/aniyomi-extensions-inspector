import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask
import java.io.BufferedReader

plugins {
    application
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.shadow)
}

dependencies {
    // Dependencies of Aniyomi, some are duplicate from root build.gradle.kts
    // keeping it here for reference
    implementation(libs.injekt.core)
    implementation(libs.jsoup)
    implementation(libs.rxjava)
    implementation(libs.bundles.okhttp)

    // AndroidCompat
    implementation(project(":AndroidCompat"))
    implementation(project(":AndroidCompat:Config"))
}

val MainClass = "inspector.MainKt"
application {
    mainClass.set(MainClass)
}

sourceSets {
    main {
        resources {
            srcDir("src/main/resources")
        }
    }
}

// should be bumped with each stable release
val inspectorVersion = "v1.0.0"

// counts commit count on master
val inspectorRevision = runCatching {
    System.getenv("ProductRevision") ?: Runtime
        .getRuntime()
        .exec("git rev-list HEAD --count")
        .let { process ->
            process.waitFor()
            val output = process.inputStream.use {
                it.bufferedReader().use(BufferedReader::readText)
            }
            process.destroy()
            "r" + output.trim()
        }
}.getOrDefault("r0")

val String.wrapped get() = """"$this""""

buildConfig {
    className("BuildConfig")
    packageName("inspector")

    useKotlinOutput()

    buildConfigField("String", "NAME", rootProject.name.wrapped)
    buildConfigField("String", "VERSION", inspectorVersion.wrapped)
    buildConfigField("String", "REVISION", inspectorRevision.wrapped)
}

tasks {
    shadowJar {
        dependencies {
            // Useless icu-related files
            exclude("com/ibm/icu/impl/data/icudt*/*/*")
        }
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to MainClass,
                    "Implementation-Title" to rootProject.name,
                    "Implementation-Vendor" to "The Tachiyomi Open Source Project",
                    "Specification-Version" to inspectorVersion,
                    "Implementation-Version" to inspectorRevision,
                ),
            )
        }
        archiveBaseName.set(rootProject.name)
        archiveVersion.set(inspectorVersion)
        archiveClassifier.set(inspectorRevision)
    }

    withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
                "-opt-in=kotlin.io.path.ExperimentalPathApi",
            )
        }
    }

    withType<ShadowJar> {
        destinationDirectory.set(File("$rootDir/inspector/build"))
        dependsOn("formatKotlin", "lintKotlin")
    }

    named("run") {
        dependsOn("formatKotlin", "lintKotlin")
    }

    withType<LintTask> {
        exclude("**/BuildConfig.kt")
        source(files("src/kotlin"))
    }

    withType<FormatTask> {
        exclude("**/BuildConfig.kt")
        source(files("src/kotlin"))
    }

    withType<ProcessResources> {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}
