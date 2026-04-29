package com.kali.nethunter.mcpchat.data

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Best-effort execution of a one-shot shell command inside a NetHunter / Kali chroot using
 * `su` and `bootkali` or `kali` (or a user-specified [KaliNethunterConfig.commandWrapper] first token).
 * Requires root. Paths and wrapper names differ by NetHunter build; failures are returned in text.
 */
object KaliNethunterCommandRunner {

    private const val maxOutputBytes = 256 * 1024
    private val suCandidates: List<String> = listOf(
        "/data/adb/magisk/su",
        "/system/xbin/su",
        "/system/bin/su",
        "/sbin/su",
    )

    data class Outcome(
        val ok: Boolean,
        val text: String,
    )

    private fun suPathOrNull(): String? = suCandidates.find { f ->
        val file = File(f)
        file.exists() && (file.canExecute() || f.endsWith("su")) // canExecute is false for some /su on older builds
    }

    fun hasSuOnSystem(): Boolean = suPathOrNull() != null

    private fun chrootBashList(su: String, shim: String, line: String): List<String> =
        listOf(su, "0", shim, "bash", "-lc", line)

    // Direct chroot command for devices without traditional wrappers (bootkali/kali/nethunter)
    private fun directChrootList(su: String, line: String): List<String> {
        // Use chroot with proper environment setup
        // Mount required filesystems first, then run command with clean env
        val chrootPath = "/data/local/nhsystem/kali-armhf"
        val envSetup = "HOME=/root PATH=/usr/bin:/bin:/usr/sbin:/sbin"
        // First mount necessary filesystems, then execute command
        val mountCmd = "mount -t proc proc $chrootPath/proc 2>/dev/null; mount -t sysfs sysfs $chrootPath/sys 2>/dev/null; mount -o bind /dev $chrootPath/dev 2>/dev/null; mount -t devpts devpts $chrootPath/dev/pts 2>/dev/null; true"
        val chrootCmd = "chroot $chrootPath /usr/bin/env -i $envSetup $line"
        return listOf(su, "-c", "$mountCmd && $chrootCmd")
    }

    fun run(
        line: String,
        timeoutSec: Long,
        config: KaliNethunterConfig,
    ): Outcome {
        if (line.isBlank()) {
            return Outcome(false, "kali_nethunter_exec: empty command")
        }
        val su = suPathOrNull()
        if (su == null) {
            return Outcome(
                false,
                "kali_nethunter_exec: no su found at ${suCandidates.joinToString()}. " +
                    "NetHunter / Kali tools require a rooted image.",
            )
        }
        val t = timeoutSec.coerceIn(5L, 600L)
        val w = config.commandWrapperOrDefault()
        val shims: List<String> = if (w.isNotEmpty()) {
            listOf(w.split(Regex("\\s+")).first())
        } else {
            listOf("bootkali", "kali", "nethunter")
        }
        var lastErr: String? = null
        // Try traditional NetHunter wrappers first
        for (sh in shims) {
            val r = runOnce(chrootBashList(su, sh, line), t, tag = "su→$sh")
            if (r.ok) return r
            lastErr = r.text
            if (r.text.contains("no such file", ignoreCase = true) || r.text.contains("not found", ignoreCase = true)) {
                continue
            }
            return r
        }
        // Fall back to direct chroot (for minimal/custom NetHunter installs)
        val directR = runOnce(directChrootList(su, line), t, tag = "su→chroot")
        if (directR.ok) return directR
        lastErr = directR.text
        return Outcome(
            false,
            lastErr ?: "kali_nethunter_exec: all attempts failed (try a custom chroot wrapper in Settings).",
        )
    }

    private fun runOnce(
        command: List<String>,
        timeoutSec: Long,
        tag: String,
    ): Outcome = runCatching {
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        val p = pb.start()
        val out = p.inputStream.use { s ->
            val acc = StringBuilder()
            val buf = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                if (total >= maxOutputBytes) break
                val take = minOf(n, maxOutputBytes - total)
                acc.append(String(buf, 0, take, Charsets.UTF_8))
                total += take
                if (total >= maxOutputBytes) {
                    acc.append("\n…(output truncated)…\n")
                    break
                }
            }
            acc.toString()
        }
        val finished = p.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            p.destroyForcibly()
            return@runCatching Outcome(
                false,
                "[$tag] (timeout ${timeoutSec}s)\n$out\n(process killed after timeout)\n",
            )
        }
        val code = p.exitValue()
        val ok = code == 0
        val prefix = "[$tag] exit=$code\n"
        return@runCatching Outcome(ok, prefix + out)
    }.getOrElse { e ->
        Outcome(false, "[$tag] kali_nethunter_exec: ${e.message ?: e.javaClass.simpleName}")
    }
}
