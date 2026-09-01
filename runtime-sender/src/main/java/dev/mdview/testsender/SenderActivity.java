package dev.mdview.testsender;

import android.app.Activity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/**
 * Test-only sender. It owns the provider and grants MD View temporary read access,
 * matching the Android file-manager contract without shell/root permission shortcuts.
 */
public final class SenderActivity extends Activity {
    private static final String TAG = "MdViewRuntimeSender";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView status = new TextView(this);
        status.setText("Opening the Markdown regression fixture…");
        status.setPadding(32, 32, 32, 32);
        setContentView(status);

        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setComponent(new ComponentName(
                "dev.mdview.app.safe",
                "dev.mdview.app.MainActivity"
        ));
        view.setDataAndType(FixtureProvider.FIXTURE_URI, "text/markdown");
        view.setClipData(ClipData.newRawUri("SKILL.md", FixtureProvider.FIXTURE_URI));
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            grantUriPermission(
                    "dev.mdview.app.safe",
                    FixtureProvider.FIXTURE_URI,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
            startActivity(view);
            finish();
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not launch MD View Safe", error);
            status.setText("Sender failed: " + error.getClass().getSimpleName() +
                    ": " + error.getMessage());
        }
    }
}
