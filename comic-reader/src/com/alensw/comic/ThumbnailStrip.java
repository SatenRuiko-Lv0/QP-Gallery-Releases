package com.alensw.comic;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

final class ThumbnailStrip extends HorizontalScrollView {
    private static final ThreadPoolExecutor DECODERS = new ThreadPoolExecutor(
            2, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    private final List<ComicReader.Page> pages;
    private final IntConsumer selectPage;
    private final FrameLayout track;
    private final SparseArray<Thumbnail> visible = new SparseArray<>();
    private final ArrayDeque<Thumbnail> spare = new ArrayDeque<>();
    private final LruCache<Integer, Bitmap> cache = new LruCache<Integer, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(Integer page, Bitmap bitmap) {
            return bitmap.getAllocationByteCount();
        }
    };
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ContentResolver resolver;
    private final int cardWidth = dp(76);
    private final int stride = cardWidth + dp(7);
    private final int edge = dp(10);
    private final int imageHeight = dp(103);
    private final int trackWidth;
    private final boolean chinese;
    private boolean active;
    private int selected;

    ThumbnailStrip(Context context, List<ComicReader.Page> pages, IntConsumer selectPage) {
        super(context);
        this.pages = pages;
        this.selectPage = selectPage;
        resolver = context.getContentResolver();
        chinese = ComicMode.chinese(context);
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        track = new FrameLayout(context);
        trackWidth = 2 * edge + (pages.size() - 1) * stride + cardWidth;
        addView(track, new FrameLayout.LayoutParams(trackWidth, -1));
    }

    // HorizontalScrollView measures its child without a width constraint.
    @Override
    protected void measureChildWithMargins(View child, int parentWidth, int widthUsed,
            int parentHeight, int heightUsed) {
        child.measure(MeasureSpec.makeMeasureSpec(trackWidth, MeasureSpec.EXACTLY), parentHeight);
    }

    void setActive(boolean value) {
        active = value;
        if (value) {
            centerSelected();
        } else {
            for (int i = visible.size() - 1; i >= 0; i--) {
                recycle(i);
            }
        }
    }

    void select(int index) {
        if (selected == index) {
            return;
        }
        Thumbnail previous = visible.get(selected);
        selected = index;
        if (previous != null) {
            previous.highlight(false);
        }
        Thumbnail current = visible.get(selected);
        if (current != null) {
            current.highlight(true);
        }
        if (active) {
            centerSelected();
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (active) {
            post(this::centerSelected);
        }
    }

    @Override
    protected void onScrollChanged(int x, int y, int oldX, int oldY) {
        super.onScrollChanged(x, y, oldX, oldY);
        populate();
    }

    private void centerSelected() {
        if (!active || getWidth() == 0) {
            return;
        }
        int offset = edge + selected * stride + cardWidth / 2 - getWidth() / 2;
        scrollTo(Math.max(0, Math.min(offset, trackWidth - getWidth())), 0);
        populate();
    }

    private void populate() {
        if (!active || getWidth() == 0) {
            return;
        }
        int first = Math.max(0, (getScrollX() - edge) / stride - 1);
        int last = Math.min(pages.size() - 1, (getScrollX() + getWidth() - edge) / stride + 1);
        for (int i = visible.size() - 1; i >= 0; i--) {
            int index = visible.keyAt(i);
            if (index < first || index > last) {
                recycle(i);
            }
        }
        for (int index = first; index <= last; index++) {
            if (visible.get(index) == null) {
                Thumbnail thumb = spare.pollFirst();
                if (thumb == null) {
                    thumb = new Thumbnail();
                }
                FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(cardWidth, -1);
                position.leftMargin = edge + index * stride;
                track.addView(thumb, position);
                visible.put(index, thumb);
                thumb.bind(index);
            }
        }
    }

    private void recycle(int position) {
        Thumbnail thumb = visible.valueAt(position);
        visible.removeAt(position);
        thumb.cancel();
        track.removeView(thumb);
        spare.addLast(thumb);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private final class Thumbnail extends FrameLayout {
        private final ImageView image;
        private final TextView number;
        private FutureTask<Void> pending;
        private int index;
        private int binding;

        Thumbnail() {
            super(ThumbnailStrip.this.getContext());
            image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setClipToOutline(true);
            image.setBackground(rounded(0xff3b393f, 7));
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams imagePosition = new LayoutParams(-1, imageHeight);
            imagePosition.topMargin = dp(8);
            addView(image, imagePosition);

            number = new TextView(getContext());
            number.setTextColor(Color.WHITE);
            number.setTextSize(11);
            number.setGravity(Gravity.CENTER);
            number.setMinWidth(dp(23));
            number.setPadding(dp(4), 0, dp(4), 0);
            number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams label = new LayoutParams(-2, dp(18), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            label.topMargin = dp(114);
            addView(number, label);
            setFocusable(true);
            setOnClickListener(view -> selectPage.accept(index));
        }

        void bind(int page) {
            index = page;
            setContentDescription(chinese ? "第 " + (page + 1) + " 页" : "Page " + (page + 1));
            number.setText(Integer.toString(page + 1));
            highlight(page == selected);
            Bitmap bitmap = cache.get(page);
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
                return;
            }
            int token = ++binding;
            pending = new FutureTask<>(() -> {
                Bitmap decoded = PageImage.thumbnail(resolver, getResources(), pages.get(page).uri, imageHeight);
                main.post(() -> {
                    if (binding == token) {
                        pending = null;
                        if (decoded != null) {
                            cache.put(page, decoded);
                            image.setImageBitmap(decoded);
                        }
                    }
                });
                return null;
            });
            DECODERS.execute(pending);
        }

        void highlight(boolean highlighted) {
            setSelected(highlighted);
            number.setBackground(highlighted ? rounded(ComicControls.ACCENT, 4) : null);
        }

        void cancel() {
            binding++;
            if (pending != null) {
                pending.cancel(false);
                DECODERS.remove(pending);
                pending = null;
            }
            image.setImageDrawable(null);
        }
    }
}
