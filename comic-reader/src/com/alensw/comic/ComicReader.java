package com.alensw.comic;

import android.app.ActionBar;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
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
    private final View endSpace;
    private final String failure;
    private int current;
    private final int initialPage;
    private float initialOffset;
    private boolean chromeActive;
    private boolean chromeVisible;
    private boolean acceptTap;
    private int previousBarsBehavior;

    ComicReader(cx activity, List<Page> pages, int initialPage, float initialOffset) {
        super(activity);
        Context context = activity;
        this.activity = activity;
        this.pages = pages;
        this.current = initialPage;
        this.initialPage = initialPage;
        this.initialOffset = initialOffset;
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
        // A short last page can reach the top when it was the image tapped in the album.
        endSpace = new View(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int width = MeasureSpec.getSize(widthSpec);
                int height = pageHeight(pages.get(pages.size() - 1), width);
                setMeasuredDimension(width, Math.max(0, list.getHeight() - height));
            }
        };
        list.addFooterView(endSpace, null, false);
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
        badge.setMargins(0, 0, dp(8), dp(8));
        addView(counter, badge);
        setOnApplyWindowInsetsListener((view, insets) -> {
            int right = dp(8) + insets.getStableInsetRight();
            int bottom = dp(8) + insets.getStableInsetBottom();
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
                    showChrome(false);
                }
                if (state == SCROLL_STATE_TOUCH_SCROLL) {
                    ComicReader.this.initialOffset = 0;
                }
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                if (visible > 0) {
                    int page = Math.min(first, pages.size() - 1);
                    loader.setViewport(page, Math.min(first + visible - 1, pages.size() - 1));
                    if (page != current) {
                        updateCounter(page);
                    }
                }
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loader.attach(getWidth());
        requestApplyInsets();
    }

    @Override
    protected void onDetachedFromWindow() {
        loader.detach();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        loader.setWidth(width);
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
        if (index == pages.size() - 1) {
            endSpace.requestLayout();
        }
        if (index == current && index == initialPage && initialOffset > 0) {
            int offset = Math.round(pageHeight(page, list.getWidth()) * initialOffset);
            initialOffset = 0;
            list.setSelectionFromTop(index, -offset);
        }
    }

    void resetChrome() {
        acceptTap = false;
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
            if (visible) {
                String title = currentPage().title;
                activity.setTitle(title == null || title.isEmpty()
                        ? (ComicMode.chinese(activity) ? "漫画阅读" : "Comic reader") : title);
            }
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
            if (chromeVisible) {
                bar.show();
            } else {
                bar.hide();
            }
        }
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
        if (initialOffset > 0 && current == initialPage) {
            return initialOffset;
        }
        View first = list.getChildAt(0);
        return first == null ? 0 : Math.max(0, -first.getTop()) / (float) Math.max(1, first.getHeight());
    }

    private void updateCounter(int page) {
        current = page;
        counter.setText((page + 1) + " / " + pages.size());
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
