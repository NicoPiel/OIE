plugins {
    java
    war
}

repositories {
    // Add donkey's lib directories for transitive dependencies
    flatDir {
        dirs("../donkey/lib", "../donkey/lib/commons", "../donkey/lib/database", "../donkey/lib/guava", "../donkey/lib/xstream")
    }
    // Add server's lib directories for transitive dependencies
    flatDir {
        dirs("../server/lib", "../server/lib/commons", "../server/lib/database", "../server/lib/extensions")
    }
    // Add client's lib directories for transitive dependencies
    flatDir {
        dirs("../client/lib")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// Configure duplicate handling for WAR task
tasks.war {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    // Project dependencies
    implementation(project(":donkey"))
    implementation(project(":client"))
    implementation(project(":server"))
    
    // Servlet API and JSP dependencies
    compileOnly("javax.servlet:javax.servlet-api:4.0.1")
    // Note: jetty-jsp and jetty-schemas are included via local file dependencies below
    
    // XStream dependency
    implementation("com.thoughtworks.xstream:xstream:1.4.20")
    
    // WebAdmin specific dependencies (Stripes, JSON, etc.)
    implementation(fileTree(mapOf("dir" to "WebContent/WEB-INF/lib", "include" to listOf("*.jar"))))
    
    // Local lib dependencies from server
    implementation(fileTree(mapOf("dir" to "../server/lib/javax", "include" to listOf("*.jar"))))
    implementation(fileTree(mapOf("dir" to "../server/lib/jetty", "include" to listOf("*.jar"))))
    implementation(fileTree(mapOf("dir" to "../server/lib/jetty/jsp", "include" to listOf("*.jar"))))
}

// Ensure client and server JARs are built before webadmin compilation
tasks.compileJava {
    dependsOn(":client:jar", ":server:jar")
}

sourceSets {
    main {
        java {
            srcDirs("src")
        }
        resources {
            srcDirs("src")
        }
    }
}

tasks.war {
    archiveFileName.set("webadmin.war")
    from("WebContent")
    
    webInf {
        from("WebContent/WEB-INF")
    }
}

tasks.register("copyWebContent", Copy::class) {
    from("WebContent")
    into("${buildDir}/tmp/war")
}

tasks.named("war") {
    dependsOn("copyWebContent")
}

// JSP compilation task (simplified - may need adjustment based on actual JSP usage)
tasks.register("compileJsps") {
    description = "Compile JSP files"
    // This is a placeholder - actual JSP compilation would need more setup
    doLast {
        println("JSP compilation would happen here")
    }
}

tasks.named("compileJava") {
    dependsOn("compileJsps")
}