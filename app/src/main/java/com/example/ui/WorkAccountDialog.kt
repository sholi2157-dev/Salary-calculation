package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.api.AuthManager

@Composable
fun WorkAccountDialog(onDismiss: () -> Unit, onGoogle: () -> Unit) {
    // Deliberately not rememberSaveable: passwords never enter saved instance state.
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val blocked = AuthManager.accountBlockReason()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (register) "יצירת חשבון" else "התחברות לחשבון") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("נתוני השימוש המקומי נשארים במכשיר ולא מועברים לחשבון אוטומטית. טפסים שלא נשמרו ייסגרו בעת ההתחברות." +
                    if (com.example.BuildConfig.VERSIONED_SYNC_ENABLED) " נתוני החשבון מסתנכרנים עם מכשירים המחוברים לאותו חשבון."
                    else " סנכרון הענן עדיין לא הופעל.")
                OutlinedTextField(email, { email = it }, label = { Text("דוא״ל") }, singleLine = true,
                    enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(password, { password = it }, label = { Text("סיסמה") }, singleLine = true,
                    enabled = !busy, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (register) OutlinedTextField(confirmation, { confirmation = it }, label = { Text("אישור סיסמה") },
                    singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                (message ?: blocked)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                TextButton(enabled = !busy, onClick = { register = !register; message = null; password = ""; confirmation = "" }) {
                    Text(if (register) "כבר יש לי חשבון" else "יצירת חשבון חדש")
                }
                if (!register) TextButton(enabled = !busy && blocked == null, onClick = {
                    busy = true
                    AuthManager.resetPassword(email) { message = it; busy = false }
                }) { Text("שכחתי סיסמה") }
                if (com.example.BuildConfig.GOOGLE_SIGN_IN_ENABLED) {
                    TextButton(enabled = !busy && blocked == null, onClick = onGoogle) { Text("התחברות עם Google") }
                } else Text("התחברות עם Google עדיין אינה זמינה בגרסה זו.")
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && blocked == null, onClick = {
                if (register && password != confirmation) message = "הסיסמאות אינן זהות"
                else {
                    busy = true
                    AuthManager.submitEmail(email, password, register) { success, error ->
                        busy = false
                        password = ""
                        confirmation = ""
                        message = error
                        if (success) onDismiss()
                    }
                }
            }) { Text(if (register) "יצירת חשבון" else "התחברות") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("ביטול") } }
    )
}
