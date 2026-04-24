package com.kali.nethunter.mcpchat.data

/**
 * Controls how the app offers NetHunter/Kali chroot tools through built-in MCP.
 *
 * [commandWrapper] is usually empty so the app tries `bootkali` then `kali` via [su] [0]. Set to a
 * full path to your NetHunter wrapper if it differs.
 */
data class KaliNethunterConfig(
    val execEnabled: Boolean = true,
    val commandWrapper: String? = null,
) {
    fun commandWrapperOrDefault(): String = commandWrapper?.trim().orEmpty()
}
