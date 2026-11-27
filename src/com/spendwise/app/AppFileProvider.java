package com.spendwise.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

public class AppFileProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (getContext() == null) {
            throw new FileNotFoundException("Context is null");
        }
        String fileName = uri.getLastPathSegment();
        if (fileName == null) {
            throw new FileNotFoundException("Invalid URI: " + uri);
        }

        File cacheFile = new File(getContext().getCacheDir(), fileName);
        if (cacheFile.exists()) {
            return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        File extFile = new File(getContext().getExternalFilesDir(null), fileName);
        if (extFile.exists()) {
            return ParcelFileDescriptor.open(extFile, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        throw new FileNotFoundException("File not found: " + fileName);
    }

    @Override
    public String getType(Uri uri) {
        if (uri != null && uri.toString().endsWith(".csv")) {
            return "text/csv";
        }
        return "application/vnd.android.package-archive";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
