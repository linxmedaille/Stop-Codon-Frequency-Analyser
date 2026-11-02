package com.tapflow.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Reads and writes the persisted [Workspace]. Unknown or broken data falls back to defaults. */
object WorkspaceCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = false
    }

    fun encode(workspace: Workspace): String = json.encodeToString(Workspace.serializer(), workspace)

    fun decode(text: String?): Workspace {
        if (text.isNullOrBlank()) return Workspace()
        return try {
            json.decodeFromString(Workspace.serializer(), text)
        } catch (_: SerializationException) {
            Workspace()
        } catch (_: IllegalArgumentException) {
            Workspace()
        }
    }
}
