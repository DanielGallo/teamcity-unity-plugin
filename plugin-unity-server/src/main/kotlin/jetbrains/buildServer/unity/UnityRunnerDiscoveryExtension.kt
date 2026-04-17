

package jetbrains.buildServer.unity

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.serverSide.discovery.BreadthFirstRunnerDiscoveryExtension
import jetbrains.buildServer.serverSide.discovery.DiscoveredObject
import jetbrains.buildServer.util.browser.Element

private data class DiscoveredUnityProject(
    val path: String,
    val unityVersion: UnityVersion? = null,
    val buildProfile: String? = null,
) : DiscoveredObject(
    UnityConstants.RUNNER_TYPE,
    buildMap {
        put(UnityConstants.PARAM_PROJECT_PATH, path)
        unityVersion?.let { put(UnityConstants.PARAM_UNITY_VERSION, unityVersion.toString()) }
        buildProfile?.let { put(UnityConstants.PARAM_BUILD_PROFILE, it) }
    },
)

class UnityRunnerDiscoveryExtension : BreadthFirstRunnerDiscoveryExtension(DEPTH_LIMIT) {
    companion object {
        private const val DEPTH_LIMIT = 3
        private const val PROFILE_SCAN_DEPTH_LIMIT = 10
        private const val PROJECT_SETTINGS_DIR = "ProjectSettings"
        private val PROJECTS_DIRS = listOf("Assets", PROJECT_SETTINGS_DIR)
        private val logger = Logger.getInstance(UnityRunnerDiscoveryExtension::class.java.name)
    }

    override fun discoverRunnersInDirectory(
        dir: Element,
        filesAndDirs: MutableList<Element>,
    ): MutableList<DiscoveredObject> {
        if (!dir.isUnityProjectDirectory()) {
            logger.debug("Directory: ${dir.fullName} seems not to be a Unity project directory, skipping")
            return mutableListOf()
        }

        val unityVersion = UnityProject(VcsUnityProjectFileAccessor(dir)).unityVersion
        val buildProfile = findBuildProfile(dir)

        logger.info("Unity project was found in directory '${dir.fullName}'${if (unityVersion == null) "" else ", associated Unity version: '$unityVersion'"}${if (buildProfile == null) "" else ", build profile: '$buildProfile'"}")
        return mutableListOf(DiscoveredUnityProject(dir.fullName, unityVersion, buildProfile))
    }

    private fun findBuildProfile(projectDir: Element): String? {
        val profiles = mutableListOf<String>()

        val conventionalDir = projectDir.children
            ?.firstOrNull { !it.isLeaf && it.name == "Assets" }
            ?.children
            ?.firstOrNull { !it.isLeaf && it.name == "Settings" }
            ?.children
            ?.firstOrNull { !it.isLeaf && it.name == "Build Profiles" }

        if (conventionalDir != null) {
            collectProfiles(conventionalDir, "Assets/Settings/Build Profiles", profiles, 0)
        }

        if (profiles.isEmpty()) {
            val assetsDir = projectDir.children?.firstOrNull { !it.isLeaf && it.name == "Assets" }
            if (assetsDir != null) {
                collectProfiles(assetsDir, "Assets", profiles, 0)
            }
        }

        return when {
            profiles.size == 1 -> {
                logger.info("Found exactly one Build Profile: '${profiles[0]}', pre-populating buildProfile parameter.")
                profiles[0]
            }
            profiles.size > 1 -> {
                logger.info("Found ${profiles.size} Build Profiles: ${profiles.joinToString(", ")}. Leaving buildProfile blank.")
                null
            }
            else -> null
        }
    }

    private fun collectProfiles(element: Element, relativePath: String, profiles: MutableList<String>, depth: Int) {
        if (depth > PROFILE_SCAN_DEPTH_LIMIT) return
        val children = element.children ?: return
        for (child in children) {
            val childPath = "$relativePath/${child.name}"
            if (child.isLeaf && child.name.endsWith(".asset") && child.isContentAvailable) {
                if (isBuildProfileAsset(child)) {
                    profiles.add(childPath)
                }
            } else if (!child.isLeaf) {
                collectProfiles(child, childPath, profiles, depth + 1)
            }
        }
    }

    private fun isBuildProfileAsset(element: Element): Boolean {
        return try {
            element.inputStream?.bufferedReader()?.use { reader ->
                reader.lineSequence().take(20).any { line -> line.contains("BuildProfile:") }
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun Element.isUnityProjectDirectory(): Boolean =
        children
            ?.filter { !it.isLeaf }
            ?.map { it.name }
            ?.containsAll(PROJECTS_DIRS)
            ?: false
}

private class VcsUnityProjectFileAccessor(projectPath: Element) : UnityProjectFilesAccessor {
    private var current = projectPath

    override fun directory(name: String): UnityProjectFilesAccessor? {
        current = current.children
            ?.firstOrNull { it.name == name } ?: return null
        return this
    }

    override fun file(name: String) = current.children
        ?.filter { it.isContentAvailable }
        ?.firstOrNull { it.name == name }
        ?.inputStream
}
