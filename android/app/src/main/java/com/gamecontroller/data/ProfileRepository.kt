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

    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** Updates the in-memory list at once and writes the file on a background thread. */
    fun save(profile: Profile) {
        val text = Protocol.json.encodeToString(Profile.serializer(), profile)
        _profiles.value = _profiles.value.filterNot { it.id == profile.id } + profile.copy(builtIn = BuiltInProfiles.all.any { it.id == profile.id })
        _profiles.value = order(_profiles.value)
        io.execute {
            val f = file(profile.id)
            val tmp = File(dir, f.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }
    }

    private fun order(list: List<Profile>): List<Profile> {
        val byId = list.associateBy { it.id }
        val builtIns = BuiltInProfiles.all.mapNotNull { byId[it.id] }
        val custom = list.filter { p -> BuiltInProfiles.all.none { it.id == p.id } }.sortedBy { it.name.lowercase() }
        return builtIns + custom
    }

    /** Deletes a custom profile, or resets a built-in one to its shipped layout. */
    fun delete(id: String) {
        val builtIn = BuiltInProfiles.all.firstOrNull { it.id == id }
        _profiles.value = order(_profiles.value.filterNot { it.id == id } + listOfNotNull(builtIn))
        io.execute { file(id).delete() }
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
