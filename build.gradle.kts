plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass.set("org.example.Main")
}

dependencies {
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.flywaydb:flyway-core:11.0.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.0.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.github.pengrad:java-telegram-bot-api:9.6.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("io.github.cdimascio:dotenv-java:3.0.0")
}

tasks.shadowJar {
    archiveBaseName.set("app")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()

    manifest {
        attributes["Main-Class"] = "org.example.Main"
    }
}