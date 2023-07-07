import org.gradle.kotlin.dsl.sourceSets
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.8.21"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.ontotext.com/repository/owlim-releases")
    }
}

val graphdbVersion = "10.2.0"

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.9.3")
    testImplementation("com.ontotext.graphdb:graphdb-tests-base:$graphdbVersion")

    implementation("com.ontotext.graphdb:graphdb-sdk:$graphdbVersion")
    implementation("com.ontotext.graphdb:graphdb-runtime:$graphdbVersion")
    implementation(files("libs/Proof-1.0-SNAPSHOT.jar"))
}

tasks.test {
    useJUnitPlatform()
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "19"
}


sourceSets.main {
    java.srcDirs("src/main/java", "src/main/kotlin")
}


application {
    mainClass.set("MainKt")
}

