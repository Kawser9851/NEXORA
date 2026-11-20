package com.nexora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        val auth = FirebaseAuth.getInstance()

        setContent {
            NEXORAScreen(auth)
        }
    }
}

@Composable
private fun NEXORAScreen(auth: FirebaseAuth) {

    var user by remember {
        mutableStateOf(auth.currentUser)
    }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            user = it.currentUser
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val bg = Brush.verticalGradient(
        listOf(
            Color(0xFF080B18),
            Color(0xFF211442),
            Color(0xFF080B18)
        )
    )

    MaterialTheme(colorScheme = darkColorScheme()) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
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

                Spacer(Modifier.height(6.dp))

                Text(
                    "Play Together. Talk Together.",
                    color = Color.LightGray
                )

                Spacer(Modifier.height(30.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF211C3D)
                    )
                ) {

                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        if (user == null) {

                            Text(
                                if (isSignUp) "Create Your Account"
                                else "Welcome Back",
                                color = Color.White,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(20.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email Address") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation =
                                    PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    if (email.isBlank() ||
                                        password.length < 6
                                    ) {
                                        message =
                                            "Enter a valid email and password (minimum 6 characters)."
                                    } else {
                                        loading = true
                                        message = ""

                                        val task =
                                            if (isSignUp) {
                                                auth.createUserWithEmailAndPassword(
                                                    email.trim(),
                                                    password
                                                )
                                            } else {
                                                auth.signInWithEmailAndPassword(
                                                    email.trim(),
                                                    password
                                                )
                                            }

                                        task.addOnCompleteListener {
                                            loading = false

                                            message =
                                                if (it.isSuccessful) {
                                                    "Welcome to NEXORA!"
                                                } else {
                                                    it.exception?.localizedMessage
                                                        ?: "Login failed."
                                                }
                                        }
                                    }
                                },
                                enabled = !loading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (isSignUp) "Create Account"
                                    else "Login"
                                )
                            }

                            TextButton(
                                onClick = {
                                    isSignUp = !isSignUp
                                    message = ""
                                },
                                enabled = !loading
                            ) {
                                Text(
                                    if (isSignUp)
                                        "Already have an account? Login"
                                    else
                                        "Create a new account"
                                )
                            }

                            HorizontalDivider(
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )

                            OutlinedButton(
                                onClick = {
                                    loading = true
                                    message = ""

                                    auth.signInAnonymously()
                                        .addOnCompleteListener {
                                            loading = false
                                            message =
                                                if (it.isSuccessful)
                                                    "Guest login successful!"
                                                else
                                                    it.exception?.localizedMessage
                                                        ?: "Guest login failed."
                                        }
                                },
                                enabled = !loading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Continue as Guest")
                            }

                        } else {

                            Text(
                                "Welcome to NEXORA!",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(16.dp))

                            Text(
                                if (user!!.isAnonymous)
                                    "Guest Account"
                                else
                                    user!!.email ?: "Signed In",
                                color = Color(0xFF9FE7D5)
                            )

                            Spacer(Modifier.height(22.dp))

                            Button(
                                onClick = {
                                    auth.signOut()
                                    message = "You have logged out."
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Logout")
                            }
                        }

                        if (loading) {
                            Spacer(Modifier.height(16.dp))
                            CircularProgressIndicator(
                                color = Color(0xFFB7A4FF)
                            )
                        }

                        if (message.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                message,
                                color = Color(0xFF9FE7D5),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "NEXORA • Free for Everyone",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}
