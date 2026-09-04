package com.derycode.deryaccount.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore("session")

/**
 * SessionManager — persists the logged-in user + active branch across
 * app restarts (cashier tablets stay logged into their branch POS).
 * Also holds Supabase config (URL + anon key) entered once at setup.
 */
class SessionManager(private val context: Context) {

    companion object {
        val KEY_USER = stringPreferencesKey("user_id")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_ROLE = stringPreferencesKey("role")
        val KEY_BRANCH = stringPreferencesKey("branch_id")
        val KEY_BRANCH_NAME = stringPreferencesKey("branch_name")
        val KEY_SB_URL = stringPreferencesKey("supabase_url")
        val KEY_SB_KEY = stringPreferencesKey("supabase_anon_key")
        val KEY_TUTORIAL_DONE = stringPreferencesKey("tutorial_done")

        fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }

    val userId: Flow<String?> = context.dataStore.data.map { it[KEY_USER] }
    val branchId: Flow<String?> = context.dataStore.data.map { it[KEY_BRANCH] }

    suspend fun saveLogin(userId: String, username: String, role: String, branchId: String, branchName: String) {
        context.dataStore.edit {
            it[KEY_USER] = userId; it[KEY_USERNAME] = username; it[KEY_ROLE] = role
            it[KEY_BRANCH] = branchId; it[KEY_BRANCH_NAME] = branchName
        }
    }

    suspend fun saveSupabase(url: String, anonKey: String) {
        context.dataStore.edit { it[KEY_SB_URL] = url; it[KEY_SB_KEY] = anonKey }
    }

    suspend fun supabaseConfig(): Pair<String, String>? {
        val data = context.dataStore.data.first()
        val url = data[KEY_SB_URL] ?: return null
        val key = data[KEY_SB_KEY] ?: return null
        return url to key
    }

    suspend fun logout() {
        context.dataStore.edit { it.clear() }
    }
}
