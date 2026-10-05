package app.nebulabox.util

import java.net.Authenticator
import java.net.PasswordAuthentication

object SocksAuth {

    const val USER = "javidtun"

    @Volatile
    private var credentials: Pair<String, String>? = null

    private var installed = false

    fun install(user: String, password: String) {
        credentials = user to password
        if (installed) return
        installed = true
        Authenticator.setDefault(
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication? {
                    val current = credentials ?: return null
                    val host = requestingHost ?: return null
                    if (host != "127.0.0.1" && host != "localhost") return null
                    return PasswordAuthentication(current.first, current.second.toCharArray())
                }
            },
        )
    }

    fun take(user: String, password: String): Pair<String, String> {
        install(user, password)
        return user to password
    }
}
