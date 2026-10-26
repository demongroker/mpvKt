package live.mehiz.mpvkt.ui.home

import java.net.URI

/**
 * URL field rules, kept free of Android so they can be unit tested.
 *
 * The field must be neutral until the owner actually types something: an empty
 * box is not an error, and a half-typed address is only flagged once it cannot
 * possibly become playable.
 */
object UrlValidation {

  /** True when [text] is an absolute, playable URL for one of [protocols]. */
  fun isPlayable(text: String, protocols: Collection<String>): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
    val scheme = uri.scheme ?: return false
    if (uri.isOpaque) return false
    if (protocols.none { it.equals(scheme, ignoreCase = true) }) return false
    return !(uri.host.isNullOrBlank() && uri.path.isNullOrBlank())
  }

  /**
   * Only complain once there is something in the field that is not playable.
   * Blank input is neutral, never an error.
   */
  fun showError(text: String, protocols: Collection<String>): Boolean =
    text.isNotBlank() && !isPlayable(text, protocols)
}
