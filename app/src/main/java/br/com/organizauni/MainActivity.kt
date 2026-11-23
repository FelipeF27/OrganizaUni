package br.com.organizauni

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.organizauni.ui.OrganizaUniApp
import br.com.organizauni.ui.OrganizaUniTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrganizaUniTheme {
                OrganizaUniApp()
            }
        }
    }
}
