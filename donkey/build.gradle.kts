plugins {
    java
    jacoco
}

// Configure repositories to include lib subdirectories
repositories {
    mavenCentral()
    flatDir {
        dirs("lib", "lib/commons", "lib/database", "lib/guava", "lib/xstream")
    }
    flatDir {
        dirs("testlib")
    }
}

// Configure source sets to match existing structure
sourceSets {
    main {
        java {
            srcDirs("src/main/java")
        }
        resources {
            srcDirs("donkeydbconf")
        }
    }
    test {
        java {
            srcDirs("src/test/java")
        }
        resources {
            srcDirs("src/test/resources", "conf")
        }
    }
}

// Dependencies from lib folder using flatDir repository
dependencies {
    // Core dependencies from lib root
    implementation(":guice-4.1.0")
    implementation(":HikariCP-2.5.1")
    implementation(":javassist-3.26.0-GA")
    implementation(":quartz-2.3.2")
    
    // Commons dependencies
    implementation(":commons-beanutils-1.9.4")
    implementation(":commons-codec-1.16.0")
    implementation(":commons-collections4-4.4")
    implementation(":commons-dbcp2-2.0.1")
    implementation(":commons-dbutils-1.7")
    implementation(":commons-io-2.13.0")
    implementation(":commons-lang3-3.13.0")
    implementation(":commons-logging-1.2")
    implementation(":commons-math3-3.0")
    implementation(":commons-pool2-2.3")
    
    // Database drivers
    implementation(":derby-10.10.2.0")
    implementation(":jtds-1.3.1")
    implementation(":mssql-jdbc-8.4.1.jre8")
    implementation(":mysql-connector-j-8.2.0")
    implementation(":ojdbc8-12.2.0.1")
    implementation(":postgresql-42.6.0")
    
    // Guava dependencies
    implementation(":checker-qual-2.10.0")
    implementation(":error_prone_annotations-2.3.4")
    implementation(":failureaccess-1.0.1")
    implementation(":guava-28.2-jre")
    implementation(":j2objc-annotations-1.3")
    implementation(":jsr305-3.0.2")
    implementation(":listenablefuture-9999.0-empty-to-avoid-conflict-with-guava")
    
    // XStream dependencies
    implementation(":xpp3-1.1.4c")
    implementation(":xstream-1.4.20")
    
    // Test dependencies
    testImplementation(":junit-4.8.1")
    testImplementation(":mockito-core-2.7.9")
    testImplementation(":byte-buddy-1.8.8")
    testImplementation(":byte-buddy-agent-1.8.8")
    testImplementation(":objenesis-2.5.1")
    testImplementation(":aopalliance-repackaged-2.4.0-b31")
    testImplementation(":javax.inject-2.4.0-b31")
}

// Create donkey-model.jar task
tasks.register<Jar>("donkeyModelJar") {
    archiveBaseName.set("donkey-model")
    archiveVersion.set("")
    destinationDirectory.set(file("setup"))
    
    from(sourceSets.main.get().output) {
        include("com/mirth/connect/donkey/model/**")
        include("com/mirth/connect/donkey/util/**")
    }
}

// Create donkey-server.jar task
tasks.register<Jar>("donkeyServerJar") {
    archiveBaseName.set("donkey-server")
    archiveVersion.set("")
    destinationDirectory.set(file("setup"))
    
    from(sourceSets.main.get().output) {
        include("com/mirth/connect/donkey/server/**")
        include("com/mirth/connect/donkey/model/**")
        include("com/mirth/connect/donkey/util/**")
    }
    
    // Include donkeydbconf resources
    from("donkeydbconf")
}

// Create setup directory and copy libs
tasks.register<Copy>("createSetup") {
    dependsOn("donkeyModelJar", "donkeyServerJar")
    
    from("lib")
    into("setup/lib")
    
    doFirst {
        file("setup").mkdirs()
        file("setup/lib").mkdirs()
        file("setup/docs").mkdirs()
    }
}

// Copy docs to setup
tasks.register<Copy>("copyDocs") {
    from("docs")
    into("setup/docs")
}

// Copy donkey JARs to server/lib/donkey (mimicking Ant build behavior)
tasks.register<Copy>("copyToServer") {
    dependsOn("donkeyModelJar", "donkeyServerJar")
    
    // Copy the JAR files
    from("setup") {
        include("donkey-model.jar")
        include("donkey-server.jar")
    }
    into("../server/lib/donkey")
    
    // Copy lib dependencies with exclusions
    from("lib") {
        exclude("log4j-1.2.16.jar")
        exclude("HikariCP-java6-2.0.1.jar")
        exclude("javassist-3.19.0-GA.jar")
        exclude("xstream/**")
        exclude("commons/**")
        exclude("database/**")
    }
    into("../server/lib/donkey")
    
    doFirst {
        delete("../server/lib/donkey")
        file("../server/lib/donkey").mkdirs()
    }
}

// Main build task
tasks.named("build") {
    dependsOn("createSetup", "copyDocs", "copyToServer")
}

// Clean task
tasks.named("clean") {
    doLast {
        delete("setup")
        delete("classes")
        delete("test_classes")
    }
}

// Test configuration
tasks.test {
    useJUnit()
    
    // JVM arguments for tests
    jvmArgs("-Xms128m", "-Xmx2048m")
    
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    
    // Generate test reports
    reports {
        junitXml.required.set(true)
        html.required.set(true)
    }
    
    finalizedBy(tasks.jacocoTestReport)
}

// JaCoCo configuration
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    // Configure output directories
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")).include("**/*.exec"))
}

// Configure JaCoCo test coverage
jacoco {
    toolVersion = "0.8.7"
}