package engineer.alberto.sarif

import io.github.detekt.sarif4k.*
import kotlinx.serialization.json.Json
import java.net.URI
import java.nio.file.Path

/** Neutral input; lines and UTF-16 columns are one-based, with exclusive ends. */
data class Finding(
    val ruleId: String,
    val path: String,
    val message: String,
    val region: Region,
    val level: Level = Level.Warning,
)

/**
 * Pure SARIF producer. Rule execution, LSP diagnostics, CodeNarc metadata, file
 * reads and upload policy belong to callers. Full format models come from sarif4k.
 */
object SarifProducer {
    fun build(
        tool: ToolComponent,
        sourceRoot: URI,
        findings: List<Finding>,
        failures: List<String> = emptyList(),
    ): SarifSchema210 {
        require(tool.name.isNotBlank()) { "Tool name is required" }
        require(sourceRoot.scheme == "file" && sourceRoot.isAbsolute && sourceRoot.path.startsWith('/') &&
            sourceRoot.path.endsWith('/') && sourceRoot.query == null && sourceRoot.fragment == null) {
            "Source root must be an absolute file URI ending in /"
        }
        val descriptors = tool.rules.orEmpty()
        require(descriptors.all { it.id.isNotBlank() } && descriptors.map { it.id }.distinct().size == descriptors.size) {
            "Rule IDs must be nonempty and unique"
        }
        val rules = descriptors.associateBy { it.id }.toMutableMap()
        val normalized = findings.map { finding ->
            require(finding.ruleId.isNotBlank() && finding.message.isNotBlank()) { "Finding requires rule ID and message" }
            validate(finding.region)
            rules.putIfAbsent(finding.ruleId, ReportingDescriptor(id = finding.ruleId))
            finding.copy(path = relativeUri(finding.path))
        }.sortedWith(compareBy({ it.path }, { it.region.startLine }, { it.region.startColumn },
            { it.ruleId }, { it.message }, { it.level.value }, { it.region.endLine }, { it.region.endColumn },
            { Json.encodeToString(Region.serializer(), it.region) }))
        // Full descriptors can reference one another by index. Keep the caller's
        // catalog order intact and append newly observed IDs deterministically.
        val existingIds = descriptors.map { it.id }.toSet()
        val sortedRules = descriptors + rules.keys.filter { it !in existingIds }.sorted().map { rules.getValue(it) }
        val indexes = sortedRules.mapIndexed { index, rule -> rule.id to index.toLong() }.toMap()
        return SarifSchema210(
            schema = "https://docs.oasis-open.org/sarif/sarif/v2.1.0/errata01/os/schemas/sarif-schema-2.1.0.json",
            version = Version.The210,
            runs = listOf(Run(
                tool = Tool(driver = tool.copy(rules = sortedRules)),
                columnKind = ColumnKind.Utf16CodeUnits,
                originalURIBaseIDS = mapOf("%SRCROOT%" to ArtifactLocation(uri = sourceRoot.toASCIIString())),
                invocations = listOf(Invocation(
                    executionSuccessful = failures.isEmpty(),
                    toolExecutionNotifications = failures.sorted().map { Notification(message = Message(text = it), level = Level.Error) },
                )),
                results = normalized.map { finding -> Result(
                    ruleID = finding.ruleId,
                    ruleIndex = indexes.getValue(finding.ruleId),
                    level = finding.level,
                    message = Message(text = finding.message),
                    locations = listOf(Location(physicalLocation = PhysicalLocation(
                        artifactLocation = ArtifactLocation(uri = finding.path, uriBaseID = "%SRCROOT%"),
                        region = finding.region,
                    ))),
                ) },
            )),
        )
    }

    private fun relativeUri(file: String): String {
        val portable = file.replace('\\', '/')
        require(portable.isNotBlank() && !portable.startsWith('/') && ':' !in portable && '\u0000' !in portable) {
            "Source path must be repository-relative"
        }
        val normalized = Path.of(portable).normalize().toString().replace('\\', '/')
        require(normalized.isNotEmpty() && normalized != "." && normalized != ".." && !normalized.startsWith("../")) {
            "Source path escapes the checkout"
        }
        return URI(null, null, normalized, null).toASCIIString()
    }

    private fun validate(region: Region) {
        val start = requireNotNull(region.startLine) { "Start line is required" }
        val column = region.startColumn
        val endLine = region.endLine
        val endColumn = region.endColumn
        require(start >= 1 && (column == null || column >= 1)) { "Invalid start position" }
        require(endLine == null || endLine >= start) { "End line precedes start" }
        require(endColumn == null || (endColumn >= 1 && endLine != null && column != null)) {
            "End column requires complete start and end positions"
        }
        require(endLine != start || endColumn == null || column == null || endColumn >= column) {
            "End column precedes start"
        }
    }
}
