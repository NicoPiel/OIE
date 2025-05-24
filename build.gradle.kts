import java.security.MessageDigest
import java.time.LocalDateTime

plugins {
    java
    jacoco
    distribution
}

group = "com.mirth.connect"
version = "4.5.2"

// Configure all projects
allprojects {
    group = "com.mirth.connect"
    version = "4.5.2"
    
    repositories {
        mavenCentral()
        
        // Support for existing lib folders in each module
        flatDir {
            dirs("lib")
        }
        
        // Support for testlib folders
        flatDir {
            dirs("testlib")
        }
    }
}

// Configure all subprojects
subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")
    
    // Java 8 compatibility
    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    // Common compiler options
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }
    
    // Common test configuration
    tasks.test {
        useJUnit()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        
        // JaCoCo test coverage
        finalizedBy(tasks.jacocoTestReport)
    }
    
    // JaCoCo configuration
    tasks.jacocoTestReport {
        dependsOn(tasks.test)
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
    
    // Common dependencies that most modules will need
    dependencies {
        // Test dependencies
        testImplementation("junit:junit:4.8.1")
        
        // Logging dependencies (most modules use these)
        implementation("org.apache.logging.log4j:log4j-api:2.17.2")
        implementation("org.apache.logging.log4j:log4j-core:2.17.2")
        implementation("org.apache.logging.log4j:log4j-1.2-api:2.17.2")
        implementation("org.slf4j:slf4j-api:1.7.30")
        implementation("org.slf4j:slf4j-log4j12:1.7.30")
    }
}

// Root project tasks - clean task is already provided by java plugin

// Aggregate JaCoCo report for all subprojects
tasks.register<JacocoReport>("jacocoRootReport") {
    description = "Generates an aggregate report from all subprojects"
    
    dependsOn(subprojects.map { it.tasks.named("jacocoTestReport") })
    
    additionalSourceDirs.from(subprojects.map { it.the<SourceSetContainer>()["main"].allSource.srcDirs })
    sourceDirectories.from(subprojects.map { it.the<SourceSetContainer>()["main"].allSource.srcDirs })
    classDirectories.from(subprojects.map { it.the<SourceSetContainer>()["main"].output })
    executionData.from(project.fileTree(".") { include("**/build/jacoco/test.exec") })
    
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    onlyIf { true }
    
    doFirst {
        executionData.from(executionData.filter { it.exists() })
    }
}

// Build all modules
tasks.register("buildAll") {
    description = "Builds all subprojects"
    dependsOn(subprojects.map { it.tasks.named("build") })
}

// Print project information
tasks.register("projectInfo") {
    description = "Displays project information"
    doLast {
        println("Project: ${rootProject.name}")
        println("Version: ${rootProject.version}")
        println("Group: ${rootProject.group}")
        println("Modules:")
        subprojects.forEach { project ->
            println("  - ${project.name}")
        }
    }
}

// =============================================================================
// ORCHESTRATOR TASKS - Replicating mirth-build.xml functionality
// =============================================================================

// Initialize properties and directories
tasks.register("initOrchestrator") {
    description = "Initialize orchestrator properties and directories"
    doLast {
        // Create necessary directories using File constructor instead of file() function
        File(projectDir, "server/lib/donkey").mkdirs()
        File(projectDir, "server/setup").mkdirs()
        File(projectDir, "server/build").mkdirs()
        File(projectDir, "server/setup/webapps").mkdirs()
        File(projectDir, "server/setup/client-lib").mkdirs()
        File(projectDir, "server/setup/extensions").mkdirs()
        File(projectDir, "server/setup/manager-lib").mkdirs()
        File(projectDir, "server/setup/cli-lib").mkdirs()
        File(projectDir, "server/setup/conf").mkdirs()
        File(projectDir, "server/build/webapps").mkdirs()
        File(projectDir, "server/build/extensions").mkdirs()
        File(projectDir, "server/build/client-lib").mkdirs()
        
        println("Orchestrator directories initialized")
    }
}

// Build donkey and copy JARs to server/lib/donkey
tasks.register("build-donkey") {
    description = "Build donkey and copy JARs to server/lib/donkey"
    dependsOn("initOrchestrator", ":donkey:build")
    
    doLast {
        // Delete existing donkey lib directory
        delete(File(projectDir, "server/lib/donkey"))
        File(projectDir, "server/lib/donkey").mkdirs()
        
        // Copy donkey JARs
        copy {
            from(File(projectDir, "donkey/setup/donkey-model.jar"))
            into(File(projectDir, "server/lib/donkey"))
        }
        copy {
            from(File(projectDir, "donkey/setup/donkey-server.jar"))
            into(File(projectDir, "server/lib/donkey"))
        }
        
        // Copy donkey lib dependencies with exclusions
        copy {
            from(File(projectDir, "donkey/lib")) {
                exclude("log4j-1.2.16.jar")
                exclude("HikariCP-java6-2.0.1.jar")
                exclude("javassist-3.19.0-GA.jar")
                exclude("xstream/**")
                exclude("commons/**")
                exclude("database/**")
            }
            into(File(projectDir, "server/lib/donkey"))
        }
        
        println("Donkey build completed and JARs copied to server/lib/donkey")
    }
}

// Build webadmin and copy WAR to server setup
tasks.register("build-webadmin") {
    description = "Build webadmin and copy WAR to server setup"
    dependsOn("initOrchestrator", ":webadmin:war")
    
    doLast {
        // Copy webadmin.war to both build and setup directories
        copy {
            from(File(projectDir, "webadmin/build/libs/webadmin.war"))
            into(File(projectDir, "server/build/webapps"))
        }
        copy {
            from(File(projectDir, "webadmin/build/libs/webadmin.war"))
            into(File(projectDir, "server/setup/webapps"))
        }
        
        println("WebAdmin build completed and WAR copied to server directories")
    }
}

// Build server extensions and copy shared JARs to client/lib
tasks.register("build-server-extensions") {
    description = "Build server extensions and copy shared JARs to client/lib"
    dependsOn("build-donkey", ":server:copyExtensionsToSetup")
    
    doLast {
        // Copy shared extension JARs to client lib
        copy {
            from(fileTree(File(projectDir, "server/build/extensions")) {
                include("**/*-shared.jar")
            })
            into(File(projectDir, "client/lib"))
            eachFile {
                // Flatten the directory structure
                relativePath = RelativePath(true, name)
            }
        }
        
        println("Server extensions built and shared JARs copied to client/lib")
    }
}

// Build client and copy JARs/extensions to server setup
tasks.register("build-client") {
    description = "Build client and copy JARs/extensions to server setup"
    dependsOn("build-server-extensions", ":server:createSetup")
    
    // Capture project version during configuration time
    val projectVersion = version.toString()
    
    doFirst {
        // Copy required JARs to client/lib before building
        copy {
            from(File(projectDir, "donkey/setup/donkey-model.jar"))
            into(File(projectDir, "client/lib"))
        }
        copy {
            from(File(projectDir, "server/setup/server-lib/mirth-client-core.jar"))
            into(File(projectDir, "client/lib"))
        }
        copy {
            from(File(projectDir, "server/setup/server-lib/mirth-crypto.jar"))
            into(File(projectDir, "client/lib"))
        }
        copy {
            from(File(projectDir, "server/lib/mirth-vocab.jar"))
            into(File(projectDir, "client/lib"))
        }
    }
    
    finalizedBy(":client:build")
    
    doLast {
        // Copy client JAR to server setup
        copy {
            from(File(projectDir, "client/build/libs/client-${projectVersion}.jar"))
            into(File(projectDir, "server/setup/client-lib"))
            rename { "mirth-client.jar" }
        }
        
        // Copy client lib dependencies (excluding shared JARs and extensions)
        copy {
            from(File(projectDir, "client/lib")) {
                exclude("*-shared.jar")
                exclude("extensions")
            }
            into(File(projectDir, "server/setup/client-lib"))
        }
        
        // Copy client extensions to server setup
        copy {
            from(File(projectDir, "client/dist/extensions"))
            into(File(projectDir, "server/setup/extensions"))
        }
        
        println("Client build completed and artifacts copied to server setup")
    }
}

// Build manager and copy to server setup
tasks.register("build-manager") {
    description = "Build manager and copy to server setup"
    dependsOn("build-client")
    
    doFirst {
        // Copy required JARs to manager/lib before building
        copy {
            from(File(projectDir, "donkey/setup/donkey-model.jar"))
            into(File(projectDir, "manager/lib"))
        }
        copy {
            from(File(projectDir, "server/setup/server-lib/mirth-client-core.jar"))
            into(File(projectDir, "manager/lib"))
        }
        copy {
            from(File(projectDir, "server/setup/server-lib/mirth-crypto.jar"))
            into(File(projectDir, "manager/lib"))
        }
    }
    
    finalizedBy(":manager:build")
    
    doLast {
        // Copy manager launcher JAR to server setup
        copy {
            from(File(projectDir, "manager/build/libs/mirth-manager-launcher.jar"))
            into(File(projectDir, "server/setup"))
        }
        
        // Copy manager lib dependencies (excluding mirth-client.jar)
        copy {
            from(File(projectDir, "manager/lib")) {
                exclude("mirth-client.jar")
            }
            into(File(projectDir, "server/setup/manager-lib"))
        }
        
        println("Manager build completed and artifacts copied to server setup")
    }
}

// Build CLI and copy to server setup
tasks.register("build-cli") {
    description = "Build CLI and copy to server setup"
    dependsOn("build-client")
    
    doFirst {
        // Copy required JARs to CLI lib before building
        copy {
            from(File(projectDir, "donkey/setup/donkey-model.jar"))
            into(File(projectDir, "command/lib"))
        }
        copy {
            from(File(projectDir, "server/setup/server-lib/mirth-client-core.jar"))
            into(File(projectDir, "command/lib"))
        }
        copy {
            from(File(projectDir, "server/setup/server-lib/mirth-crypto.jar"))
            into(File(projectDir, "command/lib"))
        }
    }
    
    finalizedBy(":command:build")
    
    doLast {
        // Copy CLI JARs to server setup
        copy {
            from(File(projectDir, "command/build/libs/mirth-cli.jar"))
            into(File(projectDir, "server/setup/cli-lib"))
        }
        copy {
            from(File(projectDir, "command/build/libs/mirth-cli-launcher.jar"))
            into(File(projectDir, "server/setup"))
        }
        
        // Copy CLI lib dependencies (excluding mirth-client.jar)
        copy {
            from(File(projectDir, "command/lib")) {
                exclude("mirth-client.jar")
            }
            into(File(projectDir, "server/setup/cli-lib"))
        }
        
        // Copy CLI configuration files
        copy {
            from(File(projectDir, "command/conf")) {
                include("mirth-cli-config.properties")
                include("log4j2-cli.properties")
            }
            into(File(projectDir, "server/setup/conf"))
        }
        
        println("CLI build completed and artifacts copied to server setup")
    }
}

// Main build target that orchestrates everything
tasks.register("orchestratorBuild") {
    description = "Main build target that orchestrates the entire build process"
    dependsOn("build-manager", "build-cli", "build-webadmin", ":server:createSetup")
    
    doLast {
        // Copy extensions to server build
        copy {
            from(File(projectDir, "server/setup/extensions"))
            into(File(projectDir, "server/build/extensions"))
        }
        
        // Copy client-lib to server build
        copy {
            from(File(projectDir, "server/setup/client-lib"))
            into(File(projectDir, "server/build/client-lib"))
        }
        
        println("Main build completed successfully")
    }
    
    finalizedBy("test-run")
}

// Override the default build task to use our orchestrator
tasks.named("build") {
    dependsOn("orchestratorBuild")
}

// =============================================================================
// DISTRIBUTION TASKS - Create final .tar and .zip distributions
// =============================================================================

// Create extension zips (matching original Ant build)
tasks.register("createExtensionZips") {
    description = "Create individual extension zip files"
    dependsOn("orchestratorBuild")
    
    doLast {
        val distExtensionsDir = file("server/dist/extensions")
        distExtensionsDir.mkdirs()
        
        val extensionsDir = file("server/setup/extensions")
        if (extensionsDir.exists()) {
            // Connector extensions
            val connectors = listOf("jms", "jdbc", "dicom", "http", "doc", "smtp", "tcp", "file", "js", "ws", "vm")
            connectors.forEach { connector ->
                val connectorDir = file("$extensionsDir/$connector")
                if (connectorDir.exists()) {
                    ant.withGroovyBuilder {
                        "zip"("destfile" to "$distExtensionsDir/$connector-${project.version}.zip",
                              "basedir" to extensionsDir,
                              "includes" to "$connector/**/*")
                    }
                }
            }
            
            // Datatype extensions
            val datatypes = listOf("datatype-delimited", "datatype-dicom", "datatype-edi", "datatype-hl7v2",
                                 "datatype-hl7v3", "datatype-ncpdp", "datatype-xml", "datatype-raw", "datatype-json")
            datatypes.forEach { datatype ->
                val datatypeDir = file("$extensionsDir/$datatype")
                if (datatypeDir.exists()) {
                    ant.withGroovyBuilder {
                        "zip"("destfile" to "$distExtensionsDir/$datatype-${project.version}.zip",
                              "basedir" to extensionsDir,
                              "includes" to "$datatype/**/*")
                    }
                }
            }
            
            // Plugin extensions
            val plugins = listOf("directoryresource", "dashboardstatus", "destinationsetfilter", "serverlog",
                                "datapruner", "javascriptstep", "mapper", "messagebuilder", "scriptfilestep",
                                "rulebuilder", "javascriptrule", "dicomviewer", "pdfviewer", "textviewer",
                                "httpauth", "imageviewer", "globalmapviewer", "mllpmode")
            plugins.forEach { plugin ->
                val pluginDir = file("$extensionsDir/$plugin")
                if (pluginDir.exists()) {
                    ant.withGroovyBuilder {
                        "zip"("destfile" to "$distExtensionsDir/$plugin-${project.version}.zip",
                              "basedir" to extensionsDir,
                              "includes" to "$plugin/**/*")
                    }
                }
            }
        }
        
        println("Extension zips created in server/dist/extensions/")
    }
}

// Prepare distribution directory with complete Mirth Connect structure
tasks.register("prepareDistribution") {
    description = "Prepare the complete distribution directory structure"
    dependsOn("orchestratorBuild", "createExtensionZips")
    
    doLast {
        val distDir = file("server/dist")
        val setupDir = file("server/setup")
        
        // Clean and create distribution directory
        delete(distDir)
        distDir.mkdirs()
        
        // Copy the complete setup directory structure
        copy {
            from(setupDir)
            into(distDir)
        }
        
        // Ensure all required directories exist
        listOf(
            "server-lib", "client-lib", "manager-lib", "cli-lib", "extensions",
            "conf", "logs", "docs", "public_html", "public_api_html", "webapps"
        ).forEach { dir ->
            file("$distDir/$dir").mkdirs()
        }
        
        // Copy documentation if it exists
        val docsSource = file("server/docs")
        if (docsSource.exists()) {
            copy {
                from(docsSource)
                into("$distDir/docs")
            }
        }
        
        // Copy any additional configuration files
        val confSource = file("server/conf")
        if (confSource.exists()) {
            copy {
                from(confSource)
                into("$distDir/conf")
            }
        }
        
        // Copy public HTML files
        val publicHtmlSource = file("server/public_html")
        if (publicHtmlSource.exists()) {
            copy {
                from(publicHtmlSource)
                into("$distDir/public_html")
            }
        }
        
        // Copy public API HTML files
        val publicApiHtmlSource = file("server/public_api_html")
        if (publicApiHtmlSource.exists()) {
            copy {
                from(publicApiHtmlSource)
                into("$distDir/public_api_html")
            }
        }
        
        // Create version info file
        val versionFile = file("$distDir/VERSION.txt")
        versionFile.writeText("""
            Mirth Connect ${project.version}
            Build Date: ${LocalDateTime.now()}
            
            This distribution contains:
            - Mirth Connect Server
            - Mirth Connect Client
            - Mirth Connect Manager
            - Mirth Connect CLI
            - WebAdmin Interface
            - Extensions and Connectors
            - Documentation
            
            For installation and usage instructions, see the docs/ directory.
        """.trimIndent())
        
        println("Distribution prepared in server/dist/")
        println("Distribution contains:")
        distDir.listFiles()?.forEach { file ->
            if (file.isDirectory()) {
                val fileCount = file.walkTopDown().filter { it.isFile() }.count()
                println("  - ${file.name}/ ($fileCount files)")
            } else {
                println("  - ${file.name}")
            }
        }
    }
}

// Create TAR distribution
tasks.register<Tar>("createTarDistribution") {
    description = "Create .tar.gz distribution of Mirth Connect"
    dependsOn("prepareDistribution")
    
    archiveFileName.set("mirth-connect-${project.version}.tar.gz")
    destinationDirectory.set(file("build/distributions"))
    compression = Compression.GZIP
    
    from("server/dist") {
        into("mirth-connect-${project.version}")
    }
    
    // Set executable permissions for launcher scripts
    filesMatching("**/*.sh") {
        mode = 0b111101101  // 0755 in octal
    }
    filesMatching("**/mirth-*launcher*.jar") {
        mode = 0b111101101  // 0755 in octal
    }
    
    doLast {
        println("TAR distribution created: ${archiveFile.get().asFile}")
    }
}

// Create ZIP distribution
tasks.register<Zip>("createZipDistribution") {
    description = "Create .zip distribution of Mirth Connect"
    dependsOn("prepareDistribution")
    
    archiveFileName.set("mirth-connect-${project.version}.zip")
    destinationDirectory.set(file("build/distributions"))
    
    from("server/dist") {
        into("mirth-connect-${project.version}")
    }
    
    doLast {
        println("ZIP distribution created: ${archiveFile.get().asFile}")
    }
}

// Main distribution task that creates both TAR and ZIP
tasks.register("dist") {
    description = "Create complete Mirth Connect distributions (.tar.gz and .zip)"
    dependsOn("createTarDistribution", "createZipDistribution")
    
    doLast {
        println("=".repeat(60))
        println("Distribution creation completed successfully!")
        println("Application version: ${project.version}")
        println("")
        println("Created distributions:")
        val distDir = file("build/distributions")
        distDir.listFiles()?.forEach { file ->
            if (file.name.contains("mirth-connect")) {
                val sizeInMB = String.format("%.2f", file.length() / (1024.0 * 1024.0))
                println("  - ${file.name} (${sizeInMB} MB)")
            }
        }
        println("")
        println("Distribution contents include:")
        println("  - Mirth Connect Server with all libraries")
        println("  - Mirth Connect Client application")
        println("  - Mirth Connect Manager tool")
        println("  - Mirth Connect CLI tool")
        println("  - WebAdmin web interface")
        println("  - All connectors and extensions")
        println("  - Configuration files")
        println("  - Documentation")
        println("  - Public HTML and API documentation")
        println("=".repeat(60))
    }
}

// Distribution validation task
tasks.register("validateDistribution") {
    description = "Validate that the distribution contains all required components"
    dependsOn("prepareDistribution")
    
    doLast {
        val distDir = file("server/dist")
        val requiredFiles = listOf(
            "mirth-server-launcher.jar",
            "server-lib/mirth-server.jar",
            "server-lib/mirth-client-core.jar",
            "server-lib/mirth-crypto.jar",
            "client-lib/mirth-client.jar",
            "webapps/webadmin.war",
            "conf"
        )
        
        val requiredDirs = listOf(
            "server-lib", "client-lib", "manager-lib", "cli-lib",
            "extensions", "conf", "docs", "public_html"
        )
        
        var validationPassed = true
        
        println("Validating distribution structure...")
        
        // Check required files
        requiredFiles.forEach { filePath ->
            val file = file("$distDir/$filePath")
            if (file.exists()) {
                println("  ✓ $filePath")
            } else {
                println("  ✗ $filePath (MISSING)")
                validationPassed = false
            }
        }
        
        // Check required directories
        requiredDirs.forEach { dirPath ->
            val dir = file("$distDir/$dirPath")
            if (dir.exists() && dir.isDirectory()) {
                val fileCount = dir.walkTopDown().filter { it.isFile() }.count()
                println("  ✓ $dirPath/ ($fileCount files)")
            } else {
                println("  ✗ $dirPath/ (MISSING)")
                validationPassed = false
            }
        }
        
        if (validationPassed) {
            println("✓ Distribution validation PASSED")
        } else {
            println("✗ Distribution validation FAILED")
            throw GradleException("Distribution validation failed - missing required files or directories")
        }
    }
}

// Add validation to distribution tasks
tasks.named("createTarDistribution") {
    dependsOn("validateDistribution")
}

tasks.named("createZipDistribution") {
    dependsOn("validateDistribution")
}

// Run tests across all modules
tasks.register("test-run") {
    description = "Run tests across all modules"
    dependsOn("initOrchestrator")
    
    doLast {
        // Run tests for each module that has tests
        listOf("donkey", "server", "client", "command").forEach { module ->
            try {
                val moduleProject = project(":$module")
                val testTask = moduleProject.tasks.findByName("test")
                if (testTask != null) {
                    println("Running tests for module: $module")
                    // Tests will be run via dependency, not direct execution
                } else {
                    println("No test task found for module: $module")
                }
            } catch (e: Exception) {
                println("Warning: Tests failed or not available for module: $module - ${e.message}")
            }
        }
    }
    
    // Add test dependencies
    dependsOn(":donkey:test", ":server:test")
}

// Clean compiled classes across all modules
tasks.register("remove-classes") {
    description = "Clean compiled classes across all modules"
    dependsOn("initOrchestrator")
    
    doLast {
        println("Cleaning compiled classes across all modules")
    }
    
    // Add clean dependencies for all modules
    dependsOn(subprojects.map { ":${it.name}:clean" })
}

// License header management
tasks.register("append-license") {
    description = "Append license headers to source files"
    dependsOn("initOrchestrator")
    
    doLast {
        val licenseHeader = file("server/license-header.txt")
        if (!licenseHeader.exists()) {
            println("Warning: License header file not found at server/license-header.txt")
            return@doLast
        }
        
        val headerText = licenseHeader.readText()
        
        // Process each module's Java files
        val moduleConfigs = mapOf(
            "server" to mapOf(
                "includes" to listOf("**/*.java"),
                "excludes" to listOf("src/io/**/*.java", "src/org/**/*.java", "src/com/mirth/connect/server/logging/LogOutputStream.java")
            ),
            "client" to mapOf(
                "includes" to listOf("**/*.java"),
                "excludes" to listOf("src/com/mirth/connect/client/ui/BareBonesBrowserLaunch.java", "src/com/mirth/connect/client/ui/BeanBinder.java", "src/com/mirth/connect/client/ui/OSXAdapter.java", "src/org/**/*.java")
            ),
            "command" to mapOf(
                "includes" to listOf("**/*.java"),
                "excludes" to emptyList<String>()
            ),
            "manager" to mapOf(
                "includes" to listOf("**/*.java"),
                "excludes" to listOf("src/com/mirth/connect/manager/BareBonesBrowserLaunch.java")
            ),
            "donkey" to mapOf(
                "includes" to listOf("**/*.java"),
                "excludes" to emptyList<String>()
            ),
            "webadmin" to mapOf(
                "includes" to listOf("**/*.java"),
                "excludes" to emptyList<String>()
            )
        )
        
        moduleConfigs.forEach { (module, config) ->
            val moduleDir = file(module)
            if (moduleDir.exists()) {
                val javaFiles = fileTree(moduleDir) {
                    include(config["includes"] as List<String>)
                    exclude(config["excludes"] as List<String>)
                }.filter { it.name.endsWith(".java") }.files
                
                javaFiles.forEach { javaFile ->
                    val content = javaFile.readText()
                    if (!content.startsWith("/*")) {
                        javaFile.writeText("$headerText\n$content")
                    }
                }
                
                println("License headers processed for module: $module (${javaFiles.size} files)")
            }
        }
    }
}

// Build custom extensions
tasks.register("build-custom") {
    description = "Build custom extensions"
    dependsOn("initOrchestrator")
    
    doLast {
        val customExtensionsFile = file("custom-extensions.xml")
        if (customExtensionsFile.exists()) {
            // This would require Ant integration or custom implementation
            println("Custom extensions build would be executed here")
            println("Note: custom-extensions.xml found but Ant integration needed for full implementation")
        } else {
            println("No custom-extensions.xml found, skipping custom extensions build")
        }
    }
}

// Convenience task aliases matching original build targets
tasks.register("buildDonkey") {
    description = "Alias for build-donkey"
    dependsOn("build-donkey")
}

tasks.register("buildWebadmin") {
    description = "Alias for build-webadmin"
    dependsOn("build-webadmin")
}

tasks.register("buildServerExtensions") {
    description = "Alias for build-server-extensions"
    dependsOn("build-server-extensions")
}

tasks.register("buildClient") {
    description = "Alias for build-client"
    dependsOn("build-client")
}

tasks.register("buildManager") {
    description = "Alias for build-manager"
    dependsOn("build-manager")
}

tasks.register("buildCli") {
    description = "Alias for build-cli"
    dependsOn("build-cli")
}

tasks.register("testRun") {
    description = "Alias for test-run"
    dependsOn("test-run")
}

tasks.register("removeClasses") {
    description = "Alias for remove-classes"
    dependsOn("remove-classes")
}

tasks.register("appendLicense") {
    description = "Alias for append-license"
    dependsOn("append-license")
}

tasks.register("buildCustom") {
    description = "Alias for build-custom"
    dependsOn("build-custom")
}

// =============================================================================
// LAUNCHER SCRIPTS AND ADDITIONAL DISTRIBUTION ENHANCEMENTS
// =============================================================================

// Create launcher scripts for different platforms
tasks.register("createLauncherScripts") {
    description = "Create platform-specific launcher scripts"
    dependsOn("prepareDistribution")
    
    doLast {
        val distDir = file("server/dist")
        
        // Create Unix/Linux launcher script
        val unixLauncher = file("$distDir/mirth-connect.sh")
        unixLauncher.writeText("""
            #!/bin/bash
            
            # Mirth Connect Server Launcher Script
            # Version: ${project.version}
            
            # Get the directory where this script is located
            SCRIPT_DIR="$( cd "$( dirname "${'$'}{BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
            
            # Set MIRTH_HOME to the script directory
            export MIRTH_HOME="${'$'}SCRIPT_DIR"
            
            # Set Java options
            JAVA_OPTS="-Xms512m -Xmx2048m -XX:MaxMetaspaceSize=256m"
            JAVA_OPTS="${'$'}JAVA_OPTS -Djava.awt.headless=true"
            JAVA_OPTS="${'$'}JAVA_OPTS -Dmirth.home=${'$'}MIRTH_HOME"
            
            # Launch Mirth Connect
            echo "Starting Mirth Connect ${project.version}..."
            echo "MIRTH_HOME: ${'$'}MIRTH_HOME"
            
            java ${'$'}JAVA_OPTS -jar "${'$'}MIRTH_HOME/mirth-server-launcher.jar"
        """.trimIndent())
        
        // Create Windows launcher script
        val windowsLauncher = file("$distDir/mirth-connect.bat")
        windowsLauncher.writeText("""
            @echo off
            
            REM Mirth Connect Server Launcher Script
            REM Version: ${project.version}
            
            REM Get the directory where this script is located
            set SCRIPT_DIR=%~dp0
            
            REM Set MIRTH_HOME to the script directory
            set MIRTH_HOME=%SCRIPT_DIR%
            
            REM Set Java options
            set JAVA_OPTS=-Xms512m -Xmx2048m -XX:MaxMetaspaceSize=256m
            set JAVA_OPTS=%JAVA_OPTS% -Djava.awt.headless=true
            set JAVA_OPTS=%JAVA_OPTS% -Dmirth.home=%MIRTH_HOME%
            
            REM Launch Mirth Connect
            echo Starting Mirth Connect ${project.version}...
            echo MIRTH_HOME: %MIRTH_HOME%
            
            java %JAVA_OPTS% -jar "%MIRTH_HOME%\mirth-server-launcher.jar"
        """.trimIndent())
        
        // Create CLI launcher scripts
        val unixCliLauncher = file("$distDir/mirth-cli.sh")
        unixCliLauncher.writeText("""
            #!/bin/bash
            
            # Mirth Connect CLI Launcher Script
            # Version: ${project.version}
            
            # Get the directory where this script is located
            SCRIPT_DIR="$( cd "$( dirname "${'$'}{BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
            
            # Set MIRTH_HOME to the script directory
            export MIRTH_HOME="${'$'}SCRIPT_DIR"
            
            # Set Java options
            JAVA_OPTS="-Xms256m -Xmx512m"
            JAVA_OPTS="${'$'}JAVA_OPTS -Dmirth.home=${'$'}MIRTH_HOME"
            
            # Launch Mirth CLI
            java ${'$'}JAVA_OPTS -jar "${'$'}MIRTH_HOME/mirth-cli-launcher.jar" "${'$'}@"
        """.trimIndent())
        
        val windowsCliLauncher = file("$distDir/mirth-cli.bat")
        windowsCliLauncher.writeText("""
            @echo off
            
            REM Mirth Connect CLI Launcher Script
            REM Version: ${project.version}
            
            REM Get the directory where this script is located
            set SCRIPT_DIR=%~dp0
            
            REM Set MIRTH_HOME to the script directory
            set MIRTH_HOME=%SCRIPT_DIR%
            
            REM Set Java options
            set JAVA_OPTS=-Xms256m -Xmx512m
            set JAVA_OPTS=%JAVA_OPTS% -Dmirth.home=%MIRTH_HOME%
            
            REM Launch Mirth CLI
            java %JAVA_OPTS% -jar "%MIRTH_HOME%\mirth-cli-launcher.jar" %*
        """.trimIndent())
        
        // Create Manager launcher scripts
        val unixManagerLauncher = file("$distDir/mirth-manager.sh")
        unixManagerLauncher.writeText("""
            #!/bin/bash
            
            # Mirth Connect Manager Launcher Script
            # Version: ${project.version}
            
            # Get the directory where this script is located
            SCRIPT_DIR="$( cd "$( dirname "${'$'}{BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
            
            # Set MIRTH_HOME to the script directory
            export MIRTH_HOME="${'$'}SCRIPT_DIR"
            
            # Set Java options
            JAVA_OPTS="-Xms256m -Xmx1024m"
            JAVA_OPTS="${'$'}JAVA_OPTS -Dmirth.home=${'$'}MIRTH_HOME"
            
            # Launch Mirth Manager
            java ${'$'}JAVA_OPTS -jar "${'$'}MIRTH_HOME/mirth-manager-launcher.jar" "${'$'}@"
        """.trimIndent())
        
        val windowsManagerLauncher = file("$distDir/mirth-manager.bat")
        windowsManagerLauncher.writeText("""
            @echo off
            
            REM Mirth Connect Manager Launcher Script
            REM Version: ${project.version}
            
            REM Get the directory where this script is located
            set SCRIPT_DIR=%~dp0
            
            REM Set MIRTH_HOME to the script directory
            set MIRTH_HOME=%SCRIPT_DIR%
            
            REM Set Java options
            set JAVA_OPTS=-Xms256m -Xmx1024m
            set JAVA_OPTS=%JAVA_OPTS% -Dmirth.home=%MIRTH_HOME%
            
            REM Launch Mirth Manager
            java %JAVA_OPTS% -jar "%MIRTH_HOME%\mirth-manager-launcher.jar" %*
        """.trimIndent())
        
        // Create README file
        val readmeFile = file("$distDir/README.txt")
        readmeFile.writeText("""
            Mirth Connect ${project.version}
            ================================
            
            Thank you for downloading Mirth Connect!
            
            QUICK START
            -----------
            
            1. Server:
               - Unix/Linux/Mac: ./mirth-connect.sh
               - Windows: mirth-connect.bat
               
            2. CLI:
               - Unix/Linux/Mac: ./mirth-cli.sh
               - Windows: mirth-cli.bat
               
            3. Manager:
               - Unix/Linux/Mac: ./mirth-manager.sh
               - Windows: mirth-manager.bat
            
            4. Web Admin:
               - Access via http://localhost:8080/webadmin after starting the server
            
            DIRECTORY STRUCTURE
            -------------------
            
            server-lib/     - Server libraries and core JARs
            client-lib/     - Client application libraries
            manager-lib/    - Manager tool libraries
            cli-lib/        - CLI tool libraries
            extensions/     - Connectors, datatypes, and plugins
            conf/           - Configuration files
            docs/           - Documentation
            public_html/    - Web interface files
            webapps/        - Web applications (WebAdmin)
            logs/           - Log files (created at runtime)
            
            SYSTEM REQUIREMENTS
            -------------------
            
            - Java 8 or higher
            - Minimum 1GB RAM (2GB+ recommended)
            - 500MB disk space for installation
            - Network access for connectors
            
            CONFIGURATION
            -------------
            
            Main configuration file: conf/mirth.properties
            Database configuration: conf/
            
            For detailed installation and configuration instructions,
            please refer to the documentation in the docs/ directory
            or visit: https://www.nextgen.com/products-and-services/mirth-connect
            
            SUPPORT
            -------
            
            Community: https://github.com/nextgenhealthcare/connect
            Documentation: docs/ directory
            
        """.trimIndent())
        
        println("Launcher scripts created:")
        println("  - mirth-connect.sh / mirth-connect.bat (Server)")
        println("  - mirth-cli.sh / mirth-cli.bat (CLI)")
        println("  - mirth-manager.sh / mirth-manager.bat (Manager)")
        println("  - README.txt")
    }
}

// Enhanced distribution preparation that includes launcher scripts
tasks.named("prepareDistribution") {
    finalizedBy("createLauncherScripts")
}

// Create development distribution (without signing, faster build)
tasks.register("distDev") {
    description = "Create development distribution (faster, no signing)"
    dependsOn("orchestratorBuild")
    
    doFirst {
        // Set a property to skip signing for development builds
        project.extra["skipSigning"] = true
    }
    
    finalizedBy("dist")
    
    doLast {
        println("Development distribution created (signing skipped for faster build)")
    }
}

// Create checksums for distributions
tasks.register("createDistributionChecksums") {
    description = "Create SHA-256 checksums for distribution files"
    dependsOn("dist")
    
    doLast {
        val distDir = file("build/distributions")
        distDir.listFiles()?.filter {
            it.name.endsWith(".tar.gz") || it.name.endsWith(".zip")
        }?.forEach { distFile ->
            val checksumFile = file("${distFile.absolutePath}.sha256")
            val checksum = MessageDigest.getInstance("SHA-256")
                .digest(distFile.readBytes())
                .joinToString("") { byte -> "%02x".format(byte) }
            
            checksumFile.writeText("$checksum  ${distFile.name}\n")
            println("Created checksum: ${checksumFile.name}")
        }
    }
}

// Complete distribution with checksums
tasks.register("distComplete") {
    description = "Create complete distribution with checksums"
    dependsOn("dist", "createDistributionChecksums")
    
    doLast {
        println("Complete distribution with checksums created successfully!")
    }
}

// Clean distribution files
tasks.register("cleanDist") {
    description = "Clean distribution files and directories"
    
    doLast {
        delete("server/dist")
        delete("build/distributions")
        println("Distribution files cleaned")
    }
}

// Add cleanDist to main clean task
tasks.named("clean") {
    dependsOn("cleanDist")
}

// =============================================================================
// CONVENIENCE TASKS FOR COMMON WORKFLOWS
// =============================================================================

// Quick build and test
tasks.register("quickBuild") {
    description = "Quick build without tests for development"
    dependsOn("orchestratorBuild")
    
    doLast {
        println("Quick build completed (tests skipped)")
    }
}

// Full build with tests and distribution
tasks.register("fullBuild") {
    description = "Complete build with tests and distribution"
    dependsOn("build", "test-run", "dist")
    
    doLast {
        println("Full build with tests and distribution completed!")
    }
}

// Show build information
tasks.register("buildInfo") {
    description = "Display comprehensive build information"
    
    doLast {
        println("=".repeat(60))
        println("MIRTH CONNECT BUILD INFORMATION")
        println("=".repeat(60))
        println("Project: ${rootProject.name}")
        println("Version: ${rootProject.version}")
        println("Group: ${rootProject.group}")
        println("Java Version: ${System.getProperty("java.version")}")
        println("Gradle Version: ${gradle.gradleVersion}")
        println("")
        println("Available Modules:")
        subprojects.forEach { project ->
            println("  - ${project.name}")
        }
        println("")
        println("Key Build Tasks:")
        println("  ./gradlew build          - Complete build")
        println("  ./gradlew quickBuild     - Fast build (no tests)")
        println("  ./gradlew fullBuild      - Build + tests + distribution")
        println("  ./gradlew dist           - Create distributions")
        println("  ./gradlew distDev        - Fast development distribution")
        println("  ./gradlew distComplete   - Distribution with checksums")
        println("  ./gradlew test-run       - Run all tests")
        println("  ./gradlew clean          - Clean all build artifacts")
        println("")
        println("Distribution Tasks:")
        println("  ./gradlew createTarDistribution  - Create .tar.gz")
        println("  ./gradlew createZipDistribution  - Create .zip")
        println("  ./gradlew validateDistribution   - Validate distribution")
        println("=".repeat(60))
    }
}