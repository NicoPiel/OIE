# Gradle Migration Plan for Mirth Connect Project

## Overview
The goal is to migrate the existing Ant-based build system in 'mirth-build.xml' and its subprojects to a Gradle-based system. This will modernize the build process, improve dependency management, and leverage Gradle's features for easier maintenance and automation. Gradle version 6.9 will be used, as specified in the user's response. The project is multi-module, so a Gradle multi-project build will be set up with subprojects for 'donkey', 'server', 'webadmin', 'client', 'manager', and 'cli'. The root project will orchestrate the build, similar to the current Ant setup.

## Step-by-Step Plan
1. **Set Up Gradle Project Structure**:
   - Create a `settings.gradle` file in the root directory to define the subprojects.
   - Each subproject will have its own `build.gradle` file to handle module-specific configurations and tasks.

2. **Map Ant Targets to Gradle Tasks**:
   - **Init Target**: Use Gradle's `ext` blocks or `gradle.properties` for property management.
   - **Clean Target**: Map to Gradle's built-in `clean` task.
   - **Compile Target**: Use the Java plugin's `compileJava` task for compilation.
   - **Build Targets**: Map Ant JAR and WAR creation to Gradle's `jar` and `war` tasks. For example, 'build-server' in donkey can be a custom task in the donkey subproject.
   - **Dependency Management**: Migrate Ant's fileset-based dependencies to Gradle's dependency declarations, potentially using Maven repositories.
   - **Extension and Plugin Builds**: Handle in subprojects with custom tasks or Gradle's source sets for splitting code into shared and specific JARs.
   - **Testing**: Use Gradle's JUnit and JaCoCo plugins to replicate Ant's testing and coverage.
   - **Distribution**: Map targets like 'create-setup' and 'create-dist' to Gradle's archive tasks (e.g., `distZip`).

3. **Handle Dependencies and Properties**:
   - Use information from 'mirth-build.properties' to define project and artifact properties in Gradle.
   - Define inter-project dependencies (e.g., server depends on donkey) using Gradle's project references.
   - External JARs can be managed via Gradle's dependency resolution to reduce reliance on local files.

4. **Mermaid Diagram for Build Flow**
   ```
   graph TD
       A[Root Build] --> B[Init]
       A --> C[Build Donkey]
       A --> D[Build Webadmin]
       A --> E[Build Server Extensions]
       A --> F[Build Client]
       A --> G[Build Manager]
       A --> H[Build CLI]
       A --> I[Dist]

       C --> J[Donkey Build]
       J --> K[Compile Donkey]
       J --> L[Create Setup for Donkey]

       D --> M[Webadmin Build]
       M --> N[Jspc]
       M --> O[Compile Webadmin]
       M --> P[Dist WAR]

       E --> Q[Server Build]
       Q --> R[Compile Server]
       Q --> S[Create Connectors]
       Q --> T[Create Plugins]

       F --> U[Client Build]
       U --> V[Compile Client]
       U --> W[Build Client JARs]

       G --> X[Manager Build]
       X --> Y[Compile Manager]
       X --> Z[Build Manager JAR]

       H --> AA[CLI Build]
       AA --> AB[Compile CLI]
       AA --> AC[Build CLI JAR]

       I --> AD[Final Distribution Assembly]
   ```

5. **Implementation Steps**:
   - Create `settings.gradle` with `include 'donkey', 'server', 'webadmin', 'client', 'manager', 'cli'`.
   - Write root `build.gradle` to define common settings and task dependencies.
   - For each subproject, apply appropriate plugins (e.g., Java, War) and define tasks based on Ant logic.
   - Migrate property files and test configurations.
   - Incrementally test the build for each module.

6. **Potential Challenges and Mitigations**:
   - **Complex JAR Creation**: Ant's fine-grained JAR splitting can be handled with Gradle's custom tasks or source sets.
   - **Property Handling**: Ensure all Ant properties are accounted for in Gradle to avoid build failures.
   - **Testing Integration**: Gradle's plugins should handle JUnit and coverage easily, but verify with initial runs.
   - **File Operations**: Use Gradle's copy and archive tasks for operations like copying extensions and resources.

7. **Estimated Effort**:
   - Implementation is estimated to take 4-8 hours, depending on the complexity of dependency resolution and custom task creation.

This plan ensures a smooth transition to Gradle while maintaining the functionality of the current Ant build.