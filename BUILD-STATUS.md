# Build status — UltimateDonutSMP 1.5.1

## What is verified

- The supplied source archive passed ZIP integrity validation.
- JDK 21 Java parser: 689/689 Java files parsed, zero syntax diagnostics (527 main source + 162 test source).
- YAML: 50 files parsed successfully.
- XML: 2 files parsed successfully.
- `build.sh` passed `sh -n` and `bash -n` syntax checks.
- The `/fly` world allow-list is present in `FlyCommand`, `PlayerMoveListener`, and `config.yml`.
- `plugin.yml` and `pom.xml` declare Folia support. This is the project's claim, not a live runtime certification.

## What is not verified

- Maven compilation, dependency resolution, JUnit execution, shade packaging, and the final JAR were not produced in this environment.
- The build script was executed and stopped with exit code 2 because `mvn` is not installed.
- No live Paper, Spigot, or Folia server test was available.

## Required build environment

- JDK 25 recommended to match the repository CI (the plugin is compiled with Java 21 bytecode target).
- Apache Maven 3.9.x or newer.
- Git and access to Maven repositories and GitHub.

## Build process

Run `build.bat` on Windows or `./build.sh` on Linux/macOS. The scripts clone/fetch GeyserDetector, check out the exact API commit used by CI, install the `geyserdetector-api` module locally, run `mvn clean verify`, then check for `target/UltimateDonutSmp-1.5.1.jar` and confirm it contains `plugin.yml`.

Do not distribute or deploy an older or partial JAR if the build script fails. Test a successful JAR on a staging server matching the target platform and Minecraft/Folia version before production use.
