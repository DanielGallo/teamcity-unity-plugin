

package jetbrains.buildServer.unity.logging

class BuildProfileBlock : LogBlock {

    override val name = "Build Profile"

    override val logFirstLine = LogType.Outside

    override val logLastLine = LogType.None

    override fun isBlockStart(text: String) = PROFILE_PATTERN.containsMatchIn(text)

    override fun isBlockEnd(text: String) = false

    override fun getText(text: String) = text

    fun extractProfilePath(text: String): String? =
        PROFILE_PATTERN.find(text)?.groupValues?.getOrNull(1)?.trim()

    companion object {
        val PROFILE_PATTERN = Regex("(?i)build profile set to:\\s*(.+)")
        const val ACTIVE_BUILD_PROFILE_PARAM = "unity.active.build.profile"
    }
}
