package com.routewise.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.routewise.app.ui.navigation.RouteWiseRoot
import com.routewise.app.ui.theme.RouteWiseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as RouteWiseApp).container
        setContent {
            RouteWiseTheme { RouteWiseRoot(container) }
        }
    }
}
