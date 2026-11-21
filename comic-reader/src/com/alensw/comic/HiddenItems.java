package com.alensw.comic;

import java.io.File;

public final class HiddenItems {
    private static final int SCAN_SUBFOLDERS = 1;
    private static final int SKIP_HIDDEN = 8;

    private HiddenItems() {}

    public static boolean includeInScan(int flags, boolean showHidden) {
        return (flags & SKIP_HIDDEN) == 0 && ((flags & SCAN_SUBFOLDERS) != 0 || showHidden);
    }

    public static boolean isHidden(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        File file = new File(path).getAbsoluteFile().toPath().normalize().toFile();
        for (File item = file; item != null; item = item.getParentFile()) {
            if (item.getName().startsWith(".") || new File(item, ".nomedia").exists()) {
                return true;
            }
        }
        return false;
    }
}
