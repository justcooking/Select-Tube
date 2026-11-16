package com.selecttube.app.util

import at.favre.lib.crypto.bcrypt.BCrypt

object PasswordSecurity {
    fun hash(password: String): String {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray())
    }

    fun verify(password: String, hash: String): Boolean {
        val result = BCrypt.verifyer().verify(password.toCharArray(), hash.toCharArray())
        return result.verified
    }
}
