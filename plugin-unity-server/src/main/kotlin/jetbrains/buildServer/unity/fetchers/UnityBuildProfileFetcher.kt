
package jetbrains.buildServer.unity.fetchers

import jetbrains.buildServer.serverSide.DataItem
import jetbrains.buildServer.serverSide.ProjectDataFetcher
import jetbrains.buildServer.util.browser.Browser
import jetbrains.buildServer.util.browser.Element
import java.io.File

class UnityBuildProfileFetcher : ProjectDataFetcher {

    override fun getType() = "UnityBuildProfile"

    override fun retrieveData(fsBrowser: Browser, projectFilePath: String): MutableList<DataItem> {
        val items = mutableListOf<DataItem>()

        // Check the conventional location first: Assets/Settings/Build Profiles/
        val conventionalDir = fsBrowser.getElement(File(projectFilePath, CONVENTIONAL_PROFILES_DIR).path)
        if (conventionalDir != null) {
            collectAssetFiles(conventionalDir, CONVENTIONAL_PROFILES_DIR, items, maxDepth = 1)
        }

        // If nothing found in the conventional location, do a shallow scan of Assets/
        if (items.isEmpty()) {
            val assetsDir = fsBrowser.getElement(File(projectFilePath, "Assets").path)
            if (assetsDir != null) {
                collectAssetFiles(assetsDir, "Assets", items, maxDepth = FALLBACK_SCAN_DEPTH)
            }
        }

        return items
    }

    private fun collectAssetFiles(
        element: Element,
        relativePath: String,
        items: MutableList<DataItem>,
        maxDepth: Int,
        currentDepth: Int = 0,
    ) {
        if (currentDepth > maxDepth) return
        val children = element.children ?: return
        for (child in children) {
            val childPath = "$relativePath/${child.name}"
            if (child.isLeaf) {
                if (child.name.endsWith(".asset")) {
                    items.add(DataItem(childPath, null))
                }
            } else {
                collectAssetFiles(child, childPath, items, maxDepth, currentDepth + 1)
            }
        }
    }

    companion object {
        private const val CONVENTIONAL_PROFILES_DIR = "Assets/Settings/Build Profiles"
        private const val FALLBACK_SCAN_DEPTH = 3
    }
}
