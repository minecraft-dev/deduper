import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.licenser)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
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
    implementation(libs.kotlin.serialization.asProvider()) {
        excludeKotlin()
    }

    implementation(libs.bundles.ktor) {
        excludeKotlin()
    }

    implementation(libs.bundles.logging)
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
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-Xjvm-default=enable",
            "-opt-in=io.ktor.server.engine.EngineAPI",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
        javaParameters = true
    }
}

tasks.test {
    useJUnitPlatform()
}

ktlint {
    enableExperimentalRules.set(true)
    disabledRules.addAll("experimental:trailing-comma", "experimental:enum-entry-name-case")
    version.set("0.43.2")
}
tasks.withType(org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask::class) {
}

license {
    header.set(resources.text.fromFile(file("header.txt")))
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
