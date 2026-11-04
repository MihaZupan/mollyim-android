package org.signal.core.ui.fonts

import android.app.Application
import android.graphics.Color
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SignalSymbolsTest {

  @Test
  fun `unspecified glyph color inherits its surrounding text color`() {
    val glyph = SignalSymbols.getSpannedString(
      ApplicationProvider.getApplicationContext(), SignalSymbols.Weight.REGULAR, SignalSymbols.Glyph.POLL
    ) as Spanned

    assertThat(glyph.getSpans(0, glyph.length, ForegroundColorSpan::class.java).toList()).isEmpty()
  }

  @Test
  fun `white is a valid resolved glyph color not an unspecified sentinel`() {
    val glyph = SignalSymbols.getSpannedString(
      ApplicationProvider.getApplicationContext(), SignalSymbols.Weight.REGULAR, SignalSymbols.Glyph.POLL, Color.WHITE
    ) as Spanned
    val span = glyph.getSpans(0, glyph.length, ForegroundColorSpan::class.java).single()

    assertThat(span.foregroundColor).isEqualTo(Color.WHITE)
    assertThat(glyph.getSpanStart(span)).isEqualTo(0)
    assertThat(glyph.getSpanEnd(span)).isEqualTo(glyph.length)
  }
}
