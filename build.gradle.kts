plugins {
    kotlin("jvm") version "2.3.0"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // Source: https://mvnrepository.com/artifact/io.ktor/ktor-server-core
    implementation("io.ktor:ktor-server-core:3.5.0")
    // Source: https://mvnrepository.com/artifact/io.ktor/ktor-server-netty-jvm
    implementation("io.ktor:ktor-server-netty-jvm:3.5.0")
    // Source: https://mvnrepository.com/artifact/io.ktor/ktor-server-websockets-jvm
    implementation("io.ktor:ktor-server-websockets-jvm:3.5.0")

    // logging library, to satisfy KTOR
    implementation("org.apache.logging.log4j:log4j-core:2.26.1")
    implementation("org.apache.logging.log4j:log4j-api:2.26.1")

    // SLF4J 2.x bridge for Log4j (Note the "slf4j2" and matching version)
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.26.1")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
