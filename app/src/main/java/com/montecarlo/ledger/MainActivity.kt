package com.montecarlo.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.montecarlo.ledger.processing.ReminderScheduler
import com.montecarlo.ledger.ui.AppView
import com.montecarlo.ledger.AppTheme
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler.scheduleDaily(this)
        setContent {
            AppTheme {
                AppView(viewModel = viewModel())
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    AppTheme {
        androidx.compose.material3.Text("Preview unavailable without ViewModel")
    }
}
