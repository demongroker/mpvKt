package live.mehiz.mpvkt.di

import live.mehiz.mpvkt.library.LibraryExclusionSource
import live.mehiz.mpvkt.library.LockPreferences
import live.mehiz.mpvkt.library.LockSession
import live.mehiz.mpvkt.library.LockedFolderExclusionSource
import live.mehiz.mpvkt.library.MediaStoreLibraryRepository
import live.mehiz.mpvkt.library.ThumbnailCache
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val LibraryModule = module {
  single { ThumbnailCache() }
  single { MediaStoreLibraryRepository(androidContext(), get()) }

  single { LockPreferences(get()) }
  single { LockSession(get()) }

  /**
   * Locked folders feed the library's single exclusion point. Hidden folders are applied
   * separately by the library screen, so this source only answers for locking.
   */
  single<LibraryExclusionSource> { LockedFolderExclusionSource(get()) }
}
