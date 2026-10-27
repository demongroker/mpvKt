package live.mehiz.mpvkt.library

/**
 * The locked-folder contribution to the library's single exclusion point. This is the
 * "clean seam" the library was built with: nothing else needs to know about PINs.
 */
class LockedFolderExclusionSource(private val session: LockSession) : LibraryExclusionSource {
  override suspend fun exclusions(): LibraryExclusions = session.exclusions()
}
