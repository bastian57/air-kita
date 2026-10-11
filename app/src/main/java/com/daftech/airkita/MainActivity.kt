package com.daftech.airkita

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.daftech.airkita.ui.nav.AirKitaNav
import com.daftech.airkita.ui.theme.AirKitaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as AirKitaApp).repository
        setContent {
            AirKitaTheme {
                AirKitaNav(repo = repo)
            }
        }
    }
}
