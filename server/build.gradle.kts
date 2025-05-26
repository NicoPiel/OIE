import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    java
    `java-library`
}

// Project dependencies
dependencies {
    implementation(project(":donkey"))
    
    // Flat directory repository for server/lib dependencies
    implementation(fileTree("lib") { include("*.jar") })
    implementation(fileTree("lib/commons") { include("*.jar") })
    implementation(fileTree("lib/database") { include("*.jar") })
    implementation(fileTree("lib/aws") { include("*.jar") })
    implementation(fileTree("lib/aws/ext") { include("*.jar") })
    implementation(fileTree("lib/aws/ext/netty") { include("*.jar") })
    implementation(fileTree("lib/hapi") { include("*.jar") })
    implementation(fileTree("lib/jackson") { include("*.jar") })
    implementation(fileTree("lib/javax") { include("*.jar") })
    implementation(fileTree("lib/javax/jaxb") { include("*.jar") })
    implementation(fileTree("lib/javax/jaxb/ext") { include("*.jar") })
    implementation(fileTree("lib/javax/jaxws") { include("*.jar") })
    implementation(fileTree("lib/javax/jaxws/ext") { include("*.jar") })
    implementation(fileTree("lib/jersey") { include("*.jar") })
    implementation(fileTree("lib/jersey/ext") { include("*.jar") })
    implementation(fileTree("lib/jetty") { include("*.jar") })
    implementation(fileTree("lib/jetty/jsp") { include("*.jar") })
    implementation(fileTree("lib/jms") { include("*.jar") })
    implementation(fileTree("lib/log4j") { include("*.jar") })
    implementation(fileTree("lib/swagger") { include("*.jar") })
    implementation(fileTree("lib/swagger/ext") { include("*.jar") })
    implementation(fileTree("lib/extensions/dimse") { include("*.jar") })
    implementation(fileTree("lib/extensions/doc") { include("*.jar") })
    implementation(fileTree("lib/extensions/file") { include("*.jar") })
    implementation(fileTree("lib/extensions/ws") { include("*.jar") })
    implementation(fileTree("lib/extensions/dicomviewer") { include("*.jar") })
    
    // Donkey lib dependencies (needed for Guava and other shared libs)
    implementation(fileTree("lib/donkey") { include("*.jar") })
    implementation(fileTree("lib/donkey/guava") { include("*.jar") })
    
    // Test dependencies
    testImplementation(fileTree("testlib") { include("*.jar") })
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
            include("**/*.js", "**/*.txt", "**/*.xml", "**/*.properties")
        }
    }
    test {
        java {
            srcDirs("test")
        }
        resources {
            srcDirs("test")
            include("**/*.xml")
        }
    }
}

// Custom task to create version.properties
val createVersionProperties by tasks.registering {
    // Capture project version during configuration time
    val projectVersion = version.toString()
    val buildDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"))
    
    doLast {
        val versionFile = File(projectDir, "version.properties")
        versionFile.writeText("""
            mirth.version=${projectVersion}
            mirth.date=${buildDate}
        """.trimIndent())
    }
}

// Compile task depends on version properties
tasks.compileJava {
    dependsOn(createVersionProperties)
    // Add explicit dependency on donkey copyToServer task
    dependsOn(":donkey:copyToServer")
}

// Copy version.properties and other resources to classes
tasks.processResources {
    dependsOn(createVersionProperties)
    from("version.properties")
    from("mirth-client.jnlp")
}

// Create setup directories
val createSetupDirs by tasks.registering {
    doLast {
        listOf(
            "setup",
            "setup/conf",
            "setup/extensions",
            "setup/public_html",
            "setup/public_api_html",
            "setup/server-lib",
            "setup/client-lib",
            "setup/manager-lib",
            "setup/cli-lib",
            "setup/logs",
            "setup/docs",
            "setup/server-launcher-lib",
            "setup/webapps",
            "build/extensions"
        ).forEach { dir ->
            File(projectDir, dir).mkdirs()
        }
    }
}

// Crypto JAR task
val createCryptoJar by tasks.registering(Jar::class) {
    dependsOn(tasks.classes)
    archiveFileName.set("mirth-crypto.jar")
    destinationDirectory.set(File(projectDir, "setup/server-lib"))
    from(sourceSets.main.get().output)
    include("com/mirth/commons/encryption/**")
}

// Client Core JAR task
val createClientCoreJar by tasks.registering(Jar::class) {
    dependsOn(tasks.classes, createCryptoJar)
    archiveFileName.set("mirth-client-core.jar")
    destinationDirectory.set(File(projectDir, "setup/server-lib"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    include("com/mirth/connect/client/core/**")
    include("com/mirth/connect/model/**")
    include("com/mirth/connect/userutil/**")
    include("com/mirth/connect/util/**")
    include("com/mirth/connect/server/util/ResourceUtil.class")
    include("com/mirth/connect/server/util/DebuggerUtil.class")
    include("org/mozilla/**")
    include("org/glassfish/jersey/**")
    include("de/**")
    include("net/lingala/zip4j/unzip/**")
    include("version.properties")
}

// Server JAR task
val createServerJar by tasks.registering(Jar::class) {
    dependsOn(tasks.classes, createClientCoreJar)
    // Add explicit dependency on copyEdiXmlFiles task when it exists
    dependsOn(tasks.named("copyEdiXmlFiles"))
    archiveFileName.set("mirth-server.jar")
    destinationDirectory.set(File(projectDir, "setup/server-lib"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    include("com/mirth/connect/server/**")
    include("com/mirth/connect/model/**")
    include("com/mirth/connect/util/**")
    include("com/mirth/connect/plugins/*.class")
    include("com/mirth/connect/connectors/*.class")
    include("org/**")
    include("net/sourceforge/jtds/ssl/**")
    include("mirth-client.jnlp")
    exclude("com/mirth/connect/server/launcher/**")
    exclude("org/dcm4che2/**")
}

// Vocab JAR task (if mirth-vocab.jar exists in lib)
val createVocabJar by tasks.registering(Copy::class) {
    from(File(projectDir, "lib/mirth-vocab.jar"))
    into(File(projectDir, "setup/server-lib"))
}

// DBConf JAR task
val createDbconfJar by tasks.registering(Jar::class) {
    archiveFileName.set("mirth-dbconf.jar")
    destinationDirectory.set(File(projectDir, "setup/server-lib"))
    from(File(projectDir, "dbconf"))
}

// Launcher JAR task
val createLauncherJar by tasks.registering(Jar::class) {
    dependsOn(tasks.classes)
    archiveFileName.set("mirth-server-launcher.jar")
    destinationDirectory.set(File(projectDir, "setup"))
    from(sourceSets.main.get().output)
    include("com/mirth/connect/server/launcher/**")
    include("com/mirth/connect/server/extprops/**")
    manifest {
        attributes(
            "Main-Class" to "com.mirth.connect.server.launcher.MirthLauncher",
            "Class-Path" to "server-lib/commons/commons-io-2.13.0.jar server-lib/commons/commons-configuration2-2.8.0.jar server-lib/commons/commons-lang3-3.13.0.jar server-lib/commons/commons-logging-1.2.jar server-lib/commons/commons-beanutils-1.9.4.jar server-lib/commons/commons-text-1.10.0.jar server-lib/commons/commons-collections-3.2.2.jar conf/"
        )
    }
}

// UserUtil Sources JAR task
val createUserutilSourcesJar by tasks.registering(Jar::class) {
    archiveFileName.set("userutil-sources.jar")
    destinationDirectory.set(File(projectDir, "setup/client-lib"))
    from(File(projectDir, "src"))
    include("com/mirth/connect/userutil/**/*.java")
    include("com/mirth/connect/server/userutil/**/*.java")
    exclude("**/package-info.java")
}

// Connector JAR creation tasks
val connectorNames = listOf("dicom", "doc", "file", "http", "jdbc", "jms", "js", "smtp", "tcp", "vm", "ws")

// Create connector tasks
val connectorTasks = mutableListOf<TaskProvider<out Task>>()

connectorNames.forEach { connectorName ->
    val createConnectorSharedJar = tasks.register<Jar>("create${connectorName.capitalize()}SharedJar") {
        dependsOn(tasks.compileJava)
        archiveFileName.set("${connectorName}-shared.jar")
        destinationDirectory.set(File(projectDir, "build/extensions/${connectorName}"))
        from(sourceSets.main.get().output)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        
        when (connectorName) {
            "dicom" -> {
                include("com/mirth/connect/connectors/dimse/DICOMReceiverProperties.class")
                include("com/mirth/connect/connectors/dimse/DICOMDispatcherProperties.class")
            }
            "doc" -> {
                include("com/mirth/connect/connectors/doc/DocumentDispatcherProperties.class")
                include("com/mirth/connect/connectors/doc/DocumentConnectorServletInterface.class")
                include("com/mirth/connect/connectors/doc/PageSize.class")
                include("com/mirth/connect/connectors/doc/Unit.class")
            }
            "file" -> {
                include("com/mirth/connect/connectors/file/SchemeProperties.class")
                include("com/mirth/connect/connectors/file/FTPSchemeProperties.class")
                include("com/mirth/connect/connectors/file/SmbDialectVersion.class")
                include("com/mirth/connect/connectors/file/SmbSchemeProperties.class")
                include("com/mirth/connect/connectors/file/SftpSchemeProperties.class")
                include("com/mirth/connect/connectors/file/S3SchemeProperties.class")
                include("com/mirth/connect/connectors/file/FileReceiverProperties.class")
                include("com/mirth/connect/connectors/file/FileDispatcherProperties.class")
                include("com/mirth/connect/connectors/file/FileScheme.class")
                include("com/mirth/connect/connectors/file/FileAction.class")
                include("com/mirth/connect/connectors/file/FileConnectorServletInterface.class")
            }
            "http" -> {
                include("com/mirth/connect/connectors/http/HttpReceiverProperties.class")
                include("com/mirth/connect/connectors/http/HttpDispatcherProperties.class")
                include("com/mirth/connect/connectors/http/HttpStaticResource.class")
                include("com/mirth/connect/connectors/http/HttpStaticResource\$ResourceType.class")
                include("com/mirth/connect/connectors/http/HttpConnectorServletInterface.class")
            }
            "jdbc" -> {
                include("com/mirth/connect/connectors/jdbc/DatabaseReceiverProperties.class")
                include("com/mirth/connect/connectors/jdbc/DatabaseDispatcherProperties.class")
                include("com/mirth/connect/connectors/jdbc/DatabaseConnectionInfo.class")
                include("com/mirth/connect/connectors/jdbc/Table.class")
                include("com/mirth/connect/connectors/jdbc/Column.class")
                include("com/mirth/connect/connectors/jdbc/DatabaseConnectorServletInterface.class")
            }
            "jms" -> {
                include("com/mirth/connect/connectors/jms/JmsConnectorProperties.class")
                include("com/mirth/connect/connectors/jms/JmsReceiverProperties.class")
                include("com/mirth/connect/connectors/jms/JmsDispatcherProperties.class")
                include("com/mirth/connect/connectors/jms/JmsConnectorServletInterface.class")
            }
            "js" -> {
                include("com/mirth/connect/connectors/js/JavaScriptReceiverProperties.class")
                include("com/mirth/connect/connectors/js/JavaScriptDispatcherProperties.class")
            }
            "smtp" -> {
                include("com/mirth/connect/connectors/smtp/SmtpDispatcherProperties.class")
                include("com/mirth/connect/connectors/smtp/SmtpConnectorServletInterface.class")
                include("com/mirth/connect/connectors/smtp/Attachment.class")
            }
            "tcp" -> {
                include("com/mirth/connect/connectors/tcp/TcpReceiverProperties.class")
                include("com/mirth/connect/connectors/tcp/TcpDispatcherProperties.class")
                include("com/mirth/connect/connectors/tcp/TcpConnectorServletInterface.class")
            }
            "vm" -> {
                include("com/mirth/connect/connectors/vm/VmReceiverProperties.class")
                include("com/mirth/connect/connectors/vm/VmDispatcherProperties.class")
            }
            "ws" -> {
                include("com/mirth/connect/connectors/ws/Binding.class")
                include("com/mirth/connect/connectors/ws/WebServiceReceiverProperties.class")
                include("com/mirth/connect/connectors/ws/WebServiceDispatcherProperties.class")
                include("com/mirth/connect/connectors/ws/DefinitionServiceMap.class")
                include("com/mirth/connect/connectors/ws/DefinitionServiceMap\$DefinitionPortMap.class")
                include("com/mirth/connect/connectors/ws/DefinitionServiceMap\$PortInformation.class")
                include("com/mirth/connect/connectors/ws/WebServiceConnectorServletInterface.class")
            }
        }
    }
    
    val createConnectorServerJar = tasks.register<Jar>("create${connectorName.capitalize()}ServerJar") {
        dependsOn(tasks.compileJava)
        archiveFileName.set("${connectorName}-server.jar")
        destinationDirectory.set(File(projectDir, "build/extensions/${connectorName}"))
        from(sourceSets.main.get().output)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        
        when (connectorName) {
            "dicom" -> {
                include("com/mirth/connect/connectors/dimse/**")
                include("org/dcm4che2/**")
                exclude("com/mirth/connect/connectors/dimse/DICOMReceiverProperties.class")
                exclude("com/mirth/connect/connectors/dimse/DICOMDispatcherProperties.class")
            }
            "doc" -> {
                include("com/mirth/connect/connectors/doc/**")
                exclude("com/mirth/connect/connectors/doc/DocumentDispatcherProperties.class")
                exclude("com/mirth/connect/connectors/doc/DocumentConnectorServletInterface.class")
                exclude("com/mirth/connect/connectors/doc/PageSize.class")
                exclude("com/mirth/connect/connectors/doc/Unit.class")
            }
            "file" -> {
                include("com/mirth/connect/connectors/file/**")
                exclude("com/mirth/connect/connectors/file/SchemeProperties.class")
                exclude("com/mirth/connect/connectors/file/FTPSchemeProperties.class")
                exclude("com/mirth/connect/connectors/file/SftpSchemeProperties.class")
                exclude("com/mirth/connect/connectors/file/S3SchemeProperties.class")
                exclude("com/mirth/connect/connectors/file/FileReceiverProperties.class")
                exclude("com/mirth/connect/connectors/file/FileDispatcherProperties.class")
                exclude("com/mirth/connect/connectors/file/FileScheme.class")
                exclude("com/mirth/connect/connectors/file/FileAction.class")
                exclude("com/mirth/connect/connectors/file/FileConnectorServletInterface.class")
            }
            "http" -> {
                include("com/mirth/connect/connectors/http/**")
                exclude("com/mirth/connect/connectors/http/HttpReceiverProperties.class")
                exclude("com/mirth/connect/connectors/http/HttpDispatcherProperties.class")
                exclude("com/mirth/connect/connectors/http/HttpStaticResource.class")
                exclude("com/mirth/connect/connectors/http/HttpStaticResource\$ResourceType.class")
                exclude("com/mirth/connect/connectors/http/HttpConnectorServletInterface.class")
            }
            "jdbc" -> {
                include("com/mirth/connect/connectors/jdbc/**")
                exclude("com/mirth/connect/connectors/jdbc/DatabaseReceiverProperties.class")
                exclude("com/mirth/connect/connectors/jdbc/DatabaseDispatcherProperties.class")
                exclude("com/mirth/connect/connectors/jdbc/DatabaseConnectionInfo.class")
                exclude("com/mirth/connect/connectors/jdbc/Table.class")
                exclude("com/mirth/connect/connectors/jdbc/Column.class")
                exclude("com/mirth/connect/connectors/jdbc/DatabaseConnectorServletInterface.class")
            }
            "jms" -> {
                include("com/mirth/connect/connectors/jms/**")
                exclude("com/mirth/connect/connectors/jms/JmsConnectorProperties.class")
                exclude("com/mirth/connect/connectors/jms/JmsReceiverProperties.class")
                exclude("com/mirth/connect/connectors/jms/JmsDispatcherProperties.class")
                exclude("com/mirth/connect/connectors/jms/JmsConnectorServletInterface.class")
            }
            "js" -> {
                include("com/mirth/connect/connectors/js/**")
                exclude("com/mirth/connect/connectors/js/JavaScriptReceiverProperties.class")
                exclude("com/mirth/connect/connectors/js/JavaScriptDispatcherProperties.class")
            }
            "smtp" -> {
                include("com/mirth/connect/connectors/smtp/**")
                exclude("com/mirth/connect/connectors/smtp/SmtpDispatcherProperties.class")
                exclude("com/mirth/connect/connectors/smtp/SmtpConnectorServletInterface.class")
                exclude("com/mirth/connect/connectors/smtp/Attachment.class")
            }
            "tcp" -> {
                include("com/mirth/connect/connectors/tcp/**")
                exclude("com/mirth/connect/connectors/tcp/TcpReceiverProperties.class")
                exclude("com/mirth/connect/connectors/tcp/TcpDispatcherProperties.class")
                exclude("com/mirth/connect/connectors/tcp/TcpConnectorServletInterface.class")
            }
            "vm" -> {
                include("com/mirth/connect/connectors/vm/**")
                exclude("com/mirth/connect/connectors/vm/VmReceiverProperties.class")
                exclude("com/mirth/connect/connectors/vm/VmDispatcherProperties.class")
            }
            "ws" -> {
                include("com/mirth/connect/connectors/ws/**")
                exclude("com/mirth/connect/connectors/ws/Binding.class")
                exclude("com/mirth/connect/connectors/ws/WebServiceReceiverProperties.class")
                exclude("com/mirth/connect/connectors/ws/WebServiceDispatcherProperties.class")
                exclude("com/mirth/connect/connectors/ws/DefinitionServiceMap.class")
                exclude("com/mirth/connect/connectors/ws/DefinitionServiceMap\$DefinitionPortMap.class")
                exclude("com/mirth/connect/connectors/ws/DefinitionServiceMap\$PortInformation.class")
                exclude("com/mirth/connect/connectors/ws/WebServiceConnectorServletInterface.class")
            }
        }
    }
    
    val copyConnectorXml = tasks.register<Copy>("copy${connectorName.capitalize()}Xml") {
        from(File(projectDir, "src/com/mirth/connect/connectors/${if (connectorName == "dicom") "dimse" else connectorName}"))
        into(File(projectDir, "build/extensions/${connectorName}"))
        include("*.xml")
    }
    
    val copyConnectorLib = tasks.register<Copy>("copy${connectorName.capitalize()}Lib") {
        from(File(projectDir, "lib/extensions/${if (connectorName == "dicom") "dimse" else connectorName}"))
        into(File(projectDir, "build/extensions/${connectorName}/lib"))
        include("*.jar")
    }
    
    connectorTasks.addAll(listOf(createConnectorSharedJar, createConnectorServerJar, copyConnectorXml, copyConnectorLib))
}

// Datatype JAR creation tasks
val datatypeNames = listOf("delimited", "dicom", "edi", "hl7v2", "hl7v3", "ncpdp", "xml", "raw", "json")

// Create datatype tasks
val datatypeTasks = mutableListOf<TaskProvider<out Task>>()

datatypeNames.forEach { datatypeName ->
    val createDatatypeSharedJar = tasks.register<Jar>("createDatatype${datatypeName.capitalize()}SharedJar") {
            dependsOn(tasks.compileJava)
            // Add dependency on copyEdiXmlFiles for EDI datatype
            if (datatypeName == "edi") {
                dependsOn("copyEdiXmlFiles")
            }
            archiveFileName.set("datatype-${datatypeName}-shared.jar")
            destinationDirectory.set(File(projectDir, "build/extensions/datatype-${datatypeName}"))
            from(sourceSets.main.get().output)
            include("com/mirth/connect/plugins/datatypes/${datatypeName}/**")
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        
        when (datatypeName) {
            "delimited" -> {
                exclude("com/mirth/connect/plugins/datatypes/delimited/DelimitedDataTypeServerPlugin.class")
                exclude("com/mirth/connect/plugins/datatypes/delimited/DelimitedBatchAdaptor.class")
                exclude("com/mirth/connect/plugins/datatypes/delimited/DelimitedBatchReader.class")
            }
            "dicom" -> {
                exclude("com/mirth/connect/plugins/datatypes/dicom/DICOMDataTypeServerPlugin.class")
            }
            "edi" -> {
                exclude("com/mirth/connect/plugins/datatypes/edi/EDIDataTypeServerPlugin.class")
            }
            "hl7v2" -> {
                exclude("com/mirth/connect/plugins/datatypes/hl7v2/HL7v2DataTypeServerPlugin.class")
                exclude("com/mirth/connect/plugins/datatypes/hl7v2/HL7v2BatchAdaptor.class")
            }
            "hl7v3" -> {
                exclude("com/mirth/connect/plugins/datatypes/hl7v3/HL7V3DataTypeServerPlugin.class")
            }
            "ncpdp" -> {
                exclude("com/mirth/connect/plugins/datatypes/ncpdp/NCPDPDataTypeServerPlugin.class")
            }
            "xml" -> {
                exclude("com/mirth/connect/plugins/datatypes/xml/XMLDataTypeServerPlugin.class")
            }
            "raw" -> {
                exclude("com/mirth/connect/plugins/datatypes/raw/RawDataTypeServerPlugin.class")
            }
            "json" -> {
                exclude("com/mirth/connect/plugins/datatypes/json/JSONDataTypeServerPlugin.class")
            }
        }
    }
    
    val createDatatypeServerJar = tasks.register<Jar>("createDatatype${datatypeName.capitalize()}ServerJar") {
            dependsOn(tasks.compileJava)
            // Add dependency on copyEdiXmlFiles for EDI datatype
            if (datatypeName == "edi") {
                dependsOn("copyEdiXmlFiles")
            }
            archiveFileName.set("datatype-${datatypeName}-server.jar")
            destinationDirectory.set(File(projectDir, "build/extensions/datatype-${datatypeName}"))
            from(sourceSets.main.get().output)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        
        when (datatypeName) {
            "delimited" -> {
                include("com/mirth/connect/plugins/datatypes/delimited/DelimitedDataTypeServerPlugin.class")
                include("com/mirth/connect/plugins/datatypes/delimited/DelimitedBatchAdaptor.class")
                include("com/mirth/connect/plugins/datatypes/delimited/DelimitedBatchReader.class")
            }
            "dicom" -> {
                include("com/mirth/connect/plugins/datatypes/dicom/DICOMDataTypeServerPlugin.class")
            }
            "edi" -> {
                include("com/mirth/connect/plugins/datatypes/edi/EDIDataTypeServerPlugin.class")
            }
            "hl7v2" -> {
                include("com/mirth/connect/plugins/datatypes/hl7v2/HL7v2DataTypeServerPlugin.class")
                include("com/mirth/connect/plugins/datatypes/hl7v2/HL7v2BatchAdaptor.class")
            }
            "hl7v3" -> {
                include("com/mirth/connect/plugins/datatypes/hl7v3/HL7V3DataTypeServerPlugin.class")
            }
            "ncpdp" -> {
                include("com/mirth/connect/plugins/datatypes/ncpdp/NCPDPDataTypeServerPlugin.class")
            }
            "xml" -> {
                include("com/mirth/connect/plugins/datatypes/xml/XMLDataTypeServerPlugin.class")
            }
            "raw" -> {
                include("com/mirth/connect/plugins/datatypes/raw/RawDataTypeServerPlugin.class")
            }
            "json" -> {
                include("com/mirth/connect/plugins/datatypes/json/JSONDataTypeServerPlugin.class")
            }
        }
    }
    
    val copyDatatypeXml = tasks.register<Copy>("copyDatatype${datatypeName.capitalize()}Xml") {
        from(File(projectDir, "src/com/mirth/connect/plugins/datatypes/${datatypeName}"))
        into(File(projectDir, "build/extensions/datatype-${datatypeName}"))
        include("*.xml")
    }
    
    val copyDatatypeLib = tasks.register<Copy>("copyDatatype${datatypeName.capitalize()}Lib") {
        from(File(projectDir, "lib/extensions/datatypes/${datatypeName}"))
        into(File(projectDir, "build/extensions/datatype-${datatypeName}/lib"))
        include("*.jar")
    }
    
    datatypeTasks.addAll(listOf(createDatatypeSharedJar, createDatatypeServerJar, copyDatatypeXml, copyDatatypeLib))
    
    // Special handling for EDI datatype XML files
    if (datatypeName == "edi") {
        val copyEdiXmlFiles = tasks.register<Copy>("copyEdiXmlFiles") {
            from(File(projectDir, "src/com/mirth/connect/plugins/datatypes/edi/xml"))
            into(File(projectDir, "build/classes/java/main/com/mirth/connect/plugins/datatypes/edi/xml"))
        }
        datatypeTasks.add(copyEdiXmlFiles)
    }
}

// Plugin JAR creation tasks
val pluginNames = listOf(
    "directoryresource", "dashboardstatus", "destinationsetfilter", "dicomviewer",
    "httpauth", "imageviewer", "javascriptrule", "javascriptstep", "mapper",
    "messagebuilder", "datapruner", "globalmapviewer", "mllpmode", "pdfviewer",
    "textviewer", "rulebuilder", "serverlog", "scriptfilerule", "scriptfilestep", "xsltstep"
)

// Create plugin tasks
val pluginTasks = mutableListOf<TaskProvider<out Task>>()

pluginNames.forEach { pluginName ->
    // Some plugins only have shared JARs, some have both shared and server
    val hasServerJar = pluginName in listOf(
        "directoryresource", "dashboardstatus", "httpauth", "globalmapviewer",
        "datapruner", "mllpmode", "serverlog"
    )
    
    val hasSharedJar = pluginName !in listOf("dicomviewer", "imageviewer", "pdfviewer", "textviewer")
    
    if (hasSharedJar) {
        val createPluginSharedJar = tasks.register<Jar>("create${pluginName.capitalize()}SharedJar") {
            dependsOn(tasks.compileJava)
            archiveFileName.set("${pluginName}-shared.jar")
            destinationDirectory.set(File(projectDir, "build/extensions/${pluginName}"))
            from(sourceSets.main.get().output)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            
            when (pluginName) {
                "directoryresource" -> {
                    include("com/mirth/connect/plugins/directoryresource/DirectoryResourceProperties.class")
                    include("com/mirth/connect/plugins/directoryresource/DirectoryResourceServletInterface.class")
                }
                "dashboardstatus" -> {
                    include("com/mirth/connect/plugins/dashboardstatus/ConnectionLogItem.class")
                    include("com/mirth/connect/plugins/dashboardstatus/DashboardConnectorStatusServletInterface.class")
                }
                "destinationsetfilter" -> {
                    include("com/mirth/connect/plugins/destinationsetfilter/DestinationSetFilterStep.class")
                    include("com/mirth/connect/plugins/destinationsetfilter/DestinationSetFilterStep\$Behavior.class")
                    include("com/mirth/connect/plugins/destinationsetfilter/DestinationSetFilterStep\$Condition.class")
                }
                "httpauth" -> {
                    include("com/mirth/connect/plugins/httpauth/HttpAuthConnectorPluginProperties.class")
                    include("com/mirth/connect/plugins/httpauth/HttpAuthConnectorPluginProperties\$AuthType.class")
                    include("com/mirth/connect/plugins/httpauth/NoneHttpAuthProperties.class")
                    include("com/mirth/connect/plugins/httpauth/basic/BasicHttpAuthProperties.class")
                    include("com/mirth/connect/plugins/httpauth/digest/DigestHttpAuthProperties.class")
                    include("com/mirth/connect/plugins/httpauth/digest/DigestHttpAuthProperties\$Algorithm.class")
                    include("com/mirth/connect/plugins/httpauth/digest/DigestHttpAuthProperties\$QOPMode.class")
                    include("com/mirth/connect/plugins/httpauth/custom/CustomHttpAuthProperties.class")
                    include("com/mirth/connect/plugins/httpauth/javascript/JavaScriptHttpAuthProperties.class")
                    include("com/mirth/connect/plugins/httpauth/oauth2/OAuth2HttpAuthProperties.class")
                    include("com/mirth/connect/plugins/httpauth/oauth2/OAuth2HttpAuthProperties\$TokenLocation.class")
                }
                "javascriptrule" -> {
                    include("com/mirth/connect/plugins/javascriptrule/JavaScriptRule.class")
                }
                "javascriptstep" -> {
                    include("com/mirth/connect/plugins/javascriptstep/JavaScriptStep.class")
                }
                "mapper" -> {
                    include("com/mirth/connect/plugins/mapper/MapperStep.class")
                    include("com/mirth/connect/plugins/mapper/MapperStep\$Scope.class")
                }
                "messagebuilder" -> {
                    include("com/mirth/connect/plugins/messagebuilder/MessageBuilderStep.class")
                }
                "datapruner" -> {
                    include("com/mirth/connect/plugins/datapruner/DataPrunerServletInterface.class")
                }
                "globalmapviewer" -> {
                    include("com/mirth/connect/plugins/globalmapviewer/GlobalMapServletInterface.class")
                }
                "mllpmode" -> {
                    include("com/mirth/connect/plugins/mllpmode/MLLPModeProperties.class")
                }
                "rulebuilder" -> {
                    include("com/mirth/connect/plugins/rulebuilder/RuleBuilderRule.class")
                    include("com/mirth/connect/plugins/rulebuilder/RuleBuilderRule\$Condition.class")
                }
                "serverlog" -> {
                    include("com/mirth/connect/plugins/serverlog/ServerLogItem.class")
                    include("com/mirth/connect/plugins/serverlog/ServerLogServletInterface.class")
                }
                "scriptfilerule" -> {
                    include("com/mirth/connect/plugins/scriptfilerule/ExternalScriptRule.class")
                }
                "scriptfilestep" -> {
                    include("com/mirth/connect/plugins/scriptfilestep/ExternalScriptStep.class")
                }
                "xsltstep" -> {
                    include("com/mirth/connect/plugins/xsltstep/XsltStep.class")
                }
            }
        }
        pluginTasks.add(createPluginSharedJar)
    }
    
    if (hasServerJar) {
        val createPluginServerJar = tasks.register<Jar>("create${pluginName.capitalize()}ServerJar") {
            dependsOn(tasks.compileJava)
            archiveFileName.set("${pluginName}-server.jar")
            destinationDirectory.set(File(projectDir, "build/extensions/${pluginName}"))
            from(sourceSets.main.get().output)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            
            when (pluginName) {
                "directoryresource" -> {
                    include("com/mirth/connect/plugins/directoryresource/**")
                    exclude("com/mirth/connect/plugins/directoryresource/DirectoryResourceProperties.class")
                    exclude("com/mirth/connect/plugins/directoryresource/DirectoryResourceServletInterface.class")
                }
                "dashboardstatus" -> {
                    include("com/mirth/connect/plugins/dashboardstatus/**")
                    exclude("com/mirth/connect/plugins/dashboardstatus/ConnectionLogItem.class")
                    exclude("com/mirth/connect/plugins/dashboardstatus/DashboardConnectorStatusServletInterface.class")
                }
                "httpauth" -> {
                    include("com/mirth/connect/plugins/httpauth/**")
                    exclude("com/mirth/connect/plugins/httpauth/HttpAuthConnectorPluginProperties.class")
                    exclude("com/mirth/connect/plugins/httpauth/HttpAuthConnectorPluginProperties\$AuthType.class")
                    exclude("com/mirth/connect/plugins/httpauth/NoneHttpAuthProperties.class")
                    exclude("com/mirth/connect/plugins/httpauth/basic/BasicHttpAuthProperties.class")
                    exclude("com/mirth/connect/plugins/httpauth/digest/DigestHttpAuthProperties.class")
                    exclude("com/mirth/connect/plugins/httpauth/digest/DigestHttpAuthProperties\$Algorithm.class")
                    exclude("com/mirth/connect/plugins/httpauth/digest/DigestHttpAuthProperties\$QOPMode.class")
                    exclude("com/mirth/connect/plugins/httpauth/custom/CustomHttpAuthProperties.class")
                    exclude("com/mirth/connect/plugins/httpauth/javascript/JavaScriptHttpAuthProperties.class")
                    exclude("com/mirth/connect/plugins/httpauth/oauth2/OAuth2HttpAuthProperties.class")
                    exclude("com/mirth/connect/plugins/httpauth/oauth2/OAuth2HttpAuthProperties\$TokenLocation.class")
                }
                "globalmapviewer" -> {
                    include("com/mirth/connect/plugins/globalmapviewer/**")
                    exclude("com/mirth/connect/plugins/globalmapviewer/GlobalMapServletInterface.class")
                }
                "datapruner" -> {
                    include("com/mirth/connect/plugins/datapruner/**")
                    exclude("com/mirth/connect/plugins/datapruner/DataPrunerServletInterface.class")
                }
                "mllpmode" -> {
                    include("com/mirth/connect/plugins/mllpmode/**")
                    exclude("com/mirth/connect/plugins/mllpmode/MLLPModeProperties.class")
                }
                "serverlog" -> {
                    include("com/mirth/connect/plugins/serverlog/**")
                    exclude("com/mirth/connect/plugins/serverlog/ServerLogItem.class")
                    exclude("com/mirth/connect/plugins/serverlog/ServerLogServletInterface.class")
                }
            }
        }
        pluginTasks.add(createPluginServerJar)
    }
    
    val copyPluginXml = tasks.register<Copy>("copy${pluginName.capitalize()}Xml") {
        from(File(projectDir, "src/com/mirth/connect/plugins/${pluginName}"))
        into(File(projectDir, "build/extensions/${pluginName}"))
        include("*.xml")
    }
    pluginTasks.add(copyPluginXml)
    
    val copyPluginLib = tasks.register<Copy>("copy${pluginName.capitalize()}Lib") {
        from(File(projectDir, "lib/extensions/${pluginName}"))
        into(File(projectDir, "build/extensions/${pluginName}/lib"))
        include("*.jar")
    }
    pluginTasks.add(copyPluginLib)
}

// Special task for httpauth userutil sources
val createHttpauthUserutilSources = tasks.register<Jar>("createHttpauthUserutilSources") {
    archiveFileName.set("httpauth-userutil-sources.jar")
    destinationDirectory.set(File(projectDir, "build/extensions/httpauth/src"))
    from(File(projectDir, "src"))
    include("com/mirth/connect/plugins/httpauth/userutil/**")
}

// Copy setup files task
val copySetupFiles by tasks.registering(Copy::class) {
    dependsOn(createSetupDirs, ":donkey:copyToServer")
    duplicatesStrategy = DuplicatesStrategy.WARN
    
    // Copy lib files
    from(File(projectDir, "lib")) {
        exclude("ant/**")
        exclude("extensions/**")
        into("server-lib")
    }
    
    // Copy conf files
    from(File(projectDir, "conf")) {
        into("conf")
    }
    
    // Copy public html files
    from(File(projectDir, "public_html")) {
        exclude("Thumbs.db")
        into("public_html")
    }
    
    // Copy public API html files
    from(File(projectDir, "public_api_html")) {
        exclude("Thumbs.db")
        into("public_api_html")
    }
    
    // Copy docs files
    from(File(projectDir, "docs")) {
        into("docs")
    }
    
    into(File(projectDir, "setup"))
}

// Copy client JARs from client build
val copyClientJars by tasks.registering(Copy::class) {
    dependsOn(":client:buildClient")
    from(File(projectDir, "../client/dist")) {
        include("mirth-client.jar")
    }
    into(File(projectDir, "setup/client-lib"))
    
    // Also copy client extension JARs
    from(File(projectDir, "../client/dist/extensions")) {
        include("**/*-client.jar")
    }
    into(File(projectDir, "setup/client-lib"))
}

// Copy extensions to setup
val copyExtensionsToSetup by tasks.registering(Copy::class) {
    dependsOn(connectorTasks + datatypeTasks + pluginTasks + createHttpauthUserutilSources)
    from(File(projectDir, "build/extensions"))
    into(File(projectDir, "setup/extensions"))
}

// Replace version tokens in extensions
val replaceVersionTokens by tasks.registering {
    dependsOn(copyExtensionsToSetup)
    // Capture project version during configuration time
    val projectVersion = version.toString()
    
    doLast {
        fileTree(File(projectDir, "setup/extensions")).matching {
            include("**/*.xml")
        }.forEach { file ->
            val content = file.readText()
            file.writeText(content.replace("@mirthversion", projectVersion))
        }
        
        fileTree(File(projectDir, "setup/public_html")).matching {
            include("*.html")
        }.forEach { file ->
            val content = file.readText()
            file.writeText(content.replace("@mirthversion", projectVersion))
        }
    }
}

// Main build task that creates all JARs and setup
val createSetup by tasks.registering {
    dependsOn(
        createSetupDirs,
        createCryptoJar,
        createClientCoreJar,
        createServerJar,
        createVocabJar,
        createDbconfJar,
        createLauncherJar,
        createUserutilSourcesJar,
        copySetupFiles,
        copyClientJars,
        copyExtensionsToSetup,
        replaceVersionTokens
    )
}

// Test tasks
tasks.test {
    dependsOn(createSetup)
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Configure default JAR task to handle duplicates
tasks.jar {
    // Add explicit dependency on copyEdiXmlFiles task
    dependsOn("copyEdiXmlFiles")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Main build task
tasks.build {
    dependsOn(createSetup)
}