// PC-program (Windows, Linux, Mac): brugerflade (Compose), PDF-udskrift og kommandolinje.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
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
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    testImplementation("junit:junit:4.13.2")
}
