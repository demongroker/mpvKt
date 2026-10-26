package live.mehiz.mpvkt.ui.player

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.widget.Toast
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.github.k1rakishou.fsaf.FileManager
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity
import live.mehiz.mpvkt.database.entities.ResumeStateEntity
import live.mehiz.mpvkt.databinding.PlayerLayoutBinding
import live.mehiz.mpvkt.domain.playbackstate.repository.PlaybackStateRepository
import live.mehiz.mpvkt.domain.resume.repository.ResumeRepository
import live.mehiz.mpvkt.library.LibraryDeletion
import live.mehiz.mpvkt.library.MediaStoreLibraryRepository
import live.mehiz.mpvkt.library.QueueAdvance
import live.mehiz.mpvkt.preferences.preference.collectAsState
import live.mehiz.mpvkt.library.LibraryLogic
import live.mehiz.mpvkt.library.LibraryPreferences
import live.mehiz.mpvkt.library.PlaybackQueue
import live.mehiz.mpvkt.library.QueueItem
import live.mehiz.mpvkt.library.QueueLogic

import live.mehiz.mpvkt.preferences.AdvancedPreferences
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.preferences.GesturePreferences
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.ui.player.controls.PlayerControls
import live.mehiz.mpvkt.ui.theme.MpvKtTheme
import org.koin.android.ext.android.inject
import java.io.File

@Suppress("TooManyFunctions", "LargeClass")
class PlayerActivity : AppCompatActivity() {

  private val viewModel: PlayerViewModel by viewModels<PlayerViewModel> { PlayerViewModelProviderFactory(this) }
  private val binding by lazy { PlayerLayoutBinding.inflate(layoutInflater) }
  private val playerObserver by lazy { PlayerObserver(this) }
  private val playbackStateRepository: PlaybackStateRepository by inject()
  private val resumeRepository: ResumeRepository by inject()
  private val libraryPreferences: LibraryPreferences by inject()
  private val libraryRepository: MediaStoreLibraryRepository by inject()
  private var resumeFileKey: String = ""

  /** The folder queue, handed over by the library so next/shuffle/delete work here. */
  private var queue: PlaybackQueue? = null

  /** Files that already failed to open in this session's auto-advance chain. */
  private val pendingFailedKeys = mutableSetOf<String>()

  /** Incremented on every load attempt and on FILE_LOADED, so the timeout can tell. */
  private var loadToken = 0

  /** MediaStore shows its own confirmation; only RESULT_OK may delete anything. */
  private val deleteLauncher = registerForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { result ->
    if (result.resultCode == RESULT_OK) onFileDeleted()
  }
  val player by lazy { binding.player }
  val windowInsetsController by lazy { WindowCompat.getInsetsController(window, window.decorView) }
  val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
  private var mediaSession: MediaSession? = null
  private val playerPreferences: PlayerPreferences by inject()
  private val audioPreferences: AudioPreferences by inject()
  private val subtitlesPreferences: SubtitlesPreferences by inject()
  private val advancedPreferences: AdvancedPreferences by inject()
  private val gesturePreferences: GesturePreferences by inject()
  private val fileManager: FileManager by inject()

  private var fileName = ""
  private var mediaPlaybackService: MediaPlaybackService? = null
  private var serviceBound = false

  private var audioFocusRequest: AudioFocusRequestCompat? = null
  private var restoreAudioFocus: () -> Unit = {}

  private var pipRect: android.graphics.Rect? = null
  val isPipSupported by lazy {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      false
    } else {
      packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
  }
  private var pipReceiver: BroadcastReceiver? = null

  private val noisyReceiver = object : BroadcastReceiver() {
    var initialized = false
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
        viewModel.pause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContentView(binding.root)

    setupMPV()
    setupAudio()
    setupMediaSession()
    setupResumeKey(intent)
    setupQueue(intent)
    startResumeTicker()
    getPlayableUri(intent)?.let(player::playFile)
    setOrientation()

    binding.controls.setContent {
      MpvKtTheme {
        val rotationMode by playerPreferences.rotationMode.collectAsState()
        PlayerControls(
          viewModel = viewModel,
          onBackPress = ::finish,
          onPlayNext = { playNextInQueue(currentFailedKeys()) },
          hasNext = nextQueueItem() != null,
          onPlayPrevious = ::playPreviousInQueue,
          hasPrevious = hasPreviousItem(),
          showSkipButtons = playerPreferences.showSkipButtons.get(),
          isShuffled = queue?.shuffled == true,
          onToggleShuffle = ::toggleShuffle,
          rotationMode = rotationMode,
          onToggleRotation = ::toggleRotation,
          onDeleteFile = ::confirmDeleteCurrentFile,
          modifier = Modifier.onGloballyPositioned {
            pipRect = it.boundsInWindow().toAndroidRect()
          },
        )
      }
    }
  }

  private fun getPlayableUri(intent: Intent): String? {
    val uri = parsePathFromIntent(intent)
    return if (uri?.startsWith("content://") == true) uri.toUri().openContentFd(this) else uri
  }

  override fun onDestroy() {
    Log.d(TAG, "Exiting")
    audioFocusRequest?.let {
      AudioManagerCompat.abandonAudioFocusRequest(audioManager, it)
    }
    audioFocusRequest = null
    mediaSession?.release()
    if (noisyReceiver.initialized) {
      unregisterReceiver(noisyReceiver)
      noisyReceiver.initialized = false
    }

    player.isExiting = true
    if (isFinishing) {
      MPVLib.command("stop")
    }
    MPVLib.removeObserver(playerObserver)
    MPVLib.destroy()

    super.onDestroy()
  }

  override fun onPause() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
      !isInPictureInPictureMode &&
      !playerPreferences.automaticBackgroundPlayback.get()
    ) {
      viewModel.pause()
    }
    saveVideoPlaybackState(fileName)
    saveResumeState()
    super.onPause()
  }

  override fun finish() {
    setReturnIntent()
    super.finish()
  }

  override fun onStop() {
    saveVideoPlaybackState(fileName)
    saveResumeState()
    if (!serviceBound && playerPreferences.automaticBackgroundPlayback.get()) {
      startBackgroundPlayback()
    } else {
      viewModel.pause()
      if (serviceBound) {
        unbindService(serviceConnection)
        serviceBound = false
      }
    }
    window.attributes.screenBrightness.let {
      if (playerPreferences.rememberBrightness.get() && it != -1f) {
        playerPreferences.defaultBrightness.set(it)
      }
    }
    super.onStop()
  }

  @SuppressLint("NewApi")
  override fun onUserLeaveHint() {
    if (isPipSupported && viewModel.paused == false && playerPreferences.automaticallyEnterPip.get()) {
      enterPictureInPictureMode()
    }
    super.onUserLeaveHint()
  }

  @SuppressLint("NewApi")
  override fun onBackPressed() {
    if (isPipSupported && viewModel.paused == false && playerPreferences.automaticallyEnterPip.get()) {
      if (viewModel.sheetShown.value == Sheets.None && viewModel.panelShown.value == Panels.None) {
        enterPictureInPictureMode()
      }
    } else {
      super.onBackPressed()
    }
  }

  override fun onStart() {
    super.onStart()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPipSupported) {
      setPictureInPictureParams(createPipParams())
    }
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    binding.root.systemUiVisibility =
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
      View.SYSTEM_UI_FLAG_LOW_PROFILE
    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode = if (playerPreferences.drawOverDisplayCutout.get()) {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      } else {
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
      }
    }

    if (playerPreferences.rememberBrightness.get()) {
      playerPreferences.defaultBrightness.get().let {
        if (it != -1f) viewModel.changeBrightnessTo(it)
      }
    }

    if (serviceBound) {
      endBackgroundPlayback()
    }
  }

  private fun copyMPVAssets() {
    Utils.copyAssets(this@PlayerActivity)
    copyMPVScripts()
    copyMPVConfigFiles()
    // fonts can be lazily loaded
    lifecycleScope.launch(Dispatchers.IO) {
      copyMPVFonts()
    }
  }

  private fun setupMPV() {
    copyMPVAssets()
    player.initialize(filesDir.path, cacheDir.path)
    MPVLib.addObserver(playerObserver)
  }

  private fun setupAudio() {
    audioPreferences.audioChannels.get().let { MPVLib.setPropertyString(it.property, it.value) }

    val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).also {
      it.setAudioAttributes(
        AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA)
          .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build(),
      )
      it.setOnAudioFocusChangeListener(audioFocusChangeListener)
    }.build()
    AudioManagerCompat.requestAudioFocus(audioManager, request).let {
      if (it == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return@let
      audioFocusRequest = request
    }
  }

  private fun copyMPVConfigFiles() {
    val applicationPath = filesDir.path
    try {
      val mpvConf = fileManager.fromUri(advancedPreferences.mpvConfStorageUri.get().toUri())
        ?: error("User hasn't set any mpvConfig directory")
      if (!fileManager.exists(mpvConf)) error("Couldn't access mpv configuration directory")
      fileManager.copyDirectoryWithContent(mpvConf, fileManager.fromPath(applicationPath), true)
    } catch (e: Exception) {
      File("$applicationPath/mpv.conf")
        .also { if (!it.exists()) it.createNewFile() }
        .writeText(advancedPreferences.mpvConf.get())
      File("$applicationPath/input.conf")
        .also { if (!it.exists()) it.createNewFile() }
        .writeText(advancedPreferences.inputConf.get())
      Log.e("PlayerActivity", "Couldn't copy mpv configuration files: ${e.message}")
    }
  }

  private fun copyMPVScripts() {
    val mpvktLua = assets.open("mpvkt.lua")
    val applicationPath = filesDir.path

    val scriptsDir = fileManager.createDir(fileManager.fromPath(applicationPath), "scripts")!!

    fileManager.deleteContent(scriptsDir)

    File("$scriptsDir/mpvkt.lua")
      .also { if (!it.exists()) it.createNewFile() }
      .writeText(mpvktLua.bufferedReader().readText())
  }

  fun setupCustomButtons(buttons: List<CustomButtonEntity>) {
    val applicationPath = filesDir.path

    val scriptsDir = fileManager.createDir(fileManager.fromPath(applicationPath), "scripts")!!

    val customButtonsContent = buildString {
      appendLine("local lua_modules = mp.find_config_file('scripts')")
      appendLine("if lua_modules then")
      appendLine("package.path = package.path .. ';' .. lua_modules .. '/?.lua;' .. lua_modules .. '/?/init.lua'")
      appendLine("end")
      appendLine("local mpvkt = require 'mpvkt'")
      buttons.forEach { button ->
        appendLine("function button${button.id}()")
        appendLine(button.content)
        appendLine("end")
        appendLine("mp.register_script_message('call_button_${button.id}', button${button.id})")
        appendLine("function button${button.id}long()")
        appendLine(button.longPressContent)
        appendLine("end")
        appendLine("mp.register_script_message('call_button_${button.id}_long', button${button.id}long)")
      }
    }

    val file = File("$scriptsDir/custombuttons.lua")
      .also { if (!it.exists()) it.createNewFile() }

    file.writeText(customButtonsContent)

    MPVLib.command("load-script", file.absolutePath)
  }

  private fun copyMPVFonts() {
    try {
      val cachePath = cacheDir.path
      val fontsDir = fileManager.fromUri(subtitlesPreferences.fontsFolder.get().toUri())
        ?: error("User hasn't set any fonts directory")
      if (!fileManager.exists(fontsDir)) error("Couldn't access fonts directory")

      val destDir = fileManager.fromPath("$cachePath/fonts")
      if (!fileManager.exists(destDir)) fileManager.createDir(fileManager.fromPath(cachePath), "fonts")

      if (fileManager.findFile(destDir, "subfont.ttf") == null) {
        resources.assets.open("subfont.ttf")
          .copyTo(File("$cachePath/fonts/subfont.ttf").outputStream())
      }

      fileManager.copyDirectoryWithContent(fontsDir, destDir, false)
    } catch (e: Exception) {
      Log.e("PlayerActivity", "Couldn't copy fonts to application directory: ${e.message}")
    }
  }

  private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
    when (it) {
      AudioManager.AUDIOFOCUS_LOSS,
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
      -> {
        val oldRestore = restoreAudioFocus
        val wasPlayerPaused = viewModel.paused ?: false
        viewModel.pause()
        restoreAudioFocus = {
          oldRestore()
          if (!wasPlayerPaused) viewModel.unpause()
        }
      }

      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
        MPVLib.command("multiply", "volume", "0.5")
        restoreAudioFocus = {
          MPVLib.command("multiply", "volume", "2")
        }
      }

      AudioManager.AUDIOFOCUS_GAIN -> {
        restoreAudioFocus()
        restoreAudioFocus = {}
      }

      AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
        Log.d("PlayerActivity", "didn't get audio focus")
      }
    }
  }

  override fun onResume() {
    super.onResume()

    viewModel.currentVolume.update {
      audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also {
        if (it < viewModel.maxVolume) viewModel.changeMPVVolumeTo(100)
      }
    }
    // Returning from background: re-read mpv so the centre icon is right at once.
    val pausedNow = MPVLib.getPropertyBoolean("pause")
    Log.d(PAUSE_TAG, "onResume pause=$pausedNow state=${CenterButtonIcon.stateLabel(pausedNow)}")
    setOrientation()
  }

  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      val binder = service as MediaPlaybackService.MediaPlaybackBinder
      mediaPlaybackService = binder.getService()
      serviceBound = true

      fileName.let { title ->
        val artist = MPVLib.getPropertyString("metadata/artist") ?: ""
        mediaPlaybackService?.setMediaInfo(title = title, artist = artist, thumbnail = MPVLib.grabThumbnail(1080))
      }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      mediaPlaybackService = null
      serviceBound = false
    }
  }

  private fun startBackgroundPlayback() {
    val intent = Intent(this, MediaPlaybackService::class.java)
    startService(intent)
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
  }

  private fun endBackgroundPlayback() {
    stopService(Intent(this, MediaPlaybackService::class.java))
    mediaPlaybackService = null
    serviceBound = false
  }

  private fun setIntentExtras(extras: Bundle?) {
    if (extras == null) return

    extras.getString("title")?.let { MPVLib.setPropertyString("force-media-title", it) }
    MPVLib.setPropertyInt("time-pos", extras.getInt("position", 0) / 1000)

    // subtitles
    if (extras.containsKey("subs")) {
      val subList = Utils.getParcelableArray<Uri>(extras, "subs")
      val subsToEnable = Utils.getParcelableArray<Uri>(extras, "subs.enable")

      for (suburi in subList) {
        val subfile = suburi.resolveUri(this) ?: continue
        val flag = if (subsToEnable.any { it == suburi }) "select" else "auto"

        Log.v(TAG, "Adding subtitles from intent extras: $subfile")
        MPVLib.command("sub-add", subfile, flag)
      }
    }

    extras.getStringArray("headers")?.let { headers ->
      if (headers[0].startsWith("User-Agent", true)) MPVLib.setPropertyString("user-agent", headers[1])
      val headersString = headers.asSequence().drop(2).chunked(2).associate { it[0] to it[1] }
        .map { "${it.key}: ${it.value.replace(",", "\\,")}" }.joinToString(",")
      MPVLib.setPropertyString("http-header-fields", headersString)
    }
  }

  @Suppress("NestedBlockDepth")
  private fun parsePathFromIntent(intent: Intent): String? {
    return when (intent.action) {
      Intent.ACTION_VIEW -> intent.data?.resolveUri(this)
      Intent.ACTION_SEND -> {
        if (intent.hasExtra(Intent.EXTRA_STREAM)) {
          intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!.resolveUri(this)
        } else {
          intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            val uri = it.trim().toUri()
            if (uri.isHierarchical && !uri.isRelative) uri.resolveUri(this) else null
          }
        }
      }

      else -> intent.getStringExtra("uri")
    }
  }

  private fun getFileName(intent: Intent): String {
    val uri = if (intent.type == "text/plain") {
      intent.getStringExtra(Intent.EXTRA_TEXT)!!.toUri()
    } else {
      (intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && uri != null) {
      val cursor = contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null)
      if (cursor?.moveToFirst() == true) return cursor.getString(0).also { cursor.close() }
    }
    return uri?.lastPathSegment?.substringAfterLast("/") ?: uri?.path ?: ""
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      if (!isInPictureInPictureMode) {
        viewModel.changeVideoAspect(playerPreferences.videoAspect.get())
      } else {
        viewModel.hideControls()
      }
    }
    super.onConfigurationChanged(newConfig)
  }

  // a bunch of observers
  @Suppress("UnusedParameter")
  internal fun onObserverEvent(property: String, value: Long) {
    if (player.isExiting) return
  }

  @Suppress("UnusedParameter")
  internal fun onObserverEvent(property: String) {
    if (player.isExiting) return
  }

  internal fun onObserverEvent(property: String, value: Boolean) {
    if (player.isExiting) return
    when (property) {
      "pause" if value -> window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      "pause" -> window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      "eof-reached" if value && playerPreferences.closeAfterReachingEndOfVideo.get() -> finishAndRemoveTask()
    }
  }

  internal fun onObserverEvent(property: String, value: String) {
    if (player.isExiting) return
    when (property.substringBeforeLast("/")) {
      "user-data/mpvkt" -> viewModel.handleLuaInvocation(property, value)
    }
  }

  @Suppress("UnusedParameter")
  internal fun onObserverEvent(property: String, value: MPVNode) {
    if (player.isExiting) return
  }

  @SuppressLint("NewApi")
  @Suppress("UnusedParameter")
  internal fun onObserverEvent(property: String, value: Double) {
    if (player.isExiting) return
    when (property) {
      "video-params/aspect" -> if (isPipSupported) createPipParams()
    }
  }

  internal fun event(eventId: Int) {
    if (player.isExiting) return
    when (eventId) {
      MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> {
        loadToken++
        Log.d(PAUSE_TAG, "file loaded pause=${MPVLib.getPropertyBoolean("pause")}")
        fileName = getFileName(intent)
        setIntentExtras(intent.extras)
        val mediaTitle = MPVLib.getPropertyString("media-title")
        if (mediaTitle.isNullOrBlank() || mediaTitle.isDigitsOnly()) {
          MPVLib.setPropertyString("media-title", fileName)
        }
        lifecycleScope.launch(Dispatchers.IO) {
          loadVideoPlaybackState(fileName)
          applyResumeState()
        }
        setOrientation()
        viewModel.changeVideoAspect(playerPreferences.videoAspect.get())
      }

      MPVLib.mpvEventId.MPV_EVENT_END_FILE -> {
        val eof = MPVLib.getPropertyBoolean("eof-reached") == true
        val idle = MPVLib.getPropertyBoolean("idle-active") == true
        val position = queue?.items?.indexOfFirst { it.fileKey == resumeFileKey } ?: -1
        val reason = when {
          eof -> "EOF"
          idle -> "STOP"
          else -> "ERROR/QUIT"
        }
        Log.d(
          QUEUE_TAG,
          "END_FILE reason=$reason eof=$eof idle=$idle queuePosition=$position " +
            "of ${queue?.items?.size ?: 0} key=$resumeFileKey name=$fileName",
        )
        if (!playerPreferences.autoPlayNext.get()) return
        val failed = currentFailedKeys()
        if (eof) {
          playNextInQueue(failed)
        } else {
          // The file we were sent to never opened: remember it and try the next one.
          val nextFailed = failed + resumeFileKey
          Log.d(QUEUE_TAG, "skipping unopenable $resumeFileKey (${nextFailed.size}/${QueueAdvance.MAX_SKIPS})")
          if (nextFailed.size <= QueueAdvance.MAX_SKIPS) {
            playNextInQueue(nextFailed)
          } else {
            Log.d(QUEUE_TAG, "too many failed files; stopping at the end of the queue")
          }
        }
      }

      MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART -> player.isExiting = false
    }
  }

  private fun setupQueue(intent: Intent) {
    queue = PlaybackQueue.fromJson(intent.getStringExtra(EXTRA_QUEUE))
      ?: queue?.copy()
  }

  private fun nextQueueItem(): QueueItem? = QueueAdvance.nextAfterEnd(
    queue = queue,
    currentKey = resumeFileKey,
    currentUri = currentUriString(),
    currentPath = currentPathString(),
    currentName = currentNameString(),
    failedKeys = currentFailedKeys(),
    loop = playerPreferences.loopFolder.get(),
  )

  private fun hasPreviousItem(): Boolean = QueueAdvance.hasPrevious(
    queue = queue,
    currentKey = resumeFileKey,
    currentUri = currentUriString(),
    currentPath = currentPathString(),
    currentName = currentNameString(),
    loop = playerPreferences.loopFolder.get(),
  )

  /** Mirrors playNextInQueue: steps back one item in the same queue. */
  private fun playPreviousInQueue() {
    val item = QueueAdvance.previous(
      queue = queue,
      currentKey = resumeFileKey,
      currentUri = currentUriString(),
      currentPath = currentPathString(),
      currentName = currentNameString(),
      loop = playerPreferences.loopFolder.get(),
    )
    if (item == null) {
      Log.d(QUEUE_TAG, "no previous item (queue=${queue?.items?.size ?: 0})")
      return
    }
    openItemInPlace(item, "Previous")
  }

  private fun currentFailedKeys(): Set<String> {
    if (pendingFailedKeys.isEmpty()) {
      intent.getStringExtra(EXTRA_FAILED_KEYS)
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.forEach { pendingFailedKeys += it }
    }
    return pendingFailedKeys.toSet()
  }

  private fun currentUriString(): String = intent.data?.toString().orEmpty()

  private fun currentPathString(): String = MPVLib.getPropertyString("path").orEmpty()

  private fun currentNameString(): String =
    fileName.ifBlank { MPVLib.getPropertyString("filename").orEmpty() }

  /**
   * Plays the next file of the queue. Matching is deliberately loose: the player's
   * idea of the current file (a descriptor path, a title) does not have to equal
   * the library's, and if the file is not in the queue at all we start the folder
   * from the top instead of freezing on the last frame.
   */
  private fun playNextInQueue(failedKeys: Set<String>) {
    val item = QueueAdvance.nextAfterEnd(
      queue = queue,
      currentKey = resumeFileKey,
      currentUri = currentUriString(),
      currentPath = currentPathString(),
      currentName = currentNameString(),
      failedKeys = failedKeys,
      loop = playerPreferences.loopFolder.get(),
    )
    if (item != null) {
      openItemInPlace(item, "Next")
      return
    }
    if (queue == null) {
      rebuildQueueThenPlay(failedKeys)
    } else {
      Log.d(QUEUE_TAG, "end of queue (loop=${playerPreferences.loopFolder.get()}, failed=${failedKeys.size})")
    }
  }

  /**
   * Swaps the file inside the running player. Starting a second PlayerActivity and
   * finishing this one is what dropped the owner back to the library, so the queue
   * buttons now load into the same session instead.
   */
  private fun openItemInPlace(item: QueueItem, direction: String) {
    pendingFailedKeys -= item.fileKey
    val session = PlayerSessionLogic.openInPlace(currentSession(), item)
    Log.d(QUEUE_TAG, PlayerSessionLogic.tapLog(direction, session, item.fileKey))

    val uriString = item.uri
    val playable = runCatching {
      if (uriString.startsWith("content://")) uriString.toUri().openContentFd(this) else uriString
    }.getOrNull()
    if (playable.isNullOrBlank()) {
      onQueueItemFailed(item, direction)
      return
    }

    // Adopt the new file's identity first, so resume, delete and rotation point at it.
    intent.data = uriString.toUri()
    intent.putExtra(EXTRA_MEDIA_ID, item.mediaId)
    intent.putExtra(EXTRA_MEDIA_PATH, item.path.ifBlank { uriString })
    queue?.let { intent.putExtra(EXTRA_QUEUE, it.toJson()) }
    resumeFileKey = session.fileKey
    fileName = item.name
    // The new file starts from the top (its own saved position is applied on load).
    MPVLib.setPropertyInt("time-pos", 0)

    val token = ++loadToken
    player.playFile(playable)
    lifecycleScope.launch {
      delay(OPEN_TIMEOUT_MS)
      // No FILE_LOADED means it never opened: stay here and try the following file.
      if (loadToken == token) onQueueItemFailed(item, direction)
    }
  }

  /** A queued file did not open: message the owner, stay open, skip forward. */
  private fun onQueueItemFailed(item: QueueItem, direction: String) {
    if (item.fileKey in pendingFailedKeys) return
    pendingFailedKeys += item.fileKey
    val session = currentSession()
    val following = PlayerSessionLogic.nextTarget(
      session = session,
      currentUri = currentUriString(),
      currentPath = currentPathString(),
      currentName = currentNameString(),
      failedKeys = pendingFailedKeys,
      loop = playerPreferences.loopFolder.get(),
    )
    val after = PlayerSessionLogic.afterFailure(session, item.fileKey, following)
    Log.d(
      QUEUE_TAG,
      "open failed: key=${item.fileKey} direction=$direction following=${following?.fileKey ?: "none"} " +
        "failed=${pendingFailedKeys.size} activityFinished=${after.activityFinished}",
    )
    if (following == null || pendingFailedKeys.size > QueueAdvance.MAX_SKIPS) {
      Toast.makeText(this, "Couldn't open the next file", Toast.LENGTH_SHORT).show()
      return
    }
    openItemInPlace(following, direction)
  }

  private fun currentSession(): PlayerSessionLogic.Session =
    PlayerSessionLogic.Session.fromQueue(queue, resumeFileKey)

  /** No queue was handed over (URL or picker): rebuild it from the file's own folder. */
  private fun rebuildQueueThenPlay(failedKeys: Set<String>) {
    lifecycleScope.launch(Dispatchers.IO) {
      val videos = runCatching { libraryRepository.loadVideos() }.getOrDefault(emptyList())
      val folder = QueueAdvance.folderOf(
        videos = videos,
        currentKey = resumeFileKey,
        currentUri = currentUriString(),
        currentPath = currentPathString(),
        currentName = currentNameString(),
      )
      if (folder == null) {
        Log.d(QUEUE_TAG, "no queue and no folder for this file — stopping cleanly")
        return@launch
      }
      val rebuilt = QueueAdvance.queueFromFolder(
        videos = videos.filter { it.folder == folder },
        folder = folder,
        shuffled = libraryPreferences.shuffledFolders.get().contains(folder),
        seed = libraryPreferences.shuffleSeed(folder).get(),
      ) ?: return@launch
      queue = rebuilt
      Log.d(QUEUE_TAG, "rebuilt queue for folder=$folder items=${rebuilt.items.size}")
      val item = QueueAdvance.nextAfterEnd(
        queue = rebuilt,
        currentKey = resumeFileKey,
        currentUri = currentUriString(),
        currentPath = currentPathString(),
        currentName = currentNameString(),
        failedKeys = failedKeys,
        loop = playerPreferences.loopFolder.get(),
      )
      withContext(Dispatchers.Main) {
        if (item != null) {
          openItemInPlace(item, "Next")
        } else {
          Log.d(QUEUE_TAG, "rebuilt queue had nothing after this file")
        }
      }
    }
  }

  /** Shuffle applies to this folder's queue only; the library keeps its own sort. */
  private fun toggleShuffle() {
    val current = queue ?: return
    val folder = current.folder
    val shuffled = !current.shuffled
    val seeded = libraryPreferences.shuffleSeed(folder)
    val seed = if (shuffled) {
      val fresh = System.currentTimeMillis()
      seeded.set(fresh)
      fresh
    } else {
      seeded.get()
    }
    queue = current.copy(shuffled = shuffled, seed = seed)
    val folders = libraryPreferences.shuffledFolders.get()
    libraryPreferences.shuffledFolders.set(if (shuffled) folders + folder else folders - folder)
  }

  /** Cycles auto (sensor) rotation and locked-to-current, and remembers the choice. */
  private fun toggleRotation() {
    val mode = playerPreferences.rotationMode.get()
    if (mode == RotationMode.Auto) {
      val locked = RotationLogic.lockTo(resources.configuration.orientation)
      playerPreferences.lockedOrientation.set(locked)
      playerPreferences.rotationMode.set(RotationMode.Locked)
      Log.d(ROTATION_TAG, "rotate pressed: auto -> locked($locked)")
    } else {
      playerPreferences.rotationMode.set(RotationMode.Auto)
      Log.d(ROTATION_TAG, "rotate pressed: locked -> auto")
    }
    setOrientation()
  }

  /** Only files that came from MediaStore can be deleted from here. */
  private fun deletableUri(): Uri? {
    val data = intent.data ?: return null
    val authority = data.authority.orEmpty()
    return if (data.scheme == "content" && authority.startsWith("media")) data else null
  }

  private fun confirmDeleteCurrentFile() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      Toast.makeText(this, "Deleting needs Android 11 or newer", Toast.LENGTH_LONG).show()
      return
    }
    val uri = deletableUri()
    if (uri == null) {
      Toast.makeText(this, "Only files from your library can be deleted here", Toast.LENGTH_LONG).show()
      return
    }
    val name = fileName.ifBlank { MPVLib.getPropertyString("filename").orEmpty() }
    val dialog = AlertDialog.Builder(this)
      .setTitle("Delete permanently?")
      .setMessage("Delete “$name”? This removes the file from your phone. It cannot be undone.")
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton("Delete") { _, _ -> requestSystemDelete(uri) }
      .create()
    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.RED)
    }
    dialog.show()
  }

  private fun requestSystemDelete(uri: Uri) {
    val pending = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
    deleteLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
  }

  /** The system confirmed the delete: drop the resume row and the queue entry. */
  private fun onFileDeleted() {
    val key = resumeFileKey
    queue = LibraryDeletion.pruneQueue(queue, key)
    if (key.isNotBlank()) {
      lifecycleScope.launch(Dispatchers.IO) { resumeRepository.clear(key) }
    }
    Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show()
    finish()
  }

  /**
   * Resume is keyed by MediaStore id + path hash, which the library passes in when
   * it starts playback; anything else falls back to the played URI, so resume still
   * works for files opened from the picker.
   */
  private fun setupResumeKey(intent: Intent) {
    val mediaId = intent.getLongExtra(EXTRA_MEDIA_ID, 0L)
    val path = intent.getStringExtra(EXTRA_MEDIA_PATH).orEmpty()
    val fallback = (intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM))?.toString().orEmpty()
    resumeFileKey = LibraryLogic.fileKey(mediaId, path.ifBlank { fallback })
  }

  private fun saveResumeState() {
    if (resumeFileKey.isBlank()) return
    val durationMs = ((viewModel.duration ?: 0).toLong()) * 1000L
    val positionMs = ((viewModel.pos ?: 0).toLong()) * 1000L
    if (durationMs <= 0) return
    lifecycleScope.launch(Dispatchers.IO) {
      resumeRepository.save(
        ResumeStateEntity(
          fileKey = resumeFileKey,
          positionMs = positionMs.coerceAtLeast(0),
          durationMs = durationMs,
          lastPlayedAt = System.currentTimeMillis(),
          completed = LibraryLogic.isCompleted(positionMs, durationMs),
        ),
      )
    }
  }

  /** Saves the position every few seconds so a crash or a kill loses at most that much. */
  private fun startResumeTicker() {
    lifecycleScope.launch {
      while (isActive) {
        delay(RESUME_TICK_MS)
        if (MPVLib.getPropertyBoolean("pause") != true) saveResumeState()
      }
    }
  }

  /** Seeks to the stored position once the file is loaded, unless it is finished. */
  private suspend fun applyResumeState() {
    if (resumeFileKey.isBlank()) return
    val state = resumeRepository.get(resumeFileKey) ?: return
    if (!LibraryLogic.shouldOfferResume(state.positionMs, state.durationMs, state.completed)) return
    withContext(Dispatchers.Main) {
      MPVLib.setPropertyInt("time-pos", (state.positionMs / 1000L).toInt())
    }
  }

  private fun saveVideoPlaybackState(mediaTitle: String) {
    if (mediaTitle.isBlank()) return
    lifecycleScope.launch(Dispatchers.IO) {
      val oldState = playbackStateRepository.getVideoDataByTitle(fileName)
      Log.d(TAG, "Saving playback state")
      playbackStateRepository.upsert(
        PlaybackStateEntity(
          mediaTitle = mediaTitle,
          lastPosition = if (playerPreferences.savePositionOnQuit.get()) {
            val pos = viewModel.pos ?: 0
            val duration = viewModel.duration ?: 0
            if (pos < duration - 1) pos else 0
          } else {
            oldState?.lastPosition ?: 0
          },
          playbackSpeed = MPVLib.getPropertyDouble("speed")!!,
          sid = player.sid,
          subDelay = (MPVLib.getPropertyDouble("sub-delay")!! * 1000).toInt(),
          subSpeed = MPVLib.getPropertyDouble("sub-speed")!!,
          secondarySid = player.secondarySid,
          secondarySubDelay = (MPVLib.getPropertyDouble("secondary-sub-delay")!! * 1000).toInt(),
          aid = player.aid,
          audioDelay = (MPVLib.getPropertyDouble("audio-delay")!! * 1000).toInt(),
        ),
      )
    }
  }

  private suspend fun loadVideoPlaybackState(mediaTitle: String) {
    if (mediaTitle.isBlank()) return
    val state = playbackStateRepository.getVideoDataByTitle(mediaTitle)
    val getDelay: (Int, Int?) -> Double = { preferenceDelay, stateDelay ->
      (stateDelay ?: preferenceDelay) / 1000.0
    }
    val subDelay = getDelay(subtitlesPreferences.defaultSubDelay.get(), state?.subDelay)
    val secondarySubDelay = getDelay(subtitlesPreferences.defaultSecondarySubDelay.get(), state?.secondarySubDelay)
    val audioDelay = getDelay(audioPreferences.defaultAudioDelay.get(), state?.audioDelay)
    state?.let {
      player.sid = it.sid
      player.secondarySid = it.secondarySid
      player.aid = it.aid
      MPVLib.setPropertyDouble("sub-delay", subDelay)
      MPVLib.setPropertyDouble("sub-delay", secondarySubDelay)
      MPVLib.setPropertyDouble("sub-delay", it.playbackSpeed)
      MPVLib.setPropertyDouble("audio-delay", audioDelay)
    }
    if (playerPreferences.savePositionOnQuit.get()) {
      state?.lastPosition?.let { if (it != 0) MPVLib.setPropertyInt("time-pos", it) }
    }
    MPVLib.setPropertyDouble("sub-speed", state?.subSpeed ?: subtitlesPreferences.defaultSubSpeed.get().toDouble())
  }

  private fun setReturnIntent() {
    Log.d(TAG, "setting return intent")
    setResult(
      RESULT_OK,
      Intent(RESULT_INTENT).apply {
        viewModel.pos?.let { putExtra("position", it * 1000) }
        viewModel.duration?.let { putExtra("duration", it * 1000) }
      },
    )
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)

    getPlayableUri(intent)?.let { MPVLib.command("loadfile", it) }
    setIntent(intent)
  }

  @RequiresApi(Build.VERSION_CODES.O)
  fun createPipParams(): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      builder.setTitle(MPVLib.getPropertyString("media-title") ?: fileName)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val autoEnter = playerPreferences.automaticallyEnterPip.get()
      builder.setAutoEnterEnabled(viewModel.paused == false && autoEnter)
      builder.setSeamlessResizeEnabled(viewModel.paused == false && autoEnter)
    }
    builder.setActions(createPipActions(this, viewModel.paused == true))
    builder.setSourceRectHint(pipRect)
    MPVLib.getPropertyInt("video-params/h")?.let {
      val height = it
      val width = it * player.getVideoOutAspect()!!
      val rational = Rational(height, width.toInt()).toFloat()
      if (rational in 0.42..2.38) builder.setAspectRatio(Rational(width.toInt(), height))
    }
    return builder.build()
  }

  override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
    if (!isInPictureInPictureMode) {
      pipReceiver?.let {
        unregisterReceiver(pipReceiver)
        pipReceiver = null
      }
      super.onPictureInPictureModeChanged(false, newConfig)
      return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      setPictureInPictureParams(createPipParams())
    }
    viewModel.hideControls()
    viewModel.hideSeekBar()
    viewModel.isBrightnessSliderShown.update { false }
    viewModel.isVolumeSliderShown.update { false }
    viewModel.sheetShown.update { Sheets.None }
    pipReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || intent.action != PIP_INTENTS_FILTER) return
        when (intent.getIntExtra(PIP_INTENT_ACTION, 0)) {
          PIP_PAUSE -> viewModel.pause()
          PIP_PLAY -> viewModel.unpause()
          PIP_FF -> viewModel.handleRightDoubleTap()
          PIP_FR -> viewModel.handleLeftDoubleTap()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          setPictureInPictureParams(createPipParams())
        }
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER), RECEIVER_NOT_EXPORTED)
    } else {
      registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER))
    }
    super.onPictureInPictureModeChanged(true, newConfig)
  }

  private fun setOrientation() {
    val mode = playerPreferences.rotationMode.get()
    val expected = RotationLogic.requestedOrientation(
      mode = mode,
      lockedOrientation = playerPreferences.lockedOrientation.get(),
      preference = playerPreferences.orientation.get(),
    )
    requestedOrientation = expected
    // A silent mismatch here is exactly what made the rotate button look dead, so
    // the value the activity actually kept is logged every time it is set.
    if (RotationLogic.applied(requestedOrientation, expected)) {
      Log.d(ROTATION_TAG, "orientation mode=$mode requested=$requestedOrientation")
    } else {
      Log.w(ROTATION_TAG, "orientation NOT applied: wanted=$expected got=$requestedOrientation")
    }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    when (keyCode) {
      KeyEvent.KEYCODE_VOLUME_UP -> {
        viewModel.changeVolumeBy(1)
        viewModel.displayVolumeSlider()
      }

      KeyEvent.KEYCODE_VOLUME_DOWN -> {
        viewModel.changeVolumeBy(-1)
        viewModel.displayVolumeSlider()
      }

      KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.handleLeftDoubleTap()
      KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.handleRightDoubleTap()
      KeyEvent.KEYCODE_SPACE -> viewModel.pauseUnpause()
      KeyEvent.KEYCODE_MEDIA_STOP -> finishAndRemoveTask()

      KeyEvent.KEYCODE_MEDIA_REWIND -> viewModel.handleLeftDoubleTap()
      KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> viewModel.handleRightDoubleTap()

      // other keys should be bound by the user in input.conf ig
      else -> {
        event?.let { player.onKey(it) }
        super.onKeyDown(keyCode, event)
      }
    }
    return true
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    if (player.onKey(event!!)) return true
    return super.onKeyUp(keyCode, event)
  }

  private fun setupMediaSession() {
    val previousAction = gesturePreferences.mediaPreviousGesture.get()
    val playAction = gesturePreferences.mediaPlayGesture.get()
    val nextAction = gesturePreferences.mediaNextGesture.get()

    mediaSession = MediaSession(this, "PlayerActivity").apply {
      setCallback(
        object : MediaSession.Callback() {
          override fun onPlay() {
            when (playAction) {
              SingleActionGesture.None -> {}
              SingleActionGesture.Seek -> {}
              SingleActionGesture.PlayPause -> {
                super.onPlay()
                viewModel.unpause()
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
              }

              SingleActionGesture.Custom -> {
                MPVLib.command("keypress", CustomKeyCodes.MediaPlay.keyCode)
              }
            }
          }

          override fun onPause() {
            when (playAction) {
              SingleActionGesture.None -> {}
              SingleActionGesture.Seek -> {}
              SingleActionGesture.PlayPause -> {
                super.onPause()
                viewModel.pause()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
              }

              SingleActionGesture.Custom -> {
                MPVLib.command("keypress", CustomKeyCodes.MediaPlay.keyCode)
              }
            }
          }

          override fun onSkipToPrevious() {
            when (previousAction) {
              SingleActionGesture.None -> {}
              SingleActionGesture.Seek -> {
                viewModel.leftSeek()
              }

              SingleActionGesture.PlayPause -> {
                viewModel.pauseUnpause()
              }

              SingleActionGesture.Custom -> {
                MPVLib.command("keypress", CustomKeyCodes.MediaPrevious.keyCode)
              }
            }
          }

          override fun onSkipToNext() {
            when (nextAction) {
              SingleActionGesture.None -> {}
              SingleActionGesture.Seek -> {
                viewModel.rightSeek()
              }

              SingleActionGesture.PlayPause -> {
                viewModel.pauseUnpause()
              }

              SingleActionGesture.Custom -> {
                MPVLib.command("keypress", CustomKeyCodes.MediaNext.keyCode)
              }
            }
          }

          override fun onStop() {
            super.onStop()
            isActive = false
            this@PlayerActivity.onStop()
          }
        },
      )
      setPlaybackState(
        PlaybackState.Builder()
          .setActions(
            PlaybackState.ACTION_PLAY or
              PlaybackState.ACTION_PAUSE or
              PlaybackState.ACTION_STOP or
              PlaybackState.ACTION_SKIP_TO_PREVIOUS or
              PlaybackState.ACTION_SKIP_TO_NEXT,
          )
          .build(),
      )
      isActive = true
    }

    val filter = IntentFilter().apply { addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY) }
    registerReceiver(noisyReceiver, filter)
    noisyReceiver.initialized = true
  }

  companion object {
    // action of result intent
    private const val RESULT_INTENT = "live.mehiz.mpvkt.ui.player.PlayerActivity.result"

    /** MediaStore id of the file being played, so resume can key on it. */
    const val EXTRA_MEDIA_ID = "live.mehiz.mpvkt.ui.player.media_id"
    const val EXTRA_MEDIA_PATH = "live.mehiz.mpvkt.ui.player.media_path"

    /** JSON of the folder queue, so next/shuffle/delete work inside playback. */
    const val EXTRA_QUEUE = "live.mehiz.mpvkt.ui.player.queue"

    /** Keys that failed to open in this auto-advance chain. */
    const val EXTRA_FAILED_KEYS = "live.mehiz.mpvkt.ui.player.failed_keys"

    private const val RESUME_TICK_MS = 5_000L

    /** How long a queued file gets to report FILE_LOADED before it counts as failed. */
    private const val OPEN_TIMEOUT_MS = 4_000L
    private const val ROTATION_TAG = "VelaRotation"
    private const val QUEUE_TAG = "VelaQueue"
    private const val PAUSE_TAG = "VelaPause"
  }
}

const val TAG = "mpvKt"
