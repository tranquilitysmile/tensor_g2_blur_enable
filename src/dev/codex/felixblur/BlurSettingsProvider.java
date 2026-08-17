package dev.codex.felixblur;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public final class BlurSettingsProvider extends ContentProvider {
    static final String PREFS = "blur_settings";
    static final String[] KEYS = {"shade", "notifications", "volume", "power", "lockscreen",
            "folders", "launcher_menu", "dock", "blur"};
    static final int[] DEFAULTS = {48, 45, 42, 45, 52, 45, 45, 100, 125};

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"get".equals(method)) return Bundle.EMPTY;
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, 0);
        Bundle out = new Bundle();
        for (int i = 0; i < KEYS.length; i++) out.putInt(KEYS[i], prefs.getInt(KEYS[i], DEFAULTS[i]));
        return out;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
