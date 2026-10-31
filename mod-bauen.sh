#!/bin/sh
cd "$(dirname "$0")"
command -v java >/dev/null || { echo "Bitte Java 21 installieren: https://adoptium.net"; exit 1; }
./gradlew build --no-daemon && cp build/libs/auktionsmod-1.0.0.jar . && echo "FERTIG: $(pwd)/auktionsmod-1.0.0.jar -> in .minecraft/mods kopieren"
