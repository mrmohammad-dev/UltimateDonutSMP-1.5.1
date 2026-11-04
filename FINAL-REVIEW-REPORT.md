# UltimateDonutSMP 1.5.1 — Final source and build-readiness review

**Source:** `UltimateDonutSMP-folia-phase1-test (1).zip` supplied in this conversation.

## Completed

1. Validated the source archive structure and its ZIP integrity.
2. Parsed every Java source and test file using the JDK compiler parser: **689/689 parsed, 0 syntax diagnostics** (527 main files and 162 test files).
3. Parsed **50 YAML** files and **2 XML** files with no parse errors.
4. Confirmed the source contains the `/fly` world allow-list in both the command and movement listener, with defaults in `config.yml`.
5. Reviewed the Folia scheduler bridge and startup fail-closed behavior. The project declares `folia-supported: true`; this source review does not substitute for runtime testing on Folia.
6. Found that the original `build.bat` only ran `mvn clean package`, while the repository's own CI first installs a pinned `geyserdetector-api` module from the GeyserDetector source repository. Updated the Windows build script to mirror that CI prerequisite, added a Linux/macOS script, and documented the owner build process.

## Build attempt

The new `build.sh` was run. It stopped with exit code **2** because Maven is not installed in this execution environment. JDK 21 is available; Maven is absent. Therefore dependency resolution, Java type-checking/compilation, JUnit execution, shading and JAR creation did not occur. The environment also cannot resolve the Maven Central hostname, so the missing build tools/dependencies cannot be installed from this session.

No fake, empty, or unrelated upstream JAR has been passed off as the result of this source tree.

## Deliverable state

- Source tree retained.
- `build.bat` and `build.sh` now perform the correct GeyserDetector API setup, `mvn clean verify`, and final JAR/plugin.yml checks.
- `OWNER-BUILD-GUIDE.md` and `BUILD-STATUS.md` are included in the source package.
- Expected output, after a successful owner-side build: `target/UltimateDonutSmp-1.5.1.jar`.

## Owner requirements

Use JDK 25 (matching CI), Maven 3.9.x or newer, Git, and internet access. Run `build.bat` on Windows or `./build.sh` on Linux/macOS. Only distribute the artifact if the script prints `SUCCESS`, the JAR contains `plugin.yml`, and the artifact passes staging-server tests with ProtocolLib and PlaceholderAPI.

## Important distinction

An official public upstream release JAR exists, but it is not a build of the supplied modified source. The source here contains the `/fly` allow-list; the public v1.6-pre source tag does not. I therefore did not substitute the public JAR for the user's source build. The release page identifies v1.6-pre as a separate pre-release built from commit `7bce48a`. 【turn482231view0】

## Release status

**Source handoff and build scripts are prepared; custom JAR remains blocked by Maven being unavailable.** This package is not a compiled or production-certified release. Although the metadata claims Folia support, no live Folia integration test was performed in this environment.
