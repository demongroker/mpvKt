package live.mehiz.mpvkt.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
  onForgotPin: (() -> Unit)? = null,
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
    dismissButton = {
      Row {
        if (onForgotPin != null) {
          TextButton(onClick = onForgotPin) { Text("Forgot PIN") }
        }
        TextButton(onClick = onDismiss) { Text("Cancel") }
      }
    },
  )
}

/**
 * The escape hatch for a forgotten PIN: it explains exactly what will be revealed before
 * doing it, because the only way to drop the hash is to unlock everything.
 */
@Composable
internal fun ForgotPinDialog(
  lockedCount: Int,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Reset the lock?") },
    text = {
      Text(
        "This removes the PIN and shows all $lockedCount locked folder" +
          (if (lockedCount == 1) "" else "s") +
          " again. There is no other way back without the PIN, and it cannot be undone.",
      )
    },
    confirmButton = { TextButton(onClick = onConfirm) { Text("Reset lock") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Keep locked") } },
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
