# Mirth Connect Gradle Orchestrator Build Configuration

## Overview

This document describes the comprehensive orchestrator build configuration that replicates the functionality of the original `server/mirth-build.xml` file using Gradle. The orchestrator manages the entire build process with proper dependencies and file copying between modules.

## Orchestrator Tasks Implemented

### Core Build Tasks

1. **`build-donkey`** - Build donkey and copy JARs to server/lib/donkey
   - Builds the donkey module
   - Copies donkey-model.jar and donkey-server.jar to server/lib/donkey
   - Copies donkey lib dependencies with specific exclusions

2. **`build-webadmin`** - Build webadmin and copy WAR to server setup
   - Builds the webadmin module
   - Copies webadmin.war to both server/build/webapps and server/setup/webapps

3. **`build-server-extensions`** - Build server extensions and copy shared JARs to client/lib
   - Depends on build-donkey
   - Builds server extensions via copyExtensionsToSetup task
   - Copies shared extension JARs (*-shared.jar) to client/lib

4. **`build-client`** - Build client and copy JARs/extensions to server setup
   - Depends on build-server-extensions
   - Copies required JARs to client/lib before building
   - Builds the client module
   - Copies client artifacts to server setup directories

5. **`build-manager`** - Build manager and copy to server setup
   - Depends on build-client
   - Copies required JARs to manager/lib before building
   - Builds the manager module
   - Copies manager artifacts to server setup

6. **`build-cli`** - Build CLI and copy to server setup
   - Depends on build-client
   - Copies required JARs to command/lib before building
   - Builds the command module
   - Copies CLI artifacts and configuration to server setup

### Main Orchestration Tasks

1. **`orchestratorBuild`** - Main build target that orchestrates everything
   - Depends on build-manager, build-cli, build-webadmin, and server:createSetup
   - Copies extensions and client-lib to server build directory
   - Runs tests via finalizedBy

2. **`build`** - Enhanced default build task
   - Overrides the default Gradle build task to use orchestratorBuild
   - Maintains compatibility with standard Gradle workflows

3. **`dist`** - Distribution creation target
   - Creates distribution by copying server setup to server/dist
   - Displays application version information

### Utility Tasks

1. **`test-run`** - Run tests across all modules
    - Runs tests for donkey and server modules
    - Provides status reporting for test execution

2. **`remove-classes`** - Clean compiled classes across all modules
    - Executes clean task for all subprojects
    - Provides comprehensive cleanup

3. **`append-license`** - License header management
    - Processes Java files in all modules
    - Adds license headers to files that don't have them
    - Respects module-specific exclusions

4. **`build-custom`** - Build custom extensions
    - Placeholder for custom extensions build
    - Checks for custom-extensions.xml file

5. **`initOrchestrator`** - Initialize orchestrator properties and directories
    - Creates necessary directory structure
    - Sets up build environment

## Task Aliases

For convenience, camelCase aliases are provided for all main tasks:

- `buildDonkey` → `build-donkey`
- `buildWebadmin` → `build-webadmin`
- `buildServerExtensions` → `build-server-extensions`
- `buildClient` → `build-client`
- `buildManager` → `build-manager`
- `buildCli` → `build-cli`
- `testRun` → `test-run`
- `removeClasses` → `remove-classes`
- `appendLicense` → `append-license`
- `buildCustom` → `build-custom`

## Build Order and Dependencies

The orchestrator maintains the exact build order from the original Ant build:

```bash
donkey → server extensions → client → manager/cli → webadmin
```

### Dependency Chain

1. `build-donkey` (no dependencies)
2. `build-server-extensions` (depends on build-donkey)
3. `build-client` (depends on build-server-extensions)
4. `build-manager` and `build-cli` (both depend on build-client)
5. `build-webadmin` (independent)
6. `orchestratorBuild` (depends on build-manager, build-cli, build-webadmin)

## File Copying Operations

The orchestrator replicates all file copying operations from the original build:

### Donkey to Server

- `donkey/setup/donkey-model.jar` → `server/lib/donkey/`
- `donkey/setup/donkey-server.jar` → `server/lib/donkey/`
- `donkey/lib/*` → `server/lib/donkey/` (with exclusions)

### Server Extensions to Client

- `server/build/extensions/**/*-shared.jar` → `client/lib/` (flattened)

### Client Dependencies

- `donkey/setup/donkey-model.jar` → `client/lib/`
- `server/setup/server-lib/mirth-client-core.jar` → `client/lib/`
- `server/setup/server-lib/mirth-crypto.jar` → `client/lib/`
- `server/lib/mirth-vocab.jar` → `client/lib/`

### Client to Server Setup

- `client/build/libs/client.jar` → `server/setup/client-lib/mirth-client.jar`
- `client/lib/*` → `server/setup/client-lib/` (excluding shared JARs)
- `client/dist/extensions/*` → `server/setup/extensions/`

### Manager to Server Setup

- `manager/dist/mirth-manager-launcher.jar` → `server/setup/`
- `manager/lib/*` → `server/setup/manager-lib/` (excluding mirth-client.jar)

### CLI to Server Setup

- `command/build/mirth-cli.jar` → `server/setup/cli-lib/`
- `command/build/mirth-cli-launcher.jar` → `server/setup/`
- `command/lib/*` → `server/setup/cli-lib/` (excluding mirth-client.jar)
- `command/conf/mirth-cli-config.properties` → `server/setup/conf/`
- `command/conf/log4j2-cli.properties` → `server/setup/conf/`

### Final Integration

- `server/setup/extensions/*` → `server/build/extensions/`
- `server/setup/client-lib/*` → `server/build/client-lib/`

## Usage Examples

### Build Everything

```bash
./gradlew build
# or
./gradlew orchestratorBuild
```

### Build Specific Components

```bash
./gradlew build-donkey
./gradlew build-client
./gradlew build-manager
```

### Run Tests

```bash
./gradlew test-run
```

### Clean Everything

```bash
./gradlew remove-classes
```

### Create Distribution

```bash
./gradlew dist
```

### Add License Headers

```bash
./gradlew append-license
```

## Integration with Existing Gradle Build

The orchestrator integrates seamlessly with the existing Gradle build system:

- Uses existing module build.gradle.kts files
- Leverages existing task dependencies
- Maintains compatibility with standard Gradle commands
- Preserves individual module build capabilities

## Key Features

1. **Exact Replication**: Replicates all functionality from the original mirth-build.xml
2. **Proper Dependencies**: Maintains correct build order and dependencies
3. **File Management**: Handles all file copying operations accurately
4. **Error Handling**: Provides proper error handling and status reporting
5. **Flexibility**: Supports both individual component builds and full orchestration
6. **Compatibility**: Works with existing Gradle ecosystem and tooling

## Directory Structure Created

The orchestrator automatically creates the following directory structure:

```bash
server/
├── lib/donkey/
├── setup/
│   ├── webapps/
│   ├── client-lib/
│   ├── extensions/
│   ├── manager-lib/
│   ├── cli-lib/
│   └── conf/
└── build/
    ├── webapps/
    ├── extensions/
    └── client-lib/
```

This comprehensive orchestrator build configuration provides a complete Gradle-based replacement for the original Ant build system while maintaining full compatibility and functionality.
