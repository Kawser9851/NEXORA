package com.nexora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent {
            val auth = remember { FirebaseAuth.getInstance() }
            var status by remember {
                mutableStateOf(if (auth.currentUser == null) "Firebase connected • Guest mode" else "Signed in")
            }
            NEXORAScreen(status = status)
        }
    }
}

@Composable
private fun NEXORAScreen(status: String) {
    val bg = Brush.verticalGradient(listOf(Color(0xFF080B18), Color(0xFF17113A), Color(0xFF080B18)))
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier.fillMaxSize().background(bg).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NEXORA", color = Color(0xFFB7A4FF), fontSize = 38.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text("Play Together. Talk Together.", color = Color(0xFFD9D6EA), fontSize = 14.sp)
                Spacer(Modifier.height(32.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF211C3D)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Welcome to NEXORA", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text(status, color = Color(0xFF9FE7D5), fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Firebase setup is included. Next: sign-in and voice rooms.",
                            color = Color(0xFFC8C4D8), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
