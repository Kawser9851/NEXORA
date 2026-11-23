package com.nexora.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

private data class FriendRequestItem(
    val id: String,
    val senderUid: String,
    val receiverUid: String
)

private data class FriendItem(
    val uid: String,
    val name: String,
    val username: String
)

@Composable
fun FriendSystem(uid: String, db: FirebaseFirestore) {
    var searchUsername by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    var incoming by remember { mutableStateOf(emptyList<FriendRequestItem>()) }
    var outgoing by remember { mutableStateOf(emptyList<FriendRequestItem>()) }
    var friends by remember { mutableStateOf(emptyList<FriendItem>()) }

    DisposableEffect(uid) {
        val registrations = mutableListOf<ListenerRegistration>()

        registrations += db.collection("friendRequests")
            .whereEqualTo("receiverUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    incoming = snapshot.documents.mapNotNull { doc ->
                        val sender = doc.getString("senderUid") ?: return@mapNotNull null
                        val receiver = doc.getString("receiverUid") ?: return@mapNotNull null
                        FriendRequestItem(doc.id, sender, receiver)
                    }
                }
            }

        registrations += db.collection("friendRequests")
            .whereEqualTo("senderUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    outgoing = snapshot.documents.mapNotNull { doc ->
                        val sender = doc.getString("senderUid") ?: return@mapNotNull null
                        val receiver = doc.getString("receiverUid") ?: return@mapNotNull null
                        FriendRequestItem(doc.id, sender, receiver)
                    }
                }
            }

        fun listenFriends(field: String) {
            registrations += db.collection("friendships")
                .whereEqualTo(field, uid)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        val current = snapshot.documents.mapNotNull { doc ->
                            val a = doc.getString("userA") ?: return@mapNotNull null
                            val b = doc.getString("userB") ?: return@mapNotNull null
                            val friendUid = if (a == uid) b else a
                            FriendItem(friendUid, friendUid, "")
                        }
                        friends = (friends.filterNot { old ->
                            old.uid in current.map { it.uid }
                        } + current).distinctBy { it.uid }
                    }
                }
        }

        listenFriends("userA")
        listenFriends("userB")

        onDispose {
            registrations.forEach { it.remove() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
    ) {
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Find Friends", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = searchUsername,
            onValueChange = { searchUsername = it.lowercase().trim() },
            label = { Text("Enter exact username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val clean = searchUsername.trim().lowercase()

                if (clean.isBlank()) {
                    statusMessage = "Enter a username first."
                } else {
                    busy = true
                    statusMessage = "Searching..."

                    db.collection("usernames").document(clean).get()
                        .addOnSuccessListener { usernameDoc ->
                            val targetUid = usernameDoc.getString("uid")

                            if (targetUid.isNullOrBlank()) {
                                busy = false
                                statusMessage = "User not found."
                            } else if (targetUid == uid) {
                                busy = false
                                statusMessage = "You cannot add yourself."
                            } else {
                                db.collection("friendships")
                                    .whereEqualTo("userA", uid)
                                    .get()
                                    .addOnSuccessListener { first ->
                                        val alreadyFriend = first.documents.any {
                                            it.getString("userB") == targetUid
                                        }

                                        db.collection("friendships")
                                            .whereEqualTo("userB", uid)
                                            .get()
                                            .addOnSuccessListener { second ->
                                                val alsoFriend = second.documents.any {
                                                    it.getString("userA") == targetUid
                                                }

                                                if (alreadyFriend || alsoFriend) {
                                                    busy = false
                                                    statusMessage = "You are already friends."
                                                } else {
                                                    checkAndSendRequest(
                                                        db, uid, targetUid
                                                    ) { result ->
                                                        busy = false
                                                        statusMessage = result
                                                    }
                                                }
                                            }
                                            .addOnFailureListener {
                                                busy = false
                                                statusMessage = it.localizedMessage ?: "Search failed."
                                            }
                                    }
                                    .addOnFailureListener {
                                        busy = false
                                        statusMessage = it.localizedMessage ?: "Search failed."
                                    }
                            }
                        }
                        .addOnFailureListener {
                            busy = false
                            statusMessage = it.localizedMessage ?: "Search failed."
                        }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Search & Send Request")
        }

        if (busy) {
            CircularProgressIndicator()
        }

        if (statusMessage.isNotBlank()) {
            Text(statusMessage, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text("Incoming Requests (${incoming.size})",
            style = MaterialTheme.typography.titleMedium)

        if (incoming.isEmpty()) {
            Text("No new friend requests.")
        }

        incoming.forEach { request ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Request from: ${request.senderUid}")
                    Row {
                        Button(
                            onClick = {
                                val batch = db.batch()
                                val requestRef = db.collection("friendRequests")
                                    .document(request.id)
                                val friendshipRef = db.collection("friendships")
                                    .document(request.id)

                                batch.update(requestRef, "status", "accepted")
                                batch.set(
                                    friendshipRef,
                                    mapOf(
                                        "userA" to request.senderUid,
                                        "userB" to request.receiverUid,
                                        "requestId" to request.id,
                                        "createdAt" to FieldValue.serverTimestamp()
                                    )
                                )

                                batch.commit()
                                    .addOnSuccessListener {
                                        statusMessage = "Friend request accepted!"
                                    }
                                    .addOnFailureListener {
                                        statusMessage = it.localizedMessage
                                            ?: "Could not accept request."
                                    }
                            }
                        ) {
                            Text("Accept")
                        }

                        Spacer(Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = {
                                db.collection("friendRequests")
                                    .document(request.id)
                                    .update("status", "rejected")
                                    .addOnSuccessListener {
                                        statusMessage = "Request rejected."
                                    }
                                    .addOnFailureListener {
                                        statusMessage = it.localizedMessage
                                            ?: "Could not reject request."
                                    }
                            }
                        ) {
                            Text("Reject")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text("Sent Requests (${outgoing.size})",
            style = MaterialTheme.typography.titleMedium)

        outgoing.forEach { request ->
            Text("Pending: ${request.receiverUid}")
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text("My Friends (${friends.size})",
            style = MaterialTheme.typography.titleMedium)

        if (friends.isEmpty()) {
            Text("No friends yet.")
        } else {
            friends.forEach { friend ->
                Text("Friend UID: ${friend.uid}")
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

private fun checkAndSendRequest(
    db: FirebaseFirestore,
    senderUid: String,
    receiverUid: String,
    onResult: (String) -> Unit
) {
    db.collection("friendRequests")
        .whereEqualTo("senderUid", senderUid)
        .whereEqualTo("receiverUid", receiverUid)
        .whereEqualTo("status", "pending")
        .get()
        .addOnSuccessListener { sent ->
            if (!sent.isEmpty) {
                onResult("Request already sent.")
                return@addOnSuccessListener
            }

            db.collection("friendRequests")
                .whereEqualTo("senderUid", receiverUid)
                .whereEqualTo("receiverUid", senderUid)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener { received ->
                    if (!received.isEmpty) {
                        onResult("This user has already sent you a request.")
                    } else {
                        val requestRef = db.collection("friendRequests").document()

                        requestRef.set(
                            mapOf(
                                "senderUid" to senderUid,
                                "receiverUid" to receiverUid,
                                "status" to "pending",
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).addOnSuccessListener {
                            onResult("Friend request sent!")
                        }.addOnFailureListener {
                            onResult(it.localizedMessage ?: "Could not send request.")
                        }
                    }
                }
                .addOnFailureListener {
                    onResult(it.localizedMessage ?: "Could not check requests.")
                }
        }
        .addOnFailureListener {
            onResult(it.localizedMessage ?: "Could not check requests.")
        }
}
