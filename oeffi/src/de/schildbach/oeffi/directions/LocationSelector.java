package de.schildbach.oeffi.directions;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.text.Html;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;

public class LocationSelector extends LinearLayout {
    public interface LocationSelectionListener {
        void onLocationSequenceSelected(List<Location> locations, boolean longHold);
    }

    private static final Logger log = LoggerFactory.getLogger(LocationSelector.class);

    private static final String PREFS_ENABLED = "user_interface_location_selector_enabled";
    private static final String PREFS_NUMROWS = "user_interface_location_selector_numrows";
    private static final String PREFS_LONGHOLDTIME = "user_interface_location_selector_longholdtime";
    private static final String PREFS_STATE = "user_interface_location_selector_state_";

    private static final int BG_UNSELECTED = R.drawable.location_selector_item_unselected_background;
    private static final int BG_FIRST = R.drawable.location_selector_item_first_background;
    private static final int BG_INTERMEDIATE = R.drawable.location_selector_item_intermediate_background;
    private static final int BG_LAST = R.drawable.location_selector_item_last_background;

    private static final int DEFAULT_NUM_ROWS = 4;
    private static final int DEFAULT_NUM_COLUMNS = 2;

    private static final long DEFAULT_LONG_HOLD_TIME = 500;

    public static class LocationAndTime implements Serializable {
        private static final long serialVersionUID = -9091619330471177490L;
        Location location;
        long addedAtTime;
    }

    public static class PrefState implements Serializable {
        private static final long serialVersionUID = -8329992953750044467L;
        LocationAndTime[] locationsAndTimes;
    }

    private static class Item {
        public LinearLayout rowLayout;
        public FrameLayout frameLayout;
        public TextView textView;
        int itemIndex;
        int rowIndex, columnIndex;
        LocationAndTime locationAndTime;
    }

    private SharedPreferences preferences;
    private boolean isEnabled;
    private int numRows, numColumns;
    private long longHoldTime;
    private NetworkId networkId;
    private LocationSelectionListener locationSelectionListener;
    private Item[] availableItems;

    private final List<Item> selectedItems = new ArrayList<>();
    private long timeEntered;
    private boolean invalidSelection;
    private Handler handler;
    private boolean timerRunning;
    private boolean stateIsChanged;

    public LocationSelector(final Context context) {
        this(context, null);
    }

    public LocationSelector(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        handler = new Handler();
    }

    public void setLocationSelectionListener(final LocationSelectionListener locationSelectionListener) {
        this.locationSelectionListener = locationSelectionListener;
    }

    public void setup(final Context context, final SharedPreferences prefs) {
        this.preferences = prefs;
        isEnabled = prefs.getBoolean(PREFS_ENABLED, false);
        if (!isEnabled) {
            super.setVisibility(GONE);
            return;
        }
        numRows = Integer.parseInt(prefs.getString(PREFS_NUMROWS, Integer.toString(DEFAULT_NUM_ROWS)));
        numColumns = DEFAULT_NUM_COLUMNS;
        try {
            longHoldTime = Long.parseLong(prefs.getString(PREFS_LONGHOLDTIME, Long.toString(DEFAULT_LONG_HOLD_TIME)));
        } catch (Exception e) {
            longHoldTime = DEFAULT_LONG_HOLD_TIME;
        }
        availableItems = new Item[numRows * numColumns];
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
                availableItems[iItem] = item;
                setItemStationName(item, null);
                setItemBackground(item, R.drawable.location_selector_item_unselected_background);
            }
            this.addView(rowLayout);
        }
        clearSelection();
    }

    private String getPrefsStateKey() {
        if (networkId == null)
            return null;
        return PREFS_STATE + networkId.name();
    }

    public void setNetwork(final NetworkId networkId) {
        if (!isEnabled)
            return;
        if (networkId.equals(this.networkId))
            return;
        this.networkId = networkId;
        final PrefState prefState = (PrefState) Objects.deserializeFromString(preferences.getString(getPrefsStateKey(), null));
        if (prefState != null) {
            final LocationAndTime[] locationsAndTimes = prefState.locationsAndTimes;
            if (locationsAndTimes != null) {
                final int count = locationsAndTimes.length;
                for (int n = 0; n < availableItems.length; n += 1) {
                    final Item item = availableItems[n];
                    if ((n < locationsAndTimes.length)) {
                        final LocationAndTime locationAndTime = locationsAndTimes[n];
                        item.locationAndTime = locationAndTime;
                        setItemStationName(item, Formats.fullLocationName(locationAndTime.location));
                    } else {
                        item.locationAndTime = null;
                        setItemStationName(item, null);
                    }
                }
            }
        }
    }

    public void persist() {
        if (!isEnabled)
            return;
        if (networkId == null)
            return;
        if (!stateIsChanged)
            return;
        final List<LocationAndTime> locationsAndTimes = new ArrayList<>();
        for (Item item : availableItems) {
            if (item.locationAndTime != null)
                locationsAndTimes.add(item.locationAndTime);
        }
        final PrefState prefState = new PrefState();
        prefState.locationsAndTimes = locationsAndTimes.toArray(new LocationAndTime[]{});
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putString(getPrefsStateKey(), Objects.serializeToString(prefState));
        editor.apply();
    }

    public void addLocation(final Location location, final long addedAtTime) {
        if (!isEnabled)
            return;
        Item oldestItem = null;
        for (Item item : availableItems) {
            final LocationAndTime itemLocationAndTime = item.locationAndTime;
            if (itemLocationAndTime != null) {
                final Location itemLocation = itemLocationAndTime.location;
                if (itemLocation.equals(location)) {
                    itemLocationAndTime.addedAtTime = addedAtTime;
                    stateIsChanged = true;
                    return; // was already added
                }
                if (oldestItem == null
                        || (oldestItem.locationAndTime != null
                            && oldestItem.locationAndTime.addedAtTime > itemLocationAndTime.addedAtTime)) {
                    oldestItem = item;
                }
            } else if (oldestItem == null || oldestItem.locationAndTime != null) {
                oldestItem = item;
            }
        }
        if (oldestItem == null)
            oldestItem = availableItems[0];
        final LocationAndTime locationAndTime = new LocationAndTime();
        locationAndTime.location = location;
        locationAndTime.addedAtTime = addedAtTime;
        oldestItem.locationAndTime = locationAndTime;
        setItemStationName(oldestItem, Formats.fullLocationName(location));
        stateIsChanged = true;
    }

    public void clearSelection() {
        if (!isEnabled)
            return;
        for (Item item : selectedItems) {
            setItemBackground(item, BG_UNSELECTED);
        }
        selectedItems.clear();
        timeEntered = 0;
        invalidSelection = false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        final int action = event.getAction();
        final float touchX = event.getX();
        final float touchY = event.getY();
        final Item currItem = findItemAtXY(Math.round(touchX), Math.round(touchY));
        final Item prevItem = selectedItems.isEmpty() ? null : selectedItems.get(selectedItems.size() - 1);
        final boolean isStillFirst = selectedItems.size() <= 1;
        final boolean isSameItem = prevItem != null && currItem != null && currItem.itemIndex == prevItem.itemIndex;
        final long now = System.currentTimeMillis();
        final long holdTime = now - timeEntered;
        final boolean isLongHold = holdTime >= longHoldTime;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            timerRunning = false;

            final int size = selectedItems.size();
            if (size >= 2)
                setItemBackground(selectedItems.get(size - 1), BG_LAST);

            if (selectedItems.isEmpty())
                return true;

            if ((currItem == null || currItem.itemIndex == selectedItems.get(0).itemIndex)
                    && selectedItems.size() > 1) {
                // cancel when returning to first item
                clearSelection();
                return true;
            }

            // data event
            if (locationSelectionListener != null) {
                final List<Location> locations = new ArrayList<>();
                for (Item item : selectedItems) {
                    final Location location = item.locationAndTime.location;
                    if (location != null)
                        locations.add(location);
                }
                locationSelectionListener.onLocationSequenceSelected(locations, isLongHold);
            }

            return true;
        }

        if (action == MotionEvent.ACTION_DOWN && currItem != null) {
            clearSelection();

            if (currItem.locationAndTime == null) {
                invalidSelection = true;
                return true;
            }

            selectedItems.add(currItem);
            timeEntered = now;
            setItemBackground(currItem, BG_FIRST);

            timerRunning = true;
            handler.postDelayed(this::onTimer, 50);

            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (invalidSelection)
                return true;
            if (currItem == null || currItem.locationAndTime == null) {
                // leaving all items
                if (!isLongHold && !isStillFirst && prevItem != null) {
                    // leaving intermediate fast, remove it
                    setItemBackground(prevItem, BG_UNSELECTED);
                    selectedItems.remove(selectedItems.size() - 1);
                }
                return true;
            } else {
                if (prevItem != null && currItem.itemIndex != prevItem.itemIndex) {
                    timeEntered = now;
                    for (Item item : selectedItems) {
                        if (item.itemIndex == currItem.itemIndex)
                            return true;
                    }
                }

                if (isLongHold) {
                    if (isSameItem) {
                        if (!isStillFirst) {
                            setItemBackground(currItem, BG_INTERMEDIATE);
                        }
                        return true;
                    }
                } else {
                    if (isSameItem)
                        return true;

                    // leaving intermediate fast, remove it
                    if (!isStillFirst && prevItem != null) {
                        setItemBackground(prevItem, BG_UNSELECTED);
                        selectedItems.remove(selectedItems.size() - 1);
                    }
                }

                selectedItems.add(currItem);
                setItemBackground(currItem, BG_LAST);
            }
            return true;
        }

        return false;
    }

    private void onTimer() {
        if (!timerRunning)
            return;

        final long now = System.currentTimeMillis();
        final long holdTime = now - timeEntered;
        final boolean isLongHold = holdTime >= longHoldTime;
        final boolean isStillFirst = selectedItems.size() <= 1;
        final Item prevItem = selectedItems.isEmpty() ? null : selectedItems.get(selectedItems.size() - 1);

        if (isLongHold && prevItem != null && !isStillFirst) {
            setItemBackground(prevItem, BG_INTERMEDIATE);
        }

        handler.postDelayed(this::onTimer, 50);
    }

    private Item findItemAtXY(final int x, final int y) {
        for (int iItem = 0; iItem < availableItems.length; iItem += 1) {
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
        return refreshItem(availableItems[iItem]);
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
        final TextView textView = item.textView;
        if (stationName != null) {
            textView.setText(Html.fromHtml(Formats.makeBreakableStationName(stationName), Html.FROM_HTML_MODE_COMPACT));
            textView.setEnabled(true);
        } else {
            textView.setText("...");
            textView.setEnabled(false);
        }
    }
}
