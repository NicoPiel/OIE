plugins {
    java
    application
}

// Configure application plugin
application {
    mainClass.set("com.mirth.connect.client.ui.Frame")
    applicationDefaultJvmArgs = listOf("-Xms512m", "-Xmx2048m")
}

// Handle duplicate files in distributions
tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Project dependencies
dependencies {
    // Donkey dependency - use the JAR output instead of project dependency to avoid transitive deps
    implementation(files("../donkey/setup/donkey-model.jar"))
    
    // Server core dependencies - needed for client compilation
    implementation(files("../server/setup/server-lib/mirth-client-core.jar"))
    implementation(files("../server/setup/server-lib/mirth-crypto.jar"))
    
    // Flat directory repository for client/lib dependencies
    implementation(fileTree("lib") { include("*.jar") })
    
    // Dependencies from server extensions (needed for client compilation)
    implementation(fileTree("../server/build/extensions") { include("**/*.jar") })
    
    // Test dependencies
    testImplementation(fileTree("../server/testlib") { include("*.jar") })
}

// Make sure donkey JAR and server core JARs are built before client compilation
tasks.compileJava {
    dependsOn(":donkey:donkeyModelJar")
    dependsOn(":server:createClientCoreJar")
    dependsOn(":server:createCryptoJar")
    dependsOn(":server:copySetupFiles")
    dependsOn(":server:copyExtensionsToSetup")
    dependsOn(":server:createVocabJar")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// Source sets configuration
sourceSets {
    main {
        java {
            srcDirs("src")
        }
        resources {
            srcDirs("src")
            include("**/*.properties", "**/*.html", "**/*.css", "**/*.js", "**/*.form", "**/*.png")
        }
    }
    test {
        java {
            srcDirs("test")
        }
    }
}

// Application configuration
application {
    mainClass.set("com.mirth.connect.client.ui.Frame")
}

// Create setup directories
val createClientSetupDirs by tasks.registering {
    doLast {
        listOf(
            "dist",
            "dist/extensions"
        ).forEach { dir ->
            file(dir).mkdirs()
        }
    }
}

// Main client JAR task
val createClientJar by tasks.registering(Jar::class) {
    dependsOn(tasks.classes, createClientSetupDirs)
    archiveFileName.set("mirth-client.jar")
    destinationDirectory.set(file("dist"))
    from(sourceSets.main.get().output)
    
    // Exclude connector classes (they go into separate extension JARs)
    exclude("com/mirth/connect/connectors/**")
    // Include only the base ConnectorClass
    include("com/mirth/connect/connectors/ConnectorClass.class")
    include("com/mirth/connect/client/**")
    include("com/mirth/connect/plugins/**")
    include("org/**")

    manifest {
        attributes(
            "Main-Class" to "com.mirth.connect.client.ui.Mirth"
        )
    }
}

// Connector names for client extension JARs
val connectorNames = listOf("dicom", "jdbc", "jms", "http", "doc", "smtp", "tcp", "file", "js", "ws", "vm")

// Create connector client extension JARs
val connectorTasks = mutableListOf<TaskProvider<out Task>>()

connectorNames.forEach { connectorName ->
    val createConnectorClientJar = tasks.register<Jar>("create${connectorName.capitalize()}ClientJar") {
        dependsOn(tasks.classes, createClientSetupDirs)
        archiveFileName.set("${connectorName}-client.jar")
        destinationDirectory.set(file("dist/extensions/${connectorName}"))
        from(sourceSets.main.get().output)
        
        when (connectorName) {
            "dicom" -> include("com/mirth/connect/connectors/dimse/**")
            "jdbc" -> include("com/mirth/connect/connectors/jdbc/**")
            "jms" -> include("com/mirth/connect/connectors/jms/**")
            "http" -> include("com/mirth/connect/connectors/http/**")
            "doc" -> include("com/mirth/connect/connectors/doc/**")
            "smtp" -> include("com/mirth/connect/connectors/smtp/**")
            "tcp" -> include("com/mirth/connect/connectors/tcp/**")
            "file" -> include("com/mirth/connect/connectors/file/**")
            "js" -> include("com/mirth/connect/connectors/js/**")
            "ws" -> include("com/mirth/connect/connectors/ws/**")
            "vm" -> include("com/mirth/connect/connectors/vm/**")
        }
    }
    connectorTasks.add(createConnectorClientJar)
}

// Datatype names for client extension JARs
val datatypeNames = listOf("delimited", "dicom", "edi", "hl7v2", "hl7v3", "ncpdp", "xml", "raw", "json")

// Create datatype client extension JARs
val datatypeTasks = mutableListOf<TaskProvider<out Task>>()

datatypeNames.forEach { datatypeName ->
    val createDatatypeClientJar = tasks.register<Jar>("createDatatype${datatypeName.capitalize()}ClientJar") {
        dependsOn(tasks.classes, createClientSetupDirs)
        archiveFileName.set("datatype-${datatypeName}-client.jar")
        destinationDirectory.set(file("dist/extensions/datatype-${datatypeName}"))
        from(sourceSets.main.get().output)
        include("com/mirth/connect/plugins/datatypes/${datatypeName}/**")
    }
    datatypeTasks.add(createDatatypeClientJar)
}

// Plugin names for client extension JARs
val pluginNames = listOf(
    "directoryresource", "dashboardstatus", "destinationsetfilter", "dicomviewer",
    "extensionmanager", "httpauth", "imageviewer", "javascriptrule", "javascriptstep",
    "mapper", "messagebuilder", "datapruner", "globalmapviewer", "mllpmode",
    "pdfviewer", "textviewer", "rulebuilder", "serverlog", "scriptfilerule",
    "scriptfilestep", "xsltstep"
)

// Create plugin client extension JARs
val pluginTasks = mutableListOf<TaskProvider<out Task>>()

pluginNames.forEach { pluginName ->
    val createPluginClientJar = tasks.register<Jar>("create${pluginName.capitalize()}ClientJar") {
        dependsOn(tasks.classes, createClientSetupDirs)
        archiveFileName.set("${pluginName}-client.jar")
        destinationDirectory.set(file("dist/extensions/${pluginName}"))
        from(sourceSets.main.get().output)
        include("com/mirth/connect/plugins/${pluginName}/**")
    }
    pluginTasks.add(createPluginClientJar)
}

// Copy shared extension JARs from server build (when available)
val copySharedExtensionJars by tasks.registering(Copy::class) {
    dependsOn(":server:copyExtensionsToSetup")
    from("../server/build/extensions") {
        include("**/*-shared.jar")
    }
    into("dist/extensions")
    // Only copy if the source directory exists
    onlyIf { file("../server/build/extensions").exists() }
}

// Main build task
val buildClient by tasks.registering {
    dependsOn(
        createClientJar,
        connectorTasks,
        datatypeTasks, 
        pluginTasks,
        copySharedExtensionJars
    )
}

// Test tasks
tasks.test {
    dependsOn(buildClient)
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Main build task
tasks.build {
    dependsOn(buildClient)
}

// Run task for the client application
tasks.named<JavaExec>("run") {
    dependsOn(buildClient)
    systemProperty("java.library.path", "lib")
}