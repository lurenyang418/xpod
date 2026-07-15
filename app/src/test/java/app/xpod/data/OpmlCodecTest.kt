package app.xpod.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class OpmlCodecTest {
    @Test fun importsOnlyUniqueHttpsFeedsAndExportsThem() {
        val source = "<opml><body><outline xmlUrl=\"https://one.example/feed.xml\"/><outline xmlUrl=\"https://one.example/feed.xml\"/><outline xmlUrl=\"http://unsafe.example/feed.xml\"/></body></opml>"
        val urls = OpmlCodec.read(ByteArrayInputStream(source.toByteArray()))
        assertEquals(listOf("https://one.example/feed.xml"), urls)

        val output = ByteArrayOutputStream()
        OpmlCodec.write(output, listOf(PodcastEntity("id", urls.single(), "One", "", "", null)))
        assertEquals(listOf("https://one.example/feed.xml"), OpmlCodec.read(ByteArrayInputStream(output.toByteArray())))
    }
}
