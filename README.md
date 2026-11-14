# Fabrarium

Fabrarium is the independent Paper/Folia technology and RPG plugin created for the ZeDaris universe. It owns research progression, custom items, production recipes, machines, and the fictional metal Zedrium. The brand is deliberately independent from the `Zed-` prefix; Zedron remains reserved for a future machine/technology line.

## Current vertical slice

- PDC-tagged Zedrium items and a PDC-tagged Refinaria Fabrilis block.
- Complete production chain: raw Zedrium recipe, regional machine refining, furnace smelting, and an advanced Zedrium core.
- Persistent technology XP and three research unlock levels.
- Persistent machine state across server restart and chunk unload. Processing uses elapsed wall-clock time.
- Public asynchronous `TechRpgService`, registered through Bukkit's `ServicesManager`.
- SQLite by default and configurable PostgreSQL support.
- Folia ownership rules: block work runs on the owning `RegionScheduler`, player work on the `EntityScheduler`, and JDBC work on a dedicated executor.

Economy and territory integrations are optional by design and must use public runtime-discovered APIs. Fabrarium never reads another plugin's tables.

## Requirements

- JDK 25 installed as the compilation toolchain
- JDK 21–24 as `JAVA_HOME` to launch the bundled Gradle 8.14.3 wrapper (the wrapper then locates JDK 25)
- Paper/Folia 26.3 or compatible server API

## Build

```bash
./gradlew test build
```

The deployable fat JAR is written to `build/libs/Fabrarium-0.1.0-SNAPSHOT.jar`.

## Commands

- `/fabrarium guia`
- `/fabrarium progresso`
- `/fabrarium dar <item>` (permission: `fabrarium.admin`)

## Configuration

The first startup creates `plugins/Fabrarium/config.yml`. Leave `database.url` empty for local SQLite, or use a PostgreSQL JDBC URL and credentials. All database initialization and queries happen outside tick threads.

## Name

`Fabrarium` is a deliberately coined, Latinized name: a place devoted to making and craft. It was selected after exact-name searches found no Minecraft project with that name on GitHub, Hangar, Modrinth, or SpigotMC.
