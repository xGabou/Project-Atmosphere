# AGENTS

This repository hosts **Project Atmosphere**, a Minecraft Forge 1.20.1 mod written in Java.

## Coding Guidelines
- Use four spaces for indentation.
- Keep braces on the same line as declarations (`if (...) {`).
- Ensure files end with a newline.

## Build / Checks
- The project uses Gradle. The wrapper is not included, so use the system `gradle` command.
- Run `gradle build` from the repository root after any changes. This is the current programmatic check. The build may fail if external dependencies cannot be resolved.
- No test suite exists yet.
- Build with **JDK 17**. Ensure the PATH and `java` command reference JDK 17 before running Gradle.

