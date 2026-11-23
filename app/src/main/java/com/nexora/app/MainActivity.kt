package com.nexora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent {
            NEXORAScreen(FirebaseAuth.getInstance())
        }
    }
}

@Composable
private fun NEXORAScreen(auth: FirebaseAuth) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val webClientId = stringResource(R.string.default_web_client_id)

    var user by remember { mutableStateOf(auth.currentUser) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    var profileLoaded by remember { mutableStateOf(false) }
    var profileExists by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf("Profile") }

    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            user = it.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(user?.uid) {
        val uid = user?.uid
        if (uid == null) {
            profileLoaded = true
            profileExists = false
            editing = false
        } else {
            profileLoaded = false
            db.collection("profiles").document(uid).get()
                .addOnSuccessListener { doc ->
                    profileExists = doc.exists()
                    if (doc.exists()) {
                        username = doc.getString("username") ?: ""
                        displayName = doc.getString("displayName") ?: ""
                        bio = doc.getString("bio") ?: ""
                        photoUrl = doc.getString("photoUrl") ?: ""
                        editing = false
                    } else {
                        username = ""
                        displayName = user?.displayName ?: ""
                        bio = ""
                        photoUrl = user?.photoUrl?.toString() ?: ""
                        editing = true
                    }
                    profileLoaded = true
                }
                .addOnFailureListener {
                    message = "Profile loading failed: ${it.localizedMessage}"
                    profileLoaded = true
                }
        }
    }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data

        if (data == null) {
            loading = false
            message = "Google Error: No result data. ResultCode=${result.resultCode}"
        } else {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken

                if (idToken.isNullOrBlank()) {
                    loading = false
                    message = "Google Error: ID Token is empty. Check Web Client ID."
                } else {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener { authTask ->
                            loading = false
                            message = if (authTask.isSuccessful) {
                                "Google Login Successful!"
                            } else {
                                "Firebase Error: ${authTask.exception?.message}"
                            }
                        }
                }
            } catch (e: ApiException) {
                loading = false
                message = "Google Error ${e.statusCode}: ${
                    GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)
                } (ResultCode=${result.resultCode})"
            } catch (e: Exception) {
                loading = false
                message = "Google Error: ${e.localizedMessage}"
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF080B18),
                            Color(0xFF17113A),
                            Color(0xFF080B18)
                        )
                    )
                )
                .padding(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "NEXORA",
                    color = Color(0xFFB7A4FF),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Play Together. Talk Together.",
                    color = Color(0xFFD9D6EA)
                )

                Spacer(Modifier.height(28.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF211C3D)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (user == null) {
                            Text(
                                if (isSignUp) "Create Account" else "Welcome to NEXORA",
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(18.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(10.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (email.isBlank() || password.length < 6) {
                                        message = "Valid email and 6+ character password required."
                                    } else {
                                        loading = true
                                        val action = if (isSignUp) {
                                            auth.createUserWithEmailAndPassword(
                                                email.trim(), password
                                            )
                                        } else {
                                            auth.signInWithEmailAndPassword(
                                                email.trim(), password
                                            )
                                        }

                                        action.addOnCompleteListener {
                                            loading = false
                                            message = if (it.isSuccessful) {
                                                "Login Successful!"
                                            } else {
                                                it.exception?.localizedMessage
                                                    ?: "Authentication failed."
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !loading
                            ) {
                                Text(if (isSignUp) "Create Account" else "Login with Email")
                            }

                            TextButton(
                                onClick = {
                                    isSignUp = !isSignUp
                                    message = ""
                                }
                            ) {
                                Text(
                                    if (isSignUp) "Already have an account? Login"
                                    else "Create a new account"
                                )
                            }

                            HorizontalDivider(color = Color.Gray)
                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    try {
                                        val options = GoogleSignInOptions.Builder(
                                            GoogleSignInOptions.DEFAULT_SIGN_IN
                                        )
                                            .requestEmail()
                                            .requestIdToken(webClientId)
                                            .build()

                                        loading = true
                                        message = "Opening Google..."
                                        val client = GoogleSignIn.getClient(
                                            context, options
                                        )
                                        googleLauncher.launch(client.signInIntent)
                                    } catch (e: Exception) {
                                        loading = false
                                        message = "Google Setup Error: ${e.localizedMessage}"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !loading
                            ) {
                                Text("Continue with Google")
                            }

                            OutlinedButton(
                                onClick = {
                                    loading = true
                                    auth.signInAnonymously()
                                        .addOnCompleteListener {
                                            loading = false
                                            message = if (it.isSuccessful) {
                                                "Guest Login Successful!"
                                            } else {
                                                it.exception?.localizedMessage
                                                    ?: "Guest Login Failed."
                                            }
                                        }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !loading
                            ) {
                                Text("Continue as Guest")
                            }

                        } else if (!profileLoaded) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Loading your profile...", color = Color.White)

                        } else if (editing) {
                            Text(
                                if (profileExists) "Edit Profile" else "Create Your Profile",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(16.dp))

                            OutlinedTextField(
                                value = username,
                                onValueChange = {
                                    username = it.lowercase().filter { c ->
                                        c.isLetterOrDigit() || c == '_'
                                    }
                                },
                                label = { Text("Unique Username") },
                                supportingText = {
                                    Text("3-20 characters: a-z, 0-9, underscore")
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Display Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = bio,
                                onValueChange = { bio = it },
                                label = { Text("Bio (max 160 characters)") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 4
                            )

                            OutlinedTextField(
                                value = photoUrl,
                                onValueChange = { photoUrl = it },
                                label = { Text("Profile Picture URL (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    val cleanUsername = username.trim().lowercase()
                                    val valid = Regex("^[a-z0-9][a-z0-9_]{1,18}[a-z0-9]$")

                                    if (!valid.matches(cleanUsername)) {
                                        message = "Username must be 3-20 characters (a-z, 0-9, _)."
                                    } else if (displayName.trim().isEmpty() ||
                                        displayName.trim().length > 30
                                    ) {
                                        message = "Display Name must be 1-30 characters."
                                    } else if (bio.length > 160) {
                                        message = "Bio must be 160 characters or less."
                                    } else if (photoUrl.length > 2048) {
                                        message = "Profile URL is too long."
                                    } else if (photoUrl.isNotBlank() &&
                                        !photoUrl.startsWith("https://")
                                    ) {
                                        message = "Profile URL must start with https://"
                                    } else {
                                        loading = true
                                        message = "Saving profile..."

                                        val uid = user!!.uid
                                        val profileRef = db.collection("profiles")
                                            .document(uid)
                                        val newUsernameRef = db.collection("usernames")
                                            .document(cleanUsername)

                                        db.runTransaction { transaction ->
                                            val oldProfile = transaction.get(profileRef)
                                            val newUsernameDoc =
                                                transaction.get(newUsernameRef)

                                            if (oldProfile.exists()) {
                                                val oldUsername =
                                                    oldProfile.getString("username") ?: ""

                                                if (oldUsername == cleanUsername) {
                                                    if (newUsernameDoc.getString("uid") != uid) {
                                                        throw Exception("Username ownership check failed.")
                                                    }
                                                } else {
                                                    if (newUsernameDoc.exists()) {
                                                        throw Exception("This username is already taken.")
                                                    }

                                                    val oldUsernameRef =
                                                        db.collection("usernames")
                                                            .document(oldUsername)
                                                    val oldUsernameDoc =
                                                        transaction.get(oldUsernameRef)

                                                    if (!oldUsernameDoc.exists() ||
                                                        oldUsernameDoc.getString("uid") != uid
                                                    ) {
                                                        throw Exception("Old username verification failed.")
                                                    }

                                                    transaction.delete(oldUsernameRef)
                                                    transaction.set(
                                                        newUsernameRef,
                                                        mapOf(
                                                            "uid" to uid,
                                                            "createdAt" to FieldValue.serverTimestamp()
                                                        )
                                                    )
                                                }
                                            } else {
                                                if (newUsernameDoc.exists()) {
                                                    throw Exception("This username is already taken.")
                                                }

                                                transaction.set(
                                                    newUsernameRef,
                                                    mapOf(
                                                        "uid" to uid,
                                                        "createdAt" to FieldValue.serverTimestamp()
                                                    )
                                                )
                                            }

                                            val createdAt = if (oldProfile.exists()) {
                                                oldProfile.get("createdAt")
                                                    ?: FieldValue.serverTimestamp()
                                            } else {
                                                FieldValue.serverTimestamp()
                                            }

                                            transaction.set(
                                                profileRef,
                                                mapOf(
                                                    "username" to cleanUsername,
                                                    "displayName" to displayName.trim(),
                                                    "bio" to bio.trim(),
                                                    "photoUrl" to photoUrl.trim(),
                                                    "createdAt" to createdAt,
                                                    "updatedAt" to FieldValue.serverTimestamp()
                                                )
                                            )
                                        }.addOnSuccessListener {
                                            loading = false
                                            profileExists = true
                                            editing = false
                                            username = cleanUsername
                                            message = "Profile saved successfully!"
                                        }.addOnFailureListener { error ->
                                            loading = false
                                            message = error.localizedMessage
                                                ?: "Could not save profile."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !loading
                            ) {
                                Text("Save Profile")
                            }

                            if (profileExists) {
                                TextButton(
                                    onClick = { editing = false }
                                ) {
                                    Text("Cancel")
                                }
                            }

                        } else {
                            Text(
                                "Your NEXORA Profile",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(18.dp))

                            Text(
                                displayName,
                                color = Color(0xFFB7A4FF),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("@$username", color = Color(0xFF9FE7D5))

                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (bio.isBlank()) "No bio added yet." else bio,
                                color = Color.White
                            )

                            if (photoUrl.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Profile picture URL saved",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    editing = true
                                    message = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Edit Profile")
                            }

                            OutlinedButton(
                                onClick = {
                                    auth.signOut()
                                    message = "Logged out."
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Logout")
                            }
                        }

                        if (loading) {
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator()
                        }

                        if (message.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                message,
                                color = Color(0xFFFFD180),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
