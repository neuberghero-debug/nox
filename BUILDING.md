# Build NOX yourself

Don't trust the release jar? Good — build it yourself in ~2 minutes and compare
the checksum. Every GitHub release lists the `sha256` of the attached jar, so a
self-built jar from the same commit should match (or tell you exactly what
differs).

## Requirements

- **JDK 21+** (the repo builds with the Gradle wrapper — no global Gradle needed)
- Git
- ~1 GB free disk for the first build (Fabric/Minecraft artifacts get cached)

## Build

```bash
git clone https://github.com/neuberghero-debug/nox.git
cd nox
git checkout <tag>          # e.g. v0.1.0-beta — match the release you want to verify

./gradlew build -x test     # or: gradle build -x test if gradle is on PATH
```

The jar lands at `build/libs/nox-<version>.jar` — copy it into your mods folder:

```
~/.local/share/modrinth/profiles/NOX/mods/          (Modrinth App, Linux)
~/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/<i>/.minecraft/mods/
%APPDATA%\.minecraft\mods\                          (Windows)
```

## Verify the jar

```bash
sha256sum build/libs/nox-*.jar      # compare with the sha256 on the release page
```

Every jar also carries an embedded `assets/noxclient/sig.txt` — sha256 over all
`dev/noxclient/**` classes + metadata, written by the `noxSig` task during build.
`TamperGuard` re-checks it at runtime, so a modified jar warns you in-game.

## Requirements at runtime

- Minecraft **26.3**
- Fabric Loader **0.20.1+** and Fabric API
- Java 21+

## Extra checks (optional)

```bash
python3 tools/nox_doctor.py   # static audit: mixins vs the 26.3 jar, setting
                              # ranges, jar integrity, asset references
```

`tools/devloop.sh` runs build → doctor → atomic deploy → log tail in one shot.
