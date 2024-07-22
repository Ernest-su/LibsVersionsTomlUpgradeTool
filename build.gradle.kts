plugins {
    kotlin("jvm") version libs.versions.kotlin
    application
}

group = "ernest.tomlversiontool"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.tomlj)
    implementation(libs.okhttp)
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("ernest.tomlversiontool.MainKt")
}