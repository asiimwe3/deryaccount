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
        val KEY_PROFILE_NAME = stringPreferencesKey("profile_name")
        val KEY_PROFILE_PHONE = stringPreferencesKey("profile_phone")
        val KEY_PROFILE_EMAIL = stringPreferencesKey("profile_email")
        val KEY_BIZ_NAME = stringPreferencesKey("biz_name")
        val KEY_BIZ_TAGLINE = stringPreferencesKey("biz_tagline")
        val KEY_BIZ_PHONE = stringPreferencesKey("biz_phone")
        val KEY_BIZ_LOCATION = stringPreferencesKey("biz_location")
        val KEY_BIZ_TIN = stringPreferencesKey("biz_tin")
        val KEY_BIZ_FOOTER = stringPreferencesKey("biz_footer")
        val KEY_BIZ_LOGO = stringPreferencesKey("biz_logo")

        fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }

    /** The shop's own identity — printed on every receipt. */
    data class BusinessProfile(
        val name: String = "",
        val tagline: String = "",
        val phone: String = "",
        val location: String = "",
        val tin: String = "",
        val footer: String = "Thank you for shopping with us!",
        /** App-storage path of the shop logo — printed on receipts, invoices & reports. */
        val logoPath: String = ""
    )

    val businessProfile: Flow<BusinessProfile?> = context.dataStore.data.map {
        val n = it[KEY_BIZ_NAME]
        if (n.isNullOrBlank()) null else BusinessProfile(
            name = n, tagline = it[KEY_BIZ_TAGLINE] ?: "",
            phone = it[KEY_BIZ_PHONE] ?: "", location = it[KEY_BIZ_LOCATION] ?: "",
            tin = it[KEY_BIZ_TIN] ?: "", footer = it[KEY_BIZ_FOOTER] ?: "Thank you for shopping with us!",
            logoPath = it[KEY_BIZ_LOGO] ?: "")
    }

    suspend fun businessProfileNow(): BusinessProfile? = businessProfile.first()

    suspend fun saveBusinessProfile(profile: BusinessProfile) {
        context.dataStore.edit {
            it[KEY_BIZ_NAME] = profile.name
            it[KEY_BIZ_TAGLINE] = profile.tagline
            it[KEY_BIZ_PHONE] = profile.phone
            it[KEY_BIZ_LOCATION] = profile.location
            it[KEY_BIZ_TIN] = profile.tin
            it[KEY_BIZ_FOOTER] = profile.footer
            it[KEY_BIZ_LOGO] = profile.logoPath
        }
    }

    /** The account owner's personal profile (who pays / who to greet). */
    data class UserProfile(
        val name: String = "",
        val phone: String = "",
        val email: String = ""
    )

    val userProfile: Flow<UserProfile?> = context.dataStore.data.map {
        val n = it[KEY_PROFILE_NAME]
        if (n.isNullOrBlank()) null else UserProfile(
            name = n, phone = it[KEY_PROFILE_PHONE] ?: "", email = it[KEY_PROFILE_EMAIL] ?: "")
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        context.dataStore.edit {
            it[KEY_PROFILE_NAME] = profile.name
            it[KEY_PROFILE_PHONE] = profile.phone
            it[KEY_PROFILE_EMAIL] = profile.email
        }
    }

    val userId: Flow<String?> = context.dataStore.data.map { it[KEY_USER] }
    val role: Flow<String?> = context.dataStore.data.map { it[KEY_ROLE] }
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
