package com.nexora.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

private data class FriendRequestItem(
    val id: String,
    val otherUid: String
)

@Composable
fun FriendSystem(uid: String, db: FirebaseFirestore) {

    var username by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var loadingAction by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    var foundUid by remember { mutableStateOf("") }
    var foundName by remember { mutableStateOf("") }
    var foundUsername by remember { mutableStateOf("") }

    var incoming by remember { mutableStateOf<List<FriendRequestItem>>(emptyList()) }
    var outgoing by remember { mutableStateOf<List<FriendRequestItem>>(emptyList()) }

    var friendsA by remember { mutableStateOf<List<String>>(emptyList()) }
    var friendsB by remember { mutableStateOf<List<String>>(emptyList()) }

    val friendNames = remember { mutableStateMapOf<String, String>() }

    val friendIds = (friendsA + friendsB).distinct()

    DisposableEffect(uid, db) {
        val registrations = mutableListOf<ListenerRegistration>()

        registrations.add(
            db.collection("friendRequests")
                .whereEqualTo("receiverUid", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        message = "Incoming requests error: ${error.message}"
                    } else {
                        incoming = snapshot?.documents
                            ?.filter { it.getString("status") == "pending" }
                            ?.map {
                                FriendRequestItem(
                                    it.id,
                                    it.getString("senderUid") ?: ""
                                )
                            } ?: emptyList()
                    }
                }
        )

        registrations.add(
            db.collection("friendRequests")
                .whereEqualTo("senderUid", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        message = "Sent requests error: ${error.message}"
                    } else {
                        outgoing = snapshot?.documents
                            ?.filter { it.getString("status") == "pending" }
                            ?.map {
                                FriendRequestItem(
                                    it.id,
                                    it.getString("receiverUid") ?: ""
                                )
                            } ?: emptyList()
                    }
                }
        )

        registrations.add(
            db.collection("friendships")
                .whereEqualTo("userA", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        message = "Friends loading error: ${error.message}"
                    } else {
                        friendsA = snapshot?.documents?.mapNotNull {
                            it.getString("userB")
                        } ?: emptyList()
                    }
                }
        )

        registrations.add(
            db.collection("friendships")
                .whereEqualTo("userB", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        message = "Friends loading error: ${error.message}"
                    } else {
                        friendsB = snapshot?.documents?.mapNotNull {
                            it.getString("userA")
                        } ?: emptyList()
                    }
                }
        )

        onDispose {
            registrations.forEach { it.remove() }
        }
    }

    LaunchedEffect(friendIds) {
        friendIds.forEach { friendUid ->
            db.collection("profiles")
                .document(friendUid)
                .get()
                .addOnSuccessListener { profile ->
                    val name = profile.getString("displayName")
                        ?: profile.getString("username")
                        ?: friendUid

                    val tag = profile.getString("username") ?: ""

                    friendNames[friendUid] =
                        if (tag.isNotBlank()) "$name (@$tag)" else name
                }
        }
    }

    fun searchFriend() {
        val cleanUsername = username.trim().lowercase()

        if (cleanUsername.isBlank()) {
            message = "Please enter a username."
            return
        }

        if (!cleanUsername.matches(Regex("^[a-z0-9_]{3,20}$"))) {
            message = "Username must be 3-20 characters."
            return
        }

        searching = true
        message = "Searching..."
        foundUid = ""
        foundName = ""
        foundUsername = ""

        db.collection("usernames")
            .document(cleanUsername)
            .get()
            .addOnSuccessListener { usernameDoc ->

                if (!usernameDoc.exists()) {
                    searching = false
                    message = "User not found. Check the username."
                    return@addOnSuccessListener
                }

                val targetUid = usernameDoc.getString("uid")

                if (targetUid.isNullOrBlank()) {
                    searching = false
                    message = "This username record is incomplete."
                    return@addOnSuccessListener
                }

                if (targetUid == uid) {
                    searching = false
                    message = "You cannot add yourself as a friend."
                    return@addOnSuccessListener
                }

                db.collection("profiles")
                    .document(targetUid)
                    .get()
                    .addOnSuccessListener { profile ->

                        searching = false

                        if (!profile.exists()) {
                            message = "This user has not completed their profile."
                            return@addOnSuccessListener
                        }

                        foundUid = targetUid
                        foundName = profile.getString("displayName") ?: "NEXORA User"
                        foundUsername = profile.getString("username") ?: cleanUsername
                        message = "User found!"
                    }
                    .addOnFailureListener {
                        searching = false
                        message = "Profile error: ${it.message}"
                    }
            }
            .addOnFailureListener {
                searching = false
                message = "Search failed: ${it.message}"
            }
    }

    fun sendRequest() {
        if (foundUid.isBlank()) {
            message = "Search for a user first."
            return
        }

        loadingAction = true
        message = "Checking friend status..."

        db.collection("friendships")
            .whereEqualTo("userA", uid)
            .whereEqualTo("userB", foundUid)
            .get()
            .addOnSuccessListener { first ->

                db.collection("friendships")
                    .whereEqualTo("userA", foundUid)
                    .whereEqualTo("userB", uid)
                    .get()
                    .addOnSuccessListener { second ->

                        if (!first.isEmpty || !second.isEmpty) {
                            loadingAction = false
                            message = "You are already friends."
                            return@addOnSuccessListener
                        }

                        db.collection("friendRequests")
                            .whereEqualTo("senderUid", uid)
                            .get()
                            .addOnSuccessListener { sent ->

                                val alreadySent = sent.documents.any {
                                    it.getString("receiverUid") == foundUid &&
                                        it.getString("status") == "pending"
                                }

                                if (alreadySent) {
                                    loadingAction = false
                                    message = "Friend request already sent."
                                    return@addOnSuccessListener
                                }

                                db.collection("friendRequests")
                                    .whereEqualTo("senderUid", foundUid)
                                    .get()
                                    .addOnSuccessListener { received ->

                                        val reversePending = received.documents.any {
                                            it.getString("receiverUid") == uid &&
                                                it.getString("status") == "pending"
                                        }

                                        if (reversePending) {
                                            loadingAction = false
                                            message = "This user has already sent you a request. Accept it below."
                                            return@addOnSuccessListener
                                        }

                                        val request = hashMapOf(
                                            "senderUid" to uid,
                                            "receiverUid" to foundUid,
                                            "status" to "pending",
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )

                                        db.collection("friendRequests")
                                            .add(request)
                                            .addOnSuccessListener {
                                                loadingAction = false
                                                message = "Friend request sent successfully!"
                                            }
                                            .addOnFailureListener {
                                                loadingAction = false
                                                message = "Could not send request: ${it.message}"
                                            }
                                    }
                                    .addOnFailureListener {
                                        loadingAction = false
                                        message = "Request check failed: ${it.message}"
                                    }
                            }
                            .addOnFailureListener {
                                loadingAction = false
                                message = "Request check failed: ${it.message}"
                            }
                    }
                    .addOnFailureListener {
                        loadingAction = false
                        message = "Friend check failed: ${it.message}"
                    }
            }
            .addOnFailureListener {
                loadingAction = false
                message = "Friend check failed: ${it.message}"
            }
    }

    fun acceptRequest(request: FriendRequestItem) {
        loadingAction = true

        val batch = db.batch()

        val requestRef = db.collection("friendRequests")
            .document(request.id)

        val friendshipRef = db.collection("friendships")
            .document(request.id)

        batch.update(requestRef, "status", "accepted")

        batch.set(
            friendshipRef,
            mapOf(
                "userA" to uid,
                "userB" to request.otherUid,
                "requestId" to request.id,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        batch.commit()
            .addOnSuccessListener {
                loadingAction = false
                message = "Friend request accepted!"
            }
            .addOnFailureListener {
                loadingAction = false
                message = "Accept failed: ${it.message}"
            }
    }

    fun rejectRequest(request: FriendRequestItem) {
        loadingAction = true

        db.collection("friendRequests")
            .document(request.id)
            .update("status", "rejected")
            .addOnSuccessListener {
                loadingAction = false
                message = "Friend request rejected."
            }
            .addOnFailureListener {
                loadingAction = false
                message = "Reject failed: ${it.message}"
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Find Friends",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it.lowercase() },
            label = { Text("Enter username") },
            placeholder = { Text("example: nexora_user") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { searchFriend() },
            enabled = !searching && !loadingAction,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (searching) "Searching..." else "Search Username")
        }

        if (message.isNotBlank()) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (foundUid.isNotBlank()) {
            HorizontalDivider()

            Text("Search Result", style = MaterialTheme.typography.titleMedium)
            Text(foundName, style = MaterialTheme.typography.titleLarge)
            Text("@$foundUsername")

            Button(
                onClick = { sendRequest() },
                enabled = !loadingAction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Friend Request")
            }
        }

        HorizontalDivider()

        Text("Incoming Requests", style = MaterialTheme.typography.titleLarge)

        if (incoming.isEmpty()) {
            Text("No incoming requests.")
        }

        incoming.forEach { request ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Request from: ${request.otherUid}")

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { acceptRequest(request) },
                            enabled = !loadingAction
                        ) {
                            Text("Accept")
                        }

                        OutlinedButton(
                            onClick = { rejectRequest(request) },
                            enabled = !loadingAction
                        ) {
                            Text("Reject")
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        Text("Sent Requests", style = MaterialTheme.typography.titleLarge)

        if (outgoing.isEmpty()) {
            Text("No pending sent requests.")
        }

        outgoing.forEach { request ->
            Text("Pending: ${request.otherUid}")
        }

        HorizontalDivider()

        Text("My Friends", style = MaterialTheme.typography.titleLarge)

        if (friendIds.isEmpty()) {
            Text("You have no friends yet.")
        }

        friendIds.forEach { friendUid ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = friendNames[friendUid] ?: "Loading profile...",
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
