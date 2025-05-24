plugins {
    java
    application
}

repositories {
    flatDir {
        dirs("lib")
    }
    // Add donkey's lib directories for transitive dependencies
    flatDir {
        dirs("../donkey/lib", "../donkey/lib/commons", "../donkey/lib/database", "../donkey/lib/guava", "../donkey/lib/xstream")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// Configure duplicate handling for distribution tasks
tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

application {
    mainClass.set("com.mirth.connect.manager.Manager")
}

dependencies {
    // Project dependencies
    implementation(project(":donkey"))
    implementation(project(":server"))
    implementation(project(":client"))
    
    // Local lib dependencies
    implementation(fileTree(mapOf("dir" to "lib", "include" to listOf("*.jar"))))
}

sourceSets {
    main {
        java {
            srcDirs("src")
        }
        resources {
            srcDirs("src")
            include("**/*.properties", "**/*.png", "**/*.jpg", "**/*.gif")
        }
    }
}

tasks.jar {
    // Add explicit dependency on donkey copyToServer task
    dependsOn(":donkey:copyToServer")
    archiveFileName.set("mirth-manager-launcher.jar")
    
    manifest {
        attributes(
            "Main-Class" to "com.mirth.connect.manager.Manager",
            "Class-Path" to configurations.runtimeClasspath.get().files
                .filter { it.name.endsWith(".jar") }
                .joinToString(" ") { "manager-lib/${it.name}" }
        )
    }
}

tasks.register("copyDependencies", Copy::class) {
    dependsOn(":donkey:copyToServer")
    from(configurations.runtimeClasspath)
    into("${buildDir}/libs/manager-lib")
    include("*.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register("copyLibs", Copy::class) {
    from("lib")
    into("${buildDir}/libs/manager-lib")
    include("*.jar")
}

tasks.register("dist") {
    dependsOn("jar", "copyDependencies", "copyLibs")
    description = "Build distribution with all dependencies"
}

tasks.named("build") {
    dependsOn("dist")
}

// Add explicit dependencies for compilation tasks
tasks.compileJava {
    dependsOn(":donkey:copyToServer")
    dependsOn(":server:copyEdiXmlFiles")
}

// Add explicit dependencies for distribution tasks
tasks.withType<CreateStartScripts> {
    dependsOn(":donkey:copyToServer")
}

tasks.withType<Tar> {
    dependsOn(":donkey:copyToServer")
}

tasks.withType<Zip> {
    dependsOn(":donkey:copyToServer")
}

// Copy log4j2.properties and images to classes
tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from("src") {
        include("log4j2.properties")
        include("**/images/**")
    }
}