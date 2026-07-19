package com.example.app.processing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.app.data.AppDatabase
import com.example.app.data.LedgerRepository
import com.example.app.util.centsToDisplay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repo = LedgerRepository(AppDatabase.getInstance(applicationContext))
        val prefs = repo.reminderPreferences.first()
        if (!prefs.enabled) return Result.success()

        val parts = mutableListOf<String>()
        val today = LocalDate.now()

        if (prefs.weeklyCheckInEnabled && today.dayOfWeek.value == 7) {
            parts += "Weekly check-in: open the app to review this week's cash flow."
        }

        if (prefs.billRemindersEnabled) {
            val dueSoon = repo.allBillOccurrences.first()
                .filter { it.is_paid == 0 }
                .filter {
                    val dueDate = runCatching { LocalDate.parse(it.due_date) }.getOrNull() ?: return@filter false
                    val daysAway = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate).toInt()
                    daysAway in 0..prefs.billReminderDaysBefore
                }
                .take(3)

            if (dueSoon.isNotEmpty()) {
                val dueLabels = dueSoon.joinToString(" • ") { occurrence ->
                    val due = runCatching { LocalDate.parse(occurrence.due_date) }.getOrNull()
                    val label = due?.format(DateTimeFormatter.ofPattern("MMM d")) ?: occurrence.due_date
                    "$label ${centsToDisplay(occurrence.amount_cents)}"
                }
                parts += "Bills due soon: $dueLabels"
            }
        }

        if (parts.isEmpty()) return Result.success()

        val content = parts.joinToString("\n")
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MonteCarlo Ledger")
            .setContentText(parts.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    private companion object {
        const val CHANNEL_ID = "ledger_reminders"
        const val NOTIFICATION_ID = 1001
    }
}
