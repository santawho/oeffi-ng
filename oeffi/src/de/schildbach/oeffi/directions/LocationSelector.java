package de.schildbach.oeffi.directions;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;

public class LocationSelector extends LinearLayout {
    private static Logger log = LoggerFactory.getLogger(LocationSelector.class);

    private final Context context;

    private FrameLayout startItem, endItem;
    private int startIndex = -1, endIndex = -1;

    public LocationSelector(final Context context) {
        super(context);
        this.context = context;
    }

    public LocationSelector(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }


    public void setup(final int numRows, final int numColumns) {
        for (int iRow = 0; iRow < numRows; iRow += 1) {
            final LinearLayout row = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.directions_location_selector_row_include, null);
            for (int iCol = 0; iCol < numColumns; iCol += 1) {
                final FrameLayout item = (FrameLayout) LayoutInflater.from(context).inflate(R.layout.directions_location_selector_column_include, null);
                final LinearLayout.LayoutParams itemLayoutParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                itemLayoutParams.setMargins(0, 0, 0, 0);
                item.setLayoutParams(itemLayoutParams);
                final TextView textView = item.findViewById(R.id.location_selector_text);
                textView.setText(String.format("B-%d-%d", iRow, iCol));
                row.addView(item);
            }
            this.addView(row);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        final int action = event.getAction();
        final int rowCount = this.getChildCount();
        final float touchX = event.getX();
        final float touchY = event.getY();
        FrameLayout currentItem = null;
        int currentIndex = -1;
        ROWLOOP: for (int iRow = 0; iRow < rowCount; iRow += 1) {
            final LinearLayout row = (LinearLayout) this.getChildAt(iRow);
            if (touchY >= row.getTop() && touchY < row.getBottom()) {
                final int columnCount = row.getChildCount();
                for (int iColumn = 0; iColumn < columnCount; iColumn += 1) {
                    final FrameLayout item = (FrameLayout) row.getChildAt(iColumn);
                    if (touchX >= item.getLeft() && touchX < item.getRight()) {
                        currentItem = item;
                        currentIndex = iRow * columnCount + iColumn;
                        break ROWLOOP;
                    }
                }
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (startItem != null) {
                // data event
            }
            if (endItem != null) {
                setItemBackground(endItem, R.drawable.location_selector_item_unselected_background);
                endItem = null;
                endIndex = -1;
            }
            if (startItem != null) {
                setItemBackground(startItem, R.drawable.location_selector_item_unselected_background);
                startItem = null;
                startIndex = -1;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN && currentItem != null) {
            startItem = currentItem;
            startIndex = currentIndex;
            setItemBackground(startItem, R.drawable.location_selector_item_first_background);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && currentItem != null) {
            if (endItem != null && currentIndex != endIndex) {
                setItemBackground(endItem, R.drawable.location_selector_item_unselected_background);
                endItem = null;
                endIndex = -1;
            }
            if (currentIndex != startIndex) {
                endItem = currentItem;
                endIndex = currentIndex;
                setItemBackground(endItem, R.drawable.location_selector_item_second_background);
            }
            return true;
        }
        return false;
    }

    private void setItemBackground(final FrameLayout item, int backgroundDrawableId) {
        final Drawable drawable = Application.getInstance().getDrawable(backgroundDrawableId);
        final TextView textView = item.findViewById(R.id.location_selector_text);
        textView.setBackground(drawable);
    }
}
