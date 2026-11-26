package com.example.focushydrate.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

class FirestoreAccountStorage(
    context: Context,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AccountStorage {

    private val prefs = context.applicationContext.getSharedPreferences("local_accounts", Context.MODE_PRIVATE)
    private val accountsCollection = db.collection("accounts")
    private var cachedAccounts: Map<String, AccountRecord> = emptyMap()

    private suspend fun ensureAuth() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    override fun read(): AccountsSnapshot = runBlocking {
        ensureAuth()
        val snapshot = accountsCollection.get().await()
        val accounts = snapshot.documents.mapNotNull { doc ->
            val id = doc.getString("id") ?: doc.id
            val email = doc.getString("email") ?: return@mapNotNull null
            val username = doc.getString("username") ?: return@mapNotNull null
            val consent = doc.getBoolean("emailNotifications") ?: false
            val salt = doc.getString("salt") ?: return@mapNotNull null
            val hash = doc.getString("hash") ?: return@mapNotNull null
            AccountRecord(
                profile = AccountProfile(id, email, username, consent),
                password = PasswordDigest(salt, hash)
            )
        }
        cachedAccounts = accounts.associateBy { it.profile.id }
        AccountsSnapshot(accounts, prefs.getString("session", null))
    }

    override fun write(snapshot: AccountsSnapshot): Boolean = runCatching {
        runBlocking {
            ensureAuth()
            val nextMap = snapshot.accounts.associateBy { it.profile.id }

            // Zapisz nowe lub zmodyfikowane konta (rejestracja / zmiana danych w ustawieniach)
            for ((id, record) in nextMap) {
                if (cachedAccounts[id] != record) {
                    val data = mapOf(
                        "id" to record.profile.id,
                        "email" to record.profile.email,
                        "username" to record.profile.username,
                        "emailNotifications" to record.profile.emailNotifications,
                        "salt" to record.password.salt,
                        "hash" to record.password.hash
                    )
                    accountsCollection.document(id).set(data).await()
                }
            }

            // Usuń z Firestore konta usunięte przez użytkownika
            for (removedId in cachedAccounts.keys - nextMap.keys) {
                accountsCollection.document(removedId).delete().await()
            }

            cachedAccounts = nextMap
        }

        // Sesja pozostaje lokalna dla konkretnego urządzenia
        prefs.edit().putString("session", snapshot.sessionId).commit()
    }.getOrDefault(false)
}