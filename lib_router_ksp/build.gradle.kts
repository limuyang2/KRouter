plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
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

// 上传
val mGroupId = "io.github.limuyang2"
val mArtifactId = "router-ksp"
val mVersion = property("ROUTER_VERSION") as String


publishing {
    publications {

        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = mGroupId
            artifactId = mArtifactId
            version = mVersion
        }
    }
    repositories {
        maven {
            url = uri(property("MAVEN_URL") as String)
            credentials {
                username = property("MAVEN_USERNAME") as String
                password = property("MAVEN_PASSWORD") as String
            }
        }

    }

}
