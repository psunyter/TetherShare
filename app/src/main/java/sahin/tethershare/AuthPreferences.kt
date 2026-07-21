package sahin.tethershare

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

class AuthPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    companion object {
        const val AUTH_ENABLED_KEY = "auth_enabled"
        const val USERS_KEY = "users" // Stored as "user1:pass1,user2:pass2"
    }

    var isAuthEnabled: Boolean
        get() = prefs.getBoolean(AUTH_ENABLED_KEY, false)
        set(value) = prefs.edit().putBoolean(AUTH_ENABLED_KEY, value).apply()

    fun getUsers(): List<Pair<String, String>> {
        val usersString = prefs.getString(USERS_KEY, "") ?: ""
        if (usersString.isEmpty()) return emptyList()
        return usersString.split(",").mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2) parts[0] to parts[1] else null
        }
    }

    fun addUser(user: String, pass: String) {
        val currentUsers = getUsers().toMutableList()
        if (currentUsers.none { it.first == user }) {
            currentUsers.add(user to pass)
            saveUsers(currentUsers)
        }
    }

    fun removeUser(user: String) {
        val currentUsers = getUsers().toMutableList()
        currentUsers.removeAll { it.first == user }
        saveUsers(currentUsers)
    }

    private fun saveUsers(users: List<Pair<String, String>>) {
        val usersString = users.joinToString(",") { "${it.first}:${it.second}" }
        prefs.edit().putString(USERS_KEY, usersString).apply()
    }

    /**
     * Returns a set of Base64 encoded "username:password" strings for fast lookup.
     */
    fun getValidCredentialsBase64(): Set<String> {
        return getUsers().map { (user, pass) ->
            Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
        }.toSet()
    }
}
