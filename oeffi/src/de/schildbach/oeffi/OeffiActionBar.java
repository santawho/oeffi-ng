/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.oeffi;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Handler;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import de.schildbach.oeffi.util.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import de.schildbach.oeffi.util.ToggleImageButton;
import de.schildbach.oeffi.util.ViewUtils;

public class OeffiActionBar extends FrameLayout implements View.OnLayoutChangeListener {
    public static OeffiActionBar findActionBar(final View parent) {
        return (OeffiActionBar) parent.findViewById(R.id.action_bar).getParent();
    }

    private final Context context;
    private final Resources res;
    private final LayoutInflater inflater;

    private Rect padding;
    private FrameLayout contentView;
    private ViewGroup[] buttonStrips;
    private View backButtonView;
    private View menuButtonView;
    private ViewGroup titlesGroup;
    private TextView primaryTitleView;
    private TextView secondaryTitleView;
    private ImageButton progressButton;

    private CharSequence primaryTitle;
    private CharSequence secondaryTitle;
    private int currentButtonStripToAddTo = 0;
    private OnClickListener titlesClicklistener;

    private boolean progressAlwaysVisible = false;
    private int progressCount = 0;
    private Animation progressAnimation = null;
    private Handler handler = new Handler();

    public OeffiActionBar(final Context context) {
        this(context, null);
    }

    public OeffiActionBar(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        this.context = context;
        this.res = getResources();
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.action_bar_frame, this);

        buttonStrips = new ViewGroup[2];
        buttonStrips[0] = (ViewGroup) inflater.inflate(R.layout.action_bar_button_strip, null);
        buttonStrips[1] = (ViewGroup) inflater.inflate(R.layout.action_bar_button_strip, null);

        ViewCompat.setOnApplyWindowInsetsListener(this, (v, windowInsets) -> {
            final Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    padding.left + insets.left,
                    padding.top + windowInsets.getSystemWindowInsetTop(),
                    padding.right + insets.right,
                    padding.bottom);
            return windowInsets;
        });

        addOnLayoutChangeListener(this);
    }

    public void addView(final View view) {
        final LinearLayout frameView = findViewById(R.id.action_bar);
        frameView.addView(view);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        padding = new Rect(getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom());

        contentView = findViewById(R.id.action_bar_content);
        backButtonView = findViewById(R.id.action_bar_back_button);
        menuButtonView = findViewById(R.id.action_bar_menu_button);
        progressButton = findViewById(R.id.action_bar_progress_button);
    }

    public void setDrawer(final OnClickListener onClickListener) {
        menuButtonView.setOnClickListener(onClickListener);
        menuButtonView.setVisibility(View.VISIBLE);
    }

    public void setBack(final OnClickListener onClickListener) {
        if (onClickListener == null) {
            backButtonView.setVisibility(View.GONE);
        } else {
            backButtonView.setOnClickListener(onClickListener);
            backButtonView.setVisibility(View.VISIBLE);
        }
    }

    private static int LAYOUT_IDS[] = new int[] {
            R.layout.action_bar_content_single_row,
            R.layout.action_bar_content_single_row_title_above,
            R.layout.action_bar_content_two_rows,
            R.layout.action_bar_content_two_rows_title_above
    };

    private int attemptLayoutId = -1;

    public void tryContentLayout() {
        setContentLayout(LAYOUT_IDS[attemptLayoutId]);
    }

    public void setContentLayout(final int layoutId) {
        if (contentView.getChildCount() > 0)
            contentView.removeViewAt(0);

        inflater.inflate(layoutId, contentView);

        titlesGroup = contentView.findViewById(R.id.action_bar_titles);
        primaryTitleView = titlesGroup.findViewById(R.id.action_bar_primary_title);
        secondaryTitleView = titlesGroup.findViewById(R.id.action_bar_secondary_title);
        setPrimaryTitle(primaryTitle);
        setSecondaryTitle(secondaryTitle);
        setTitlesOnClickListener(titlesClicklistener);

        for (final ViewGroup buttonStrip : buttonStrips) {
            if (buttonStrip != null) {
                final ViewGroup parent = (ViewGroup) buttonStrip.getParent();
                if (parent != null)
                    parent.removeView(buttonStrip);
            }
        }

        final FrameLayout b1 = contentView.findViewById(R.id.action_bar_button_strip_1);
        final FrameLayout b2 = contentView.findViewById(R.id.action_bar_button_strip_2);
        b1.addView(buttonStrips[0]);
        b2.addView(buttonStrips[1]);
    }

    @Override
    public void onLayoutChange(
            final View v,
            final int left, final int top, final int right, final int bottom,
            final int oldLeft, final int oldTop, final int oldRight, final int oldBottom) {
        if (attemptLayoutId < 0 || attemptLayoutId >= LAYOUT_IDS.length - 1) {
            attemptLayoutId = -1;
            return;
        }

        boolean textTruncated = false;
        if (primaryTitleView == null) {
            attemptLayoutId = -1;
            return;
        }
        final Layout layout = primaryTitleView.getLayout();
        final int lineCount = layout.getLineCount();
        if (lineCount > 0) {
            final int ellipsisCount = layout.getEllipsisCount(lineCount - 1);
            textTruncated = ellipsisCount > 0;
        }

        boolean buttonsTruncated = false;
        if (!textTruncated) {
            final View container = findViewById(R.id.action_bar_button_horizontal);
            if (container != null) {
                final ViewParent parent = container.getParent();
                if (parent != null) {
                    final int containerWidth = container.getWidth();
                    final int parentWidth = ((View) parent).getWidth();
                    buttonsTruncated = containerWidth >= parentWidth;
                }
            }
        }

        if (textTruncated || buttonsTruncated) {
            attemptLayoutId += 1;
            tryContentLayout();
        }
    }

    public void concludeSetup() {
        attemptLayoutId = 0;
        tryContentLayout();
    }

    public void setPrimaryTitle(final CharSequence title) {
        primaryTitle = title;
        if (primaryTitleView != null)
            primaryTitleView.setText(primaryTitle);
    }

    public void setPrimaryTitle(final int titleRes) {
        setPrimaryTitle(context.getString(titleRes));
    }

    public void setSecondaryTitle(final CharSequence title) {
        secondaryTitle = title;
        if (secondaryTitleView != null) {
            secondaryTitleView.setText(secondaryTitle);
            ViewUtils.setVisibility(secondaryTitleView, secondaryTitle != null);
        }
    }

    public void setSecondaryTitle(final int titleRes) {
        setSecondaryTitle(context.getString(titleRes));
    }

    public void setTitlesOnClickListener(final OnClickListener listener) {
        titlesClicklistener = listener;
        if (titlesGroup != null) {
            titlesGroup.setOnClickListener(listener);
            titlesGroup.setFocusable(listener != null);
            titlesGroup.setClickable(listener != null);
        }
    }

//    public void swapTitles() {
//        final View view = titlesGroup.getChildAt(0);
//        titlesGroup.removeViewAt(0);
//        titlesGroup.addView(view, 1);
//    }

    public void setCustomTitles(final int layoutRes) {
        final View view = inflater.inflate(layoutRes, titlesGroup, false);
        titlesGroup.removeViewAt(0);
        titlesGroup.addView(view, 0);
        primaryTitleView = null;
        secondaryTitleView = null;
    }

    public void addButtonSplit() {
        if (currentButtonStripToAddTo < buttonStrips.length - 1)
            currentButtonStripToAddTo += 1;
    }

    private LinearLayout.LayoutParams getButtonLayoutParams() {
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, // res.getDimensionPixelSize(R.dimen.action_bar_button_width),
                LayoutParams.MATCH_PARENT,
                0f);
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        final int paddingHorizontal = res.getDimensionPixelSize(R.dimen.action_bar_padding_horizontal);
        final int paddingVertical = res.getDimensionPixelSize(R.dimen.action_bar_padding_vertical);
        layoutParams.setMargins(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
        return layoutParams;
    }

    private <TImageButton extends ImageButton> TImageButton addButton(
            final TImageButton button, final int drawableRes, final int descriptionRes) {
        button.setImageResource(drawableRes);
        button.setScaleType(ScaleType.CENTER);
        if (descriptionRes != 0) {
            final String description = context.getString(descriptionRes);
            button.setContentDescription(description);
            button.setTooltipText(description);
        }
        // button.setMinimumHeight(res.getDimensionPixelSize(R.dimen.action_bar_height));
        buttonStrips[currentButtonStripToAddTo]
                .addView(button, 0, getButtonLayoutParams());
        return button;
    }

    public ImageButton addButton(final int drawableRes, final int descriptionRes) {
        return addButton(new AppCompatImageButton(context), drawableRes, descriptionRes);
    }

    public ToggleImageButton addToggleButton(final int drawableRes, final int descriptionRes) {
        return addButton(new ToggleImageButton(context), drawableRes, descriptionRes);
    }

    public View addProgressButton() {
        progressAlwaysVisible = true;
        progressButton.setVisibility(View.VISIBLE);
        progressButton.setTooltipText(progressButton.getContentDescription());
        return getProgressButton();
    }

    public View getProgressButton() {
        return progressButton;
    }

    public void startProgress() {
        if (progressCount++ == 0) {
            handler.removeCallbacksAndMessages(null);
            handler.post(() -> {
                progressButton.setVisibility(View.VISIBLE);
                if (progressAnimation == null) {
                    progressAnimation = AnimationUtils.loadAnimation(context, R.anim.rotate);
                    progressButton.startAnimation(progressAnimation);
                }
            });
        }
    }

    public void stopProgress() {
        if (--progressCount <= 0) {
            progressCount = 0;
            handler.postDelayed(() -> {
                if (progressAnimation != null) {
                    progressButton.clearAnimation();
                    progressAnimation = null;
                }
                if (!progressAlwaysVisible)
                    progressButton.setVisibility(View.GONE);
            }, 200);
        }
    }

    public int getProgressCount() {
        return progressCount;
    }

    public void overflow(final int menuResId, final PopupMenu.OnMenuItemClickListener menuItemClickListener) {
        final View overflowButton = findViewById(R.id.action_bar_overflow_button);
        overflowButton.setVisibility(View.VISIBLE);
        overflowButton.setOnClickListener(v -> {
            final PopupMenu overflowMenu = new PopupMenu(context, menuResId, v);
            overflowMenu.setOnMenuItemClickListener(menuItemClickListener);
            overflowMenu.show();
        });
    }
}
