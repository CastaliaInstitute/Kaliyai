package com.kali.nethunter.mcpchat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.kali.nethunter.mcpchat.ui.ChatApp
import com.kali.nethunter.mcpchat.ui.ChatViewModel

class MainActivity : ComponentActivity() {
    private lateinit var chatViewModel: ChatViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatViewModel = ViewModelProvider(this, ChatViewModel.Factory)[ChatViewModel::class.java]
        enableEdgeToEdge()
        setContent {
            ChatApp(chatViewModel)
        }
        if (BuildConfig.DEBUG) {
            chatViewModel.applyDebugIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (BuildConfig.DEBUG) {
            chatViewModel.applyDebugIntent(intent)
        }
    }
}
