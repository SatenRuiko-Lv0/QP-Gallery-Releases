package com.alensw.comic;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.alensw.ui.c.cx;

import java.util.List;

final class ComicReader extends FrameLayout {
    private static final String AUTO_SCROLL_SECONDS = "comic_auto_scroll_seconds";
    private static final int[] AUTO_INTERVALS = {2, 3, 5, 8, 12};

    static final class Page {
        final Uri uri;
        final String title;
        final int sourceIndex;
        float aspect;

        Page(Uri uri, String title, int sourceIndex) {
            this.uri = uri;
            this.title = title;
            this.sourceIndex = sourceIndex;
        }
    }

    private final List<Page> pages;
    private final PageLoader loader;
    private final cx activity;
    private final ListView list;
    private final TextView counter;
    private final ComicControls controls;
    private final String failure;
    private int current;
    private int anchorPage;
    // NaN anchors a newly selected image. A saved offset is relative to its page height.
    private float anchorOffset;
    private boolean hasScrolled;
    private boolean chromeActive;
    private boolean chromeVisible;
    private boolean acceptTap;
    private boolean autoScroll;
    private final Runnable advanceAuto = this::advanceAutoScroll;
    private int autoInterval;
    private int previousBarsBehavior;

    ComicReader(cx activity, List<Page> pages, int initialPage, float initialOffset) {
        super(activity);
        Context context = activity;
        this.activity = activity;
        this.pages = pages;
        this.current = initialPage;
        anchorPage = initialPage;
        anchorOffset = initialOffset;
        autoInterval = preferences().getInt(AUTO_SCROLL_SECONDS, 3);
        loader = new PageLoader(context, pages, initialPage, new PageLoader.Listener() {
            @Override
            public void aspectReady(int index, float aspect) {
                updatePageAspect(index, aspect);
            }

            @Override
            public void imageReady(int index) {
                PageView view = visibleView(index);
                if (view != null) {
                    view.refreshImage();
                }
            }
        });
        failure = ComicMode.chinese(context) ? "无法加载图片" : "Could not load image";
        setBackgroundColor(Color.BLACK);

        list = new ListView(context);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setStackFromBottom(true);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setFocusableInTouchMode(true);
        list.setVerticalScrollBarEnabled(false);
        list.setSelector(android.R.color.transparent);
        GestureDetector taps = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                acceptTap = true;
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent event) {
                if (acceptTap) {
                    showChrome(!chromeVisible);
                }
                return acceptTap;
            }

            @Override
            public boolean onScroll(MotionEvent start, MotionEvent end, float dx, float dy) {
                acceptTap = false;
                setAutoScroll(false);
                showChrome(false);
                return false;
            }
        });
        taps.setOnDoubleTapListener(null);
        list.setOnTouchListener((view, event) -> {
            taps.onTouchEvent(event);
            return false;
        });
        list.setRecyclerListener(view -> {
            if (view instanceof PageView) {
                ((PageView) view).clear();
            }
        });
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        counter = new TextView(context);
        counter.setGravity(Gravity.CENTER);
        counter.setTextDirection(View.TEXT_DIRECTION_LTR);
        counter.setTextColor(Color.WHITE);
        counter.setTextSize(10);
        counter.setIncludeFontPadding(false);
        counter.setPadding(dp(5), dp(3), dp(5), dp(3));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xb3595959);
        background.setCornerRadius(dp(2));
        counter.setBackground(background);
        LayoutParams badge = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.RIGHT | Gravity.BOTTOM);
        badge.setMargins(0, 0, dp(8), dp(6));
        addView(counter, badge);
        controls = new ComicControls(this, pages, this::jumpToPage, activity::onBackPressed,
                () -> setAutoScroll(!autoScroll), this::showReaderSettings);
        setOnApplyWindowInsetsListener((view, insets) -> {
            int right = dp(8) + insets.getStableInsetRight();
            WindowInsets rootInsets = view.getRootWindowInsets();
            WindowInsets window = rootInsets == null ? insets : rootInsets;
            int bottomInset = Build.VERSION.SDK_INT >= 30
                    ? SystemBars.bottomInset(window) : window.getSystemWindowInsetBottom();
            int topInset = Build.VERSION.SDK_INT >= 30
                    ? SystemBars.topInset(window) : window.getSystemWindowInsetTop();
            controls.setInsets(window.getStableInsetLeft(), topInset, window.getStableInsetRight(), bottomInset);
            int bottom = dp(6) + bottomInset;
            if (badge.rightMargin != right || badge.bottomMargin != bottom) {
                badge.rightMargin = right;
                badge.bottomMargin = bottom;
                counter.setLayoutParams(badge);
            }
            if (Build.VERSION.SDK_INT >= 30 && chromeActive && !chromeVisible
                    && SystemBars.areVisible(activity, insets)) {
                post(() -> {
                    if (chromeActive && !chromeVisible) {
                        SystemBars.apply(activity, false);
                    }
                });
            }
            return insets;
        });
        updateCounter(initialPage);

        list.setAdapter(new PagesAdapter());
        list.setSelectionFromTop(initialPage, 0);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int state) {
                if (state != SCROLL_STATE_IDLE) {
                    hasScrolled = true;
                    showChrome(false);
                }
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                if (visible > 0) {
                    int last = first + visible - 1;
                    loader.setViewport(first, last);
                    int page = currentPageInViewport(first, last);
                    if (page != current) {
                        updateCounter(page);
                    }
                }
            }
        });
    }

    private int currentPageInViewport(int first, int last) {
        if (!hasScrolled && anchorPage >= first && anchorPage <= last) {
            return anchorPage;
        }
        if (last == pages.size() - 1 && !list.canScrollList(1)) {
            return last;
        }
        if (first == 0 && !list.canScrollList(-1)) {
            return first;
        }
        int center = list.getHeight() / 2;
        for (int i = first; i < last; i++) {
            if (list.getChildAt(i - first).getBottom() > center) {
                return i;
            }
        }
        return last;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loader.attach(getWidth());
        requestApplyInsets();
    }

    @Override
    protected void onDetachedFromWindow() {
        setAutoScroll(false);
        controls.setVisible(false);
        loader.detach();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) {
            setAutoScroll(false);
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        positionAnchor();
        loader.setWidth(width);
    }

    private void positionAnchor() {
        Page page = pages.get(anchorPage);
        if (hasScrolled || getWidth() == 0 || getHeight() == 0 || page.aspect == 0) {
            return;
        }
        int height = pageHeight(page, getWidth());
        // Leave the start of a tall page visible. ListView clamps to the album boundaries.
        int top = Float.isNaN(anchorOffset)
                ? Math.max(0, (getHeight() - height) / 2)
                : Math.min(getHeight() - 1, -Math.round(height * anchorOffset));
        list.setSelectionFromTop(anchorPage, top);
    }

    private void jumpToPage(int page) {
        setAutoScroll(false);
        anchorPage = page;
        anchorOffset = Float.NaN;
        hasScrolled = false;
        updateCounter(page);
        loader.setViewport(page, page);
        if (pages.get(page).aspect == 0) {
            list.setSelectionFromTop(page, 0);
        } else {
            positionAnchor();
        }
    }

    private PageView visibleView(int index) {
        View child = list.getChildAt(index - list.getFirstVisiblePosition());
        if (child instanceof PageView) {
            PageView view = (PageView) child;
            if (view.index == index) {
                return view;
            }
        }
        return null;
    }

    private void updatePageAspect(int index, float aspect) {
        Page page = pages.get(index);
        if (aspect <= 0 || !Float.isFinite(aspect) || page.aspect == aspect) {
            return;
        }
        page.aspect = aspect;
        PageView view = visibleView(index);
        if (view != null) {
            view.requestLayout();
        }
        if (index == anchorPage || view != null) {
            positionAnchor();
        }
    }

    void resetChrome() {
        acceptTap = false;
        setAutoScroll(false);
        if (Build.VERSION.SDK_INT >= 30 && !chromeActive) {
            previousBarsBehavior = SystemBars.behavior(activity);
        }
        chromeActive = true;
        chromeVisible = false;
        activity.a((ViewGroup) null);
        applyChrome();
    }

    void releaseChrome() {
        if (!chromeActive) {
            return;
        }
        chromeActive = false;
        acceptTap = false;
        setAutoScroll(false);
        controls.setVisible(false);
        if (Build.VERSION.SDK_INT >= 30) {
            SystemBars.release(activity, previousBarsBehavior);
        }
        ActionBar bar = activity.getActionBar();
        if (bar != null) {
            bar.show();
        }
    }

    private void showChrome(boolean visible) {
        if (chromeActive && visible != chromeVisible) {
            chromeVisible = visible;
            applyChrome();
        }
    }

    private void applyChrome() {
        activity.a(chromeVisible);
        if (Build.VERSION.SDK_INT >= 30) {
            SystemBars.apply(activity, chromeVisible);
        }
        ActionBar bar = activity.getActionBar();
        if (bar != null) {
            bar.hide();
        }
        if (chromeVisible) {
            updateControls();
        }
        controls.setVisible(chromeVisible);
        counter.setVisibility(chromeVisible ? View.GONE : View.VISIBLE);
    }

    private void updateControls() {
        String title = currentPage().title;
        controls.showPage(current, title == null || title.isEmpty()
                ? (ComicMode.chinese(activity) ? "漫画阅读" : "Comic reader") : title);
    }

    private SharedPreferences preferences() {
        return PreferenceManager.getDefaultSharedPreferences(activity);
    }

    private void setAutoScroll(boolean enabled) {
        if (enabled == autoScroll) {
            return;
        }
        autoScroll = enabled;
        controls.setAutoScroll(enabled);
        if (enabled) {
            showChrome(false);
            postDelayed(advanceAuto, autoInterval * 1000L);
        } else {
            removeCallbacks(advanceAuto);
        }
    }

    private void advanceAutoScroll() {
        if (!isShown() || !list.canScrollList(1)) {
            setAutoScroll(false);
            return;
        }
        if (!chromeVisible) {
            list.smoothScrollBy(Math.round(list.getHeight() * 0.82f), 360);
        }
        postDelayed(advanceAuto, autoInterval * 1000L);
    }

    private void showReaderSettings() {
        setAutoScroll(false);
        boolean zh = ComicMode.chinese(activity);
        String[] labels = new String[AUTO_INTERVALS.length];
        int selected = -1;
        for (int i = 0; i < labels.length; i++) {
            int seconds = AUTO_INTERVALS[i];
            labels[i] = seconds + (zh ? " 秒" : " s");
            if (seconds == autoInterval) {
                selected = i;
            }
        }
        new AlertDialog.Builder(activity)
                .setTitle(zh ? "自动滚动间隔" : "Auto-scroll interval")
                .setSingleChoiceItems(labels, selected, (dialog, index) -> {
                    autoInterval = AUTO_INTERVALS[index];
                    preferences().edit().putInt(AUTO_SCROLL_SECONDS, autoInterval).apply();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static final class SystemBars {
        static int behavior(cx activity) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            return controller == null ? WindowInsetsController.BEHAVIOR_DEFAULT
                    : controller.getSystemBarsBehavior();
        }

        static boolean areVisible(cx activity, WindowInsets insets) {
            return insets.isVisible(WindowInsets.Type.statusBars())
                    || (hideNavigation(activity) && insets.isVisible(WindowInsets.Type.navigationBars()));
        }

        static int bottomInset(WindowInsets insets) {
            return insets.getInsets(WindowInsets.Type.navigationBars()
                    | WindowInsets.Type.displayCutout()).bottom;
        }

        static int topInset(WindowInsets insets) {
            return insets.getInsets(WindowInsets.Type.statusBars()
                    | WindowInsets.Type.displayCutout()).top;
        }

        static void apply(cx activity, boolean visible) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller == null) {
                return;
            }
            if (visible) {
                controller.show(types(activity));
            } else {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(types(activity));
            }
        }

        static void release(cx activity, int previousBehavior) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(previousBehavior);
                controller.show(types(activity));
            }
        }

        private static int types(cx activity) {
            return WindowInsets.Type.statusBars()
                    | (hideNavigation(activity) ? WindowInsets.Type.navigationBars() : 0);
        }

        private static boolean hideNavigation(cx activity) {
            return (activity.getWindow().getDecorView().getSystemUiVisibility()
                    & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0;
        }
    }

    Page currentPage() {
        return pages.get(current);
    }

    float currentOffset() {
        if (!hasScrolled && current == anchorPage) {
            return anchorOffset;
        }
        View page = visibleView(current);
        return page == null ? 0 : -page.getTop() / (float) Math.max(1, page.getHeight());
    }

    private void updateCounter(int page) {
        current = page;
        counter.setText((page + 1) + " / " + pages.size());
        if (chromeVisible) {
            updateControls();
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private int pageHeight(Page page, int width) {
        if (page.aspect == 0) {
            return Math.max(dp(96), list.getHeight());
        }
        return Math.max(1, Math.min(250000, Math.round(width * page.aspect)));
    }

    private final class PagesAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return pages.size();
        }

        @Override
        public Page getItem(int position) {
            return pages.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).sourceIndex;
        }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            PageView view = recycled instanceof PageView ? (PageView) recycled : new PageView();
            view.bind(getItem(position), position);
            return view;
        }
    }

    private final class PageView extends FrameLayout {
        private final ImageView image;
        private final TextView error;
        private Page page;
        private int index = -1;
        private PageImage displayed;

        PageView() {
            super(ComicReader.this.getContext());
            image = new ImageView(getContext());
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            error = new TextView(getContext());
            error.setText(failure);
            error.setTextColor(0xffaaaaaa);
            error.setGravity(Gravity.CENTER);
            error.setVisibility(GONE);
            addView(error, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

        void bind(Page next, int index) {
            if (page == next) {
                return;
            }
            clear();
            page = next;
            this.index = index;
            refreshImage();
        }

        void refreshImage() {
            if (index < 0) {
                return;
            }
            PageImage result = loader.image(index);
            if (result == null) {
                error.setVisibility(loader.failed(index) ? VISIBLE : GONE);
                return;
            }
            if (displayed != result) {
                stopAnimation();
                Drawable drawable = result.drawable(getResources());
                image.setImageDrawable(drawable);
                displayed = result;
                error.setVisibility(GONE);
                if (drawable instanceof Animatable && isAttachedToWindow()) {
                    ((Animatable) drawable).start();
                }
            }
        }

        void clear() {
            stopAnimation();
            image.setImageDrawable(null);
            error.setVisibility(GONE);
            page = null;
            index = -1;
            displayed = null;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = page == null ? 0 : pageHeight(page, width);
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            refreshImage();
            if (image.getDrawable() instanceof Animatable) {
                ((Animatable) image.getDrawable()).start();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            stopAnimation();
            super.onDetachedFromWindow();
        }

        private void stopAnimation() {
            if (image.getDrawable() instanceof Animatable) {
                ((Animatable) image.getDrawable()).stop();
            }
        }
    }
}
