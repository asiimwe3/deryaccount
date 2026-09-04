package com.derycode.deryaccount.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin Supabase REST (PostgREST) client.
 * Config points at the DeryAccount Supabase project.
 */
class SupabaseClient(private val baseUrl: String, private val anonKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = "application/json".toMediaType()

    /** GET rows from a table. Returns empty list when offline/errors. */
    fun select(table: String, query: String = ""): JSONArray? {
        val url = "$baseUrl/rest/v1/$table$query"
        val req = Request.Builder()
            .url(url)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Accept", "application/json")
            .get().build()
        return try {
            http.newCall(req).execute().use { res ->
                if (res.isSuccessful) JSONArray(res.body?.string() ?: "[]") else null
            }
        } catch (e: Exception) { null }
    }

    /** POST / PATCH upsert rows. Returns true on success. */
    fun upsert(table: String, rows: JSONArray, onConflict: String = "id"): Boolean {
        val url = "$baseUrl/rest/v1/$table?on_conflict=$onConflict"
        val req = Request.Builder()
            .url(url)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(rows.toString().toRequestBody(json))
            .build()
        return try {
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    /** Execute an RPC function. */
    fun rpc(fn: String, body: JSONObject): JSONObject? {
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/$fn")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(json))
            .build()
        return try {
            http.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val txt = res.body?.string()
                    if (txt.isNullOrBlank() || txt == "null") JSONObject() else JSONObject(txt)
                } else null
            }
        } catch (e: Exception) { null }
    }

    fun online(): Boolean {
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/branches?select=id&limit=1")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .get().build()
        return try {
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    companion object {
        // Placeholder — real keys injected at setup time via Settings screen or build config.
        @Volatile var instance: SupabaseClient? = null
            private set

        fun init(baseUrl: String, anonKey: String) {
            if (instance == null || instance!!.baseUrl != baseUrl) {
                instance = SupabaseClient(baseUrl, anonKey)
            }
        }
    }
}
