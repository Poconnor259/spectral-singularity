package app.shouldersofgiants.guardian.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRepository {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "$timestamp: $message"
        val currentList = _logs.value.toMutableList()
        currentList.add(entry)
        _logs.value = currentList
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
