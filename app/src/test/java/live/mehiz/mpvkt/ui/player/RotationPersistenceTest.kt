package live.mehiz.mpvkt.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.preference.Preference
import live.mehiz.mpvkt.preferences.preference.PreferenceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Minimal in-memory PreferenceStore so preference round-trips can be tested on the JVM. */
private class FakePreferenceStore : PreferenceStore {
  private val values = mutableMapOf<String, Any?>()

  private fun <T> pref(key: String, defaultValue: T): Preference<T> = object : Preference<T> {
    override fun key(): String = key
    @Suppress("UNCHECKED_CAST")
    override fun get(): T = values[key] as? T ?: defaultValue
    override fun set(value: T) {
      values[key] = value
    }

    override fun isSet(): Boolean = values.containsKey(key)
    override fun delete() {
      values.remove(key)
    }

    override fun defaultValue(): T = defaultValue
    override fun changes(): Flow<T> = flowOf(get())
    override fun stateIn(scope: CoroutineScope): StateFlow<T> = MutableStateFlow(get())
  }

  override fun getString(key: String, defaultValue: String): Preference<String> = pref(key, defaultValue)
  override fun getLong(key: String, defaultValue: Long): Preference<Long> = pref(key, defaultValue)
  override fun getInt(key: String, defaultValue: Int): Preference<Int> = pref(key, defaultValue)
  override fun getFloat(key: String, defaultValue: Float): Preference<Float> = pref(key, defaultValue)
  override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = pref(key, defaultValue)
  override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> = pref(key, defaultValue)

  override fun <T> getObject(
    key: String,
    defaultValue: T,
    serializer: (T) -> String,
    deserializer: (String) -> T,
  ): Preference<T> = object : Preference<T> {
    override fun key(): String = key
    override fun get(): T = (values[key] as? String)?.let(deserializer) ?: defaultValue
    override fun set(value: T) {
      values[key] = serializer(value)
    }

    override fun isSet(): Boolean = values.containsKey(key)
    override fun delete() {
      values.remove(key)
    }

    override fun defaultValue(): T = defaultValue
    override fun changes(): Flow<T> = flowOf(get())
    override fun stateIn(scope: CoroutineScope): StateFlow<T> = MutableStateFlow(get())
  }

  override fun getAll(): Map<String, *> = values
}

class RotationPersistenceTest {

  @Test
  fun rotationDefaultsToAutoSoExistingBehaviourIsUnchanged() {
    val preferences = PlayerPreferences(FakePreferenceStore())
    assertEquals(RotationMode.Auto, preferences.rotationMode.get())
  }

  @Test
  fun theChoiceSurvivesANewSession() {
    val store = FakePreferenceStore()
    PlayerPreferences(store).apply {
      rotationMode.set(RotationMode.Locked)
      lockedOrientation.set(1)
    }
    // A fresh PlayerPreferences over the same store is a new playback session.
    val nextSession = PlayerPreferences(store)
    assertEquals(RotationMode.Locked, nextSession.rotationMode.get())
    assertEquals(1, nextSession.lockedOrientation.get())
  }

  @Test
  fun theButtonCyclesBetweenAutoAndLocked() {
    assertEquals(RotationMode.Locked, RotationMode.Auto.toggled())
    assertEquals(RotationMode.Auto, RotationMode.Locked.toggled())
  }

  @Test
  fun autoPlayNextAndLoopDefaults() {
    val preferences = PlayerPreferences(FakePreferenceStore())
    assertTrue(preferences.autoPlayNext.get())
    assertFalse(preferences.loopFolder.get())
  }
}
