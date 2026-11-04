#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

GEYSER_REPO="https://github.com/Pooyan3748/GeyserDetector.git"
GEYSER_COMMIT="8a080acecd0d55353b159118496cbe224f0f9845"
EXPECTED_JAR="target/UltimateDonutSmp-1.5.1.jar"

fail() {
  printf '\nERROR: %s\n' "$1" >&2
  exit "${2:-1}"
}

for tool in java mvn git jar; do
  command -v "$tool" >/dev/null 2>&1 || fail "Required tool '$tool' was not found on PATH. Install a JDK, Maven 3.9.x+, and Git." 2
done

echo '[1/5] Checking build tools...'
java -version
mvn -version

echo
echo '[2/5] Preparing the pinned GeyserDetector API source...'
if [[ -d GeyserDetector/.git ]]; then
  git -C GeyserDetector fetch origin || fail 'Could not fetch GeyserDetector.'
elif [[ -e GeyserDetector ]]; then
  fail 'GeyserDetector exists but is not a Git checkout. Move it aside and retry.' 2
else
  git clone "$GEYSER_REPO" GeyserDetector || fail 'Could not clone GeyserDetector.'
fi
git -C GeyserDetector checkout --detach "$GEYSER_COMMIT" || fail 'Could not check out the pinned GeyserDetector commit.'

echo
echo '[3/5] Installing the exact GeyserDetector API dependency locally...'
mvn --batch-mode --no-transfer-progress -f GeyserDetector/pom.xml -pl geyserdetector-api -am install -DskipTests \
  || fail 'GeyserDetector API installation failed; project compilation was not attempted.'

echo
echo '[4/5] Running project tests and building the shaded plugin...'
mvn --batch-mode --no-transfer-progress clean verify \
  || fail 'Maven tests or project packaging failed. Do not deploy an older or partial JAR.'

echo
echo '[5/5] Verifying the final plugin JAR...'
[[ -f "$EXPECTED_JAR" ]] || fail "Maven completed but $EXPECTED_JAR was not created." 3
jar tf "$EXPECTED_JAR" | grep -Fxq 'plugin.yml' || fail 'plugin.yml is missing from the JAR.' 4

echo
echo "SUCCESS: $EXPECTED_JAR"
echo 'Copy this JAR to the server plugins folder only after testing it on a staging server.'
