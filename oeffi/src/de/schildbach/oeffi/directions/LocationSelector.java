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

package de.schildbach.oeffi.directions;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.text.Html;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.ColorHash;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.Objects;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;

public class LocationSelector extends LinearLayout implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    public interface LocationSelectionListener {
        void onLocationSequenceSelected(List<Location> locations, boolean longHold, View lastView);
        void onSingleLocationSelected(Location location, boolean longHold, View selectedView);
    }

    private static final Logger log = LoggerFactory.getLogger(LocationSelector.class);

    private static final ColorHash colorHashLightMode = new ColorHash(
            Arrays.asList(0.25, 0.32, 0.39, 0.45, 0.49), // available lightness values
            Arrays.asList(0.50, 0.60, 0.70, 0.80, 0.90), // available saturation values
//            Arrays.asList(0.30, 0.45, 0.60, 0.75, 0.90), // available saturation values
            0, 360,                  // hue range
            ColorHash::md5Hash              // try ColorHash::javaHash  or  ColorHash::bkdrHash
    );
    private static final ColorHash colorHashDarkMode = new ColorHash(
            Arrays.asList(0.85, 0.78, 0.71, 0.65, 0.61), // available lightness values
            Arrays.asList(0.50, 0.60, 0.70, 0.80, 0.90), // available saturation values
//            Arrays.asList(0.30, 0.45, 0.60, 0.75, 0.90), // available saturation values
            0, 360,                  // hue range
            ColorHash::md5Hash              // try ColorHash::javaHash  or  ColorHash::bkdrHash
    );

    private static final String PREFS_ENABLED = "user_interface_location_selector_enabled";
    private static final String PREFS_COLORIZED = "user_interface_location_selector_colorized";
    private static final String PREFS_NUMROWS = "user_interface_location_selector_numrows";
    private static final String PREFS_LONGHOLDTIME = "user_interface_location_selector_longholdtime";
    private static final String PREFS_LONGHOLDMENU = "user_interface_location_selector_longholdmenu";
    private static final String PREFS_STATE = "user_interface_location_selector_state_";

    private static final int BG_UNSELECTED = R.drawable.location_selector_item_unselected_background;
    private static final int BG_FIRST = R.drawable.location_selector_item_first_background;
    private static final int BG_INTERMEDIATE = R.drawable.location_selector_item_intermediate_background;
    private static final int BG_LAST = R.drawable.location_selector_item_last_background;

    private static final int DEFAULT_NUM_ROWS = 4;
    private static final int DEFAULT_NUM_COLUMNS = 2;

    private static final long DEFAULT_LONG_HOLD_TIME = 500;

    public static class ItemData implements Serializable {
        private static final long serialVersionUID = -9091619330471177490L;
        Location location;
        long addedAtTime;
        boolean isPinned;
    }

    public static class PrefState implements Serializable {
        private static final long serialVersionUID = -8329992953750044467L;
        ItemData[] items;
    }

    private static class Item {
        public LinearLayout rowLayout;
        public FrameLayout frameLayout;
        public TextView textView;
        public ImageView pinnedView;
        int itemIndex;
        int rowIndex, columnIndex;
        ItemData itemData;
    }

    private SharedPreferences preferences;
    private boolean isEnabled;
    private boolean isColorized;
    private int numRows, numColumns;
    private long longHoldTime;
    private boolean useLongHoldMenu;
    private final boolean darkMode;
    private NetworkId networkId;
    private LocationSelectionListener locationSelectionListener;
    private Item[] availableItems;

    private final List<Item> selectedItems = new ArrayList<>();
    private long timeEntered;
    private boolean isContextOperation;
    private boolean isOperationCompleted;
    private boolean invalidSelection;
    private boolean timerRunning;
    private boolean stateIsChanged;
    private final Handler handler;

    public LocationSelector(final Context context) {
        this(context, null);
    }

    public LocationSelector(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        handler = new Handler();

        this.darkMode = Application.getInstance().isDarkMode();
    }

    public void setLocationSelectionListener(final LocationSelectionListener locationSelectionListener) {
        this.locationSelectionListener = locationSelectionListener;
    }

    public void setup(final Context context, final SharedPreferences prefs) {
        if (this.preferences != null)
            this.preferences.unregisterOnSharedPreferenceChangeListener(this);
        this.preferences = prefs;
        prefs.registerOnSharedPreferenceChangeListener(this);
        isEnabled = prefs.getBoolean(PREFS_ENABLED, true);
        if (!isEnabled) {
            super.setVisibility(GONE);
            return;
        }
        super.setVisibility(VISIBLE);
        isColorized = prefs.getBoolean(PREFS_COLORIZED, true);
        numRows = Integer.parseInt(prefs.getString(PREFS_NUMROWS, Integer.toString(DEFAULT_NUM_ROWS)));
        numColumns = DEFAULT_NUM_COLUMNS;
        try {
            longHoldTime = Long.parseLong(prefs.getString(PREFS_LONGHOLDTIME, Long.toString(DEFAULT_LONG_HOLD_TIME)));
        } catch (Exception e) {
            longHoldTime = DEFAULT_LONG_HOLD_TIME;
        }
        useLongHoldMenu = prefs.getBoolean(PREFS_LONGHOLDMENU, false);
        availableItems = new Item[numRows * numColumns];
        this.removeAllViews();
        for (int iRow = 0, iItem = -1; iRow < numRows; iRow += 1) {
            final LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            rowLayout.setOrientation(HORIZONTAL);
            for (int iCol = 0; iCol < numColumns; iCol += 1) {
                final FrameLayout frameLayout = (FrameLayout) LayoutInflater.from(context).inflate(R.layout.location_selector_item, null);
                frameLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                final TextView textView = frameLayout.findViewById(R.id.location_selector_text);
                final ImageView pinnedView = frameLayout.findViewById(R.id.location_selector_pinned);
                rowLayout.addView(frameLayout);
                final Item item = new Item();
                iItem += 1;
                item.rowIndex = iRow;
                item.columnIndex = iCol;
                item.itemIndex = iItem;
                item.rowLayout = rowLayout;
                item.frameLayout = frameLayout;
                item.textView = textView;
                item.pinnedView = pinnedView;
                availableItems[iItem] = item;
                setItemStationName(item, null);
                setItemPinning(item, false);
                setItemBackground(item, R.drawable.location_selector_item_unselected_background);
            }
            this.addView(rowLayout);
        }
        clearSelection();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, @Nullable final String key) {
        if (!(PREFS_ENABLED.equals(key)
                || PREFS_COLORIZED.equals(key)
                || PREFS_NUMROWS.equals(key)
                || PREFS_LONGHOLDTIME.equals(key)
                || PREFS_LONGHOLDMENU.equals(key)
        )) {
            return;
        }

        setup(getContext(), sharedPreferences);
        setupContent();
    }

    private String getPrefsStateKey() {
        if (networkId == null)
            return null;
        return PREFS_STATE + networkId.name();
    }

    public void setNetwork(final NetworkId networkId) {
        if (!isEnabled)
            return;
        if (networkId != null && networkId.equals(this.networkId))
            return;

        this.networkId = networkId;

        setupContent();
    }

    private void setupContent() {
        ItemData[] items = null;
        final PrefState prefState = (PrefState) Objects.deserializeFromString(
                preferences.getString(getPrefsStateKey(), null));
        if (prefState != null)
            items = prefState.items;
        if (items == null)
            items = new ItemData[0];
        Arrays.sort(items, (a, b) -> {
            final boolean aIsPinned = a.isPinned;
            final boolean bIsPinned = b.isPinned;
            if (aIsPinned && !bIsPinned) return -1;
            if (!aIsPinned && bIsPinned) return 1;
            return (int) (b.addedAtTime - a.addedAtTime);
        });
        for (int n = 0; n < availableItems.length; n += 1) {
            final Item item = availableItems[n];
            if (n < items.length) {
                final ItemData itemData = items[n];
                item.itemData = itemData;
                setItemStationName(item, Formats.fullLocationName(itemData.location));
                setItemPinning(item, itemData.isPinned);
            } else {
                item.itemData = null;
                setItemStationName(item, null);
                setItemPinning(item, false);
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
        final List<ItemData> itemDatas = new ArrayList<>();
        for (Item item : availableItems) {
            if (item.itemData != null)
                itemDatas.add(item.itemData);
        }
        final PrefState prefState = new PrefState();
        prefState.items = itemDatas.toArray(new ItemData[]{});
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putString(getPrefsStateKey(), Objects.serializeToString(prefState));
        editor.apply();
    }

    private Item getItemByLocation(final Location location) {
        if (location == null)
            return null;
        for (Item item : availableItems) {
            final ItemData itemData = item.itemData;
            if (itemData != null) {
                final Location itemLocation = itemData.location;
                if (location.equals(itemLocation))
                    return item;
            }
        }
        return null;
    }

    public boolean isPinned(final Location location) {
        final Item item = getItemByLocation(location);
        if (item == null)
            return false;
        final ItemData itemData = item.itemData;
        if (itemData == null)
            return false;
        return itemData.isPinned;
    }

    public void setPinned(final Location location, final boolean isPinned) {
        final Item item = getItemByLocation(location);
        if (item == null)
            return;
        final ItemData itemData = item.itemData;
        if (itemData == null)
            return;
        if (itemData.isPinned == isPinned)
            return;

        itemData.isPinned = isPinned;
        setItemPinning(item, isPinned);
        stateIsChanged = true;
    }

    public void addLocation(final Location location, final long addedAtTime) {
        if (!isEnabled || location == null)
            return;
        Item oldestItem = null;
        for (Item item : availableItems) {
            final ItemData itemData = item.itemData;
            if (itemData != null) {
                final Location itemLocation = itemData.location;
                if (itemLocation.equals(location)) {
                    itemData.addedAtTime = addedAtTime;
                    stateIsChanged = true;
                    return; // was already added
                }
                if (!itemData.isPinned
                        && (oldestItem == null
                        || (oldestItem.itemData != null
                            && oldestItem.itemData.addedAtTime > itemData.addedAtTime))) {
                    oldestItem = item;
                }
            } else if (oldestItem == null || oldestItem.itemData != null) {
                oldestItem = item;
            }
        }
        if (oldestItem == null)
            return;

        final ItemData itemData = new ItemData();
        itemData.location = location;
        itemData.addedAtTime = addedAtTime;
        oldestItem.itemData = itemData;
        setItemStationName(oldestItem, Formats.fullLocationName(location));
        setItemPinning(oldestItem, false);
        stateIsChanged = true;
    }

    public void removeLocation(final Location location) {
        if (!isEnabled)
            return;
        for (Item item : availableItems) {
            final ItemData itemData = item.itemData;
            if (itemData != null) {
                final Location itemLocation = itemData.location;
                if (itemLocation.equals(location)) {
                    item.itemData = null;
                    setItemStationName(item, null);
                    setItemPinning(item, false);
                    stateIsChanged = true;
                    break;
                }
            }
        }
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
        isContextOperation = false;
        isOperationCompleted = false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(final MotionEvent event) {
//        log.debug(event.toString());
        final int action = event.getActionMasked();
        final float touchX = event.getX();
        final float touchY = event.getY();
        final Item currItem = findItemAtXY(Math.round(touchX), Math.round(touchY));
        final Item prevItem = selectedItems.isEmpty() ? null : selectedItems.get(selectedItems.size() - 1);
        final boolean isStillFirst = selectedItems.size() <= 1;
        final boolean isSameItem = prevItem != null && currItem != null && currItem.itemIndex == prevItem.itemIndex;
        final long now = System.currentTimeMillis();
        final long holdTime = now - timeEntered;
        final boolean isLongHold = holdTime >= longHoldTime;
        final boolean isVeryLongHold = holdTime >= 2 * longHoldTime;

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            // second finger down
            isContextOperation = true;
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            final boolean wasOperationCompleted = isOperationCompleted;
            timerRunning = false;
            isOperationCompleted = false;

            if (wasOperationCompleted)
                return true;

            if (invalidSelection) {
                clearSelection();
                DialogBuilder.get(getContext())
                        .setTitle(R.string.directions_location_selector_help_title)
                        .setMessage(R.string.directions_location_selector_help_text)
                        .setPositiveButton(android.R.string.ok, null)
                        .create().show();
                return true;
            }

            final int size = selectedItems.size();
            if (size >= 2)
                setItemBackground(selectedItems.get(size - 1), BG_LAST);
            else if (size == 1)
                setItemBackground(selectedItems.get(0), BG_FIRST);

            if (selectedItems.isEmpty())
                return true;

            if ((currItem == null || currItem.itemIndex == selectedItems.get(0).itemIndex) && size > 1) {
                // cancel when returning to first item
                clearSelection();
                return true;
            }

            if (useLongHoldMenu && isLongHold && size == 1)
                isContextOperation = true;

            // data event
            if (locationSelectionListener != null) {
                if (isContextOperation) {
                    if (size == 1) {
                        final Item item = selectedItems.get(0);
                        final Location location = item.itemData.location;
                        locationSelectionListener.onSingleLocationSelected(location, isLongHold, item.textView);
                    }
                } else {
                    final List<Location> locations = new ArrayList<>();
                    for (Item item : selectedItems) {
                        final Location location = item.itemData.location;
                        if (location != null)
                            locations.add(location);
                    }
                    locationSelectionListener.onLocationSequenceSelected(locations, isLongHold, selectedItems.get(size - 1).textView);
                }
            }

            return true;
        }

        if (action == MotionEvent.ACTION_DOWN && currItem != null) {
            clearSelection();

            if (currItem.itemData == null) {
                invalidSelection = true;
            } else {
                selectedItems.add(currItem);
                timeEntered = now;
                setItemBackground(currItem, BG_FIRST);
            }

            timerRunning = true;
            handler.postDelayed(this::onTimer, 50);

            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (invalidSelection || isOperationCompleted)
                return true;
            if (currItem == null || currItem.itemData == null) {
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

                if (isVeryLongHold && isStillFirst && useLongHoldMenu && locationSelectionListener != null) {
                    isOperationCompleted = true;
                    final Item item = selectedItems.get(0);
                    final Location location = item.itemData.location;
                    locationSelectionListener.onSingleLocationSelected(location, true, item.textView);
                }

                if (isLongHold) {
                    if (isSameItem) {
                        setItemBackground(currItem, BG_INTERMEDIATE);
                        return true;
                    }
                } else {
                    if (isSameItem)
                        return true;

                    // leaving intermediate fast, remove it
                    if (prevItem != null) {
                        if (isStillFirst) {
                            setItemBackground(prevItem, BG_FIRST);
                        } else {
                            setItemBackground(prevItem, BG_UNSELECTED);
                            selectedItems.remove(selectedItems.size() - 1);
                        }
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
        final boolean isVeryLongHold = holdTime >= 2 * longHoldTime;
        final boolean isStillFirst = selectedItems.size() <= 1;
        final Item prevItem = selectedItems.isEmpty() ? null : selectedItems.get(selectedItems.size() - 1);

        if (isLongHold && prevItem != null) {
            if (isVeryLongHold && isStillFirst) {
                if (useLongHoldMenu && locationSelectionListener != null) {
                    isContextOperation = true;
                    isOperationCompleted = true;
                    final Item item = selectedItems.get(0);
                    final Location location = item.itemData.location;
                    locationSelectionListener.onSingleLocationSelected(location, true, item.textView);

                    timerRunning = false;
                }
            } else {
                setItemBackground(prevItem, BG_INTERMEDIATE);
            }
        }

        if (timerRunning)
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
        item.pinnedView = item.frameLayout.findViewById(R.id.location_selector_pinned);
        return item;
    }

    private void setItemBackground(final Item item, int backgroundDrawableId) {
        item.textView.setBackground(getContext().getDrawable(backgroundDrawableId));
    }

    private void setItemStationName(final Item item, final String stationName) {
        final TextView textView = item.textView;
        if (stationName != null) {
            final ColorHash colorHash = darkMode ? colorHashDarkMode : colorHashLightMode;
            textView.setText(Html.fromHtml(Formats.makeBreakableStationName(stationName), Html.FROM_HTML_MODE_COMPACT));
            final int color = isColorized ? colorHash.toARGB(stationName) : getResources().getColor(R.color.fg_significant);
//            log.debug("\"{}\"  -->  #{}", stationName, String.format("%08x", color));
            textView.setTextColor(color);
            textView.setEnabled(true);
        } else {
            textView.setText("...");
            textView.setTextColor(getResources().getColor(R.color.fg_insignificant));
            textView.setEnabled(false);
        }
    }

    private void setItemPinning(final Item item, final boolean isPinned) {
        item.pinnedView.setVisibility(isPinned ? View.VISIBLE : View.GONE);
    }
}
