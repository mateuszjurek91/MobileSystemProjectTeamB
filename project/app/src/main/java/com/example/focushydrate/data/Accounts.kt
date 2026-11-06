package com.example.focushydrate.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class AccountProfile(val id: String, val email: String, val username: String, val emailNotifications: Boolean)
data class PasswordDigest(val salt: String, val hash: String)
data class AccountRecord(val profile: AccountProfile, val password: PasswordDigest)
data class AccountsSnapshot(val accounts: List<AccountRecord> = emptyList(), val sessionId: String? = null)
interface AccountStorage {
    fun read(): AccountsSnapshot
    fun write(snapshot: AccountsSnapshot): Boolean
}
enum class AccountIssue { EMAIL, USERNAME, PASSWORD, EXISTS, CREDENTIALS, CURRENT_PASSWORD, STORAGE }
sealed interface AccountResult {
    data class Success(val profile: AccountProfile?) : AccountResult
    data class Failure(val issue: AccountIssue) : AccountResult
}

object Passwords {
    fun create(password: String): PasswordDigest {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return PasswordDigest(hex(salt), hex(derive(password, salt)))
    }
    fun matches(password: String, stored: PasswordDigest): Boolean =
        runCatching { MessageDigest.isEqual(derive(password, bytes(stored.salt)), bytes(stored.hash)) }.getOrDefault(false)
    private fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 210_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

class LocalAccounts(private val storage: AccountStorage) {
    fun current(): AccountProfile? {
        val snapshot = storage.read()
        return snapshot.accounts.find { it.profile.id == snapshot.sessionId }?.profile
    }
    @Synchronized
    fun register(email: String, username: String, password: String, consent: Boolean): AccountResult {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        validate(cleanEmail, username.trim(), password)?.let { return AccountResult.Failure(it) }
        val snapshot = storage.read()
        if (snapshot.accounts.any { it.profile.email == cleanEmail }) return AccountResult.Failure(AccountIssue.EXISTS)
        val profile = AccountProfile(UUID.randomUUID().toString(), cleanEmail, username.trim(), consent)
        return persist(snapshot.copy(accounts = snapshot.accounts + AccountRecord(profile, Passwords.create(password)), sessionId = profile.id), profile)
    }
    @Synchronized
    fun login(email: String, password: String): AccountResult {
        val snapshot = storage.read()
        val record = snapshot.accounts.find { it.profile.email.equals(email.trim(), ignoreCase = true) }
        if (record == null || !Passwords.matches(password, record.password)) return AccountResult.Failure(AccountIssue.CREDENTIALS)
        return persist(snapshot.copy(sessionId = record.profile.id), record.profile)
    }
    @Synchronized
    fun update(id: String, username: String? = null, email: String? = null,
        newPassword: String? = null, currentPassword: String = "", consent: Boolean? = null): AccountResult {
        val snapshot = storage.read()
        val record = snapshot.accounts.find { it.profile.id == id && snapshot.sessionId == id }
            ?: return AccountResult.Failure(AccountIssue.CREDENTIALS)
        if ((email != null || newPassword != null) && !Passwords.matches(currentPassword, record.password))
            return AccountResult.Failure(AccountIssue.CURRENT_PASSWORD)
        val cleanEmail = email?.trim()?.lowercase(Locale.ROOT) ?: record.profile.email
        val name = username?.trim() ?: record.profile.username
        validate(cleanEmail, name, newPassword)?.let { return AccountResult.Failure(it) }
        if (snapshot.accounts.any { it.profile.id != id && it.profile.email == cleanEmail }) return AccountResult.Failure(AccountIssue.EXISTS)
        val profile = record.profile.copy(email = cleanEmail, username = name, emailNotifications = consent ?: record.profile.emailNotifications)
        val updated = record.copy(profile = profile, password = newPassword?.let(Passwords::create) ?: record.password)
        return persist(snapshot.copy(accounts = snapshot.accounts.map { if (it.profile.id == id) updated else it }), profile)
    }
    fun verifyPassword(id: String, password: String): Boolean {
        val snapshot = storage.read()
        val record = snapshot.accounts.find { it.profile.id == id && snapshot.sessionId == id } ?: return false
        return Passwords.matches(password, record.password)
    }
    @Synchronized
    fun delete(id: String, password: String): AccountResult {
        val snapshot = storage.read()
        val record = snapshot.accounts.find { it.profile.id == id && snapshot.sessionId == id }
            ?: return AccountResult.Failure(AccountIssue.CREDENTIALS)
        if (!Passwords.matches(password, record.password)) return AccountResult.Failure(AccountIssue.CURRENT_PASSWORD)
        return persist(snapshot.copy(accounts = snapshot.accounts.filterNot { it.profile.id == id }, sessionId = null), null)
    }
    @Synchronized
    fun logout(): AccountResult = persist(storage.read().copy(sessionId = null), null)
    private fun persist(snapshot: AccountsSnapshot, profile: AccountProfile?): AccountResult =
        if (storage.write(snapshot)) AccountResult.Success(profile) else AccountResult.Failure(AccountIssue.STORAGE)
    private fun validate(email: String, username: String, password: String?): AccountIssue? = when {
        email.length > 254 || !Regex("^[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+$").matches(email) -> AccountIssue.EMAIL
        username.length !in 2..30 -> AccountIssue.USERNAME
        password != null && password.length !in 8..128 -> AccountIssue.PASSWORD
        else -> null
    }
}
