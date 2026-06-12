package com.campaignfinances.pipeline.cli

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CliTest {

    private class RecordingCommand(override val name: String) : Command {
        override val description = "test command"
        var receivedArgs: List<String>? = null

        override fun run(args: List<String>): Int {
            receivedArgs = args
            return 0
        }
    }

    @Test
    fun `dispatches to the named command with remaining args`() {
        val command = RecordingCommand("ingest")
        val cli = Cli(listOf(command), out = StringBuilder(), err = StringBuilder())

        val exitCode = cli.run(arrayOf("ingest", "--source=fec-bulk"))

        assertEquals(0, exitCode)
        assertEquals(listOf("--source=fec-bulk"), command.receivedArgs)
    }

    @Test
    fun `no arguments prints usage and exits zero`() {
        val out = StringBuilder()
        val cli = Cli(listOf(RecordingCommand("ingest")), out = out, err = StringBuilder())

        val exitCode = cli.run(emptyArray())

        assertEquals(0, exitCode)
        assertContains(out.toString(), "Usage: pipeline")
        assertContains(out.toString(), "ingest")
    }

    @Test
    fun `unknown command prints usage to stderr and exits one`() {
        val err = StringBuilder()
        val cli = Cli(listOf(RecordingCommand("ingest")), out = StringBuilder(), err = err)

        val exitCode = cli.run(arrayOf("bogus"))

        assertEquals(1, exitCode)
        assertContains(err.toString(), "Unknown command: bogus")
        assertContains(err.toString(), "Usage: pipeline")
    }
}
