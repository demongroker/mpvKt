package live.mehiz.mpvkt.ui.player

import live.mehiz.mpvkt.library.PlaybackQueue
import live.mehiz.mpvkt.library.QueueAdvance
import live.mehiz.mpvkt.library.QueueItem

/**
 * What happens to the player session when a queue button is used.
 *
 * The player is a single screen: Next and Previous must swap the file inside it, so
 * [Session] carries an explicit [activityFinished] flag that is only ever true when
 * the owner leaves the player on purpose. That flag is the thing the old code got
 * wrong (it started a new activity and finished the old one, which dropped the user
 * back to the library).
 */
object PlayerSessionLogic {

  data class Session(
    val fileKey: String,
    val queueSize: Int,
    val queue: PlaybackQueue?,
    val activityFinished: Boolean,
    val resumePositionMs: Long,
  ) {
    companion object {
      fun fromQueue(queue: PlaybackQueue?, currentKey: String): Session = Session(
        fileKey = currentKey,
        queueSize = queue?.items?.size ?: 0,
        queue = queue,
        activityFinished = false,
        resumePositionMs = 0,
      )
    }
  }

  /** Next/Previous tap: adopt the target file, stay on screen, restart at 0:00. */
  fun openInPlace(session: Session, target: QueueItem): Session = session.copy(
    fileKey = target.fileKey,
    queueSize = session.queue?.items?.size ?: 0,
    activityFinished = false,
    resumePositionMs = 0,
  )

  /**
   * The target could not be opened: stay in the player, remember it, and move to the
   * following item (null when there is nothing left — the session still stays open).
   */
  fun afterFailure(session: Session, failedKey: String, following: QueueItem?): Session =
    session.copy(
      fileKey = following?.fileKey ?: session.fileKey,
      activityFinished = false,
      resumePositionMs = if (following == null) session.resumePositionMs else 0,
    )

  /** Resolves the next target with the already-failed keys skipped. */
  fun nextTarget(
    session: Session,
    currentUri: String,
    currentPath: String,
    currentName: String,
    failedKeys: Set<String>,
    loop: Boolean,
  ): QueueItem? = QueueAdvance.nextAfterEnd(
    queue = session.queue,
    currentKey = session.fileKey,
    currentUri = currentUri,
    currentPath = currentPath,
    currentName = currentName,
    failedKeys = failedKeys,
    loop = loop,
  )

  fun previousTarget(
    session: Session,
    currentUri: String,
    currentPath: String,
    currentName: String,
    loop: Boolean,
  ): QueueItem? = QueueAdvance.previous(
    queue = session.queue,
    currentKey = session.fileKey,
    currentUri = currentUri,
    currentPath = currentPath,
    currentName = currentName,
    loop = loop,
  )

  /** The line logged under VelaQueue for every Next/Previous tap. */
  fun tapLog(direction: String, session: Session, targetKey: String?): String =
    "$direction tap: current=${session.fileKey} target=${targetKey ?: "none"} " +
      "activityFinished=${session.activityFinished} queue=${session.queueSize}"
}

/**
 * The centre play/pause button mirrors mpv, never the last tap: while mpv reports
 * "not paused" the icon is Pause, and it only flips when mpv confirms the change.
 */
object CenterButtonIcon {

  /** True when the Pause glyph should be shown (mpv says the file is playing). */
  fun showPause(paused: Boolean?): Boolean = paused == false

  /** True when the Play glyph should be shown. Unknown state counts as paused. */
  fun showPlay(paused: Boolean?): Boolean = paused != false

  fun stateLabel(paused: Boolean?): String = when (paused) {
    true -> "paused"
    false -> "playing"
    null -> "unknown"
  }
}
