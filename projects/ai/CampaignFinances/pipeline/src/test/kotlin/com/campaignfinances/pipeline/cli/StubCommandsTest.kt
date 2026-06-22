package com.campaignfinances.pipeline.cli

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class StubCommandsTest {

    private fun assertStubBehavior(command: Command, err: StringBuilder) {
        val exitCode = command.run(emptyList())

        assertEquals(2, exitCode, "'${command.name}' stub must exit with code 2")
        assertContains(err.toString(), "'${command.name}' is not implemented yet")
    }

    @Test
    fun `dedup stub reports not implemented and exits 2`() {
        val err = StringBuilder()
        assertStubBehavior(DedupCommand(err), err)
    }

    @Test
    fun `reconcile stub reports not implemented and exits 2`() {
        val err = StringBuilder()
        assertStubBehavior(ReconcileCommand(err), err)
    }
}
