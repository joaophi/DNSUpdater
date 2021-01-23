import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.21"
    kotlin("kapt") version "1.4.21"
}

group = "me.pedro"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")

    implementation("com.squareup.moshi:moshi:1.11.0")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.11.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}


tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "me.pedro.dnsupdater.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}