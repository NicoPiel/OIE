plugins {
    java
    application
}

repositories {
    flatDir {
        dirs("lib", "testlib")
    }
    // Add donkey's lib directories for transitive dependencies
    flatDir {
        dirs("../donkey/lib", "../donkey/lib/commons", "../donkey/lib/database", "../donkey/lib/guava", "../donkey/lib/xstream")
    }
    // Add server's lib directories for transitive dependencies
    flatDir {
        dirs("../server/lib", "../server/lib/commons", "../server/lib/database", "../server/lib/extensions")
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
    mainClass.set("com.mirth.connect.cli.launcher.CommandLineLauncher")
}

dependencies {
    // Project dependencies
    implementation(project(":donkey"))
    implementation(project(":server"))
    implementation(project(":client"))
    
    // Local lib dependencies
    implementation(fileTree(mapOf("dir" to "lib", "include" to listOf("*.jar"))))
    
    // Test dependencies
    testImplementation(fileTree(mapOf("dir" to "testlib", "include" to listOf("*.jar"))))
}

sourceSets {
    main {
        java {
            srcDirs("src")
        }
    }
    test {
        java {
            srcDirs("test")
        }
    }
}

// Create CLI JAR (without launcher)
tasks.register<Jar>("cliJar") {
    archiveFileName.set("mirth-cli.jar")
    from(sourceSets.main.get().output)
    include("com/mirth/connect/cli/**")
    exclude("com/mirth/connect/cli/launcher/**")
}

// Create CLI Launcher JAR
tasks.register<Jar>("cliLauncherJar") {
    archiveFileName.set("mirth-cli-launcher.jar")
    from(sourceSets.main.get().output)
    include("com/mirth/connect/cli/launcher/**")
    
    manifest {
        attributes(
            "Main-Class" to "com.mirth.connect.cli.launcher.CommandLineLauncher",
            "Class-Path" to "cli-lib/log4j-api-2.17.2.jar cli-lib/log4j-core-2.17.2.jar cli-lib/commons-io-2.13.0.jar conf/"
        )
    }
}

// Copy configuration files
tasks.register("copyConf", Copy::class) {
    from("conf")
    into("${buildDir}/libs/conf")
}

// Copy CLI libraries
tasks.register("copyCliLibs", Copy::class) {
    from("lib")
    into("${buildDir}/libs/cli-lib")
    include("*.jar")
}

// Copy project dependencies to cli-lib
tasks.register("copyDependencies", Copy::class) {
    from(configurations.runtimeClasspath)
    into("${buildDir}/libs/cli-lib")
    include("*.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register("dist") {
    dependsOn("cliJar", "cliLauncherJar", "copyConf", "copyCliLibs", "copyDependencies")
    description = "Build CLI distribution with all components"
    
    doLast {
        copy {
            from(tasks.named("cliJar").get().outputs.files)
            from(tasks.named("cliLauncherJar").get().outputs.files)
            into("${buildDir}/libs")
        }
    }
}

tasks.named("build") {
    dependsOn("dist")
}

// Disable the default jar task since we have custom jars
tasks.jar {
    enabled = false
}

// Test configuration
tasks.test {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}