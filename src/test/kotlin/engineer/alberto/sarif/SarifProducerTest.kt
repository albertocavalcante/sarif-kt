package engineer.alberto.sarif

import io.github.detekt.sarif4k.*
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class SarifProducerTest {
    private val root = URI("file:///checkout/")
    private val tool = ToolComponent(name = "test-linter")
    private val finding = Finding("unused", "src/space #%.kt", "Unused value", Region(startLine = 1, startColumn = 4))

    @Test fun `report preserves coordinates and escapes paths`() {
        val report = SarifProducer.build(tool, root, listOf(finding))
        val result = report.runs.single().results!!.single()
        assertEquals("unused", result.ruleID)
        assertEquals(0L, result.ruleIndex)
        assertEquals("src/space%20%23%25.kt", result.locations!!.single().physicalLocation!!.artifactLocation!!.uri)
        assertEquals(4L, result.locations!!.single().physicalLocation!!.region!!.startColumn)
        assertEquals(ColumnKind.Utf16CodeUnits, report.runs.single().columnKind)
        val json = SarifSerializer.toJson(report)
        assertEquals(report, SarifSerializer.fromJson(json))
        Files.createDirectories(Path.of("build/conformance"))
        Files.writeString(Path.of("build/conformance/kotlin.sarif"), json)
    }

    @Test fun `output is deterministic and failures remain explicit`() {
        val findings = listOf(finding, finding.copy(ruleId = "another", path = "other.kt"))
        assertEquals(SarifProducer.build(tool, root, findings), SarifProducer.build(tool, root, findings.reversed()))
        val failed = SarifProducer.build(tool, root, emptyList(), listOf("cannot read input"))
        assertFalse(failed.runs.single().invocations!!.single().executionSuccessful)
        assertNotNull(failed.runs.single().results)
        assertTrue(failed.runs.single().results!!.isEmpty())
        Files.createDirectories(Path.of("build/conformance"))
        Files.writeString(Path.of("build/conformance/kotlin-failed.sarif"), SarifSerializer.toJson(failed))
    }

    @Test fun `region metadata participates in deterministic ordering`() {
        val a = finding.copy(region = finding.region.copy(snippet = ArtifactContent(text = "first")))
        val b = finding.copy(region = finding.region.copy(snippet = ArtifactContent(text = "second")))
        assertEquals(SarifProducer.build(tool, root, listOf(a, b)), SarifProducer.build(tool, root, listOf(b, a)))
    }

    @Test fun `rule relationships retain their referenced descriptor indexes`() {
        val rules = listOf(
            ReportingDescriptor(id = "z", relationships = listOf(ReportingDescriptorRelationship(
                target = ReportingDescriptorReference(id = "a", index = 1),
            ))),
            ReportingDescriptor(id = "a"),
        )
        val report = SarifProducer.build(tool.copy(rules = rules), root, emptyList())
        val output = report.runs.single().tool.driver.rules!!
        val reference = output.first { it.id == "z" }.relationships!!.single().target
        assertEquals(reference.id, output[reference.index!!.toInt()].id)
    }

    @Test fun `invalid paths regions and duplicate rules are rejected`() {
        for (path in listOf("../escape.kt", "/absolute.kt", "C:\\src\\file.kt", "")) {
            assertFailsWith<IllegalArgumentException> { SarifProducer.build(tool, root, listOf(finding.copy(path = path))) }
        }
        assertFailsWith<IllegalArgumentException> { SarifProducer.build(tool, root, listOf(finding.copy(region = Region(startLine = 0)))) }
        assertFailsWith<IllegalArgumentException> { SarifProducer.build(tool.copy(rules = listOf(ReportingDescriptor(id = "r"), ReportingDescriptor(id = "r"))), root, emptyList()) }
    }
}
