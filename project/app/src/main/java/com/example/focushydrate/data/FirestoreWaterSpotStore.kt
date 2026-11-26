package com.example.focushydrate.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

class FirestoreWaterSpotStore(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val spotsCollection = db.collection("water_spots")
    private val feedbackCollection = db.collection("spot_feedback")

    private suspend fun ensureAuth() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    fun observeSpots(): Flow<List<WaterSpot>> = callbackFlow {
        var registration: ListenerRegistration? = null
        val job = launch {
            try {
                ensureAuth()
                registration = spotsCollection.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        val name = doc.getString("name") ?: return@mapNotNull null
                        val lat = doc.getDouble("latitude") ?: return@mapNotNull null
                        val lon = doc.getDouble("longitude") ?: return@mapNotNull null
                        val ownerId = doc.getString("ownerId") ?: ""
                        runCatching { WaterSpot(doc.id, name, lat, lon, ownerId) }.getOrNull()
                    }.orEmpty()
                    trySend(list)
                }
            } catch (e: Exception) {
                close(e)
            }
        }
        awaitClose {
            job.cancel()
            registration?.remove()
        }
    }

    fun observeFeedback(): Flow<List<SpotFeedback>> = callbackFlow {
        var registration: ListenerRegistration? = null
        val job = launch {
            try {
                ensureAuth()
                registration = feedbackCollection.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        val spotId = doc.getString("spotId") ?: return@mapNotNull null
                        val authorId = doc.getString("authorId") ?: return@mapNotNull null
                        val authorName = doc.getString("authorName") ?: ""
                        val statusRaw = doc.getString("status") ?: return@mapNotNull null
                        val status = runCatching { FeedbackStatus.valueOf(statusRaw) }.getOrNull() ?: return@mapNotNull null
                        val note = doc.getString("note") ?: ""
                        val createdAt = doc.getLong("createdAt") ?: 0L
                        val votes = (doc.get("votes") as? Map<*, *>)?.entries.orEmpty().mapNotNull voteEntry@{ (id, value) ->
                            val voterId = id as? String ?: return@voteEntry null
                            if (voterId == authorId) return@voteEntry null
                            val vote = FeedbackVote.entries.find { it.name == value } ?: return@voteEntry null
                            voterId to vote
                        }.toMap()
                        runCatching { SpotFeedback(spotId, authorId, authorName, status, note, createdAt, votes) }.getOrNull()
                    }.orEmpty().sortedByDescending { it.createdAt }
                    trySend(list)
                }
            } catch (e: Exception) {
                close(e)
            }
        }
        awaitClose {
            job.cancel()
            registration?.remove()
        }
    }

    suspend fun addSpot(spot: WaterSpot) {
        ensureAuth()
        val data = mapOf(
            "id" to spot.id,
            "name" to spot.name,
            "latitude" to spot.latitude,
            "longitude" to spot.longitude,
            "ownerId" to spot.ownerId,
            "createdAt" to System.currentTimeMillis()
        )
        // Transactions fail offline instead of queuing writes in the local cache.
        db.runTransaction { it.set(spotsCollection.document(spot.id), data) }.await()
    }

    suspend fun saveFeedback(report: SpotFeedback) {
        ensureAuth()
        val docId = "${report.spotId}_${report.authorId}"
        val data = mapOf(
            "spotId" to report.spotId,
            "authorId" to report.authorId,
            "authorName" to report.authorName,
            "status" to report.status.name,
            "note" to report.note,
            "createdAt" to report.createdAt
        )
        db.runTransaction { it.set(feedbackCollection.document(docId), data, SetOptions.merge()) }.await()
    }

    suspend fun toggleFeedbackVote(report: SpotFeedback, voterId: String, vote: FeedbackVote) {
        require(voterId.isNotBlank() && voterId != report.authorId)
        ensureAuth()
        val document = feedbackCollection.document("${report.spotId}_${report.authorId}")
        val field = FieldPath.of("votes", voterId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(document)
            check(snapshot.exists() && snapshot.getString("authorId") == report.authorId)
            val value = if (snapshot.get(field) == vote.name) FieldValue.delete() else vote.name
            transaction.update(document, field, value)
        }.await()
    }

    suspend fun deleteSpot(spotId: String) {
        ensureAuth()
        val relatedFeedback = feedbackCollection.whereEqualTo("spotId", spotId).get(Source.SERVER).await()
        db.runTransaction { transaction ->
            transaction.delete(spotsCollection.document(spotId))
            relatedFeedback.documents.forEach { transaction.delete(it.reference) }
        }.await()
    }

    suspend fun deleteFeedback(spotId: String, authorId: String) {
        ensureAuth()
        db.runTransaction { it.delete(feedbackCollection.document("${spotId}_${authorId}")) }.await()
    }

    suspend fun deleteAccountData(accountId: String) {
        ensureAuth()
        val ownedSpots = spotsCollection.whereEqualTo("ownerId", accountId).get().await()
        val ownedIds = ownedSpots.documents.map { it.id }.toSet()
        val userFeedback = feedbackCollection.whereEqualTo("authorId", accountId).get().await()
        val spotFeedback = if (ownedIds.isNotEmpty()) {
            feedbackCollection.whereIn("spotId", ownedIds.take(30)).get().await().documents
        } else emptyList()

        db.runBatch { batch ->
            ownedSpots.documents.forEach { batch.delete(it.reference) }
            (userFeedback.documents + spotFeedback)
                .distinctBy { it.id }
                .forEach { batch.delete(it.reference) }
        }.await()
    }

    fun deleteAccountDataBlocking(accountId: String): Boolean =
        runCatching { runBlocking { deleteAccountData(accountId) }; true }.getOrDefault(false)
}
