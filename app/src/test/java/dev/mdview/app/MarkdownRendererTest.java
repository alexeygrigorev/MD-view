package dev.mdview.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MarkdownRendererTest {
    @Test
    public void rendersCoreAndGfmMarkdown() {
        String markdown = "# Title\n\n~~old~~\n\n| A | B |\n|---|---|\n| 1 | 2 |";
        String html = MarkdownRenderer.toHtmlFragment(markdown);

        assertTrue(html.contains("<h1>Title</h1>"));
        assertTrue(html.contains("<del>old</del>"));
        assertTrue(html.contains("<table>"));
    }

    @Test
    public void escapesRawHtmlAndSanitizesUnsafeLinks() {
        String html = MarkdownRenderer.toHtmlFragment(
                "<script>alert('no')</script>\n\n[x](javascript:alert(1))"
        );

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("href=\"javascript:"));
    }

    @Test
    public void createsStandaloneDocumentForBothThemes() {
        String light = MarkdownRenderer.toHtmlDocument("**Hello**", false);
        String dark = MarkdownRenderer.toHtmlDocument("**Hello**", true);

        assertTrue(light.startsWith("<!doctype html>"));
        assertTrue(light.contains("#FFFFFF"));
        assertTrue(dark.contains("#0D1312"));
    }

    @Test
    public void safelyRendersAComplexSkillStyleDocument() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("---\nname: regression-fixture\ndescription: complex document\n---\n\n");
        markdown.append("# Complex document — raw and rendered\n\n");
        markdown.append("| Task | Size / complexity | Model | Effort | Batching |\n");
        markdown.append("|---|---|---|---|---|\n");
        for (int index = 0; index < 40; index++) {
            markdown.append("| Source ").append(index)
                    .append(" | 4,000–12,000 words | standard | medium | one source |\n");
        }
        markdown.append("\n```bash\nwc -w transcripts/source.txt\n```\n\n");
        for (int index = 1; index <= 260; index++) {
            markdown.append(index).append(". **Rule ").append(index)
                    .append(".** Measure → parse → verify; do not guess.\n");
        }
        markdown.append("\n`long-line-");
        for (int index = 0; index < 700; index++) {
            markdown.append('x');
        }
        markdown.append("`\n");

        MarkdownRenderer.RenderResult result =
                MarkdownRenderer.renderSafely(markdown.toString(), false);

        assertNotNull(result);
        assertNotNull(result.htmlDocument);
        assertTrue(result.htmlDocument.startsWith("<!doctype html>"));
        assertTrue(result.htmlDocument.contains("Complex document"));
    }

    @Test
    public void pathologicalOrderedMarkersCannotEscapeSafeRendering() {
        StringBuilder markdown = new StringBuilder("List:\n");
        for (int index = 1; index <= 5_000; index++) {
            markdown.append(index).append(". ");
        }
        markdown.append("\nend\n");

        MarkdownRenderer.RenderResult result =
                MarkdownRenderer.renderSafely(markdown.toString(), false);

        assertNotNull(result);
        assertNotNull(result.htmlDocument);
        assertTrue(result.htmlDocument.startsWith("<!doctype html>"));
    }

    @Test
    public void veryLargeDocumentsUseBoundedFallback() {
        StringBuilder markdown = new StringBuilder(MarkdownRenderer.MAX_FULL_RENDER_CHARS + 1);
        for (int index = 0; index <= MarkdownRenderer.MAX_FULL_RENDER_CHARS; index++) {
            markdown.append('a');
        }

        MarkdownRenderer.RenderResult result =
                MarkdownRenderer.renderSafely(markdown.toString(), false);

        assertTrue(result.fallback);
        assertTrue(result.htmlDocument.contains("complete source remains available"));
        assertTrue(result.htmlDocument.length() < 50_000);
    }

    @Test
    public void fallbackEscaperHandlesMarkupCharacters() {
        assertTrue(MarkdownRenderer.escapeHtml("<&>\"'")
                .equals("&lt;&amp;&gt;&quot;&#39;"));
    }

    @Test
    public void nativePreviewAddsReadableTableSeparatorsAndSupportedTags() {
        String adapted = MarkdownRenderer.forNativeTextView(
                "<table><tr><th>A</th><td>B</td></tr></table><code>x</code><del>y</del>"
        );

        assertFalse(adapted.contains("<table>"));
        assertTrue(adapted.contains("|"));
        assertTrue(adapted.contains("<tt>x</tt>"));
        assertTrue(adapted.contains("<s>y</s>"));
    }
}
