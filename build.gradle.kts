plugins {
    kotlin("jvm") version "2.1.10"
}

group = "me.gavin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    // Our serialization library, so we can talk to the obd2 adapter over USB
    implementation("com.fazecast:jSerialComm:[2.0.0,3.0.0)")
}

tasks.test {
    useJUnitPlatform()
}