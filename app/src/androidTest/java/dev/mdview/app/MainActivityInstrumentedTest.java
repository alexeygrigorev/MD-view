package dev.mdview.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Runtime regression for the uploaded 24,702-byte/374-line document shape.
 * Prose is redacted because the repository is public; byte count, line count,
 * line lengths, Markdown punctuation, Unicode punctuation, tables and fences
 * are preserved exactly.
 */
public final class MainActivityInstrumentedTest
        extends ActivityInstrumentationTestCase2<MainActivity> {

    private String fixture;

    public MainActivityInstrumentedTest() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        fixture = readFixture();
        assertEquals(24_702, fixture.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(374, fixture.split("\\n", -1).length);

        File input = new File(getInstrumentation().getTargetContext().getCacheDir(), "SKILL.md");
        try (FileOutputStream output = new FileOutputStream(input)) {
            output.write(fixture.getBytes(StandardCharsets.UTF_8));
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.fromFile(input));
        intent.setType("text/markdown");
        setActivityIntent(intent);
    }

    public void testSkillShapedFileOpensAndSurvivesModeChanges() throws Exception {
        MainActivity activity = getActivity();
        getInstrumentation().waitForIdleSync();

        TextView raw = awaitTextView(activity, "Raw Markdown source", 30_000L);
        assertNotNull("Raw Markdown view was not created", raw);
        assertEquals("The complete source must be displayed", fixture, raw.getText().toString());
        assertFalse("Activity finished while opening the document", activity.isFinishing());

        clickText(activity, "Rendered");
        getInstrumentation().waitForIdleSync();
        Thread.sleep(2_000L);
        assertFalse("Activity finished in rendered mode", activity.isFinishing());

        clickText(activity, "Split");
        getInstrumentation().waitForIdleSync();
        Thread.sleep(3_000L);
        assertFalse("Activity finished after returning to split mode", activity.isFinishing());
        assertEquals("Raw source changed after mode switches", fixture, raw.getText().toString());
    }

    private TextView awaitTextView(Activity activity, String description, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        TextView result;
        do {
            getInstrumentation().waitForIdleSync();
            result = findTextViewByDescription(activity.getWindow().getDecorView(), description);
            if (result != null && result.getText().length() == fixture.length()) {
                return result;
            }
            Thread.sleep(100L);
        } while (System.currentTimeMillis() < deadline && !activity.isFinishing());
        return result;
    }

    private void clickText(Activity activity, String label) {
        TextView button = findTextViewByText(activity.getWindow().getDecorView(), label);
        assertNotNull("Missing mode button: " + label, button);
        getInstrumentation().runOnMainSync(button::performClick);
    }

    private static TextView findTextViewByDescription(View view, String description) {
        if (view instanceof TextView && description.contentEquals(view.getContentDescription())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = findTextViewByDescription(group.getChildAt(index), description);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static TextView findTextViewByText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = findTextViewByText(group.getChildAt(index), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String readFixture() throws Exception {
        String encoded;
        try (InputStream input = getInstrumentation().getContext().getAssets()
                .open("skill-shape.md.gz.b64")) {
            encoded = new String(readAll(input), StandardCharsets.US_ASCII).trim();
        }
        byte[] compressed = Base64.decode(encoded, Base64.DEFAULT);
        try (GZIPInputStream gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed))) {
            return new String(readAll(gzip), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
