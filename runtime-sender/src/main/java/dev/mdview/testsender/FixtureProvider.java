package dev.mdview.testsender;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Test-only provider that mimics a file manager's granted document URI. */
public final class FixtureProvider extends ContentProvider {
    static final String AUTHORITY = "dev.mdview.testsender.fixture";
    static final Uri FIXTURE_URI = Uri.parse("content://" + AUTHORITY + "/SKILL.md");
    private static final String FILE_NAME = "SKILL.md";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        requireFixtureUri(uri);
        return "text/markdown";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        requireFixtureUri(uri);
        File file = fixtureFile();
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row.add(FILE_NAME);
            } else if (OpenableColumns.SIZE.equals(column)) {
                row.add(file.length());
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        requireFixtureUri(uri);
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("The runtime fixture is read-only.");
        }
        File file = fixtureFile();
        if (!file.isFile()) {
            throw new FileNotFoundException("Runtime fixture was not installed: " + file);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File fixtureFile() {
        if (getContext() == null) {
            throw new IllegalStateException("Provider context is unavailable.");
        }
        return new File(getContext().getFilesDir(), FILE_NAME);
    }

    private static void requireFixtureUri(Uri uri) {
        if (uri == null || !AUTHORITY.equals(uri.getAuthority()) ||
                !"/SKILL.md".equals(uri.getPath())) {
            throw new IllegalArgumentException("Unknown runtime fixture URI: " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only provider");
    }
}
