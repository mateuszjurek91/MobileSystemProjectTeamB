package com.example.focushydrate

import com.example.focushydrate.data.*
import org.junit.Assert.*
import org.junit.Test

class AccountsTest {
    private class MemoryStorage : AccountStorage {
        var data = AccountsSnapshot()
        var failWrites = false
        override fun read() = data
        override fun write(snapshot: AccountsSnapshot): Boolean {
            if (failWrites) return false
            data = snapshot; return true
        }
    }
    @Test fun registrationNormalizesEmailAndKeepsConsent() {
        val storage = MemoryStorage(); val accounts = LocalAccounts(storage)
        val result = accounts.register(" TEST@example.com ", " Test User ", "password123", true) as AccountResult.Success
        assertEquals("test@example.com", result.profile?.email)
        assertEquals("Test User", result.profile?.username)
        assertTrue(result.profile?.emailNotifications == true)
        assertEquals(result.profile, LocalAccounts(storage).current())
        assertNotEquals("password123", storage.data.accounts.single().password.hash)
    }
    @Test fun rejectsDuplicateEmailIgnoringCase() {
        val accounts = LocalAccounts(MemoryStorage())
        accounts.register("test@example.com", "Test", "password123", false)
        assertEquals(AccountResult.Failure(AccountIssue.EXISTS), accounts.register("TEST@example.com", "Other", "password123", false))
    }
    @Test fun rejectsInvalidRegistrationFields() {
        val accounts = LocalAccounts(MemoryStorage())
        assertEquals(AccountResult.Failure(AccountIssue.EMAIL), accounts.register("not-an-email", "Test", "password123", false))
        assertEquals(AccountResult.Failure(AccountIssue.USERNAME), accounts.register("a@b.com", " ", "password123", false))
        assertEquals(AccountResult.Failure(AccountIssue.PASSWORD), accounts.register("a@b.com", "Test", "12345678", false))
        assertTrue(accounts.register("a@b.com", "Test", "123456789", false) is AccountResult.Success)
    }
    @Test fun passwordChangeRejectsWrongCurrentPasswordAndInvalidatesOldPassword() {
        val accounts = LocalAccounts(MemoryStorage())
        val profile = (accounts.register("a@b.com", "Test", "password123", false) as AccountResult.Success).profile!!
        assertEquals(AccountResult.Failure(AccountIssue.CURRENT_PASSWORD), accounts.update(profile.id, newPassword = "newpassword", currentPassword = "wrong"))
        assertTrue(accounts.update(profile.id, newPassword = "newpassword", currentPassword = "password123") is AccountResult.Success)
        accounts.logout()
        assertEquals(AccountResult.Failure(AccountIssue.CREDENTIALS), accounts.login("a@b.com", "password123"))
        assertTrue(accounts.login("a@b.com", "newpassword") is AccountResult.Success)
    }
    @Test fun emailChangeRequiresPasswordAndChangesLogin() {
        val accounts = LocalAccounts(MemoryStorage())
        val profile = (accounts.register("a@b.com", "Test", "password123", false) as AccountResult.Success).profile!!
        assertEquals(AccountResult.Failure(AccountIssue.CURRENT_PASSWORD), accounts.update(profile.id, email = "new@b.com", currentPassword = "wrong"))
        accounts.update(profile.id, email = "new@b.com", currentPassword = "password123")
        accounts.logout()
        assertEquals(AccountResult.Failure(AccountIssue.CREDENTIALS), accounts.login("a@b.com", "password123"))
        assertTrue(accounts.login("new@b.com", "password123") is AccountResult.Success)
    }
    @Test fun deletingAccountRequiresPasswordAndPreservesOtherAccounts() {
        val accounts = LocalAccounts(MemoryStorage())
        accounts.register("first@b.com", "First", "password123", false)
        val second = (accounts.register("second@b.com", "Second", "password456", false) as AccountResult.Success).profile!!
        assertEquals(AccountResult.Failure(AccountIssue.CURRENT_PASSWORD), accounts.delete(second.id, "wrong"))
        assertTrue(accounts.delete(second.id, "password456") is AccountResult.Success)
        assertNull(accounts.current())
        assertEquals(AccountResult.Failure(AccountIssue.CREDENTIALS), accounts.login("second@b.com", "password456"))
        assertTrue(accounts.login("first@b.com", "password123") is AccountResult.Success)
    }
    @Test fun failedWriteDoesNotCreateAnAccountOrSession() {
        val storage = MemoryStorage().apply { failWrites = true }
        val accounts = LocalAccounts(storage)
        assertEquals(AccountResult.Failure(AccountIssue.STORAGE), accounts.register("test@b.com", "Test", "password123", false))
        assertNull(accounts.current()); assertTrue(storage.data.accounts.isEmpty())
    }
    @Test fun sessionCannotEditAnotherAccount() {
        val accounts = LocalAccounts(MemoryStorage())
        val first = (accounts.register("first@b.com", "First", "password123", false) as AccountResult.Success).profile!!
        accounts.register("second@b.com", "Second", "password456", false)
        assertEquals(AccountResult.Failure(AccountIssue.CREDENTIALS), accounts.update(first.id, username = "Changed"))
    }
    @Test fun passwordSaltsAreUniqueAndVerifyTheSamePassword() {
        val first = Passwords.create("testpassword")
        val second = Passwords.create("testpassword")
        assertNotEquals(first.salt, second.salt); assertNotEquals(first.hash, second.hash)
        assertTrue(Passwords.matches("testpassword", first)); assertFalse(Passwords.matches("wrong", first))
    }
}
