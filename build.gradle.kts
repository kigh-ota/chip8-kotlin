plugins {
    kotlin("jvm") version "1.5.10"
    id("org.openjfx.javafxplugin") version "0.0.8"
}

javafx {
    modules("javafx.controls", "javafx.graphics")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("ch.qos.logback:logback-classic:1.2.5")
}
