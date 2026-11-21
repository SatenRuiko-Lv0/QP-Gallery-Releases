package com.alensw.comic;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;
import java.util.function.IntConsumer;

final class ComicControls {
    private static final int PANEL = 0xe018181b;
    static final int ACCENT = 0xff7462b4;

    private final FrameLayout reader;
    private final LinearLayout top;
    private final LinearLayout bottom;
    private final TextView title;
    private final TextView pageNumber;
    private final ThumbnailStrip thumbnails;
    private final SeekBar progress;
    private final ToolbarButton clock;
    private final boolean chinese;
    private boolean scrubbing;

    ComicControls(FrameLayout reader, List<ComicReader.Page> pages, IntConsumer select,
            Runnable back, Runnable toggleAuto, Runnable settings) {
        this.reader = reader;
        Context context = reader.getContext();
        chinese = ComicMode.chinese(context);
        top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(PANEL);
        top.setClickable(true);
        top.addView(button(Icon.BACK, chinese ? "返回" : "Back", back), buttonSize());

        title = label(19);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        clock = button(Icon.CLOCK, "", toggleAuto);
        setAutoScroll(false);
        top.addView(clock, buttonSize());
        top.addView(button(Icon.SETTINGS, chinese ? "阅读设置" : "Reader settings", settings), buttonSize());
        reader.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setBackgroundColor(PANEL);
        bottom.setClickable(true);
        thumbnails = new ThumbnailStrip(context, pages, select);
        bottom.addView(thumbnails, new LinearLayout.LayoutParams(-1, dp(134)));

        LinearLayout seekRow = new LinearLayout(context);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        seekRow.setPadding(dp(16), 0, dp(16), 0);
        pageNumber = label(14);
        pageNumber.setGravity(Gravity.CENTER);
        TextView total = label(14);
        total.setGravity(Gravity.CENTER);
        total.setText(Integer.toString(pages.size()));
        int labelWidth = Math.max(dp(26), (int) Math.ceil(total.getPaint().measureText(total.getText().toString())));
        seekRow.addView(pageNumber, new LinearLayout.LayoutParams(labelWidth, -1));

        progress = new SeekBar(context);
        progress.setContentDescription(chinese ? "跳转到页码" : "Jump to page");
        progress.setMax(pages.size() - 1);
        progress.setEnabled(pages.size() > 1);
        progress.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        progress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xff99969e));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                scrubbing = true;
            }

            @Override
            public void onProgressChanged(SeekBar bar, int index, boolean fromUser) {
                if (fromUser) {
                    displayPage(index);
                    if (!scrubbing) {
                        select.accept(index);
                    }
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                scrubbing = false;
                select.accept(bar.getProgress());
            }
        });
        seekRow.addView(progress, new LinearLayout.LayoutParams(0, -1, 1));
        seekRow.addView(total, new LinearLayout.LayoutParams(labelWidth, -1));
        bottom.addView(seekRow, new LinearLayout.LayoutParams(-1, dp(48)));
        reader.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setVisible(false);
    }

    void setVisible(boolean visible) {
        top.setVisibility(visible ? View.VISIBLE : View.GONE);
        bottom.setVisibility(visible ? View.VISIBLE : View.GONE);
        thumbnails.setActive(visible);
    }

    void setInsets(int left, int topInset, int right, int bottomInset) {
        top.setPadding(left, topInset, right, 0);
        bottom.setPadding(left, 0, right, bottomInset);
    }

    void showPage(int index, String name) {
        title.setText(name);
        if (!scrubbing) {
            progress.setProgress(index);
            displayPage(index);
        }
    }

    void setAutoScroll(boolean active) {
        clock.setSelected(active);
        clock.setContentDescription(chinese ? (active ? "停止自动滚动" : "自动滚动")
                : (active ? "Stop auto scroll" : "Auto scroll"));
        clock.invalidate();
    }

    private void displayPage(int index) {
        pageNumber.setText(Integer.toString(index + 1));
        thumbnails.select(index);
    }

    private LinearLayout.LayoutParams buttonSize() {
        return new LinearLayout.LayoutParams(dp(48), dp(52));
    }

    private TextView label(int size) {
        TextView view = new TextView(reader.getContext());
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        return view;
    }

    private ToolbarButton button(Icon icon, String description, Runnable action) {
        ToolbarButton button = new ToolbarButton(reader.getContext(), icon);
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ffffff),
                null, new ColorDrawable(Color.WHITE)));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private int dp(int value) {
        return Math.round(value * reader.getResources().getDisplayMetrics().density);
    }

    private enum Icon { BACK, CLOCK, SETTINGS }

    private static final class ToolbarButton extends View {
        private final Icon icon;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ToolbarButton(Context context, Icon icon) {
            super(context);
            this.icon = icon;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.translate(getWidth() / 2f, getHeight() / 2f);
            float scale = getResources().getDisplayMetrics().density;
            canvas.scale(scale, scale);
            paint.setColor(isSelected() ? 0xffc6b6ff : Color.WHITE);
            paint.setStrokeWidth(2);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            switch (icon) {
                case BACK:
                    canvas.drawLine(-8, 0, 8, 0, paint);
                    canvas.drawLine(-8, 0, -1, -7, paint);
                    canvas.drawLine(-8, 0, -1, 7, paint);
                    break;
                case CLOCK:
                    canvas.drawCircle(0, 0, 9, paint);
                    canvas.drawLine(0, -5, 0, 0, paint);
                    canvas.drawLine(0, 0, 4, 3, paint);
                    break;
                case SETTINGS:
                    canvas.drawCircle(0, 0, 6.5f, paint);
                    canvas.drawCircle(0, 0, 2.6f, paint);
                    for (int tooth = 0; tooth < 8; tooth++) {
                        canvas.drawLine(0, -7, 0, -9.5f, paint);
                        canvas.rotate(45);
                    }
                    break;
            }
            canvas.restore();
        }
    }
}
