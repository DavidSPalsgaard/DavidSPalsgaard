// PC-program (Windows, Linux, Mac): PDF-udskrift og kommandolinje. Brugerfladen kommer her.
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

application {
    applicationName = "binumre"
    mainClass.set("dk.binumre.desktop.MainKt")
}

dependencies {
    implementation(project(":core"))
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    testImplementation("junit:junit:4.13.2")
}
