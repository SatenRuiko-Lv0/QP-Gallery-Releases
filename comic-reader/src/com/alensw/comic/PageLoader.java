package com.alensw.comic;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.SparseArray;

import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Schedules only the visible pages and their immediate neighbours. */
final class PageLoader {
    private static final int AHEAD = 4;
    private static final int BEHIND = 2;
    private static final int MIB = 1024 * 1024;
    private static final ExecutorService DECODERS = Executors.newFixedThreadPool(2);

    interface Listener {
        void aspectReady(int index, float aspect);
        void imageReady(int index);
    }

    private final ContentResolver resolver;
    private final Resources resources;
    private final List<ComicReader.Page> pages;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SparseArray<Decode> running = new SparseArray<>(2);
    private final SparseArray<PageImage> visibleImages = new SparseArray<>();
    private final BitSet failed = new BitSet();
    private final BitSet attemptedPrefetch = new BitSet();
    private final LruCache<Integer, PageImage> cache;

    private boolean connected;
    private int width;
    private int first;
    private int last;
    private int direction = 1;

    PageLoader(Context context, List<ComicReader.Page> pages, int initialPage, Listener listener) {
        this.resolver = context.getContentResolver();
        this.resources = context.getResources();
        this.pages = pages;
        this.listener = listener;
        first = last = initialPage;
        int budget = (int) Math.max(1, Math.min(48L * MIB, Runtime.getRuntime().maxMemory() / 8));
        cache = new LruCache<Integer, PageImage>(budget) {
            @Override
            protected int sizeOf(Integer key, PageImage image) {
                return image.bytes;
            }
        };
    }

    void attach(int displayWidth) {
        connected = true;
        setWidth(displayWidth);
        schedule();
    }

    void detach() {
        connected = false;
        invalidateImages();
        failed.clear();
    }

    void setWidth(int displayWidth) {
        if (width != displayWidth) {
            width = displayWidth;
            invalidateImages();
            schedule();
        }
    }

    private void invalidateImages() {
        cache.evictAll();
        visibleImages.clear();
        attemptedPrefetch.clear();
        for (int i = 0; i < running.size(); i++) {
            running.valueAt(i).cancelled = true;
        }
    }

    void setViewport(int first, int last) {
        if (this.first == first && this.last == last) {
            return;
        }
        if (this.first != first) {
            direction = first > this.first ? 1 : -1;
        }
        this.first = first;
        this.last = last;
        attemptedPrefetch.clear();
        for (int i = visibleImages.size() - 1; i >= 0; i--) {
            if (!visible(visibleImages.keyAt(i))) {
                visibleImages.removeAt(i);
            }
        }
        for (int i = 0; i < running.size(); i++) {
            Decode decode = running.valueAt(i);
            if (!nearby(decode.index)) {
                decode.cancelled = true;
            }
        }
        schedule();
    }

    PageImage image(int index) {
        PageImage image = visibleImages.get(index);
        if (image == null) {
            image = cache.get(index);
            if (image != null && visible(index)) {
                visibleImages.put(index, image);
            }
        }
        return image;
    }

    boolean failed(int index) {
        return failed.get(index);
    }

    private boolean visible(int index) {
        return index >= first && index <= last;
    }

    private int nearbyStart() {
        return Math.max(0, first - (direction > 0 ? BEHIND : AHEAD));
    }

    private int nearbyEnd() {
        return Math.min(pages.size() - 1, last + (direction > 0 ? AHEAD : BEHIND));
    }

    private boolean nearby(int index) {
        return index >= nearbyStart() && index <= nearbyEnd();
    }

    private boolean needsImage(int index) {
        return running.get(index) == null && !failed(index) && image(index) == null;
    }

    private void schedule() {
        if (!connected || width <= 0) {
            return;
        }
        while (running.size() < 2) {
            int index = nextVisible();
            if (index == -1) {
                index = nextPrefetch();
                if (index == -1) {
                    return;
                }
                attemptedPrefetch.set(index);
            }
            Decode decode = new Decode(index, width);
            running.put(index, decode);
            DECODERS.execute(decode);
        }
    }

    private int nextVisible() {
        for (int index = first; index <= last; index++) {
            if (needsImage(index)) {
                return index;
            }
        }
        return -1;
    }

    private int nextPrefetch() {
        for (int i = 0; i < running.size(); i++) {
            // Leave a worker available for a page that scrolls into view.
            if (!visible(running.keyAt(i))) {
                return -1;
            }
        }
        int near = findPrefetch(direction, AHEAD);
        return near >= 0 ? near : findPrefetch(-direction, BEHIND);
    }

    private int findPrefetch(int step, int count) {
        if (nearbyBytes(step, count) >= prefetchBudget(step)) {
            return -1;
        }
        int start = step > 0 ? last + 1 : first - 1;
        for (int i = 0, index = start; i < count; i++, index += step) {
            if (index < 0 || index >= pages.size()) {
                break;
            }
            if (!attemptedPrefetch.get(index) && needsImage(index)) {
                return index;
            }
        }
        return -1;
    }

    private int prefetchBudget(int step) {
        int divisor = step == direction ? 2 : 4;
        return Math.min(cache.maxSize(), Math.max(8 * MIB, cache.maxSize() / divisor));
    }

    private int nearbyBytes(int step, int count) {
        int bytes = 0;
        int start = step > 0 ? last + 1 : first - 1;
        for (int i = 0, index = start; i < count && index >= 0 && index < pages.size();
                i++, index += step) {
            PageImage image = cache.get(index);
            if (image != null) {
                bytes += image.bytes;
            }
        }
        return bytes;
    }

    private void finish(Decode decode, PageImage image) {
        running.remove(decode.index);
        int index = decode.index;
        if (connected && !decode.cancelled && nearby(index)) {
            if (image == null) {
                failed.set(index);
            } else if (visible(index)) {
                visibleImages.put(index, image);
                cache.put(index, image);
            } else {
                int step = index < first ? -1 : 1;
                int count = step == direction ? AHEAD : BEHIND;
                if (image.bytes <= prefetchBudget(step) - nearbyBytes(step, count)) {
                    cache.put(index, image);
                }
            }
            if (visible(index)) {
                listener.imageReady(index);
            }
        }
        schedule();
    }

    private final class Decode implements Runnable {
        final int index;
        final int decodeWidth;
        volatile boolean cancelled;

        Decode(int index, int decodeWidth) {
            this.index = index;
            this.decodeWidth = decodeWidth;
        }

        @Override
        public void run() {
            PageImage image = cancelled ? null
                    : PageImage.decode(resolver, resources, pages.get(index).uri, decodeWidth, aspect -> {
                        if (cancelled) {
                            throw new CancellationException();
                        }
                        main.post(() -> {
                            if (connected && !cancelled) {
                                listener.aspectReady(index, (float) aspect);
                            }
                        });
                    });
            main.post(() -> finish(this, image));
        }
    }
}
