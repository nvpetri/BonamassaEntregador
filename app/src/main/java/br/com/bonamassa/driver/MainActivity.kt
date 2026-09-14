package br.com.bonamassa.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.bonamassa.driver.ui.DriverApp
import br.com.bonamassa.driver.ui.DriverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { DriverTheme { if (BuildConfig.DEMO_MODE) DriverApp() else br.com.bonamassa.driver.connected.ConnectedDriverApp() } }
    }
}
