#!/usr/bin/env bash
# Rewrites gradle.lockfile and gradle/verification-metadata.xml from a full
# build. Gradle only resolves the host's LWJGL natives, so the other platforms'
# are checksummed from Maven Central and added too; otherwise CI (Linux) would
# fail verification for natives a macOS developer never downloaded.
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew build runData --write-locks --write-verification-metadata sha256 --quiet

classifiers=(natives-linux natives-macos natives-macos-arm64 natives-windows natives-windows-x86 natives-windows-arm64)
metadata=gradle/verification-metadata.xml

grep -o '<component group="org.lwjgl" name="[^"]*" version="[^"]*"' "$metadata" |
    sed -E 's/.*name="([^"]*)" version="([^"]*)"/\1 \2/' |
    while read -r name version; do
        for classifier in "${classifiers[@]}"; do
            file="$name-$version-$classifier.jar"
            grep -q "<artifact name=\"$file\">" "$metadata" && continue
            sha=$(curl -sfL "https://repo1.maven.org/maven2/org/lwjgl/$name/$version/$file" | shasum -a 256 | cut -d' ' -f1)
            entry="         <artifact name=\"$file\">\n            <sha256 value=\"$sha\" origin=\"Maven Central, for other platforms\"/>\n         </artifact>"
            sed -i.bak "s#^\(      <component group=\"org.lwjgl\" name=\"$name\" version=\"$version\">\)\$#\1\n$entry#" "$metadata"
        done
    done
rm -f "$metadata.bak"
