package dev.mdview.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RawSourceRendererTest {
    @Test
    public void createsSelectableUnwrappedSourcePageByDefault() {
        String html = RawSourceRenderer.toHtmlDocument(
                "# Heading\n\n    code\nlong-line",
                0xFF112233,
                0xFFEEDDCC
        );

        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains("white-space:pre"));
        assertTrue(html.contains("display:inline-block"));
        assertTrue(html.contains("overflow-wrap:normal"));
        assertTrue(html.contains("#112233"));
        assertTrue(html.contains("#EEDDCC"));
        assertTrue(html.contains("# Heading\n\n    code\nlong-line"));
    }

    @Test
    public void createsWrappedSourcePageWhenRequested() {
        String html = RawSourceRenderer.toHtmlDocument(
                "one very long source line",
                0xFFFFFFFF,
                0xFF000000,
                true
        );

        assertTrue(html.contains("display:block"));
        assertTrue(html.contains("width:100%"));
        assertTrue(html.contains("max-width:100%"));
        assertTrue(html.contains("white-space:pre-wrap"));
        assertTrue(html.contains("word-break:break-word"));
        assertTrue(html.contains("word-wrap:break-word"));
        assertTrue(html.contains("overflow-wrap:break-word"));
        assertFalse(html.contains("display:inline-block"));
    }

    @Test
    public void sourceCannotBreakOutOfThePreElement() {
        String html = RawSourceRenderer.toHtmlDocument(
                "</pre><script>alert('no')</script>&",
                0xFFFFFFFF,
                0xFF000000,
                true
        );

        assertFalse(html.contains("</pre><script>"));
        assertFalse(html.contains("<script>alert"));
        assertTrue(html.contains("&lt;/pre&gt;&lt;script&gt;alert(&#39;no&#39;)&lt;/script&gt;&amp;"));
    }
}
