// Fælles kerne uden platformsafhængigheder: startlister, OFeed, datamodel og layout.
// Bruges af PC-programmet og senere af Android-appen.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    // Findes i forvejen på Android; på PC kommer den herfra
    api("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
