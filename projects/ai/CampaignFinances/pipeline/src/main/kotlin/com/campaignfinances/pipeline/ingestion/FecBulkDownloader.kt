package com.campaignfinances.pipeline.ingestion

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream

/**
 * Downloads and unzips FEC bulk files, caching extracted `.txt` files under
 * [cacheDir] (default `~/.cache/campaign-finances/fec/<cycle>/`).
 *
 * - Downloads stream to disk (the contributions zip is ~1.7 GB — never held in
 *   memory) and follow fec.gov's 302 redirect to S3 (Ktor follows by default).
 * - A SHA-256 of every download is logged for provenance.
 * - The zip is deleted after extraction; a cached `.txt` is reused as-is.
 *   Delete the cache directory to force a re-download.
 *
 * @param cacheDir where extracted files are kept between runs
 * @param out where progress lines are printed
 */
class FecBulkDownloader(
    private val cacheDir: Path = Path.of(System.getProperty("user.home"), ".cache", "campaign-finances", "fec"),
    private val out: Appendable = System.out,
) {

    /**
     * Returns the local path of the extracted `.txt` for [type] and [cycle],
     * downloading and unzipping it first unless already cached.
     */
    fun fetch(cycle: Int, type: FecBulkFileType): Path {
        val dir = cacheDir.resolve(cycle.toString()).also { it.createDirectories() }

        val txt = dir.resolve(type.txtName)
        if (txt.exists()) {
            out.appendLine("[${type.key}] using cached ${txt.toAbsolutePath()}")
            return txt
        }

        val zip = dir.resolve(type.zipName(cycle))
        download(type.url(cycle), zip)
        unzipSingleTxt(zip, txt)
        Files.deleteIfExists(zip)
        return txt
    }

    /**
     * Streams [url] to [target], computing a SHA-256 on the way through
     * (a [DigestOutputStream] hashes bytes as they are written, so the file is
     * read exactly once).
     */
    private fun download(url: String, target: Path) {
        out.appendLine("downloading $url ...")
        val digest = MessageDigest.getInstance("SHA-256")

        // Bulk files are large and per-request timeouts would kill slow
        // downloads; 0 disables the timeout for this client.
        val client = HttpClient(CIO) { engine { requestTimeout = 0 } }

        // Ktor's API is coroutine-based; runBlocking bridges it into this
        // synchronous batch pipeline.
        client.use { runBlocking { streamToFile(client, url, target, digest) } }

        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        out.appendLine("downloaded ${target.fileName} (${Files.size(target)} bytes, sha256=$sha256)")
    }

    /**
     * Issues the GET, checks the response status, and streams the body into
     * [target], hashing bytes into [digest] as they're written.
     *
     * @throws IllegalStateException if the response status is not 2xx
     */
    private suspend fun streamToFile(client: HttpClient, url: String, target: Path, digest: MessageDigest) {
        client.prepareGet(url).execute { response ->
            check(response.status.isSuccess()) { "GET $url failed: ${response.status}" }
            DigestOutputStream(target.outputStream().buffered(), digest).use { sink ->
                response.bodyAsChannel().copyTo(sink)
            }
        }
    }

    /**
     * Extracts the single `.txt` entry from [zip] to [target].
     * FEC bulk zips contain exactly one data file.
     */
    private fun unzipSingleTxt(zip: Path, target: Path) {
        ZipFile(zip.toFile()).use { zipFile ->
            val entry = zipFile.entries().asSequence().firstOrNull { it.name.endsWith(".txt") }
                ?: error("no .txt entry found in ${zip.fileName}")
            zipFile.getInputStream(entry).use { input ->
                target.outputStream().buffered().use { output -> input.copyTo(output) }
            }
        }
    }
}
