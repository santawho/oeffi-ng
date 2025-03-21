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

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Criteria;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Filterable;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.PopupMenu;
import android.widget.TextView.OnEditorActionListener;
import com.google.common.base.Strings;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.GeocoderThread;
import de.schildbach.oeffi.util.LocationHelper;
import de.schildbach.oeffi.util.MultiDrawable;
import de.schildbach.oeffi.util.PopupHelper;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;

import javax.annotation.Nullable;
import java.util.Locale;

public class LocationView extends FrameLayout implements LocationHelper.Callback {
    public interface Listener {
        void changed();
    }

    private final Resources res;
    private final LocationHelper locationHelper;
    private final Drawable selectableItemBackground;

    private PopupMenu.OnMenuItemClickListener contextMenuItemClickListener;
    private Listener listener;

    private AutoCompleteTextView textView;
    private View chooseView;
    private MultiDrawable leftDrawable, rightDrawable;
    private TextWatcher textChangedListener;
    private int hintRes = 0;
    private String hint;

    private LocationType locationType = LocationType.ANY;
    private String id = null;
    private Point coord;
    private String place;

    public LocationView(final Context context) {
        this(context, null, 0);
    }

    public LocationView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LocationView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        res = context.getResources();
        locationHelper = new LocationHelper((LocationManager) context.getSystemService(Context.LOCATION_SERVICE), this);

        final TypedArray ta = context.obtainStyledAttributes(new int[] { android.R.attr.selectableItemBackground });
        selectableItemBackground = ta.getDrawable(0);
        ta.recycle();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Bundle state = new Bundle();
        state.putParcelable("super_state", super.onSaveInstanceState());
        state.putSerializable("location_type", locationType);
        state.putString("location_id", id);
        state.putSerializable("location_coord", coord);
        state.putString("location_place", place);
        state.putString("text", getText());
        state.putString("hint", hint);
        state.putInt("hint_res", hintRes);
        return state;
    }

    @Override
    protected void onRestoreInstanceState(final Parcelable state) {
        if (state instanceof Bundle) {
            final Bundle bundle = (Bundle) state;
            super.onRestoreInstanceState(bundle.getParcelable("super_state"));
            locationType = ((LocationType) bundle.getSerializable("location_type"));
            id = bundle.getString("location_id");
            coord = (Point) bundle.getSerializable("location_coord");
            place = bundle.getString("location_place");
            setText(bundle.getString("text"));
            hint = bundle.getString("hint");
            hintRes = bundle.getInt("hint_res");
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        final Context context = getContext();

        textView = new AutoCompleteTextView(context) {
            final Handler handler = new Handler();

            @Override
            protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
                handler.post(() -> chooseView.requestLayout());

                super.onSizeChanged(w, h, oldw, oldh);
            }
        };
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.font_size_large));
        textView.setSingleLine();
        textView.setMaxLines(3);
        textView.setHorizontallyScrolling(false);
        textView.setSelectAllOnFocus(true);
        textView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        textView.setThreshold(0);
        textView.setCompoundDrawablePadding(res.getDimensionPixelSize(R.dimen.text_padding_horizontal_lax));
        final int paddingCram = res.getDimensionPixelSize(R.dimen.text_padding_horizontal_cram);
        final int paddingLax = res.getDimensionPixelSize(R.dimen.text_padding_horizontal_verylax);
        textView.setPadding(paddingLax, paddingCram, paddingLax, paddingCram);
        textView.setOnItemClickListener((parent, view, position, id) -> {
            // workaround for NPE
            if (parent == null)
                return;

            final Location location = (Location) parent.getItemAtPosition(position);

            // workaround for NPE
            if (location == null)
                return;

            setLocation(location);

            afterLocationViewInput(false);
            fireChanged();
        });

        leftDrawable = new MultiDrawable(context);
        leftDrawable.add(R.drawable.space_24dp);
        leftDrawable.add(R.drawable.ic_station_grey600_24dp);
        leftDrawable.add(R.drawable.ic_flag_grey600_24dp);
        leftDrawable.add(R.drawable.ic_place_grey600_24dp);
        leftDrawable.add(R.drawable.ic_location_searching_grey600_24dp);
        leftDrawable.add(R.drawable.ic_gps_fixed_grey600_24dp);
        rightDrawable = new MultiDrawable(context);
        rightDrawable.add(R.drawable.ic_more_vert_grey600_24dp);
        rightDrawable.add(R.drawable.ic_clear_grey600_24dp);
        textView.setCompoundDrawablesWithIntrinsicBounds(leftDrawable, null, rightDrawable, null);

        textChangedListener = new TextWatcher() {
            public void afterTextChanged(final Editable s) {
            }

            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
            }

            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
                locationType = LocationType.ANY;
                id = null;
                hint = null;
                updateAppearance();

                locationHelper.stop();
            }
        };
        textView.addTextChangedListener(textChangedListener);

        chooseView = new View(context) {
            @Override
            protected void onMeasure(final int wMeasureSpec, final int hMeasureSpec) {
                final int width = textView.getCompoundPaddingRight() + textView.getCompoundDrawablePadding();
                final int minHeight = res.getDimensionPixelOffset(R.dimen.directions_form_location_min_height);
                final int height = Math.max(textView.getMeasuredHeight(), minHeight);
                setMeasuredDimension(width, height);
            }
        };
        chooseView.setContentDescription(context.getString(R.string.directions_location_view_more_description));
        chooseView.setBackgroundDrawable(selectableItemBackground);

        addView(textView, new LocationView.LayoutParams(LocationView.LayoutParams.MATCH_PARENT,
                LocationView.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        addView(chooseView, new LocationView.LayoutParams(LocationView.LayoutParams.WRAP_CONTENT,
                LocationView.LayoutParams.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        setEnabled(isEnabled());
        updateAppearance();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);
        if (textView != null) {
            textView.setEnabled(enabled);
            textView.setTextColor(getResources().getColor(enabled ? R.color.fg_significant : R.color.fg_insignificant));
        }
        if (chooseView != null)
            chooseView.setEnabled(enabled);
    }

    private void setText(final CharSequence text) {
        final int threshold = textView.getThreshold();
        textView.setThreshold(Integer.MAX_VALUE);
        textView.removeTextChangedListener(textChangedListener);
        textView.setText(text);
        textView.addTextChangedListener(textChangedListener);
        textView.setThreshold(threshold);
    }

    private String getText() {
        final String text = textView.getText().toString().trim();
        if (text.length() > 0)
            return text;
        else
            return null;
    }

    public void setHint(final int hintRes) {
        this.hintRes = hintRes;
        updateAppearance();
    }

    public <T extends ListAdapter & Filterable> void setAdapter(final T autoCompleteAdapter) {
        textView.setAdapter(autoCompleteAdapter);
    }

    public void setImeOptions(final int imeOptions) {
        textView.setImeOptions(imeOptions | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    }

    public void setOnEditorActionListener(final OnEditorActionListener onEditorActionListener) {
        textView.setOnEditorActionListener(onEditorActionListener);
    }

    public void setContextMenuItemClickListener(final PopupMenu.OnMenuItemClickListener contextMenuItemClickListener) {
        this.contextMenuItemClickListener = contextMenuItemClickListener;
    }

    public void setListener(final Listener listener) {
        this.listener = listener;
    }

    public void acquireLocation() {
        if (!locationHelper.isRunning()) {
            final Criteria criteria = new Criteria();
            criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
            criteria.setAccuracy(Criteria.ACCURACY_COARSE);
            locationHelper.startLocation(criteria, false, Constants.LOCATION_FOREGROUND_UPDATE_TIMEOUT_MS);
        }
    }

    public void onLocationStart(final String provider) {
        locationType = LocationType.COORD;
        coord = null;
        hint = res.getString(R.string.acquire_location_start, provider);
        updateAppearance();

        afterLocationViewInput(true);
    }

    public void onLocationStop(final boolean timedOut) {
        if (timedOut) {
            hint = res.getString(R.string.acquire_location_timeout);
            updateAppearance();
        }
    }

    public void onLocationFail() {
        hint = res.getString(R.string.acquire_location_no_provider);
        updateAppearance();
    }

    public void onLocation(final Point here) {
        if (locationType == LocationType.COORD)
            setLocation(Location.coord(here));
    }

    public void reset() {
        locationType = LocationType.ANY;
        id = null;
        coord = null;
        place = null;
        setText(null);
        hint = null;
        updateAppearance();
        fireChanged();

        locationHelper.stop();
    }

    public void setLocation(final Location location) {
        if (location == null) {
            reset();
            return;
        }
        locationType = location.type;
        id = location.id;
        coord = location.coord;
        place = location.place;
        setText(location.uniqueShortName());
        updateAppearance();

        if (locationType == LocationType.COORD && coord != null) {
            hint = res.getString(R.string.directions_location_view_coordinate) + ": "
                    + String.format(Locale.ENGLISH, "%1$.6f, %2$.6f", coord.getLatAsDouble(), coord.getLonAsDouble());
            updateAppearance();

            new GeocoderThread(getContext(), coord, new GeocoderThread.Callback() {
                public void onGeocoderResult(final Address address) {
                    if (locationType == LocationType.COORD) {
                        setLocation(GeocoderThread.addressToLocation(address));
                        hint = null;
                    }
                }

                public void onGeocoderFail(final Exception exception) {
                    if (locationType == LocationType.COORD) {
                        setText(null);
                        updateAppearance();
                    }
                }
            });
        }

        fireChanged();
    }

    public @Nullable Location getLocation() {
        final String name = getText();

        if (locationType == LocationType.COORD && coord == null)
            return null;
        else if (locationType == LocationType.ANY && Strings.isNullOrEmpty(name))
            return null;
        else
            return new Location(locationType, id, coord, name != null ? place : null, name);
    }

    private final OnClickListener contextButtonClickListener = new OnClickListener() {
        public void onClick(final View v) {
            final PopupMenu popupMenu = new PopupMenu(getContext(), v);
            popupMenu.inflate(R.menu.directions_location_context);
            PopupHelper.setForceShowIcon(popupMenu);
            popupMenu.setOnMenuItemClickListener(contextMenuItemClickListener);
            popupMenu.show();
        }
    };

    private final OnClickListener clearButtonClickListener = new OnClickListener() {
        public void onClick(final View v) {
            reset();
            textView.requestFocus();
        }
    };

    private void updateAppearance() {
        if (locationType == LocationType.COORD && coord == null)
            leftDrawable.selectDrawableByResId(R.drawable.ic_location_searching_grey600_24dp);
        else
            leftDrawable.selectDrawableByResId(LocationView.locationTypeIconRes(locationType));

        if (getText() == null) {
            if (contextMenuItemClickListener == null) {
                rightDrawable.selectDrawableByResId(R.drawable.ic_clear_grey600_24dp);
                chooseView.setOnClickListener(null);
            } else {
                rightDrawable.selectDrawableByResId(R.drawable.ic_more_vert_grey600_24dp);
                chooseView.setOnClickListener(contextButtonClickListener);
            }
        } else {
            rightDrawable.selectDrawableByResId(R.drawable.ic_clear_grey600_24dp);
            chooseView.setOnClickListener(clearButtonClickListener);
        }

        if (hint != null)
            textView.setHint(hint);
        else if (hintRes != 0)
            textView.setHint(hintRes);
        else
            textView.setHint(null);
    }

    private void afterLocationViewInput(final boolean blockActionGo) {
        int action = textView.getImeOptions() & EditorInfo.IME_MASK_ACTION;
        if (blockActionGo && action == EditorInfo.IME_ACTION_GO)
            action = 0;
        if (action != 0)
            textView.onEditorAction(action);
    }

    public void exchangeWith(final LocationView other) {
        final LocationType tempLocationType = other.locationType;
        final String tempId = other.id;
        final Point tempCoord = other.coord;
        final String tempPlace = other.place;
        final String tempText = other.getText();
        final String tempHint = other.hint;

        other.locationType = this.locationType;
        other.id = this.id;
        other.coord = this.coord;
        other.place = this.place;
        other.setText(this.getText());
        other.hint = this.hint;

        this.locationType = tempLocationType;
        this.id = tempId;
        this.coord = tempCoord;
        this.place = tempPlace;
        this.setText(tempText);
        this.hint = tempHint;

        this.updateAppearance();
        other.updateAppearance();

        this.fireChanged();
        other.fireChanged();
    }

    private void fireChanged() {
        if (listener != null)
            listener.changed();
    }

    public static int locationTypeIconRes(final @Nullable LocationType locationType) {
        if (locationType == null || locationType == LocationType.ANY)
            return R.drawable.space_24dp;
        else if (locationType == LocationType.STATION)
            return R.drawable.ic_station_grey600_24dp;
        else if (locationType == LocationType.POI)
            return R.drawable.ic_flag_grey600_24dp;
        else if (locationType == LocationType.ADDRESS)
            return R.drawable.ic_place_grey600_24dp;
        else if (locationType == LocationType.COORD)
            return R.drawable.ic_gps_fixed_grey600_24dp;
        else
            throw new IllegalStateException("cannot handle: " + locationType);
    }
}
