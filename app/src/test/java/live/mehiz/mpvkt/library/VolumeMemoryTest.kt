package live.mehiz.mpvkt.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeMemoryTest {

  @Test
  fun aStoredVolumeIsRestoredWhenTheOptionIsOn() {
    assertEquals(65, VolumeMemory.onStart(remember = true, stored = 65))
  }

  @Test
  fun theOptionOffLeavesMpvAlone() {
    assertNull(VolumeMemory.onStart(remember = false, stored = 65))
  }

  @Test
  fun nothingStoredMeansNothingToApply() {
    assertNull(VolumeMemory.onStart(remember = true, stored = null))
  }

  @Test
  fun outOfRangeValuesAreClampedBothWays() {
    assertEquals(0, VolumeMemory.clamp(-20))
    assertEquals(VolumeMemory.MAX_VOLUME, VolumeMemory.clamp(999))
    assertEquals(VolumeMemory.MAX_VOLUME, VolumeMemory.onStart(remember = true, stored = 500))
  }

  @Test
  fun aMissingReadingIsStoredAsMpvDefault() {
    assertEquals(100, VolumeMemory.toStore(null))
    assertEquals(80, VolumeMemory.toStore(80))
    assertEquals(VolumeMemory.MAX_VOLUME, VolumeMemory.toStore(300))
  }

  @Test
  fun percentMatchesTheClampedValue() {
    assertEquals(100, VolumeMemory.percent(100))
    assertEquals(0, VolumeMemory.percent(-5))
  }
}
