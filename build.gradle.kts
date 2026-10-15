plugins {
    kotlin("jvm") version "2.3.0"
    `java-library`
    `maven-publish`
}

group = "engineer.alberto"
version = "0.1.0-SNAPSHOT"

repositories { mavenCentral() }
kotlin { jvmToolchain(17) }
java { withSourcesJar(); withJavadocJar() }

dependencies {
    api("io.github.detekt.sarif4k:sarif4k:0.7.0")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.2")
}
tasks.test { useJUnitPlatform() }

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            pom {
                name.set("sarif-kt")
                description.set("A small SARIF producer for Kotlin analyzers")
                url.set("https://github.com/albertocavalcante/sarif-kt")
                licenses { license { name.set("Apache-2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0") } }
            }
        }
    }
}
