import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import com.android.build.gradle.BaseExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        // Shitpack repo which contains our tools and dependencies
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.4.0")
        // Cloudstream gradle plugin which makes everything work and builds plugins
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}


fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()
fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // compatible with other git hosting services, like gitlab, gitDab, codeBerg
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/phisher98/Phisher98")
    }

    android {

        defaultConfig {
            minSdk = 26
            compileSdkVersion(33)
            //noinspection OldTargetApi
            targetSdk = 33
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
    }

    dependencies {
        val apk by configurations
        val implementation by configurations

        // Stubs for all Cloudstream classes
        //noinspection GradleDeprecatedConfiguration
        apk("com.lagradost:cloudstream3:pre-release")
        implementation(kotlin("stdlib")) // adds standard kotlin features, like listOf, mapOf etc
        implementation("com.github.Blatzar:NiceHttp:0.4.11") // http library
        implementation("org.jsoup:jsoup:1.18.1") // html parser
        //noinspection GradleDependency
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
        implementation("com.faendir.rhino:rhino-android:1.6.0")
        implementation("me.xdrop:fuzzywuzzy:1.4.0")
        implementation("com.google.code.gson:gson:2.11.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
