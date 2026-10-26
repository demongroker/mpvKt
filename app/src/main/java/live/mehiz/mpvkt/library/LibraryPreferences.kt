package live.mehiz.mpvkt.library

import live.mehiz.mpvkt.preferences.preference.PreferenceStore

class LibraryPreferences(preferenceStore: PreferenceStore) {
  /** Folders the owner pinned to the top of the library. */
  val pinnedFolders = preferenceStore.getStringSet("library_pinned_folders")

  /** Folders the owner hid from the library. Locked folders will add to this set. */
  val hiddenFolders = preferenceStore.getStringSet("library_hidden_folders")

  val sortOrder = preferenceStore.getString("library_sort_order", LibrarySort.DateAdded.name)

  /** How many media rows are loaded per page while scrolling. */
  val pageSize = preferenceStore.getInt("library_page_size", 120)

  /** Folders whose queue plays shuffled. Shuffle never changes the library's sort. */
  val shuffledFolders = preferenceStore.getStringSet("library_shuffled_folders")

  private val store = preferenceStore

  /** Seed per folder, so a shuffled queue keeps its order between sessions. */
  fun shuffleSeed(folder: String) = store.getLong("library_shuffle_seed_${folder.hashCode()}")
}
