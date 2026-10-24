package com.absensiapp.school.data.remote

object SupabaseConfig {
    /**
     * URL Proyek Supabase Anda.
     * Ganti dengan URL proyek Supabase Anda (contoh: https://xyzcompany.supabase.co)
     */
    var SUPABASE_URL: String = "https://fqxialxxlhqlfsmpeogs.supabase.co"

    /**
     * Supabase Public Anonymous API Key (anon key).
     * Dapat ditemukan di Project Settings -> API pada dashboard Supabase.
     */
    var SUPABASE_ANON_KEY: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZxeGlhbHh4bGhxbGZzbXBlb2dzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA5ODMzMDEsImV4cCI6MjEwNjU1OTMwMX0.KhC-tkaM8SQ5-pQBEZiW8LpbQ8aFhvjmJ0rVK8dKm74"

    fun isConfigured(): Boolean {
        return SUPABASE_URL.startsWith("https://") &&
                !SUPABASE_URL.contains("your-project-id") &&
                SUPABASE_ANON_KEY != "your-anon-key-here" &&
                SUPABASE_ANON_KEY.isNotEmpty()
    }
}
