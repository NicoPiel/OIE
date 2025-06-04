plugins {
    java
    application
}

application {
    mainClass.set("com.mirth.connect.simplesender.SimpleSender")
}

dependencies {
    // Local lib dependencies
    implementation(fileTree(mapOf("dir" to "lib", "include" to listOf("*.jar"))))
    
    // PostgreSQL JDBC driver (using local lib version)
    // implementation("org.postgresql:postgresql:42.5.0") // Modern version if needed
}

sourceSets {
    main {
        java {
            srcDirs("src")
        }
    }
}

tasks.jar {
    archiveFileName.set("simplesender.jar")
    
    manifest {
        attributes(
            "Main-Class" to "com.mirth.connect.simplesender.SimpleSender"
        )
    }
}

// Create distribution with dependencies
tasks.register("copyDependencies", Copy::class) {
    from(configurations.runtimeClasspath)
    into("${buildDir}/libs/lib")
    include("*.jar")
}

tasks.register("copyLibs", Copy::class) {
    from("lib")
    into("${buildDir}/libs/lib")
    include("*.jar")
}

// Copy samples
tasks.register("copySamples", Copy::class) {
    from("samples")
    into("${buildDir}/libs/samples")
}

tasks.register("dist") {
    dependsOn("jar", "copyDependencies", "copyLibs", "copySamples")
    description = "Build distribution with all dependencies and samples"
}

tasks.named("build") {
    dependsOn("dist")
}