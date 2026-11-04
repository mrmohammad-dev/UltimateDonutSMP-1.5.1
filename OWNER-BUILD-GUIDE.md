# Owner build guide — UltimateDonutSMP 1.5.1

## Requirements

- A full JDK. The repository CI currently builds with Temurin JDK 25; the plugin bytecode target remains Java 21.
- Apache Maven 3.9.x or newer.
- Git.
- Internet access to the Maven repositories in `pom.xml` and the pinned GeyserDetector source repository.

## Recommended build

Run the script from the extracted project root:

- Windows: double-click `build.bat` or run it from Command Prompt.
- Linux/macOS: `chmod +x build.sh && ./build.sh`.

The script follows the repository CI dependency setup:

1. Clone/fetch GeyserDetector.
2. Check out the pinned API commit `8a080acecd0d55353b159118496cbe224f0f9845`.
3. Install the `geyserdetector-api` module into the local Maven repository.
4. Run `mvn clean verify` for tests and packaging.
5. Confirm that the expected shaded JAR exists and contains `plugin.yml`.

Expected artifact:

`target/UltimateDonutSmp-1.5.1.jar`

Only treat the JAR as built successfully when the script prints `SUCCESS`. If a Maven dependency, test, or package step fails, do not rename an old artifact or deploy a partial JAR.

## Server installation

1. Test the JAR on a staging server matching the target server software and Minecraft/Folia version.
2. Ensure the required dependencies listed in `plugin.yml` are installed (ProtocolLib and PlaceholderAPI).
3. Back up the server and its plugin data before replacing the production JAR.
4. Stop the server, replace the plugin JAR, and start the server. Review startup logs for linkage, scheduler, database, and configuration errors before allowing players in.

## Important

The uploaded source contains custom changes beyond the public v1.5.1 release (including the `/fly` world allow-list). A public upstream release JAR is not a substitute for building this exact source tree.
