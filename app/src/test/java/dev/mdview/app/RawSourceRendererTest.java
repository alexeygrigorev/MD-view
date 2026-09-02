package dev.mdview.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RawSourceRendererTest {
    @Test
    public void createsSelectablePreformattedSourcePage() {
        String html = RawSourceRenderer.toHtmlDocument(
                "# Heading\n\n    code\nlong-line",
                0xFF112233,
                0xFFEEDDCC
        );

        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains("white-space:pre"));
        assertTrue(html.contains("display:inline-block"));
        assertTrue(html.contains("#112233"));
        assertTrue(html.contains("#EEDDCC"));
        assertTrue(html.contains("# Heading\n\n    code\nlong-line"));
    }

    @Test
    public void sourceCannotBreakOutOfThePreElement() {
        String html = RawSourceRenderer.toHtmlDocument(
                "</pre><script>alert('no')</script>&",
                0xFFFFFFFF,
                0xFF000000
        );

        assertFalse(html.contains("</pre><script>"));
        assertFalse(html.contains("<script>alert"));
        assertTrue(html.contains("&lt;/pre&gt;&lt;script&gt;alert(&#39;no&#39;)&lt;/script&gt;&amp;"));
    }
}
