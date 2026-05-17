package com.farkasandrasdev.familykanbanapp

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

val supabase = createSupabaseClient(
    supabaseUrl = supabaseUrl(),
    supabaseKey = supabaseKey()
) {
    install(Auth)
    install(Postgrest)
}
