/*
 * Root build file
 *
 * For more detailed information on multi-project builds, please refer to
 * https://docs.gradle.org/9.2.1/userguide/multi_project_builds.html
 */

// Configure Ant to have access to JUnit task

ant.lifecycleLogLevel = AntBuilder.AntMessagePriority.INFO

configurations {
    create("antJUnit")
}

dependencies {
    // ant-junit4 is required for JUnit 4 annotation support (@Test, etc.)
    "antJUnit"("org.apache.ant:ant-junit:1.10.15") {
        exclude(group = "junit", module = "junit")
    }
    "antJUnit"("org.apache.ant:ant-junit4:1.10.15") {
        exclude(group = "junit", module = "junit")
    }
}

repositories {
    mavenCentral()
}

// Make JUnit available to Ant
afterEvaluate {
    ant.withGroovyBuilder {
        "taskdef"(
            "name" to "junit",
            "classname" to "org.apache.tools.ant.taskdefs.optional.junit.JUnitTask",
            "classpath" to configurations["antJUnit"].asPath
        )
        "taskdef"(
            "name" to "junitreport",
            "classname" to "org.apache.tools.ant.taskdefs.optional.junit.XMLResultAggregator",
            "classpath" to configurations["antJUnit"].asPath
        )
    }
}

// Pass Gradle properties to Ant
project.findProperty("disableSigning")?.let {
    ant.properties["disableSigning"] = it.toString()
}

project.findProperty("disableTests")?.let {
    ant.properties["disableTests"] = it.toString()
}

// Define properties from mirth-build.properties, adjusted for root directory
ant.properties["donkey"] = project.file("donkey").absolutePath
ant.properties["server"] = project.file("server").absolutePath
ant.properties["client"] = project.file("client").absolutePath
ant.properties["webadmin"] = project.file("webadmin").absolutePath
ant.properties["manager"] = project.file("manager").absolutePath
ant.properties["cli"] = project.file("command").absolutePath
ant.properties["version"] = "4.5.2"

// Derived properties
ant.properties["donkey.setup"] = "${ant.properties["donkey"]}/setup"
ant.properties["server.setup"] = "${ant.properties["server"]}/setup"
ant.properties["server.build"] = "${ant.properties["server"]}/build"

tasks.register("build-donkey") {
    doLast {
        val donkeyDir = ant.properties["donkey"] ?: "donkey"
        val serverDir = ant.properties["server"] ?: "server"
        val donkeySetupDir = ant.properties["donkey.setup"] ?: "$donkeyDir/setup"

        ant.withGroovyBuilder {
            "ant"(
                "antfile" to "build.xml",
                "dir" to donkeyDir,
                "target" to "build"
            )
        }

        delete("$serverDir/lib/donkey")
        copy {
            from("$donkeySetupDir/donkey-model.jar")
            into("$serverDir/lib/donkey")
        }
        copy {
            from("$donkeySetupDir/donkey-server.jar")
            into("$serverDir/lib/donkey")
        }
        copy {
            from("$donkeyDir/lib")
            into("$serverDir/lib/donkey")
            exclude("log4j-1.2.16.jar")
            exclude("HikariCP-java6-2.0.1.jar")
            exclude("javassist-3.19.0-GA.jar")
            exclude("xstream/**")
            exclude("commons/**")
            exclude("database/**")
        }
    }
}

tasks.register("build-webadmin") {
    // Commented out in original XML
}

tasks.register("build-server-extensions") {
    dependsOn("build-donkey")
    doLast {
        val serverDir = ant.properties["server"] ?: "server"
        val clientDir = ant.properties["client"] ?: "client"
        val serverBuildDir = ant.properties["server.build"] ?: "$serverDir/build"

        ant.withGroovyBuilder {
            "ant"(
                "antfile" to "build.xml",
                "dir" to serverDir,
                "target" to "create-plugins"
            )
        }

        copy {
            from("$serverBuildDir/extensions/")
            into("$clientDir/lib")
            include("**/*-shared.jar")
            eachFile {
                relativePath = RelativePath(true, name) // flatten
            }
            includeEmptyDirs = false
        }
    }
}

tasks.register("build-client") {
    dependsOn("build-server-extensions")
    doLast {
        val donkeySetupDir = ant.properties["donkey.setup"] ?: "donkey/setup"
        val serverSetupDir = ant.properties["server.setup"] ?: "server/setup"
        val clientDir = ant.properties["client"] ?: "client"
        val serverDir = ant.properties["server"] ?: "server"

        copy {
            from("$donkeySetupDir/donkey-model.jar")
            into("$clientDir/lib")
        }
        copy {
            from("$serverSetupDir/server-lib/mirth-client-core.jar")
            into("$clientDir/lib")
        }
        copy {
            from("$serverSetupDir/server-lib/mirth-crypto.jar")
            into("$clientDir/lib")
        }
        copy {
            from("$serverDir/lib/mirth-vocab.jar")
            into("$clientDir/lib")
        }

        ant.withGroovyBuilder {
            "ant"(
                "antfile" to "ant-build.xml",
                "dir" to clientDir,
                "target" to "build"
            )
        }

        copy {
            from("$clientDir/dist/mirth-client.jar")
            into("$serverSetupDir/client-lib/")
        }

        copy {
            from("$clientDir/lib")
            into("$serverSetupDir/client-lib")
            exclude("*-shared.jar")
            exclude("extensions")
        }

        copy {
            from("$clientDir/dist/extensions")
            into("$serverSetupDir/extensions")
        }
    }
}

tasks.register("build-manager") {
    dependsOn("build-client")
    doLast {
        val donkeySetupDir = ant.properties["donkey.setup"] ?: "donkey/setup"
        val serverSetupDir = ant.properties["server.setup"] ?: "server/setup"
        val managerDir = ant.properties["manager"] ?: "manager"

        copy {
            from("$donkeySetupDir/donkey-model.jar")
            into("$managerDir/lib")
        }
        copy {
            from("$serverSetupDir/server-lib/mirth-client-core.jar")
            into("$managerDir/lib")
        }
        copy {
            from("$serverSetupDir/server-lib/mirth-crypto.jar")
            into("$managerDir/lib")
        }

        ant.withGroovyBuilder {
            "ant"(
                "antfile" to "ant-build.xml",
                "dir" to managerDir,
                "target" to "build"
            )
        }

        copy {
            from("$managerDir/dist/mirth-manager-launcher.jar")
            into(serverSetupDir)
        }

        copy {
            from("$managerDir/lib")
            into("$serverSetupDir/manager-lib")
            exclude("mirth-client.jar")
        }
    }
}

tasks.register("build-cli") {
    dependsOn("build-client")
    doLast {
        val donkeySetupDir = ant.properties["donkey.setup"] ?: "donkey/setup"
        val serverSetupDir = ant.properties["server.setup"] ?: "server/setup"
        val cliDir = ant.properties["cli"] ?: "command"
        val version = ant.properties["version"] ?: "4.5.2"

        copy {
            from("$donkeySetupDir/donkey-model.jar")
            into("$cliDir/lib")
        }
        copy {
            from("$serverSetupDir/server-lib/mirth-client-core.jar")
            into("$cliDir/lib")
        }
        copy {
            from("$serverSetupDir/server-lib/mirth-crypto.jar")
            into("$cliDir/lib")
        }

        ant.withGroovyBuilder {
            "ant"(
                "antfile" to "build.xml",
                "dir" to cliDir,
                "target" to "build"
            ) {
                "property"("name" to "version", "value" to version)
            }
        }

        copy {
            from("$cliDir/build")
            into("$serverSetupDir/cli-lib")
            include("mirth-cli.jar")
        }

        copy {
            from("$cliDir/build")
            into(serverSetupDir)
            include("mirth-cli-launcher.jar")
        }

        copy {
            from("$cliDir/lib")
            into("$serverSetupDir/cli-lib")
            exclude("mirth-client.jar")
        }

        copy {
            from("$cliDir/conf")
            into("$serverSetupDir/conf")
            include("mirth-cli-config.properties")
            include("log4j2-cli.properties")
        }
    }
}

tasks.register("test-run") {
    val junitClasspath = configurations["antJUnit"].asPath
    doLast {
        val donkeyDir = ant.properties["donkey"] as String
        val serverDir = ant.properties["server"] as String
        val clientDir = ant.properties["client"] as String
        val cliDir = ant.properties["cli"] as String

        fun createWrapper(dir: String, buildFile: String, cp: String): File {
            val wrapperFile = File(dir, "build-wrapper.xml")
            val content = """
                <project default="wrapper">
                    <taskdef name="junit" classname="org.apache.tools.ant.taskdefs.optional.junit.JUnitTask">
                        <classpath>
                            <pathelement path="$cp"/>
                        </classpath>
                    </taskdef>
                    <import file="$buildFile"/>
                </project>
            """.trimIndent()
            wrapperFile.writeText(content)
            return wrapperFile
        }

        val donkeyWrapper = createWrapper(donkeyDir, "build.xml", junitClasspath)
        val serverWrapper = createWrapper(serverDir, "build.xml", junitClasspath)
        val clientWrapper = createWrapper(clientDir, "ant-build.xml", junitClasspath)
        val cliWrapper = createWrapper(cliDir, "build.xml", junitClasspath)

        try {
            ant.withGroovyBuilder {
                try {
                    "ant"("antfile" to donkeyWrapper.name, "dir" to donkeyDir, "target" to "test-run")
                } catch (e: Exception) {
                    println("Donkey tests failed: ${e.message}")
                }
                try {
                    "ant"("antfile" to serverWrapper.name, "dir" to serverDir, "target" to "test-run")
                } catch (e: Exception) {
                    println("Server tests failed: ${e.message}")
                }
                try {
                    "ant"("antfile" to clientWrapper.name, "dir" to clientDir, "target" to "test-run")
                } catch (e: Exception) {
                    println("Client tests failed: ${e.message}")
                }
                try {
                    "ant"("antfile" to cliWrapper.name, "dir" to cliDir, "target" to "test-run")
                } catch (e: Exception) {
                    println("CLI tests failed: ${e.message}")
                }
            }
        } finally {
            donkeyWrapper.delete()
            serverWrapper.delete()
            clientWrapper.delete()
            cliWrapper.delete()
        }
    }
}

val disableTests = project.hasProperty("disableTests")
tasks.register("build-time-tests") {
    if (!disableTests) {
        dependsOn("test-run")
    }
}

tasks.register("build") {
    group = "build"
    description = "Builds the project"
    dependsOn("build-manager", "build-cli", "build-webadmin")
    doLast {
        val serverDir = ant.properties["server"] ?: "server"
        val serverSetupDir = ant.properties["server.setup"] ?: "server/setup"
        val serverBuildDir = ant.properties["server.build"] ?: "server/build"
        val version = ant.properties["version"] ?: "4.5.2"

        ant.withGroovyBuilder {
            "ant"(
                "antfile" to "build.xml",
                "dir" to serverDir,
                "target" to "create-setup"
            ) {
                "property"("name" to "version", "value" to version)
            }
        }

        copy {
            from("$serverSetupDir/extensions/")
            into("$serverBuildDir/extensions/")
        }

        copy {
            from("$serverSetupDir/client-lib")
            into("$serverBuildDir/client-lib")
        }
    }
    finalizedBy("build-time-tests")
}

tasks.register("dist") {
    dependsOn("build-manager", "build-cli", "build-webadmin")
    doLast {
        val version = ant.properties["version"] ?: "4.5.2"
        val serverDir = ant.properties["server"] ?: "server"

        println("Application version: $version")
        ant.withGroovyBuilder {
            "ant"(
                "antfile" to "build.xml",
                "dir" to serverDir,
                "target" to "create-dist"
            ) {
                "property"("name" to "version", "value" to version)
            }
        }
    }
}

tasks.register("append-license") {
    doLast {
        val serverDir = ant.properties["server"] ?: "server"
        val clientDir = ant.properties["client"] ?: "client"
        val cliDir = ant.properties["cli"] ?: "command"
        val managerDir = ant.properties["manager"] ?: "manager"
        val donkeyDir = ant.properties["donkey"] ?: "donkey"
        val webadminDir = ant.properties["webadmin"] ?: "webadmin"

        ant.withGroovyBuilder {
            "path"("id" to "header.classpath") {
                "fileset"("dir" to "$serverDir/lib") {
                    "include"("name" to "**/*.jar")
                }
            }

            "taskdef"("name" to "header", "classname" to "com.mirth.tools.header.HeaderTask") {
                "classpath"("refid" to "header.classpath")
            }

            "header"("headerFile" to "$serverDir/license-header.txt") {
                "fileset"("dir" to serverDir, "includes" to "**/*.java", "excludes" to "src/io/**/*.java src/org/**/*.java src/com/mirth/connect/server/logging/LogOutputStream.java")
            }

            "header"("headerFile" to "$serverDir/license-header.txt") {
                "fileset"("dir" to clientDir, "includes" to "**/*.java", "excludes" to "src/com/mirth/connect/client/ui/BareBonesBrowserLaunch.java src/com/mirth/connect/client/ui/BeanBinder.java src/com/mirth/connect/client/ui/OSXAdapter.java src/org/**/*.java")
            }

            "header"("headerFile" to "$serverDir/license-header.txt") {
                "fileset"("dir" to cliDir, "includes" to "**/*.java")
            }

            "header"("headerFile" to "$serverDir/license-header.txt") {
                "fileset"("dir" to managerDir, "includes" to "**/*.java", "excludes" to "src/com/mirth/connect/manager/BareBonesBrowserLaunch.java")
            }

            "header"("headerFile" to "$serverDir/license-header.txt") {
                "fileset"("dir" to donkeyDir, "includes" to "**/*.java")
            }

            "header"("headerFile" to "$serverDir/license-header.txt") {
                "fileset"("dir" to webadminDir, "includes" to "**/*.java")
            }
        }
    }
}

tasks.register("remove-classes") {
    doLast {
        val donkeyDir = ant.properties["donkey"] ?: "donkey"
        val serverDir = ant.properties["server"] ?: "server"
        val clientDir = ant.properties["client"] ?: "client"

        ant.withGroovyBuilder {
            "ant"("antfile" to "build.xml", "dir" to donkeyDir, "target" to "remove-classes")
            "ant"("antfile" to "build.xml", "dir" to serverDir, "target" to "remove-classes")
            "ant"("antfile" to "ant-build.xml", "dir" to clientDir, "target" to "remove-classes")
        }
    }
}

tasks.register("build-custom") {
    doLast {
        ant.withGroovyBuilder {
            "ant"("antfile" to "custom-extensions.xml", "target" to "build")
        }
    }
}