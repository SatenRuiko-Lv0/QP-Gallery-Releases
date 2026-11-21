package com.alensw.comic;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.alensw.a.e;
import com.alensw.bean.CommonFile;
import com.alensw.bean.CommonFolder;
import com.alensw.ui.c.cx;

import java.util.ArrayList;
import java.util.Locale;

public final class ComicMode {
    private static final String KEY = "comic_reader_vertical";
    private static final String SAVED_PAGE = "comic_reader_page";
    private static final String SAVED_OFFSET = "comic_reader_offset";

    private ComicMode() {}

    static boolean chinese(Context context) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        return "zh".equals(locale.getLanguage());
    }

    public static void showStandaloneUpdateMessage(Context context) {
        String message = chinese(context) ? "漫画版使用独立安装包更新"
                : "Use the standalone APK to update this comic edition.";
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void addPreference(PreferenceScreen screen) {
        Preference orientation = screen.findPreference("screen_orientation");
        if (orientation == null || screen.findPreference(KEY) != null) {
            return;
        }
        Context context = screen.getContext();
        boolean zh = chinese(context);
        SwitchPreference toggle = new SwitchPreference(context);
        toggle.setKey(KEY);
        toggle.setTitle(zh ? "漫画阅读模式" : "Comic reading mode");
        toggle.setSummary(zh ? "连续竖向滚动，右下角显示页码"
                : "Scroll vertically with a page counter in the lower right");
        toggle.setDefaultValue(false);
        toggle.setIconSpaceReserved(true);
        PreferenceGroup group = orientation.getParent();
        (group == null ? screen : group).addPreference(toggle);
    }

    public static boolean openLocal(cx activity, View root, e folder, char type,
            Bundle state) {
        if (!enabled(activity) || folder == null || type != 'I') {
            return false;
        }
        if (resetReaderChrome(root)) {
            return true;
        }
        ArrayList<ComicReader.Page> pages = new ArrayList<>();
        for (int i = 0; i < folder.j(); i++) {
            if (folder.f(i) == 'I') {
                Uri uri = folder.d(i);
                addPage(pages, uri, uri == null ? null : uri.getLastPathSegment(), i);
            }
        }
        return open(activity, root, pages, folder.c, state);
    }

    public static boolean openFolder(cx activity, View root, CommonFolder folder,
            CommonFile current, Bundle state) {
        if (!enabled(activity) || folder == null || current == null || current.h() != 'I') {
            return false;
        }
        if (resetReaderChrome(root)) {
            return true;
        }
        ArrayList<ComicReader.Page> pages = new ArrayList<>();
        for (int i = 0; i < folder.o(); i++) {
            CommonFile entry = folder.a(i);
            if (entry != null && entry.h() == 'I') {
                addPage(pages, entry.i(), entry.a(), i);
            }
        }
        return open(activity, root, pages, folder.k, state);
    }

    private static boolean enabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY, false);
    }

    private static void addPage(ArrayList<ComicReader.Page> pages, Uri uri, String title, int index) {
        if (uri != null && !Uri.EMPTY.equals(uri)) {
            pages.add(new ComicReader.Page(uri, title, index));
        }
    }

    private static boolean open(cx activity, View root, ArrayList<ComicReader.Page> pages,
            int selectedIndex, Bundle state) {
        if (!(root instanceof ViewGroup) || pages.isEmpty()) {
            return false;
        }
        int selected = -1;
        int restored = -1;
        Uri uri = state == null ? null : (Uri) state.getParcelable(SAVED_PAGE);
        for (int i = 0; i < pages.size(); i++) {
            ComicReader.Page page = pages.get(i);
            if (page.sourceIndex == selectedIndex) {
                selected = i;
            }
            if (page.uri.equals(uri)) {
                restored = i;
            }
        }
        if (selected < 0 && restored < 0) {
            return false;
        }
        float offset = restored < 0 ? Float.NaN : state.getFloat(SAVED_OFFSET);
        ViewGroup container = (ViewGroup) root;
        // The original viewer is still responsible for the activity and its Back stack.
        for (int i = 0; i < container.getChildCount(); i++) {
            View previous = container.getChildAt(i);
            if (previous.getVisibility() == View.VISIBLE) {
                previous.setVisibility(View.INVISIBLE);
            }
            previous.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        ComicReader reader = new ComicReader(activity, pages,
                restored >= 0 ? restored : selected, offset);
        container.addView(reader, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        reader.requestFocus();
        reader.resetChrome();
        activity.invalidateOptionsMenu();
        return true;
    }

    private static ComicReader reader(View root) {
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) root;
        View last = group.getChildAt(group.getChildCount() - 1);
        return last instanceof ComicReader ? (ComicReader) last : null;
    }

    public static boolean active(View root) {
        return reader(root) != null;
    }

    public static boolean resetReaderChrome(View root) {
        ComicReader reader = reader(root);
        if (reader == null) {
            return false;
        }
        reader.resetChrome();
        return true;
    }

    public static void releaseReaderChrome(View root) {
        ComicReader reader = reader(root);
        if (reader != null) {
            reader.releaseChrome();
        }
    }

    public static int selectedSourceIndex(View root) {
        ComicReader reader = reader(root);
        return reader == null ? -1 : reader.currentPage().sourceIndex;
    }

    public static void save(View root, Bundle state) {
        ComicReader reader = reader(root);
        if (reader != null) {
            state.putParcelable(SAVED_PAGE, reader.currentPage().uri);
            state.putFloat(SAVED_OFFSET, reader.currentOffset());
        }
    }
}
