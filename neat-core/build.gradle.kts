
plugins {
    kotlin("jvm") version "1.6.0"
}

group = "com.blackthorne"
version = "1.6.23-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("io.mockk:mockk:1.10.0")
//    implementation(kotlin("stdlib-jdk8"))
}


tasks {
    val sourcesJar by creating(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    val javadocJar by creating(Jar::class) {
        dependsOn.add(javadoc)
        archiveClassifier.set("javadoc")
        from(javadoc)
    }

    artifacts {
        archives(sourcesJar)
        archives(javadocJar)
        archives(jar)
    }
}
