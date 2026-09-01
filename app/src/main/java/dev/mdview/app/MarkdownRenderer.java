package dev.mdview.app;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.Arrays;
import java.util.List;

/** Converts CommonMark plus common GitHub-flavored extensions to safe, offline HTML. */
final class MarkdownRenderer {
    /**
     * Rendering very large documents into both an Android TextView and WebView can multiply memory
     * use. Raw source remains available above this limit, while the preview shows an explanation.
     */
    static final int MAX_FULL_RENDER_CHARS = 2_000_000;
    private static final int MAX_HTML_CHARS = 4_000_000;
    private static final int MAX_FALLBACK_SOURCE_CHARS = 500_000;

    /**
     * Initializes CommonMark only when a preview is requested. Keeping it in a nested holder lets
     * renderSafely catch runtime linkage failures on older Android releases instead of failing while
     * MarkdownRenderer itself is being initialized.
     */
    private static final class Engine {
        private static final List<Extension> EXTENSIONS = Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create()
        );

        private static final Parser PARSER = Parser.builder()
                .extensions(EXTENSIONS)
                .build();

        private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder()
                .extensions(EXTENSIONS)
                .escapeHtml(true)
                .sanitizeUrls(true)
                .build();

        private Engine() {
        }
    }

    private MarkdownRenderer() {
    }

    static String toHtmlFragment(String markdown) {
        Node document = Engine.PARSER.parse(markdown == null ? "" : markdown);
        return Engine.HTML_RENDERER.render(document);
    }

    static String toHtmlDocument(String markdown, boolean darkTheme) {
        return buildHtmlDocument(toHtmlFragment(markdown), darkTheme);
    }

    /**
     * Renders without allowing malformed or unusually complex Markdown to terminate the activity.
     * Stack overflows and memory pressure are converted into a readable fallback preview.
     */
    static RenderResult renderSafely(String markdown, boolean darkTheme) {
        String source = markdown == null ? "" : markdown;

        if (source.length() > MAX_FULL_RENDER_CHARS) {
            String message = "Rendered preview is disabled for this very large document. " +
                    "The complete source remains available in Raw view.";
            return fallbackResult(source, darkTheme, message, false);
        }

        try {
            String fragment = toHtmlFragment(source);
            if (fragment.length() > MAX_HTML_CHARS) {
                String message = "Rendered preview became too large to display safely. " +
                        "The complete source remains available in Raw view.";
                return fallbackResult(source, darkTheme, message, false);
            }
            return new RenderResult(
                    buildHtmlDocument(fragment, darkTheme),
                    fragment,
                    false,
                    null
            );
        } catch (StackOverflowError error) {
            return fallbackResult(
                    source,
                    darkTheme,
                    "This document exceeded the Markdown parser's nesting limit. " +
                            "A plain-text preview is shown instead.",
                    true
            );
        } catch (OutOfMemoryError error) {
            return fallbackResult(
                    "",
                    darkTheme,
                    "Rendered preview was disabled because Android reported low memory. " +
                            "The source is still available in Raw view.",
                    false
            );
        } catch (LinkageError error) {
            return fallbackResult(
                    source,
                    darkTheme,
                    "This Android version could not initialize the Markdown engine. " +
                            "A plain-text preview is shown instead.",
                    true
            );
        } catch (RuntimeException exception) {
            return fallbackResult(
                    source,
                    darkTheme,
                    "The Markdown preview could not be generated. A plain-text preview is shown instead.",
                    true
            );
        }
    }

    private static RenderResult fallbackResult(String source, boolean darkTheme, String message,
                                               boolean includeSource) {
        StringBuilder fragment = new StringBuilder();
        fragment.append("<div class=\"render-note\"><strong>Preview fallback</strong><br>")
                .append(escapeHtml(message))
                .append("</div>");

        if (includeSource && source != null && !source.isEmpty()) {
            String displayedSource = source;
            boolean truncated = false;
            if (displayedSource.length() > MAX_FALLBACK_SOURCE_CHARS) {
                displayedSource = displayedSource.substring(0, MAX_FALLBACK_SOURCE_CHARS);
                truncated = true;
            }
            fragment.append("<pre class=\"plain-fallback\">")
                    .append(escapeHtml(displayedSource));
            if (truncated) {
                fragment.append("\n\n[Plain-text preview truncated; Raw view contains the complete document.]");
            }
            fragment.append("</pre>");
        }

        String fragmentString = fragment.toString();
        return new RenderResult(
                buildHtmlDocument(fragmentString, darkTheme),
                fragmentString,
                true,
                message
        );
    }

    private static String buildHtmlDocument(String fragment, boolean darkTheme) {
        String background = darkTheme ? "#0D1312" : "#FFFFFF";
        String foreground = darkTheme ? "#E7F0EE" : "#17201F";
        String muted = darkTheme ? "#A7B6B3" : "#5A6664";
        String divider = darkTheme ? "#2A3936" : "#DDE4E2";
        String codeBackground = darkTheme ? "#17211F" : "#F3F6F5";
        String accent = darkTheme ? "#5EEAD4" : "#0F766E";

        String css = "html{color-scheme:" + (darkTheme ? "dark" : "light") + ";}" +
                "body{box-sizing:border-box;margin:0;padding:20px 18px 56px;" +
                "background:" + background + ";color:" + foreground + ";" +
                "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;" +
                "font-size:16px;line-height:1.58;overflow-wrap:anywhere;}" +
                "*,*:before,*:after{box-sizing:inherit;}" +
                "h1,h2,h3,h4,h5,h6{line-height:1.25;margin:1.35em 0 .55em;font-weight:700;}" +
                "h1{font-size:2em;border-bottom:1px solid " + divider + ";padding-bottom:.28em;}" +
                "h2{font-size:1.55em;border-bottom:1px solid " + divider + ";padding-bottom:.24em;}" +
                "h3{font-size:1.25em;}p{margin:.8em 0;}" +
                "a{color:" + accent + ";text-decoration-thickness:.08em;text-underline-offset:.16em;}" +
                "blockquote{margin:1em 0;padding:.1em 1em;border-left:4px solid " + accent + ";" +
                "color:" + muted + ";background:" + codeBackground + ";}" +
                "code,kbd,samp{font-family:'Roboto Mono','SFMono-Regular',Consolas,monospace;" +
                "font-size:.9em;background:" + codeBackground + ";border-radius:5px;padding:.15em .32em;}" +
                "pre{overflow:auto;background:" + codeBackground + ";border:1px solid " + divider + ";" +
                "border-radius:10px;padding:14px;line-height:1.45;}" +
                "pre code{padding:0;background:transparent;white-space:pre;}" +
                "table{display:block;width:100%;overflow-x:auto;border-collapse:collapse;margin:1em 0;}" +
                "th,td{border:1px solid " + divider + ";padding:7px 10px;text-align:left;}" +
                "th{background:" + codeBackground + ";font-weight:650;}" +
                "hr{height:1px;border:0;background:" + divider + ";margin:1.7em 0;}" +
                "img{max-width:100%;height:auto;border-radius:8px;}" +
                "ul,ol{padding-left:1.7em;}li+li{margin-top:.22em;}" +
                ".render-note{margin:0 0 1em;padding:12px 14px;border:1px solid " + divider + ";" +
                "border-left:4px solid " + accent + ";border-radius:8px;background:" + codeBackground + ";}" +
                ".plain-fallback{white-space:pre-wrap;overflow-wrap:anywhere;}";

        return "<!doctype html><html><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=5\">" +
                "<style>" + css + "</style></head><body>" +
                (fragment == null ? "" : fragment) +
                "</body></html>";
    }

    static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 32);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&#39;");
                    break;
                default:
                    escaped.append(character);
                    break;
            }
        }
        return escaped.toString();
    }

    static final class RenderResult {
        final String htmlDocument;
        final String htmlFragment;
        final boolean fallback;
        final String notice;

        RenderResult(String htmlDocument, String htmlFragment, boolean fallback, String notice) {
            this.htmlDocument = htmlDocument;
            this.htmlFragment = htmlFragment;
            this.fallback = fallback;
            this.notice = notice;
        }
    }
}
