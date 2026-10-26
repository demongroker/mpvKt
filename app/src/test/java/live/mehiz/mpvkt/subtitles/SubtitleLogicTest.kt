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
  fun providerJsonIsParsedAndMalformedEntriesSkipped() {
    val body = """
      [
        {"SubFileName":"a.srt","SubLanguageID":"eng","SubFormat":"srt",
         "SubDownloadsCnt":"2589294","SubDownloadLink":"https://dl.example/a.gz","IDSubtitleFile":"11"},
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
  }

  @Test
  fun brokenJsonIsNotAnException() {
    assertEquals(emptyList<SubtitleHit>(), SubtitleLogic.parseHits("<html>not json</html>"))
    assertEquals(emptyList<SubtitleHit>(), SubtitleLogic.parseHits(""))
  }

  @Test
  fun srtFilesRankAboveOtherFormatsAndPopularityBreaksTies() {
    val hits = listOf(
      SubtitleHit("1", "a.ass", "eng", "ass", 900_000, "u1"),
      SubtitleHit("2", "b.srt", "eng", "srt", 10, "u2"),
      SubtitleHit("3", "c.srt", "eng", "srt", 5000, "u3"),
    )
    assertEquals(listOf("c.srt", "b.srt", "a.ass"), SubtitleLogic.rank(hits).map { it.fileName })
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
