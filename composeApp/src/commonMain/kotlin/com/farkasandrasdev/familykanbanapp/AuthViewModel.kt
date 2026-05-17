package com.farkasandrasdev.familykanbanapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farkasandrasdev.familykanbanapp.model.UserProfile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val profile: UserProfile) : AuthState
    data class Error(val message: String) : AuthState
}

class AuthViewModel : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state

    init {
        observeSession()
    }

    private fun observeSession() {
        viewModelScope.launch {
            supabase.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        _state.value = AuthState.Authenticated(
                            UserProfile(
                                id = user?.id ?: "",
                                displayName = user?.userMetadata
                                    ?.get("display_name")
                                    ?.toString()
                                    ?.trim('"')
                                    ?: user?.email?.substringBefore('@')
                                    ?: "Family Member",
                                avatarUrl = user?.userMetadata
                                    ?.get("avatar_url")
                                    ?.toString()
                                    ?.trim('"')
                            )
                        )
                    }
                    is SessionStatus.NotAuthenticated -> _state.value = AuthState.Unauthenticated
                    is SessionStatus.Initializing -> _state.value = AuthState.Loading
                    is SessionStatus.RefreshFailure -> _state.value = AuthState.Error("Session expired. Please sign in again.")
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            } catch (e: Exception) {
                _state.value = AuthState.Error(e.message ?: "Sign in failed")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                supabase.auth.signOut()
            } catch (e: Exception) {
                _state.value = AuthState.Error(e.message ?: "Sign out failed")
            }
        }
    }
}
