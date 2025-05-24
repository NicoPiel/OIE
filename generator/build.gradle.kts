plugins {
    java
    application
}

repositories {
    flatDir {
        dirs("lib")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

application {
    mainClass.set("com.mirth.connect.model.generator.HL7ModelGenerator")
}

dependencies {
    // Local lib dependencies - exclude problematic SLF4J jar
    implementation(fileTree(mapOf("dir" to "lib", "include" to listOf("*.jar"), "exclude" to listOf("slf4j-log4j12-*.jar"))))
    
    // Use only one SLF4J binding - prefer the one from root project
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("org.slf4j:slf4j-log4j12:1.7.30")
}

sourceSets {
    main {
        java {
            srcDirs("src")
            exclude("**/test/**")
        }
    }
}

// Create the model generator JAR
tasks.jar {
    archiveFileName.set("model-generator.jar")
    from(sourceSets.main.get().output)
}

// Task to generate vocabulary source code
tasks.register<JavaExec>("generateVocabSource") {
    dependsOn("jar")
    description = "Generate HL7 vocabulary source code"
    
    classpath = configurations.runtimeClasspath.get() + files("${buildDir}/libs/model-generator.jar")
    mainClass.set("com.mirth.connect.model.generator.HL7ModelGenerator")
    
    args("reference", "${buildDir}/vocab/src", "templates")
    
    doFirst {
        file("${buildDir}/vocab/src").mkdirs()
    }
}

// Compile generated vocabulary source
tasks.register<JavaCompile>("compileVocab") {
    dependsOn("generateVocabSource")
    description = "Compile generated vocabulary classes"
    
    source = fileTree("${buildDir}/vocab/src")
    destinationDirectory.set(file("${buildDir}/vocab/classes"))
    classpath = files("${buildDir}/libs/model-generator.jar")
    
    doFirst {
        file("${buildDir}/vocab/classes").mkdirs()
    }
}

// Create vocabulary JAR
tasks.register<Jar>("vocabJar") {
    dependsOn("compileVocab")
    description = "Create vocabulary JAR"
    
    archiveFileName.set("mirth-vocab-1.2.jar")
    from("${buildDir}/vocab/classes")
    from(sourceSets.main.get().output) {
        include("**/hl7v2/**/*.class")
    }
    
    destinationDirectory.set(file("${buildDir}/vocab/dist"))
    
    doFirst {
        file("${buildDir}/vocab/dist").mkdirs()
    }
}

// Main distribution task
tasks.register("dist") {
    dependsOn("jar", "vocabJar")
    description = "Build complete distribution including vocabulary"
}

tasks.named("build") {
    dependsOn("dist")
}

// Configure distribution tasks to handle duplicates
tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Clean task to remove vocab directory
tasks.clean {
    delete("${buildDir}/vocab")
}