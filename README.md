# sarif-kt

A small JVM producer for SARIF 2.1.0, using the existing
[sarif4k](https://github.com/detekt/sarif4k) format bindings. The core has no gvy,
Groovy, CodeNarc, or LSP dependency.

```kotlin
val report = SarifProducer.build(
    tool = ToolComponent(name = "my-linter"),
    sourceRoot = URI("file:///checkout/"),
    findings = listOf(Finding(
        ruleId = "unused",
        path = "src/main.kt",
        message = "Unused declaration",
        region = Region(startLine = 2, startColumn = 1),
    )),
)
val json = SarifSerializer.toJson(report)
```

Import the producer from `engineer.alberto.sarif` and models/serializer from
`io.github.detekt.sarif4k`. Regions use one-based lines, UTF-16 columns and
exclusive ends. Relative paths are normalized and URI-escaped; absolute paths
and paths outside the checkout are rejected. Resolve native user paths at the
application's IO boundary before calling the producer.

The producer sorts findings, preserves the caller's rule catalog order and
descriptor references, and appends missing rule IDs deterministically. It explicitly
records execution failures. It does not run analyzers, read source files, write
reports, upload results, or select a CI findings threshold.

## Development

JDK 17 or later:

```sh
./gradlew build
./gradlew publishToMavenLocal
```

Local Maven coordinates: `engineer.alberto:sarif-kt:0.1.0-SNAPSHOT`.
Maven Central publication is not configured. The Gradle wrapper distribution is
pinned to 9.2.1 with its SHA-256 checksum.

## Extracting the gvy writer

This library separates the reusable producer responsibilities identified in
gvy's `diagnostics/sarif`: tool and rule descriptors, deterministic reports,
locations, severity, and JSON through established format bindings.

Keep `SarifRuleRegistry` in gvy: its CodeNarc catalog and Groovy compiler
descriptions are domain knowledge. Keep LSP-to-finding conversion in a gvy adapter
or a separate optional adapter module. LSP positions are zero-based UTF-16, so
add one to lines and columns at that boundary. Do not convert them through bytes
or count code points again.

The gvy migration should preserve its current public facade while delegating
report construction here. Convert its rule catalog to sarif4k reporting
descriptors, resolve file paths against the configured workspace, and adapt each
LSP diagnostic to `Finding`. Before removing the old models, compare empty runs,
compiler errors, CodeNarc warnings, help URIs, Unicode positions, filenames with
reserved URI characters, and unsuccessful analysis against shared fixtures.

The library is independently buildable and can be consumed through a Gradle
composite build or Maven local during migration. A remote Maven publication must
exist before gvy's normal build can depend on its coordinates; the existing gvy
build is therefore unchanged by this extraction.
