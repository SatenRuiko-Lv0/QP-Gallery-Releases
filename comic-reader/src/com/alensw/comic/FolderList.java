package com.alensw.comic;

import com.alensw.a.e;

import java.io.File;
import java.util.ArrayList;

public final class FolderList {
    private FolderList() {}

    public static int listChildren(e folder, boolean includeHidden) {
        if (folder.h == null) {
            folder.h = new ArrayList<>();
        } else {
            folder.h.clear();
        }

        // Alpha 2's ARM64 fsScanFolders returns zero without enumerating the directory.
        File[] directories;
        try {
            directories = folder.listFiles(file -> file.isDirectory()
                    && (includeHidden || !file.getName().startsWith(".")));
        } catch (SecurityException inaccessible) {
            return 0;
        }
        if (directories == null) {
            return 0;
        }
        for (File directory : directories) {
            folder.a('D', 0, 0L, directory.getName());
        }
        return folder.h.size();
    }
}
