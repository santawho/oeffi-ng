package de.schildbach.oeffi.directions;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Formats;

public class LocationSelector extends LinearLayout {
    private static final Logger log = LoggerFactory.getLogger(LocationSelector.class);

    private static final String PREFS_ENABLED = "user_interface_location_selector_enabled";
    private static final String PREFS_NUMROWS = "user_interface_location_selector_numrows";

    private static final int LOCATION_SELECTOR_DEFAULT_NUM_ROWS = 4;
    private static final int LOCATION_SELECTOR_DEFAULT_NUM_COLUMNS = 2;

    private class Item {
        public LinearLayout rowLayout;
        public FrameLayout frameLayout;
        public TextView textView;
        int itemIndex;
        int rowIndex, columnIndex;
    }

    private Item[] items;

    private int numRows, numColumns;
    private Item startItem, endItem;

    public LocationSelector(final Context context) {
        this(context, null);
    }

    public LocationSelector(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
    }

    public void setup(final Context context, final SharedPreferences prefs) {
        final boolean enabled = prefs.getBoolean(PREFS_ENABLED, false);
        if (!enabled) {
            super.setVisibility(GONE);
            return;
        }
        numRows = Integer.parseInt(prefs.getString(PREFS_NUMROWS, Integer.toString(LOCATION_SELECTOR_DEFAULT_NUM_ROWS)));
        numColumns = LOCATION_SELECTOR_DEFAULT_NUM_COLUMNS;
        items = new Item[numRows * numColumns];
        for (int iRow = 0, iItem = -1; iRow < numRows; iRow += 1) {
            final LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            rowLayout.setOrientation(HORIZONTAL);
            for (int iCol = 0; iCol < numColumns; iCol += 1) {
                final FrameLayout frameLayout = (FrameLayout) LayoutInflater.from(context).inflate(R.layout.location_selector_item, null);
                frameLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                final TextView textView = frameLayout.findViewById(R.id.location_selector_text);
                rowLayout.addView(frameLayout);
                final Item item = new Item();
                iItem += 1;
                item.rowIndex = iRow;
                item.columnIndex = iCol;
                item.itemIndex = iItem;
                item.rowLayout = rowLayout;
                item.frameLayout = frameLayout;
                item.textView = textView;
                items[iItem] = item;
                setItemStationName(item, String.format("Niedernhausen, djfkslj jfkls jklsdf jklfs B-%d-%d", iRow, iCol));
                setItemBackground(item, R.drawable.location_selector_item_unselected_background);
            }
            this.addView(rowLayout);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        final int action = event.getAction();
        final float touchX = event.getX();
        final float touchY = event.getY();
        final Item item = findItemAtXY(Math.round(touchX), Math.round(touchY));
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (startItem != null) {
                // data event
            }
            if (endItem != null) {
                setItemBackground(endItem, R.drawable.location_selector_item_unselected_background);
                endItem = null;
            }
            if (startItem != null) {
                setItemBackground(startItem, R.drawable.location_selector_item_unselected_background);
                startItem = null;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN && item != null) {
            startItem = item;
            setItemBackground(startItem, R.drawable.location_selector_item_first_background);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && item != null) {
            if (endItem != null && item.itemIndex != endItem.itemIndex) {
                setItemBackground(endItem, R.drawable.location_selector_item_unselected_background);
                endItem = null;
            }
            if (startItem != null && item.itemIndex != startItem.itemIndex) {
                endItem = item;
                setItemBackground(endItem, R.drawable.location_selector_item_second_background);
            }
            return true;
        }
        return false;
    }

    private Item findItemAtXY(final int x, final int y) {
        for (int iItem = 0; iItem < items.length; iItem += 1) {
            final Item item = refreshItem(iItem);
            final FrameLayout frameLayout = item.frameLayout;
            if (x >= frameLayout.getLeft() && x < frameLayout.getRight()) {
                final LinearLayout rowLayout = item.rowLayout;
                if (y >= rowLayout.getTop() && y < rowLayout.getBottom()) {
                    return item;
                }
            }
        }
        return null;
    }

    private Item refreshItem(final int iItem) {
        return refreshItem(items[iItem]);
    }

    private Item refreshItem(final Item item) {
        final LinearLayout row = (LinearLayout) this.getChildAt(item.rowIndex);
        item.frameLayout = (FrameLayout) row.getChildAt(item.columnIndex);
        item.textView = item.frameLayout.findViewById(R.id.location_selector_text);
        return item;
    }

    private void setItemBackground(final Item item, int backgroundDrawableId) {
        item.textView.setBackground(getContext().getDrawable(backgroundDrawableId));
    }

    private void setItemStationName(final Item item, final String stationName) {
        item.textView.setText(Html.fromHtml(Formats.makeBreakableStationName(stationName), Html.FROM_HTML_MODE_COMPACT));
    }
}
