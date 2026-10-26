package live.mehiz.mpvkt.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleLogicTest {

  @Test
  fun releaseNameIsTurnedIntoAReadableQuery() {
    assertEquals(
      "Game of Thrones S01E01 1080p WEB-DL",
      SubtitleLogic.cleanName("Game.of.Thrones.S01E01.1080p.WEB-DL.mkv"),
    )
    assertEquals("The Matrix 1999", SubtitleLogic.cleanName("[Group] The.Matrix.1999.mp4"))
  }

  @Test
  fun fallbackQueryKeepsTheShowAndEpisodeMarker() {
    val cleaned = SubtitleLogic.cleanName("Game.of.Thrones.S01E01.1080p.BluRay.x264.mkv")
    assertEquals("Game of Thrones S01E01", SubtitleLogic.fallbackQuery(cleaned))
  }

  @Test
  fun fallbackQueryKeepsOnlyTheFirstWordsOfAMovie() {
    val cleaned = SubtitleLogic.cleanName("The.Matrix.Resurrections.2021.1080p.WEB.H264-NAISU.mkv")
    assertEquals("The Matrix Resurrections", SubtitleLogic.fallbackQuery(cleaned))
  }

  @Test
  fun queriesAreExactThenLooseAndNeverDuplicated() {
    assertEquals(
      listOf("Dark S01E01 720p", "Dark S01E01"),
      SubtitleLogic.queries("Dark.S01E01.720p.mkv"),
    )
    assertEquals(emptyList<String>(), SubtitleLogic.queries(""))
  }

  @Test
  fun keylessProviderJsonIsParsedAndMalformedEntriesSkipped() {
    val body = """
      [
        {"SubFileName":"a.srt","SubLanguageID":"eng","SubFormat":"srt",
         "SubDownloadsCnt":"2589294","SubRating":"7.9",
         "SubDownloadLink":"https://dl.example/a.gz","IDSubtitleFile":"11"},
        {"SubFileName":"no-link.srt"},
        {"SubFileName":"b.ass","SubLanguageID":"eng","SubFormat":"ass",
         "SubDownloadsCnt":"12","SubDownloadLink":"https://dl.example/b.gz","IDSubtitleFile":"12"}
      ]
    """.trimIndent()
    val hits = SubtitleLogic.parseHits(body)
    assertEquals(2, hits.size)
    assertEquals("a.srt", hits[0].fileName)
    assertEquals(2589294, hits[0].downloads)
    assertEquals("eng", hits[0].language)
    assertEquals(7.9, hits[0].rating, 0.001)
    assertEquals(SubtitleLogic.PROVIDER_LEGACY, hits[0].providerId)
  }

  @Test
  fun openSubtitlesComJsonIsFlattenedIntoTheSameHitType() {
    val body = """
      {"total_count":1,"data":[
        {"attributes":{"language":"en","download_count":4210,"ratings":8.4,"format":"srt",
          "release":"Game.of.Thrones.S01E01.1080p.WEB-DL",
          "files":[{"file_id":987654,"file_name":"Game.of.Thrones.S01E01.srt"}],
          "feature_details":{"movie_name":"Game of Thrones"}}},
        {"attributes":{"language":"en","files":[]}}
      ]}
    """.trimIndent()
    val hits = SubtitleLogic.parseComHits(body)
    assertEquals(1, hits.size)
    assertEquals("987654", hits[0].downloadRef)
    assertEquals(4210, hits[0].downloads)
    assertEquals(8.4, hits[0].rating, 0.001)
    assertEquals(SubtitleLogic.PROVIDER_COM, hits[0].providerId)
  }

  @Test
  fun brokenJsonIsNotAnException() {
    assertEquals(emptyList<SubtitleHit>(), SubtitleLogic.parseHits("<html>not json</html>"))
    assertEquals(emptyList<SubtitleHit>(), SubtitleLogic.parseHits(""))
    assertEquals(emptyList<SubtitleHit>(), SubtitleLogic.parseComHits(""))
    assertEquals(emptyList<SubtitleHit>(), SubtitleLogic.parseComHits("""{"data":"nope"}"""))
  }

  @Test
  fun httpStatusMapsToTheSharedProviderErrors() {
    assertEquals(ProviderError.KeyMissing, SubtitleLogic.errorFor(401))
    assertEquals(ProviderError.KeyMissing, SubtitleLogic.errorFor(403))
    assertEquals(ProviderError.RateLimited, SubtitleLogic.errorFor(429))
    assertEquals(ProviderError.ProviderUnavailable, SubtitleLogic.errorFor(404))
    assertEquals(ProviderError.ProviderUnavailable, SubtitleLogic.errorFor(503))
    assertTrue(SubtitleLogic.isSuccess(200))
    assertFalse(SubtitleLogic.isSuccess(429))
    assertTrue(SubtitleLogic.isServerFailure(500))
  }

  @Test
  fun quotaWordingCountsAsRateLimited() {
    assertTrue(SubtitleLogic.looksRateLimited("""{"message":"Too many requests"}"""))
    assertTrue(SubtitleLogic.looksRateLimited("Daily quota exceeded"))
    assertFalse(SubtitleLogic.looksRateLimited("""{"message":"not found"}"""))
  }

  @Test
  fun srtFilesRankAboveOtherFormatsThenByRatingAndPopularity() {
    val hits = listOf(
      SubtitleHit("1", "a.ass", "eng", "ass", 900_000, 9.0, SubtitleLogic.PROVIDER_LEGACY, "u1"),
      SubtitleHit("2", "b.srt", "eng", "srt", 10, 1.0, SubtitleLogic.PROVIDER_LEGACY, "u2"),
      SubtitleHit("3", "c.srt", "eng", "srt", 5000, 5.0, SubtitleLogic.PROVIDER_LEGACY, "u3"),
      SubtitleHit("4", "d.srt", "eng", "srt", 50, 9.5, SubtitleLogic.PROVIDER_LEGACY, "u4"),
    )
    assertEquals(listOf("d.srt", "c.srt", "b.srt", "a.ass"), SubtitleLogic.rank(hits).map { it.fileName })
  }

  @Test
  fun onlyRealSubtitleCuesCountAsASubtitle() {
    assertTrue(SubtitleLogic.looksLikeSubtitle("1\r\n00:00:10,038 --> 00:00:12,873\r\nHello\r\n"))
    assertFalse(SubtitleLogic.looksLikeSubtitle("<html><body>404 Not Found</body></html>"))
    assertFalse(SubtitleLogic.looksLikeSubtitle(""))
  }

  @Test
  fun languageCodesGetReadableLabels() {
    assertEquals("English", SubtitleLogic.languageLabel("eng"))
    assertEquals("Indonesian", SubtitleLogic.languageLabel("IND"))
    assertEquals("XXX", SubtitleLogic.languageLabel("xxx"))
  }
}
