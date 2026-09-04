package cn.edu.bistu.kebiao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cn.edu.bistu.kebiao.ui.KebiaoApp
import cn.edu.bistu.kebiao.ui.theme.KebiaoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KebiaoTheme {
                KebiaoApp(repository = (application as KebiaoApplication).repository)
            }
        }
    }
}
