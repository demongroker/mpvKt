package live.mehiz.mpvkt.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Asks for the lock PIN. [onSubmit] returns true when the PIN was accepted, false to
 * keep the dialog open with an error. The PIN is never logged and never persisted here.
 */
@Composable
internal fun PinPromptDialog(
  title: String,
  subtitle: String? = null,
  onDismiss: () -> Unit,
  onSubmit: (String) -> Boolean,
) {
  var pin by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (subtitle != null) Text(subtitle)
        OutlinedTextField(
          value = pin,
          onValueChange = {
            pin = it.filter(Char::isDigit).take(LibraryLock.MAX_PIN_LENGTH)
            error = null
          },
          singleLine = true,
          label = { Text("PIN") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) Text(error!!)
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          if (onSubmit(pin)) {
            onDismiss()
          } else {
            error = "Wrong PIN"
            pin = ""
          }
        },
        enabled = pin.isNotEmpty(),
      ) { Text("Unlock") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/** Creates the PIN the first time the owner locks a folder. Asked twice. */
@Composable
internal fun SetPinDialog(
  onDismiss: () -> Unit,
  onSave: (String) -> Unit,
) {
  var first by remember { mutableStateOf("") }
  var second by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  val valid = LibraryLock.isValidPin(first) && first == second

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Set a lock PIN") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("This PIN hides locked folders. ${LibraryLock.MIN_PIN_LENGTH}-${LibraryLock.MAX_PIN_LENGTH} digits.")
        OutlinedTextField(
          value = first,
          onValueChange = {
            first = it.filter(Char::isDigit).take(LibraryLock.MAX_PIN_LENGTH)
            error = null
          },
          singleLine = true,
          label = { Text("PIN") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = second,
          onValueChange = {
            second = it.filter(Char::isDigit).take(LibraryLock.MAX_PIN_LENGTH)
            error = null
          },
          singleLine = true,
          label = { Text("Repeat PIN") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          modifier = Modifier.fillMaxWidth(),
        )
        val message = error
          ?: if (first.isNotEmpty() && !LibraryLock.isValidPin(first)) {
            "At least ${LibraryLock.MIN_PIN_LENGTH} digits"
          } else if (second.isNotEmpty() && first != second) {
            "The two PINs do not match"
          } else {
            null
          }
        if (message != null) Text(message)
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          if (valid) {
            onSave(first)
            onDismiss()
          } else {
            error = "Choose a PIN of at least ${LibraryLock.MIN_PIN_LENGTH} digits"
            second = ""
          }
        },
        enabled = valid,
      ) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
