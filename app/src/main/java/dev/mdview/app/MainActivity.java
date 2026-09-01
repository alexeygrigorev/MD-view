package dev.mdview.app;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Html;
import android.text.Layout;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A deliberately small, permission-free Markdown viewer with raw, rendered, and split modes.
 */
public final class MainActivity extends Activity {
    private static final int REQUEST_OPEN_DOCUMENT = 4101;
    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_RAW_RECOVERY_CHARS = 500_000;
    private static final int MAX_SAVED_TEXT_BYTES = 384 * 1024;
    private static final String PREVIEW_BASE_URL = "https://mdview.invalid/";

    private static final String STATE_MODE = "mode";
    private static final String STATE_URI = "uri";
    private static final String STATE_NAME = "name";
    private static final String STATE_TEXT = "text";

    private enum Mode {
        RAW,
        RENDERED,
        SPLIT
    }

    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger loadGeneration = new AtomicInteger();

    private int surfaceColor;
    private int surfaceAltColor;
    private int paneSurfaceColor;
    private int textPrimaryColor;
    private int textSecondaryColor;
    private int dividerColor;
    private int accentColor;
    private int accentSoftColor;
    private int onAccentColor;

    private LinearLayout root;
    private LinearLayout splitContainer;
    private LinearLayout rawPane;
    private LinearLayout renderedPane;
    private FrameLayout contentHolder;
    private FrameLayout renderedContentHost;
    private View emptyState;
    private View loadingOverlay;
    private TextView rawTextView;
    private TextView renderedFallbackView;
    private WebView renderedWebView;
    private TextView titleView;
    private TextView subtitleView;
    private TextView rawModeButton;
    private TextView renderedModeButton;
    private TextView splitModeButton;

    private Mode currentMode = Mode.SPLIT;
    private String currentUriString;
    private String currentName;
    private String currentMarkdown;
    private int currentByteCount;
    private boolean documentLoaded;
    private boolean webPreviewAvailable = true;
    private String nativePreviewReason;
    private MarkdownRenderer.RenderResult currentRenderResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadThemeColors();
        configureSystemBars();
        buildInterface();

        if (savedInstanceState != null) {
            currentMode = parseMode(savedInstanceState.getString(STATE_MODE));
            String savedText = savedInstanceState.getString(STATE_TEXT);
            String savedName = savedInstanceState.getString(STATE_NAME);
            String savedUri = savedInstanceState.getString(STATE_URI);

            if (savedText != null) {
                loadText(savedName == null ? "Markdown" : savedName, savedText, null,
                        savedText.getBytes(StandardCharsets.UTF_8).length);
            } else if (savedUri != null) {
                loadUri(Uri.parse(savedUri));
            } else {
                handleIntent(getIntent());
            }
        } else {
            handleIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_MODE, currentMode.name());
        outState.putString(STATE_URI, currentUriString);
        outState.putString(STATE_NAME, currentName);

        if (currentUriString == null && currentMarkdown != null && currentByteCount <= MAX_SAVED_TEXT_BYTES) {
            outState.putString(STATE_TEXT, currentMarkdown);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updatePaneLayout();
    }

    @Override
    protected void onDestroy() {
        loadGeneration.incrementAndGet();
        fileExecutor.shutdownNow();
        if (renderedWebView != null) {
            renderedWebView.stopLoading();
            renderedWebView.loadUrl("about:blank");
            renderedWebView.destroy();
        }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_DOCUMENT || resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();
        if (uri == null) {
            showToast("No document was selected.");
            return;
        }

        if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
            try {
                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (SecurityException ignored) {
                // Some document providers grant access only for the current activity; that is sufficient.
            }
        }
        loadUri(uri);
    }

    private void loadThemeColors() {
        surfaceColor = getColor(R.color.surface);
        surfaceAltColor = getColor(R.color.surface_alt);
        paneSurfaceColor = getColor(R.color.pane_surface);
        textPrimaryColor = getColor(R.color.text_primary);
        textSecondaryColor = getColor(R.color.text_secondary);
        dividerColor = getColor(R.color.divider);
        accentColor = getColor(R.color.accent);
        accentSoftColor = getColor(R.color.accent_soft);
        onAccentColor = getColor(R.color.on_accent);
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(surfaceColor);
        getWindow().setNavigationBarColor(surfaceColor);

        int visibility = 0;
        if (!isDarkTheme() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            visibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (!isDarkTheme() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            visibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(visibility);
    }

    private boolean isDarkTheme() {
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void buildInterface() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(surfaceColor);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        root.addView(createToolbar());
        root.addView(createDivider());
        root.addView(createModeBar());
        root.addView(createDivider());

        contentHolder = new FrameLayout(this);
        contentHolder.setBackgroundColor(surfaceAltColor);
        root.addView(contentHolder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        splitContainer = new LinearLayout(this);
        splitContainer.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        splitContainer.setDividerDrawable(solidDividerDrawable());
        splitContainer.setDividerPadding(0);
        contentHolder.addView(splitContainer, frameMatch());

        rawTextView = createRawTextView();
        renderedContentHost = createRenderedContentHost();
        rawPane = createPane("RAW", rawTextView);
        renderedPane = createPane("RENDERED", renderedContentHost);
        splitContainer.addView(rawPane);
        splitContainer.addView(renderedPane);

        emptyState = createEmptyState();
        contentHolder.addView(emptyState, frameMatch());

        loadingOverlay = createLoadingOverlay();
        loadingOverlay.setVisibility(View.GONE);
        contentHolder.addView(loadingOverlay, frameMatch());

        setContentView(root);
        updatePaneLayout();
        showEmptyState();
    }

    private View createToolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), dp(10), dp(12), dp(10));
        toolbar.setBackgroundColor(surfaceColor);
        toolbar.setMinimumHeight(dp(66));

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.setGravity(Gravity.CENTER_VERTICAL);

        titleView = new TextView(this);
        titleView.setText("MD View");
        titleView.setTextColor(textPrimaryColor);
        titleView.setTextSize(19);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);

        subtitleView = new TextView(this);
        subtitleView.setText("Raw + rendered Markdown");
        subtitleView.setTextColor(textSecondaryColor);
        subtitleView.setTextSize(12);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);

        titleColumn.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        titleColumn.addView(subtitleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        toolbar.addView(titleColumn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView openButton = createActionButton("Open");
        openButton.setContentDescription("Open a Markdown document");
        openButton.setOnClickListener(view -> openDocumentPicker());
        toolbar.addView(openButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
        ));

        return toolbar;
    }

    private View createModeBar() {
        LinearLayout modeBar = new LinearLayout(this);
        modeBar.setOrientation(LinearLayout.HORIZONTAL);
        modeBar.setGravity(Gravity.CENTER);
        modeBar.setPadding(dp(12), dp(7), dp(12), dp(7));
        modeBar.setBackgroundColor(surfaceColor);

        rawModeButton = createModeButton("Raw", Mode.RAW);
        renderedModeButton = createModeButton("Rendered", Mode.RENDERED);
        splitModeButton = createModeButton("Split", Mode.SPLIT);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                0,
                dp(40),
                1f
        );
        buttonParams.setMarginEnd(dp(6));
        modeBar.addView(rawModeButton, buttonParams);

        LinearLayout.LayoutParams renderedParams = new LinearLayout.LayoutParams(
                0,
                dp(40),
                1f
        );
        renderedParams.setMarginEnd(dp(6));
        modeBar.addView(renderedModeButton, renderedParams);
        modeBar.addView(splitModeButton, new LinearLayout.LayoutParams(0, dp(40), 1f));

        updateModeButtons();
        return modeBar;
    }

    private TextView createModeButton(String text, Mode mode) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(view -> setMode(mode));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            button.setTooltipText(text + " view");
        }
        return button;
    }

    private TextView createActionButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(onAccentColor);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setBackground(roundedDrawable(accentColor, dp(12), 0, Color.TRANSPARENT));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private LinearLayout createPane(String label, View content) {
        LinearLayout pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setBackgroundColor(paneSurfaceColor);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(textSecondaryColor);
        labelView.setTextSize(11);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labelView.setGravity(Gravity.CENTER_VERTICAL);
        labelView.setPadding(dp(14), 0, dp(14), 0);
        labelView.setLetterSpacing(.09f);
        pane.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(34)
        ));
        pane.addView(createDivider());
        pane.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        return pane;
    }

    @SuppressLint("WrongConstant")
    private TextView createRawTextView() {
        TextView raw = new TextView(this);
        raw.setBackgroundColor(paneSurfaceColor);
        raw.setTextColor(textPrimaryColor);
        raw.setTextSize(14);
        raw.setTypeface(Typeface.MONOSPACE);
        raw.setGravity(Gravity.TOP | Gravity.START);
        raw.setPadding(dp(16), dp(14), dp(16), dp(40));
        raw.setIncludeFontPadding(false);
        raw.setLineSpacing(0f, 1.18f);
        raw.setTextIsSelectable(true);
        raw.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        raw.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        raw.setHorizontallyScrolling(true);
        raw.setHorizontalScrollBarEnabled(true);
        raw.setVerticalScrollBarEnabled(true);
        raw.setScrollbarFadingEnabled(false);
        raw.setMovementMethod(ScrollingMovementMethod.getInstance());
        raw.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        raw.setContentDescription("Raw Markdown source");
        return raw;
    }

    private FrameLayout createRenderedContentHost() {
        FrameLayout host = new FrameLayout(this);
        host.setBackgroundColor(paneSurfaceColor);
        renderedContentHost = host;

        renderedFallbackView = createRenderedFallbackView();
        renderedFallbackView.setVisibility(View.GONE);
        host.addView(renderedFallbackView, frameMatch());

        try {
            renderedWebView = createRenderedWebView();
            host.addView(renderedWebView, frameMatch());
        } catch (RuntimeException | LinkageError | OutOfMemoryError error) {
            renderedWebView = null;
            webPreviewAvailable = false;
            nativePreviewReason =
                    "Android's WebView component is unavailable. Native preview mode is active.";
            showNativePreviewMessage(nativePreviewReason);
        }
        return host;
    }

    @SuppressLint("WrongConstant")
    private TextView createRenderedFallbackView() {
        TextView fallback = new TextView(this);
        fallback.setBackgroundColor(paneSurfaceColor);
        fallback.setTextColor(textPrimaryColor);
        fallback.setTextSize(16);
        fallback.setGravity(Gravity.TOP | Gravity.START);
        fallback.setPadding(dp(18), dp(16), dp(18), dp(40));
        fallback.setIncludeFontPadding(false);
        fallback.setLineSpacing(0f, 1.2f);
        fallback.setTextIsSelectable(true);
        fallback.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        fallback.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        fallback.setVerticalScrollBarEnabled(true);
        fallback.setScrollbarFadingEnabled(false);
        fallback.setMovementMethod(ScrollingMovementMethod.getInstance());
        fallback.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        fallback.setContentDescription("Native rendered Markdown preview");
        return fallback;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createRenderedWebView() {
        WebView webView = new WebView(this);
        webView.setBackgroundColor(paneSurfaceColor);
        webView.setContentDescription("Rendered Markdown preview");

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setGeolocationEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setSaveFormData(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setDefaultFontSize(16);
        settings.setTextZoom(100);
        settings.setMediaPlaybackRequiresUserGesture(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
        }

        webView.setWebViewClient(createPreviewWebViewClient());
        return webView;
    }

    private WebViewClient createPreviewWebViewClient() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new RendererAwarePreviewWebViewClient();
        }
        return new PreviewWebViewClient();
    }

    private class PreviewWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handlePreviewLink(request.getUrl());
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handlePreviewLink(Uri.parse(url));
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private final class RendererAwarePreviewWebViewClient extends PreviewWebViewClient {
        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            return handleRenderProcessGone(view, detail != null && detail.didCrash());
        }
    }

    private boolean handleRenderProcessGone(WebView view, boolean crashed) {
        if (view != null && renderedContentHost != null) {
            renderedContentHost.removeView(view);
        }
        if (view != null) {
            try {
                view.destroy();
            } catch (RuntimeException ignored) {
                // The renderer is already gone; there may be nothing left to destroy.
            }
        }
        if (view == renderedWebView) {
            renderedWebView = null;
        }
        webPreviewAvailable = false;

        String reason = crashed
                ? "Android's WebView renderer stopped while displaying this document. " +
                "Native preview mode is active, and Raw view remains available."
                : "Android reclaimed the WebView renderer. Native preview mode is active.";
        nativePreviewReason = reason;
        showNativePreview(reason);
        showToast("Preview switched to safe mode.");
        return true;
    }

    private View createEmptyState() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(surfaceAltColor);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(28), dp(24), dp(28), dp(24));

        TextView icon = new TextView(this);
        icon.setText("MD");
        icon.setTextColor(onAccentColor);
        icon.setTextSize(19);
        icon.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setShape(GradientDrawable.OVAL);
        iconBackground.setColor(accentColor);
        icon.setBackground(iconBackground);
        card.addView(icon, new LinearLayout.LayoutParams(dp(70), dp(70)));

        TextView heading = new TextView(this);
        heading.setText("Open a Markdown file");
        heading.setTextColor(textPrimaryColor);
        heading.setTextSize(22);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        headingParams.topMargin = dp(20);
        card.addView(heading, headingParams);

        TextView explanation = new TextView(this);
        explanation.setText("View the exact source and the rendered document together. You can also open .md files directly from Android’s file manager.");
        explanation.setTextColor(textSecondaryColor);
        explanation.setTextSize(15);
        explanation.setGravity(Gravity.CENTER);
        explanation.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams explanationParams = new LinearLayout.LayoutParams(
                Math.min(dp(420), getResources().getDisplayMetrics().widthPixels - dp(48)),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        explanationParams.topMargin = dp(10);
        card.addView(explanation, explanationParams);

        TextView chooseButton = createActionButton("Choose file");
        chooseButton.setContentDescription("Choose a Markdown document");
        chooseButton.setOnClickListener(view -> openDocumentPicker());
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(46)
        );
        chooseParams.topMargin = dp(22);
        card.addView(chooseButton, chooseParams);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        frame.addView(card, cardParams);
        return frame;
    }

    private View createLoadingOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor((surfaceAltColor & 0x00FFFFFF) | 0xE6000000);
        overlay.setClickable(true);
        ProgressBar progressBar = new ProgressBar(this);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                dp(52),
                dp(52),
                Gravity.CENTER
        );
        overlay.addView(progressBar, progressParams);
        return overlay;
    }

    private View createDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(dividerColor);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        ));
        return divider;
    }

    private GradientDrawable solidDividerDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(dividerColor);
        drawable.setSize(dp(1), dp(1));
        return drawable;
    }

    private GradientDrawable roundedDrawable(int fillColor, int radius, int strokeWidth,
                                             int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private void setMode(Mode mode) {
        currentMode = mode;
        updateModeButtons();
        applyMode();
    }

    private void updateModeButtons() {
        styleModeButton(rawModeButton, currentMode == Mode.RAW);
        styleModeButton(renderedModeButton, currentMode == Mode.RENDERED);
        styleModeButton(splitModeButton, currentMode == Mode.SPLIT);
    }

    private void styleModeButton(TextView button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setTextColor(selected ? accentColor : textSecondaryColor);
        button.setBackground(roundedDrawable(
                selected ? accentSoftColor : surfaceAltColor,
                dp(10),
                selected ? dp(1) : 0,
                accentColor
        ));
        button.setSelected(selected);
    }

    private void applyMode() {
        if (!documentLoaded) {
            splitContainer.setVisibility(View.GONE);
            return;
        }

        splitContainer.setVisibility(View.VISIBLE);
        rawPane.setVisibility(currentMode == Mode.RENDERED ? View.GONE : View.VISIBLE);
        renderedPane.setVisibility(currentMode == Mode.RAW ? View.GONE : View.VISIBLE);
        updatePaneLayout();
    }

    private void updatePaneLayout() {
        if (splitContainer == null || rawPane == null || renderedPane == null) {
            return;
        }

        boolean wide = getResources().getConfiguration().screenWidthDp >= 700 ||
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        splitContainer.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        if (currentMode == Mode.SPLIT) {
            if (wide) {
                rawPane.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.MATCH_PARENT, 1f));
                renderedPane.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            } else {
                rawPane.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                renderedPane.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            }
        } else {
            rawPane.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            renderedPane.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        }
    }

    private void showEmptyState() {
        documentLoaded = false;
        currentUriString = null;
        currentName = null;
        currentMarkdown = null;
        currentRenderResult = null;
        currentByteCount = 0;
        titleView.setText("MD View");
        subtitleView.setText("Raw + rendered Markdown");
        emptyState.setVisibility(View.VISIBLE);
        splitContainer.setVisibility(View.GONE);
        setLoading(false);
        updateModeButtons();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            showEmptyState();
            return;
        }

        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri sharedUri = extractSharedUri(intent);
            if (sharedUri != null) {
                loadUri(sharedUri);
                return;
            }

            CharSequence sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (sharedText != null) {
                String text = sharedText.toString();
                loadText("Shared Markdown", text, null,
                        text.getBytes(StandardCharsets.UTF_8).length);
                return;
            }
        }

        Uri data = intent.getData();
        if (data != null) {
            loadUri(data);
        } else {
            showEmptyState();
        }
    }

    @SuppressWarnings("deprecation")
    private Uri extractSharedUri(Intent intent) {
        Object stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (stream instanceof Uri) {
            return (Uri) stream;
        }

        ClipData clipData = intent.getClipData();
        if (clipData != null && clipData.getItemCount() > 0) {
            return clipData.getItemAt(0).getUri();
        }
        return null;
    }

    private void openDocumentPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/markdown",
                "text/x-markdown",
                "text/plain",
                "application/markdown",
                "application/octet-stream"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_OPEN_DOCUMENT);
        } catch (ActivityNotFoundException exception) {
            showToast("No Android document picker is available.");
        }
    }

    private void loadText(String name, String markdown, Uri uri, int byteCount) {
        int generation = loadGeneration.incrementAndGet();
        setLoading(true);
        titleView.setText(TextUtils.isEmpty(name) ? "Opening…" : name);
        subtitleView.setText("Preparing preview");
        boolean darkTheme = isDarkTheme();
        String source = markdown == null ? "" : markdown;

        fileExecutor.execute(() -> {
            MarkdownRenderer.RenderResult renderResult =
                    MarkdownRenderer.renderSafely(source, darkTheme);
            runOnUiThread(() -> {
                if (generation != loadGeneration.get() || isFinishing()) {
                    return;
                }
                showMarkdown(name, source, uri, byteCount, renderResult);
            });
        });
    }

    private void loadUri(Uri uri) {
        if (uri == null) {
            showToast("The document address is missing.");
            return;
        }

        int generation = loadGeneration.incrementAndGet();
        setLoading(true);
        titleView.setText("Opening…");
        subtitleView.setText("Reading document");
        boolean darkTheme = isDarkTheme();

        fileExecutor.execute(() -> {
            try {
                ContentResolver resolver = getContentResolver();
                String name = queryDisplayName(resolver, uri);
                byte[] bytes;
                try (InputStream stream = resolver.openInputStream(uri)) {
                    if (stream == null) {
                        throw new IOException("The document provider returned no data.");
                    }
                    bytes = readLimited(stream, MAX_FILE_BYTES);
                }
                String markdown = decodeText(bytes);
                MarkdownRenderer.RenderResult renderResult =
                        MarkdownRenderer.renderSafely(markdown, darkTheme);

                runOnUiThread(() -> {
                    if (generation != loadGeneration.get() || isFinishing()) {
                        return;
                    }
                    showMarkdown(name, markdown, uri, bytes.length, renderResult);
                });
            } catch (FileTooLargeException exception) {
                showLoadError(generation, "This file is larger than 8 MB.");
            } catch (SecurityException exception) {
                showLoadError(generation, "Android did not grant access to this file.");
            } catch (Exception exception) {
                String message = exception.getMessage();
                showLoadError(generation, message == null || message.trim().isEmpty()
                        ? "The file could not be opened."
                        : "Could not open file: " + message);
            }
        });
    }

    private void showLoadError(int generation, String message) {
        runOnUiThread(() -> {
            if (generation != loadGeneration.get() || isFinishing()) {
                return;
            }
            setLoading(false);
            showToast(message);
            if (documentLoaded) {
                titleView.setText(currentName == null ? "Markdown" : currentName);
                subtitleView.setText(formatDocumentSubtitle(currentByteCount));
            } else {
                showEmptyState();
            }
        });
    }

    private void showMarkdown(String name, String markdown, Uri uri, int byteCount,
                              MarkdownRenderer.RenderResult renderResult) {
        documentLoaded = true;
        currentName = TextUtils.isEmpty(name) ? fallbackName(uri) : name;
        currentMarkdown = markdown == null ? "" : markdown;
        currentByteCount = Math.max(0, byteCount);
        currentUriString = uri == null ? null : uri.toString();
        currentRenderResult = renderResult;

        titleView.setText(currentName);
        subtitleView.setText(formatDocumentSubtitle(currentByteCount) +
                (renderResult != null && renderResult.fallback ? "  •  Safe preview" : ""));
        updateModeButtons();
        setRawTextSafely(currentMarkdown);
        showRenderedPreview(renderResult);

        emptyState.setVisibility(View.GONE);
        setLoading(false);
        applyMode();
    }

    private void setRawTextSafely(String source) {
        try {
            rawTextView.setText(source);
        } catch (RuntimeException | OutOfMemoryError error) {
            int end = Math.min(source.length(), MAX_RAW_RECOVERY_CHARS);
            String recovery = source.substring(0, end) +
                    "\n\n[Raw display was shortened because Android could not lay out the complete document.]";
            try {
                rawTextView.setText(recovery);
            } catch (RuntimeException | OutOfMemoryError secondError) {
                rawTextView.setText("The document opened, but Android could not display its raw text.");
            }
            showToast("Raw view was shortened to keep the app stable.");
        }
        rawTextView.scrollTo(0, 0);
    }

    private void showRenderedPreview(MarkdownRenderer.RenderResult renderResult) {
        if (renderResult == null) {
            nativePreviewReason = "The preview could not be prepared. Raw view remains available.";
            showNativePreview(nativePreviewReason);
            return;
        }

        if (webPreviewAvailable && renderedWebView != null) {
            try {
                renderedFallbackView.setVisibility(View.GONE);
                renderedWebView.setVisibility(View.VISIBLE);
                renderedWebView.loadDataWithBaseURL(
                        PREVIEW_BASE_URL,
                        renderResult.htmlDocument,
                        "text/html",
                        StandardCharsets.UTF_8.name(),
                        null
                );
                return;
            } catch (RuntimeException | OutOfMemoryError error) {
                webPreviewAvailable = false;
                nativePreviewReason = "Android could not display the WebView preview. " +
                        "Native preview mode is active.";
            }
        }

        showNativePreview(nativePreviewReason);
    }

    private void showNativePreview(String reason) {
        if (renderedFallbackView == null) {
            return;
        }

        String fragment = currentRenderResult == null ? "" : currentRenderResult.htmlFragment;
        if (!TextUtils.isEmpty(reason)) {
            fragment = "<p><strong>Safe preview</strong><br>" +
                    MarkdownRenderer.escapeHtml(reason) + "</p>" + fragment;
        }

        try {
            CharSequence nativeText;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                nativeText = Html.fromHtml(fragment, Html.FROM_HTML_MODE_LEGACY);
            } else {
                //noinspection deprecation
                nativeText = Html.fromHtml(fragment);
            }
            renderedFallbackView.setText(nativeText);
        } catch (RuntimeException | OutOfMemoryError error) {
            String source = currentMarkdown == null ? "" : currentMarkdown;
            int end = Math.min(source.length(), MAX_RAW_RECOVERY_CHARS);
            renderedFallbackView.setText(source.substring(0, end));
        }

        renderedFallbackView.scrollTo(0, 0);
        renderedFallbackView.setVisibility(View.VISIBLE);
        if (renderedWebView != null) {
            renderedWebView.setVisibility(View.GONE);
        }
    }

    private void showNativePreviewMessage(String message) {
        if (renderedFallbackView == null) {
            return;
        }
        renderedFallbackView.setText(message);
        renderedFallbackView.setVisibility(View.VISIBLE);
    }

    private String queryDisplayName(ContentResolver resolver, Uri uri) {
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor cursor = resolver.query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        String displayName = cursor.getString(index);
                        if (!TextUtils.isEmpty(displayName)) {
                            return displayName;
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // Fall through to the URI-derived name.
            }
        }
        return fallbackName(uri);
    }

    private String fallbackName(Uri uri) {
        if (uri == null) {
            return "Markdown";
        }
        String segment = uri.getLastPathSegment();
        if (segment == null || segment.trim().isEmpty()) {
            return "Markdown";
        }
        int colon = segment.lastIndexOf(':');
        return colon >= 0 && colon + 1 < segment.length() ? segment.substring(colon + 1) : segment;
    }

    private byte[] readLimited(InputStream stream, int maxBytes) throws IOException,
            FileTooLargeException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = stream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new FileTooLargeException();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String decodeText(byte[] bytes) {
        if (bytes.length >= 3 &&
                (bytes[0] & 0xFF) == 0xEF &&
                (bytes[1] & 0xFF) == 0xBB &&
                (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private boolean handlePreviewLink(Uri uri) {
        if (uri == null) {
            return true;
        }

        String scheme = uri.getScheme();
        if (scheme == null || "about".equalsIgnoreCase(scheme) || "data".equalsIgnoreCase(scheme)) {
            return false;
        }

        if ("mdview.invalid".equalsIgnoreCase(uri.getHost())) {
            // Permit in-page #anchors, but block relative document navigation.
            String path = uri.getPath();
            return uri.getFragment() == null || !(path == null || path.isEmpty() || "/".equals(path));
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if ("http".equals(normalizedScheme) || "https".equals(normalizedScheme) ||
                "mailto".equals(normalizedScheme) || "tel".equals(normalizedScheme) ||
                "geo".equals(normalizedScheme)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException exception) {
                showToast("No app can open this link.");
            }
        } else {
            showToast("This link type was blocked.");
        }
        return true;
    }

    private String formatDocumentSubtitle(int bytes) {
        return formatBytes(bytes) + "  •  Raw + rendered";
    }

    private String formatBytes(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private Mode parseMode(String value) {
        if (value == null) {
            return Mode.SPLIT;
        }
        try {
            return Mode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return Mode.SPLIT;
        }
    }

    private void setLoading(boolean loading) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private FrameLayout.LayoutParams frameMatch() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class FileTooLargeException extends Exception {
    }
}
