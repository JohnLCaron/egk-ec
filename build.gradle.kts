import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    application
    alias(libs.plugins.serialization)
}

group = "org.cryptobiotic"
version = "2.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("libs/verificatum-vecj-2.2.0.jar"))
    implementation(libs.bundles.eglib)
    implementation(libs.bundles.logging)
    testImplementation(libs.bundles.egtest)
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
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
        options.release.set(17)
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
        kotlinOptions.jvmTarget = "17"
    }
}

tasks.register<Jar>("uberJar") {
    archiveClassifier = "uber"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

/*
tasks.register("fatJar", Jar::class.java) {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName = "egkec"

    manifest {
        attributes("Main-Class" to "org.cryptobiotic.eg.cli.RunShowSystem")
    }
    from(configurations.runtimeClasspath.get()
        .onEach { println("add from runtimeClasspath: ${it.name}") }
        .map { if (it.isDirectory) it else zipTree(it) })
    val sourcesMain: SourceSet = sourceSets.main.get()
    from(sourcesMain.output)

    val sourcesTest: SourceSet = sourceSets.test.get()
    from(sourcesTest) {
        include("resources/*.xml")
    }
}

 */
