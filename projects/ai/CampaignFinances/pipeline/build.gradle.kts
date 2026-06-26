plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    application
}

group = "com.campaignfinances"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.flywaydb:flyway-core:11.8.2")
    implementation("org.flywaydb:flyway-mysql:11.8.2")
    implementation("org.jooq:jooq:3.20.4")
    implementation("com.mysql:mysql-connector-j:9.3.0")
    implementation("io.ktor:ktor-client-core:3.1.3")
    implementation("io.ktor:ktor-client-cio:3.1.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.testcontainers:junit-jupiter:1.21.1")
    testImplementation("org.testcontainers:mysql:1.21.1")
    testImplementation("io.ktor:ktor-client-mock:3.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.campaignfinances.pipeline.MainKt")
}

// The Flyway Gradle plugin (11.8.2) is incompatible with Gradle 9, so migrations
// run through our own CLI: `pipeline migrate` (env: CF_DB_URL, CF_DB_USER, CF_DB_PASSWORD)
tasks.register<JavaExec>("flywayMigrate") {
    group = "database"
    description = "Apply Flyway schema migrations via the pipeline CLI"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.campaignfinances.pipeline.MainKt")
    args("migrate")
}

tasks.test {
    useJUnitPlatform()
    // Docker Engine 29+ rejects docker-java's default API version (1.32) with HTTP 400;
    // pin a version both ways docker-java reads it
    systemProperty("api.version", "1.44")
    environment("DOCKER_API_VERSION", "1.44")
}
