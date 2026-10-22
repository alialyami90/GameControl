package com.gamecontroller.data

import android.content.Context
import android.util.Log
import com.gamecontroller.model.Profile
import com.gamecontroller.profiles.BuiltInProfiles
import com.gamecontroller.profiles.ProfileJson
import com.gamecontroller.protocol.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Profiles on disk, one JSON file each in filesDir/profiles. Built-in profiles are always present;
 * editing one saves an override file with the same id, and deleting that override restores the default.
 */
class ProfileRepository(context: Context) {
    private val dir = File(context.filesDir, "profiles").apply { mkdirs() }
    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val saved = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            try {
                Protocol.json.decodeFromString(Profile.serializer(), f.readText())
            } catch (e: Exception) {
                Log.w(TAG, "Skipping unreadable profile ${f.name}", e)
                null
            }
        }.associateBy { it.id }
        val builtIns = BuiltInProfiles.all.map { b -> saved[b.id]?.copy(builtIn = true) ?: b }
        val custom = saved.values.filter { s -> BuiltInProfiles.all.none { it.id == s.id } }.sortedBy { it.name.lowercase() }
        _profiles.value = builtIns + custom
    }

    fun get(id: String): Profile? = _profiles.value.firstOrNull { it.id == id }

    fun save(profile: Profile) {
        val f = file(profile.id)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(Protocol.json.encodeToString(Profile.serializer(), profile))
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
        reload()
    }

    /** Deletes a custom profile, or resets a built-in one to its shipped layout. */
    fun delete(id: String) {
        file(id).delete()
        reload()
    }

    fun isModifiedBuiltIn(id: String) = BuiltInProfiles.all.any { it.id == id } && file(id).exists()

    fun importJson(text: String): Profile {
        val p = ProfileJson.import(text)
        save(p)
        return p
    }

    private fun file(id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    companion object {
        private const val TAG = "ProfileRepository"
    }
}
