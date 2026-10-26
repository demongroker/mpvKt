package live.mehiz.mpvkt.ui.player

import live.mehiz.mpvkt.library.LibraryLogic
import live.mehiz.mpvkt.library.PlaybackQueue
import live.mehiz.mpvkt.library.QueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSessionLogicTest {

  private fun item(id: Long, name: String, folder: String = "Films") = QueueItem(
    fileKey = LibraryLogic.fileKey(id, "/storage/emulated/0/$folder/$name"),
    mediaId = id,
    uri = "content://media/external/video/media/$id",
    path = "/storage/emulated/0/$folder/$name",
    name = name,
  )

  private fun sessionOf(vararg names: String): PlayerSessionLogic.Session {
    val queue: PlaybackQueue = PlaybackQueue("Films", names.mapIndexed { i, n -> item(i + 1L, n) })
    return PlayerSessionLogic.Session.fromQueue(queue, queue.items[0].fileKey)
  }

  @Test
  fun nextLoadsTheTargetAndKeepsThePlayerOpen() {
    val session = sessionOf("a.mkv", "b.mkv", "c.mkv")
    val target = PlayerSessionLogic.nextTarget(session, "", "", "", emptySet(), loop = false)
    assertEquals("b.mkv", target?.name)

    val after = PlayerSessionLogic.openInPlace(session, target!!)
    assertEquals(target.fileKey, after.fileKey)
    assertFalse("Next must not finish the player activity", after.activityFinished)
    assertEquals(0L, after.resumePositionMs)
    assertEquals(3, after.queueSize)
    assertEquals(session.queue, after.queue)
  }

  @Test
  fun previousLoadsTheTargetAndKeepsThePlayerOpen() {
    val queue = PlaybackQueue("Films", listOf(item(1, "a.mkv"), item(2, "b.mkv"), item(3, "c.mkv")))
    val session = PlayerSessionLogic.Session.fromQueue(queue, queue.items[2].fileKey)
    val target = PlayerSessionLogic.previousTarget(session, "", "", "", loop = false)
    assertEquals("b.mkv", target?.name)

    val after = PlayerSessionLogic.openInPlace(session, target!!)
    assertEquals(target.fileKey, after.fileKey)
    assertFalse(after.activityFinished)
    assertEquals(0L, after.resumePositionMs)
  }

  @Test
  fun aFailedTargetKeepsThePlayerOpenAndSkipsForward() {
    val session = sessionOf("a.mkv", "b.mkv", "c.mkv")
    val failedFirst = session.queue!!.items[1]
    val following = session.queue.items[2]

    val after = PlayerSessionLogic.afterFailure(session, failedFirst.fileKey, following)
    assertFalse(after.activityFinished)
    assertEquals(following.fileKey, after.fileKey)

    // And the next lookup skips the file that already failed.
    val next = PlayerSessionLogic.nextTarget(
      PlayerSessionLogic.openInPlace(session, following),
      "",
      "",
      "",
      setOf(failedFirst.fileKey),
      loop = false,
    )
    assertTrue(next == null || next.fileKey != failedFirst.fileKey)
  }

  @Test
  fun nothingLeftStillLeavesThePlayerOpen() {
    val session = sessionOf("a.mkv", "b.mkv")
    val after = PlayerSessionLogic.afterFailure(session, session.queue!!.items[1].fileKey, null)
    assertFalse(after.activityFinished)
    assertEquals(session.fileKey, after.fileKey)
  }

  @Test
  fun theTapLogNamesBothKeysAndTheFinishedFlag() {
    val session = sessionOf("a.mkv", "b.mkv")
    val target = session.queue!!.items[1]
    val line = PlayerSessionLogic.tapLog("Next", session, target.fileKey)
    assertTrue(line.contains("Next tap"))
    assertTrue(line.contains("current=${session.fileKey}"))
    assertTrue(line.contains("target=${target.fileKey}"))
    assertTrue(line.contains("activityFinished=false"))
  }

  @Test
  fun theCentreIconFollowsMpvAndNotTheLastTap() {
    // mpv says playing -> Pause glyph
    assertTrue(CenterButtonIcon.showPause(false))
    assertFalse(CenterButtonIcon.showPlay(false))
    // mpv says paused (including a gesture-driven pause) -> Play glyph
    assertFalse(CenterButtonIcon.showPause(true))
    assertTrue(CenterButtonIcon.showPlay(true))
    // unknown state is treated as paused rather than pretending to play
    assertFalse(CenterButtonIcon.showPause(null))
    assertTrue(CenterButtonIcon.showPlay(null))
    assertEquals("playing", CenterButtonIcon.stateLabel(false))
    assertEquals("paused", CenterButtonIcon.stateLabel(true))
    assertEquals("unknown", CenterButtonIcon.stateLabel(null))
  }

  @Test
  fun aQueueWithoutItemsStillHasNoTarget() {
    val empty = PlayerSessionLogic.Session.fromQueue(null, "some-key")
    assertNull(PlayerSessionLogic.nextTarget(empty, "", "", "", emptySet(), loop = true))
    assertNull(PlayerSessionLogic.previousTarget(empty, "", "", "", loop = true))
  }
}
