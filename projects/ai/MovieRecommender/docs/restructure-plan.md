# Restructure & code review plan

## Context

The README documents a standard Gradle layout (`build.gradle.kts`,
`settings.gradle.kts`, `src/main/kotlin/com/rag/...` with `model/`, `ollama/`,
`store/`, `rag/` sub-packages — see README.md "Project structure") and every
`.kt` file already declares the matching `package com.rag.*`. However, the
files originally sat flat in the project root, and the Gradle build files
didn't exist — so the project couldn't be built/run via `./gradlew run` as
documented.

This plan (a) restructures the project to match its own documentation, (b)
adds the missing Gradle build configuration so it actually builds, and (c)
fixes issues found during a code review of the 6 source files.

## Step 1 — Move source files into the documented package layout

Move each file to mirror its `package` declaration (paths only, no code changes):

| From | To |
|---|---|
| `Main.kt` | `src/main/kotlin/com/rag/Main.kt` |
| `KnowledgeBase.kt` | `src/main/kotlin/com/rag/KnowledgeBase.kt` |
| `Models.kt` | `src/main/kotlin/com/rag/model/Models.kt` |
| `OllamaClient.kt` | `src/main/kotlin/com/rag/ollama/OllamaClient.kt` |
| `VectorStore.kt` | `src/main/kotlin/com/rag/store/VectorStore.kt` |
| `RagEngine.kt` | `src/main/kotlin/com/rag/rag/RagEngine.kt` |

## Step 2 — Add `settings.gradle.kts` and `build.gradle.kts`

`settings.gradle.kts`: `rootProject.name = "MovieRecommender"`.

`build.gradle.kts`, using the exact dependency versions from the README's
"Dependencies" table:

- Plugins: `kotlin("jvm") version "2.0.21"`, `kotlin("plugin.serialization") version "2.0.21"`, `application`
  (bumped from the README's documented 1.9.22 — that compiler version can't
  parse JDK 25's version string, see Verification)
- Dependencies: `ktor-client-core`, `ktor-client-cio`, `ktor-client-content-negotiation`,
  `ktor-serialization-kotlinx-json` (all 2.3.7); `kotlinx-coroutines-core` (1.7.3);
  `kotlinx-serialization-json` (1.6.2); `ch.qos.logback:logback-classic` (1.4.14)
- `application { mainClass.set("com.rag.MainKt") }`
- `jvmTarget = "17"` (per README's "Java 17+" prerequisite)
- `tasks.named<JavaExec>("run") { standardInput = System.\`in\` }` — without
  this, `./gradlew run` doesn't connect stdin, so `readlnOrNull()` in `Main.kt`
  returns `null` immediately and the REPL exits right after printing the
  examples.
- Test deps: `testImplementation(kotlin("test"))`, `tasks.test { useJUnitPlatform() }`

## Step 3 — Bootstrap the Gradle wrapper

`gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are copied
from a local IntelliJ-generated project, with `gradle-wrapper.properties`
pointing at Gradle 9.2.0 (chosen for compatibility with the JDK on this
machine; already cached locally so no network access is needed).

The README's documented Kotlin plugin version (1.9.22) was incompatible with
this machine's JDK 25 (see Verification), so the `kotlin("jvm")`/
`kotlin("plugin.serialization")` plugin versions were bumped to 2.0.21 while
keeping the README's library versions (Ktor/coroutines/serialization/Logback)
unchanged.

## Step 4 — Add `src/main/resources/logback.xml`

The README claims logging is "suppressed in REPL mode", but no Logback config
existed, so Ktor's CIO engine would log at INFO by default and clutter the
REPL. A minimal config sets the root level to `WARN` with a simple console
appender.

## Step 5 — Code review fixes

1. **No error handling around Ollama calls (main finding)** —
   `OllamaClient.embed`/`chat` make raw Ktor calls with no try/catch. Any
   failure (Ollama not running, model not pulled, connection refused — exactly
   the cases the README's Troubleshooting section anticipates) throws
   uncaught through `runBlocking`:
   - `store.indexAll(...)` crashes the whole app *before* the REPL starts,
     with a raw stack trace instead of a friendly message.
   - `engine.recommend(input)` in the REPL loop crashes the entire session on
     one bad query, skipping `ollama.close()`.

   Fix in `Main.kt`: wrap `store.indexAll(...)` in try/catch printing a
   friendly message that points at the Troubleshooting steps (`ollama serve`,
   `ollama pull ...`) and exit cleanly; wrap the per-iteration
   `engine.recommend(input)` call in try/catch so one failed query prints a
   warning and continues the REPL instead of killing it.

2. **Minor — `MediaItem.toEmbeddableText()`**: always emitted `"Tags: "` even
   when `tags` is empty, producing `"Tags: | <description>"`. Guarded so empty
   tags don't add a stray `"Tags: |"` segment to the embedded text.

3. **No tests** — added `src/test/kotlin/...` with two small unit tests for
   the pure logic identified during review:
   - `VectorStore.cosineSimilarity`: visibility changed from `private` to
     `internal` so it's testable; `VectorStoreTest.kt` covers identical
     vectors (score ≈ 1.0), orthogonal vectors (score ≈ 0.0), and the
     zero-vector edge case (score == 0.0).
   - `MediaItem.toEmbeddableText()`: `ModelsTest.kt` checks the formatted
     output, including the empty-tags case from item 2.

## Verification

1. `./gradlew build` — compiles all sources under the new layout and runs the
   new unit tests. Requires no Ollama/network (Gradle distribution is cached).

   **JDK note**: this machine's default JDK is 25.0.2. Even after bumping the
   Kotlin plugin to 2.0.21, `compileKotlin` failed with
   `java.lang.IllegalArgumentException: 25.0.2` from
   `org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse` — the
   bundled compiler can't parse JDK 25's version string. Fixed by adding
   `gradle.properties` with `org.gradle.java.home` pointing at the installed
   JDK 23 (Corretto 23.0.2), after running `./gradlew --stop` to clear the
   stale daemon. With that in place, `./gradlew build` succeeds: all 6 unit
   tests pass (`ModelsTest`, `VectorStoreTest`).

2. If `ollama serve` is running locally with `nomic-embed-text` and `llama3`
   pulled, run `./gradlew run --console=plain` and confirm the REPL accepts
   input (validates the `standardInput` fix) and produces a recommendation.
   Otherwise, confirm the new try/catch in `Main.kt` prints a friendly error
   instead of a raw stack trace.
