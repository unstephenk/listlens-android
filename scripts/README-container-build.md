# ListLens — Container Build Setup

This repo is intended to be buildable inside the OpenClaw workspace container.

## Why this exists
Gradle/Android builds require:
- a **JDK (Java 17)**
- an **Android SDK**

We do **not** commit SDK/JDK binaries to git. Instead, we create repo-local `.jdk` and `.sdk` pointers.

## Quick start
From the repo root:

```bash
./scripts/setup-container-android.sh
./gradlew :app:assembleDebug
```

### What the script does
- Symlinks:
  - `./.jdk` → `../birch/.jdk`
  - `./.sdk` → `../birch/.sdk`
- Writes `local.properties` with:
  - `sdk.dir=<repo>/.sdk`

This assumes you have a sibling repo `../birch` that already has `.jdk` and `.sdk` set up.

## If you don't have ../birch
You can still use this pattern:
- Create your own `.jdk` (Temurin 17)
- Create your own `.sdk` (Android SDK)
- Ensure `local.properties` points to `.sdk`

Then Gradle should work because `gradlew` prefers repo-local `.jdk` when `JAVA_HOME` is not set.
