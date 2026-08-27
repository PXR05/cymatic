package com.pxr.cymatic.data.launcher

import org.json.JSONArray
import org.json.JSONObject

sealed interface PinnedItem {
    data class App(val packageName: String) : PinnedItem
    data class Folder(val name: String, val packages: List<String>) : PinnedItem
}

object PinnedLayoutCodec {

    fun encode(items: List<PinnedItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            when (item) {
                is PinnedItem.App -> array.put(
                    JSONObject()
                        .put("type", "app")
                        .put("pkg", item.packageName)
                )

                is PinnedItem.Folder -> array.put(
                    JSONObject()
                        .put("type", "folder")
                        .put("name", item.name)
                        .put("apps", JSONArray(item.packages))
                )
            }
        }
        return array.toString()
    }

    fun decode(json: String): List<PinnedItem> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.getJSONObject(index)
                when (obj.optString("type")) {
                    "app" -> obj.optString("pkg")
                        .takeIf { it.isNotBlank() }
                        ?.let { PinnedItem.App(it) }

                    "folder" -> {
                        val packages = obj.optJSONArray("apps")
                            ?.let { apps ->
                                (0 until apps.length()).mapNotNull { i ->
                                    apps.optString(i).takeIf { it.isNotBlank() }
                                }
                            }
                            ?: emptyList()
                        if (packages.isEmpty()) {
                            null
                        } else {
                            PinnedItem.Folder(
                                name = obj.optString("name").ifBlank { "Folder" },
                                packages = packages
                            )
                        }
                    }

                    else -> null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
