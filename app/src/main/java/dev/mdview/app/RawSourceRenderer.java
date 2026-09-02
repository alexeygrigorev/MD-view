package dev.mdview.app;

import java.util.Locale;

/** Builds the read-only raw-source page displayed in the same WebView surface as the preview. */
final class RawSourceRenderer {
    private RawSourceRenderer() {
    }

    static String toHtmlDocument(String source, int backgroundColor, int foregroundColor) {
        String background = cssColor(backgroundColor);
        String foreground = cssColor(foregroundColor);
        String escapedSource = MarkdownRenderer.escapeHtml(source == null ? "" : source);

        String css = "html{color-scheme:light dark;}" +
                "html,body{box-sizing:border-box;margin:0;min-height:100%;" +
                "background:" + background + ";color:" + foreground + ";}" +
                "*,*:before,*:after{box-sizing:inherit;}" +
                "body{-webkit-text-size-adjust:100%;}" +
                "pre{display:inline-block;min-width:100%;margin:0;padding:14px 16px 40px;" +
                "font-family:'Roboto Mono','SFMono-Regular',Consolas,monospace;" +
                "font-size:14px;line-height:1.35;white-space:pre;word-break:normal;" +
                "overflow-wrap:normal;tab-size:4;}";

        return "<!doctype html><html><head>" +
                "<meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1," +
                "maximum-scale=5,user-scalable=yes\">" +
                "<style>" + css + "</style></head><body><pre>" +
                escapedSource +
                "</pre></body></html>";
    }

    private static String cssColor(int color) {
        return String.format(Locale.US, "#%06X", color & 0x00FFFFFF);
    }
}
