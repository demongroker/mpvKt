package live.mehiz.mpvkt.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.security.SecureSecrets
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

/**
 * Subtitle provider section of the Subtitles preferences.
 *
 * The keyless endpoint stays the default; OpenSubtitles.com is opt-in and its
 * credentials are written through [SecureSecrets] (Android Keystore), never into
 * the preference store in the clear and never into the source.
 */
@Composable
fun SubtitleProviderSettings(preferences: SubtitlesPreferences) {
  val useCom by preferences.useOpenSubtitlesCom.collectAsState()
  val language by preferences.downloadLanguage.collectAsState()

  SwitchPreference(
    value = useCom,
    onValueChange = preferences.useOpenSubtitlesCom::set,
    title = { Text("Use OpenSubtitles.com (API key)") },
    summary = {
      Text(
        if (useCom) {
          "Searching your OpenSubtitles.com account"
        } else {
          "Searching the keyless OpenSubtitles endpoint — no account needed"
        },
      )
    },
  )

  if (useCom) {
    SecretField("API key", SecureSecrets.OS_COM_API_KEY, mask = false)
    SecretField("Username", SecureSecrets.OS_COM_USERNAME, mask = false)
    SecretField("Password", SecureSecrets.OS_COM_PASSWORD, mask = true)
  }

  TextFieldPreference(
    value = language,
    onValueChange = preferences.downloadLanguage::set,
    textToValue = { it.trim() },
    title = { Text("Subtitle language") },
    summary = { Text(if (language.isBlank()) "eng" else language) },
    textField = { value, onValueChange, _ ->
      OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Language code (eng, ind, may…)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      )
    },
  )
}

@Composable
private fun SecretField(title: String, secretName: String, mask: Boolean) {
  val context = LocalContext.current
  var text by remember { mutableStateOf("") }
  var saved by remember { mutableStateOf(SecureSecrets.isSet(context, secretName)) }

  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    OutlinedTextField(
      value = text,
      onValueChange = { text = it },
      label = { Text(title) },
      singleLine = true,
      visualTransformation = if (mask) PasswordVisualTransformation() else VisualTransformation.None,
      trailingIcon = { if (saved) Icon(Icons.Default.Check, null) },
      modifier = Modifier.fillMaxWidth(),
    )
    Row {
      TextButton(
        enabled = text.isNotBlank(),
        onClick = {
          SecureSecrets.put(context, secretName, text)
          saved = SecureSecrets.isSet(context, secretName)
          text = ""
        },
      ) { Text("Save") }
      TextButton(
        enabled = saved,
        onClick = {
          SecureSecrets.clear(context, secretName)
          saved = false
        },
      ) { Text("Clear") }
    }
    Text(
      text = if (saved) "Stored encrypted on this device" else "Not set",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
