import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // NOTE: bumped from the README's documented 1.9.22 — that compiler version
    // throws on this machine's JDK 25 (it can't parse "25.0.2" as a Java version).
    // Library versions below remain pinned to the README's documented versions.
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

group = "com.rag"
version = "1.0.0"

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.7"

dependencies {
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    testImplementation(kotlin("test"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

application {
    mainClass.set("com.rag.MainKt")
}

tasks.named<JavaExec>("run") {
    // Connect stdin so the interactive REPL in Main.kt can read user input.
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}
