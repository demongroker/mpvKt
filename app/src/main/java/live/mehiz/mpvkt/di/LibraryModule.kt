package live.mehiz.mpvkt.di

import live.mehiz.mpvkt.library.LibraryExclusionSource
import live.mehiz.mpvkt.library.MediaStoreLibraryRepository
import live.mehiz.mpvkt.library.ThumbnailCache
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

val LibraryModule = module {
  single { ThumbnailCache() }
  single { MediaStoreLibraryRepository(androidContext(), get()) }

  /**
   * Nothing is excluded beyond hidden folders today. The locked-folder feature
   * replaces this binding; no screen or repository changes are needed for that.
   */
  single<LibraryExclusionSource> { LibraryExclusionSource.None }
}
