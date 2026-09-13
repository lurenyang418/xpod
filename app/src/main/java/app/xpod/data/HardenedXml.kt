package app.xpod.data

import java.io.ByteArrayInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Creates a pull parser for untrusted XML (network feeds, imported OPML) that never processes
 * DTDs: DOCDECL processing is disabled and any input that declares a DOCTYPE before the root
 * element is rejected outright, so entity-expansion payloads cannot reach the parser.
 * [app.xpod.data.reader.EpubBookParser] applies the same rule to its local DOM parsing.
 */
internal fun newHardenedXmlPullParser(bytes: ByteArray): XmlPullParser {
  require(!hasDoctypeDeclaration(bytes)) { "XML must not contain a DOCTYPE declaration" }
  return XmlPullParserFactory.newInstance().newPullParser().apply {
    // Some implementations (kxml2) refuse to set this feature even though off is their
    // default; tolerate that, but verify DOCDECL processing really is disabled.
    runCatching { setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
    check(!getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)) {
      "XML parser must not process DOCTYPE declarations"
    }
    setInput(ByteArrayInputStream(bytes), null)
  }
}

/**
 * Cheap prefix scan that stops at the root element, so large documents are never walked in
 * full. It tolerates a BOM, whitespace, an XML declaration or other processing instructions,
 * and comments before the root element; NUL bytes are skipped so UTF-16 input scans like
 * ASCII. Any other `<!` markup before the root element is a DTD and reports true.
 */
private fun hasDoctypeDeclaration(bytes: ByteArray): Boolean {
  var index = 0
  fun next(): Int {
    while (index < bytes.size) {
      val value = bytes[index++].toInt() and 0xFF
      if (value != 0) return value
    }
    return -1
  }
  var current = next()
  while (current != -1) {
    if (current != '<'.code) {
      // BOM bytes and whitespace before the prolog; anything else is left to the parser.
      current = next()
      continue
    }
    when (next()) {
      '?'.code -> { // XML declaration or processing instruction: skip to its end.
        var value = next()
        while (value != -1 && value != '>'.code) value = next()
        current = next()
      }
      '!'.code -> {
        // Only comments and DTDs may start with "<!" before the root element.
        if (next() != '-'.code || next() != '-'.code) return true
        var first = next()
        var second = next()
        var third = next()
        while (third != -1 && !(first == '-'.code && second == '-'.code && third == '>'.code)) {
          first = second
          second = third
          third = next()
        }
        current = next()
      }
      else -> return false // Root element reached without a DOCTYPE.
    }
  }
  return false
}
