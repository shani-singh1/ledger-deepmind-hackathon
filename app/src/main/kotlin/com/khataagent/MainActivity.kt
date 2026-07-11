package com.khataagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Placeholder — Agent C (app workstream) replaces this with the real navigation + 4 screens
 * and KhataTheme. Exists now so the project configures and builds end-to-end.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("KhataAgent — scaffolding")
                }
            }
        }
    }
}
