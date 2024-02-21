import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    application
    alias(libs.plugins.serialization)
}

group = "org.cryptobiotic"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.eglib)
    implementation(libs.bundles.logging)
    testImplementation(libs.bundles.egtest)
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

////////////////

tasks {
    val ENABLE_PREVIEW = "--enable-preview"
    withType<JavaCompile>() {
        options.compilerArgs.add(ENABLE_PREVIEW)
        // Optionally we can show which preview feature we use.
        options.compilerArgs.add("-Xlint:preview")
        // options.compilerArgs.add("--enable-native-access=org.openjdk.jextract")
        // Explicitly setting compiler option --release
        // is needed when we wouldn't set the
        // sourceCompatiblity and targetCompatibility
        // properties of the Java plugin extension.
        options.release.set(21)
    }
    withType<Test>().all {
        useJUnitPlatform()
        minHeapSize = "512m"
        maxHeapSize = "8g"
        jvmArgs = listOf("-Xss128m", "--enable-preview")

        // Make tests run in parallel
        // More info: https://www.jvt.me/posts/2021/03/11/gradle-speed-parallel/
        systemProperties["junit.jupiter.execution.parallel.enabled"] = "true"
        systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
        systemProperties["junit.jupiter.execution.parallel.mode.classes.default"] = "concurrent"
    }
    withType<JavaExec>().all {
        jvmArgs("--enable-preview")
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }
}
