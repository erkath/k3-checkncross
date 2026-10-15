package me.k3.organizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import me.k3.organizer.ui.day.DayScreen
import me.k3.organizer.ui.theme.MyOrganizerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyOrganizerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DayScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
