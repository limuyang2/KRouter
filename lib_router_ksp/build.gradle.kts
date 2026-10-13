plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
    signing
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.10")
    implementation("com.google.code.gson:gson:2.14.0")
//    implementation("com.squareup:kotlinpoet:2.2.0")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
//    implementation(kotlin("reflect"))
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir("../router-index-protocol/src/main/kotlin")
}

val mGroupId = "io.github.limuyang2"
val mArtifactId = "router-ksp"
val mVersion = property("ROUTER_VERSION") as String

group = mGroupId
version = mVersion

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
        artifactId = mArtifactId
    }
}

apply(from = rootProject.file("gradle/maven-central.gradle.kts"))
