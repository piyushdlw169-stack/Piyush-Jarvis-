package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.JarvisMainScreen
import com.example.ui.theme.JarvisCommandTheme
import com.example.viewmodel.JarvisViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        // STEP 2: Enable edge-to-edge display and initialize Compose UI
        enableEdgeToEdge()
        setContent {
            JarvisCommandTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val jarvisViewModel: JarvisViewModel = viewModel()
                    JarvisMainScreen(viewModel = jarvisViewModel)
                }
            }
        }
    }
}
