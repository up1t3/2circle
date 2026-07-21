package com.twocircle.bike.feature.auth.data

import com.twocircle.bike.feature.auth.ui.AuthError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    @Test
    fun `User model validation`() {
        val user = User(
            id = "1",
            email = "test@2circle.app",
            displayName = "Test Rider",
            city = "Екатеринбург"
        )

        assertEquals("1", user.id)
        assertEquals("test@2circle.app", user.email)
        assertEquals("Test Rider", user.displayName)
        assertEquals("Екатеринбург", user.city)
    }

    @Test
    fun `AuthError sealed interface hierarchy validation`() {
        assertNotNull(AuthError.InvalidEmail)
        assertNotNull(AuthError.WeakPassword)
        assertNotNull(AuthError.EmptyName)
        assertNotNull(AuthError.PasswordMismatch)
        assertNotNull(AuthError.TermsNotAccepted)
        assertNotNull(AuthError.NetworkError)
        assertNotNull(AuthError.GenericError)
    }
}
