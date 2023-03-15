import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.licenser)
}

val jvmVersion = 19

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmVersion))
    }
}

application {
    mainClass.set("io.mcdev.deduper.Main")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.serialization) {
        excludeKotlin()
    }

    implementation(libs.bundles.ktor) {
        excludeKotlin()
    }

    implementation(libs.bundles.logging)

    implementation(libs.typesafe.config)
    implementation(libs.kotlin.serialization.hocon) {
        excludeKotlin()
    }

    implementation(libs.postgres)
    implementation(libs.hikari)

    implementation(platform(libs.jdbi.bom))
    implementation(libs.bundles.jdbi)
    implementation(libs.jdbi.postgres)
    implementation(libs.bundles.jdbi.kotlin)

    implementation(libs.github)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.bundles.jjwt)

    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)

    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.junit)
}

fun ExternalModuleDependency.excludeKotlin() {
    exclude(group = "org.jetbrains.kotlin")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "$jvmVersion"
        freeCompilerArgs = listOf(
            "-Xjvm-default=all",
            "-Xjdk-release=$jvmVersion",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
        javaParameters = true
    }
}

tasks.test {
    useJUnitPlatform()
}

license {
    header.set(resources.text.fromFile(file("header.txt")))
}

ktlint {
    version.set("0.48.2")
}

tasks.register("format") {
    group = "minecraft"
    description = "Formats source code according to project style"
    dependsOn(tasks.licenseFormat, tasks.ktlintFormat)
}

tasks.distTar {
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}
tasks.distZip {
    enabled = false
}
