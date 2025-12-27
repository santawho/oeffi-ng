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

package de.schildbach.oeffi.util.locationview;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.location.Address;
import android.location.Criteria;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView.OnEditorActionListener;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.network.LocationSearchProviderFactory;
import de.schildbach.oeffi.stations.FavoriteStationsActivity;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.GeocoderThread;
import de.schildbach.oeffi.util.LocationHelper;
import de.schildbach.oeffi.util.PopupHelper;
import de.schildbach.pte.provider.locationsearch.LocationSearchProviderId;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Product;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Set;

public class LocationView extends LinearLayout implements LocationHelper.Callback {
    private static final Logger log = LoggerFactory.getLogger(LocationView.class);

    public interface Listener {
        NetworkId getNetwork();
        Set<Product> getPreferredProducts();
        Handler getHandler();
        void changed(LocationView view);
    }

    private final Resources res;
    private final LocationHelper locationHelper;

    private Listener listener;

    private AutoCompleteTextView textView;
    private ViewGroup typeButtons;
    private ImageButton menuButton;
    private ImageButton contactButton;
    private ImageButton currentLocationButton;
    private ImageButton favoriteStationButton;
    private ImageButton alternateSearchButton;
    private ImageButton clearButton;
    private ImageButton modeButton;
    private AutoCompleteLocationAdapter autoCompleteLocationAdapter;
    private TextWatcher textChangedListener;
    private int hintRes = 0;
    private String hint;

    private Location location;
    private LocationType locationType = LocationType.ANY;
    private boolean stationAsAddress;
    private boolean stationAsAddressEnabled;
    private String id = null;
    private Point coord;
    private String place;

    public LocationView(final Context context) {
        this(context, null, 0);
    }

    public LocationView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
        setSaveEnabled(true);
    }

    public LocationView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        setOrientation(HORIZONTAL);

        res = context.getResources();
        locationHelper = new LocationHelper((LocationManager) context.getSystemService(Context.LOCATION_SERVICE), this);

        setup(context);
    }

    public void setStationAsAddressEnabled(final boolean stationAsAddressEnabled) {
        this.stationAsAddressEnabled = stationAsAddressEnabled;
    }

    public void toggleAlternateSearchProviderNominatim() {
        final LocationSearchProviderId currentProvider = autoCompleteLocationAdapter.getAlternateSearchProviderId();
        setAlternateSearchProvider(currentProvider != LocationSearchProviderId.Nominamtim
                ? LocationSearchProviderId.Nominamtim : null);
    }

    public void setAlternateSearchProvider(final LocationSearchProviderId searchProviderId) {
        if (autoCompleteLocationAdapter != null)
            autoCompleteLocationAdapter.setAlternateSearchProvider(searchProviderId);

        if (searchProviderId == null) {
            hint = null;
        } else {
            final String name = LocationSearchProviderFactory.provider(searchProviderId).getDescription().getName();
            hint = getContext().getString(R.string.directions_location_context_using_alternate_search, name);
        }
        updateAppearance();
    }

    public void resetBehaviour() {
        setAlternateSearchProvider(null);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Bundle state = new Bundle();
        state.putParcelable("super_state", super.onSaveInstanceState());
        state.putSerializable("location_type", locationType);
        state.putBoolean("station_as_address", stationAsAddress);
        state.putBoolean("station_as_address_enabled", stationAsAddressEnabled);
        state.putString("location_id", id);
        state.putSerializable("location_coord", coord);
        state.putString("location_place", place);
        final String text = getText();
        state.putString("text", text);
        state.putString("hint", hint);
        state.putInt("hint_res", hintRes);
        state.putSerializable("location", location);
        return state;
    }

    @Override
    protected void onRestoreInstanceState(final Parcelable state) {
        if (state instanceof Bundle) {
            final Bundle bundle = (Bundle) state;
            super.onRestoreInstanceState(bundle.getParcelable("super_state"));
            locationType = ((LocationType) bundle.getSerializable("location_type"));
            stationAsAddress = bundle.getBoolean("station_as_address");
            stationAsAddressEnabled = bundle.getBoolean("station_as_address_enabled");
            id = bundle.getString("location_id");
            coord = (Point) bundle.getSerializable("location_coord");
            place = bundle.getString("location_place");
            setText(bundle.getString("text"));
            hint = bundle.getString("hint");
            hintRes = bundle.getInt("hint_res");
            location = (Location) bundle.getSerializable("location");
            if (location != null)
                setLocation(location);
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    @Override
    protected void dispatchSaveInstanceState(final SparseArray<Parcelable> container) {
        // do not save the sub-elements
        // super.dispatchSaveInstanceState(container);
        dispatchFreezeSelfOnly(container);
    }

    @Override
    protected void dispatchRestoreInstanceState(final SparseArray<Parcelable> container) {
        // do not restore the sub-elements
        // super.dispatchRestoreInstanceState(container);
        dispatchThawSelfOnly(container);
    }

    @Override
    protected void onLayout(final boolean changed, final int l, final int t, final int r, final int b) {
        super.onLayout(changed, l, t, r, b);

        textView.setDropDownHorizontalOffset(-textView.getLeft());
        textView.setDropDownWidth(this.getWidth());
        textView.setDropDownVerticalOffset((int) res.getDimension(R.dimen.text_padding_vertical_verylax));
    }

    private void setup(final Context context) {
        inflate(context, R.layout.location_view, this);
        textView = findViewById(R.id.location_view_text);
        textView.setOnItemClickListener((parent, view, position, id) -> {
            // workaround for NPE
            if (parent == null)
                return;

            final Location location = (Location) parent.getItemAtPosition(position);

            // workaround for NPE
            if (location == null)
                return;

            setLocation(location);

            fireChanged();
            afterLocationViewInput(false);
        });

        textChangedListener = new TextWatcher() {
            public void afterTextChanged(final Editable s) {
            }

            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
            }

            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
                locationType = LocationType.ANY;
                id = null;
                hint = null;
                location = null;
                updateAppearance();

                locationHelper.stop();
            }
        };
        textView.addTextChangedListener(textChangedListener);

        typeButtons = findViewById(R.id.location_view_type_buttons);
        menuButton = findViewById(R.id.location_view_menu_button);
        contactButton = findViewById(R.id.location_view_contact_button);
        alternateSearchButton = findViewById(R.id.location_view_alternate_search_button);
        favoriteStationButton = findViewById(R.id.location_view_favorite_station_button);
        currentLocationButton = findViewById(R.id.location_view_current_location_button);
        menuButton.setOnClickListener((view) -> {
            final PopupMenu popupMenu = new PopupMenu(getContext(), view);
            popupMenu.inflate(R.menu.directions_location_context);
            PopupHelper.setForceShowIcon(popupMenu);
            popupMenu.setOnMenuItemClickListener(item -> {
                final int itemId = item.getItemId();
                if (itemId == R.id.directions_location_current_location)
                    setToCurrentLocation();
                else if (itemId == R.id.directions_location_contact)
                    selectFromContacts();
                else if (itemId == R.id.directions_location_favorite_station)
                    selectFromFavoriteStation();
                else if (itemId == R.id.directions_location_alternate_search)
                    toggleAlternateSearchProviderNominatim();
                else
                    return false;
                return true;
            });
            popupMenu.show();
        });
        contactButton.setOnClickListener(v -> selectFromContacts());
        favoriteStationButton.setOnClickListener(v -> selectFromFavoriteStation());
        currentLocationButton.setOnClickListener(v -> setToCurrentLocation());
        alternateSearchButton.setOnClickListener(v -> toggleAlternateSearchProviderNominatim());


        clearButton = findViewById(R.id.location_view_clear_button);
        clearButton.setOnClickListener((view) -> {
            reset();
            textView.requestFocus();
        });

        modeButton = findViewById(R.id.location_view_mode_button);
        modeButton.setOnClickListener((view) -> {
            if (locationType == LocationType.STATION && stationAsAddressEnabled) {
                stationAsAddress = !stationAsAddress;
                updateAppearance();
            }
        });

        updateAppearance();
        setEnabled(isEnabled());
    }

    private final ActivityResultLauncher<String> requestLocationPermissionLauncher =
            getActivity().registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted)
                    acquireLocation();
            });
    private static class PickContact extends ActivityResultContract<Void, Uri> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context, final Void unused) {
            return new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI);
        }

        @Override
        public Uri parseResult(final int resultCode, @Nullable final Intent intent) {
            if (resultCode == Activity.RESULT_OK && intent != null)
                return intent.getData();
            else
                return null;
        }
    }
    private final ActivityResultLauncher<Void> pickContactLauncher =
            getActivity().registerForActivityResult(new PickContact(), contentUri -> {
                if (contentUri == null)
                    return;
                final OeffiActivity activity = getActivity();
                try {
                    final Cursor c = activity.managedQuery(contentUri, null, null, null, null);
                    if (c.moveToFirst()) {
                        final String data = c
                                .getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS))
                                .replace("\n", " ");
                        log.info("Picked from contacts: {}", data);
                        // old code: targetLocationView.setLocation(new Location(LocationType.ADDRESS, null, null, data));
                        final AutoCompleteLocationsHandler autoCompleteLocationsHandler = new AutoCompleteLocationsHandler(
                                activity,
                                listener.getNetwork(), listener.getHandler(), listener.getPreferredProducts());
                        autoCompleteLocationsHandler.addJob(data, this);
                        autoCompleteLocationsHandler.start(result -> {
                            if (result.success)
                                fireChanged();
                        });
                    }
                } catch (final Exception e) {
                    log.error("cannot get contact from result", e);
                    DialogBuilder.get(activity)
                            .setMessage(activity.getString(R.string.acquire_location_contacts_app_failed_return_data))
                            .create().show();
                }
            });

    private void selectFromContacts() {
        final OeffiActivity activity = getActivity();
        try {
            pickContactLauncher.launch(null);
        } catch (final Exception e) {
            log.error("contact app not accessible", e);
            DialogBuilder.get(activity)
                    .setMessage(activity.getString(R.string.acquire_location_contacts_app_not_supported))
                    .create().show();
        }
    }

    private final ActivityResultLauncher<NetworkId> pickStationLauncher =
            getActivity().registerForActivityResult(new FavoriteStationsActivity.PickFavoriteStation(), contentUri -> {
                if (contentUri == null)
                    return;
                final Cursor c = getActivity().managedQuery(contentUri, null, null, null, null);
                if (c.moveToFirst()) {
                    final Location location = FavoriteStationsProvider.getLocation(c).getNick();
                    log.info("Picked {} from station favorites", location);
                    setLocation(location);
                }
            });

    private void selectFromFavoriteStation() {
        final NetworkId network = listener.getNetwork();
        if (network != null)
            pickStationLauncher.launch(network);
    }

    public void setToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            acquireLocation();
        } else {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);
        if (textView != null) {
            textView.setEnabled(enabled);
            textView.setTextColor(getResources().getColor(enabled ? R.color.fg_significant : R.color.fg_insignificant));
        }
        if (menuButton != null)
            menuButton.setEnabled(enabled);
        if (contactButton != null)
            contactButton.setEnabled(enabled);
        if (alternateSearchButton != null)
            alternateSearchButton.setEnabled(enabled);
        if (favoriteStationButton != null)
            favoriteStationButton.setEnabled(enabled);
        if (currentLocationButton != null)
            currentLocationButton.setEnabled(enabled);
        if (clearButton != null)
            clearButton.setEnabled(enabled);
    }

    public void setText(final CharSequence text) {
        final int threshold = textView.getThreshold();
        textView.setThreshold(Integer.MAX_VALUE);
        textView.removeTextChangedListener(textChangedListener);
        textView.setText(text);
        textView.addTextChangedListener(textChangedListener);
        textView.setThreshold(threshold);
    }

    public String getText() {
        final String text = textView.getText().toString().trim();
        return text.isEmpty() ? null : text;
    }

    public void setHint(final int hintRes) {
        this.hintRes = hintRes;
        updateAppearance();
    }

    private void setAdapter(final AutoCompleteLocationAdapter autoCompleteAdapter) {
        this.autoCompleteLocationAdapter = autoCompleteAdapter;
        textView.setAdapter(autoCompleteAdapter);
    }

    public void setImeOptions(final int imeOptions) {
        textView.setImeOptions(imeOptions | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    }

    public void setOnEditorActionListener(final OnEditorActionListener onEditorActionListener) {
        textView.setOnEditorActionListener(onEditorActionListener);
    }

    public void setListener(final Listener listener) {
        this.listener = listener;
        setAdapter(new AutoCompleteLocationAdapter(this, listener.getNetwork()));
    }

    public void acquireLocation() {
        if (!locationHelper.isRunning()) {
            final Criteria criteria = new Criteria();
            criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
            criteria.setAccuracy(Criteria.ACCURACY_COARSE);
            locationHelper.startLocation(criteria, true, Constants.LOCATION_FOREGROUND_UPDATE_TIMEOUT_MS);
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
        location = null;
        locationType = LocationType.ANY;
        stationAsAddress = false;
        id = null;
        coord = null;
        place = null;
        setText(null);
        hint = null;
        autoCompleteLocationAdapter.resetFilters();
        resetBehaviour();
        updateAppearance();
        fireChanged();

        locationHelper.stop();
    }

    public void setLocation(final Location location) {
        this.location = location;
        if (location == null) {
            reset();
            return;
        }
        locationType = location.type;
        stationAsAddress = false;
        id = location.id;
        coord = location.coord;
        place = location.place;
        setText(Formats.makeBreakableStationName(Formats.fullLocationName(location, true)));
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

    public void convertToGeoLocation(final boolean force, final boolean removeIdWhenConverting) {
        final Location convertedLocation = getLocation(force || stationAsAddressEnabled, removeIdWhenConverting);
        setLocation(convertedLocation);
    }

    public @Nullable Location getLocation() {
        return getLocation(stationAsAddress, false);
    }

    public @Nullable Location getLocation(final boolean convertStationToAddress, final boolean removeIdWhenConverting) {
        if (location != null) {
            if (locationType == LocationType.STATION && convertStationToAddress)
                return new Location(LocationType.ADDRESS,
                        removeIdWhenConverting ? null : location.id,
                        location.coord, location.place, location.name);
            else
                return location;
        }

        final String name = getText();

        if (locationType == LocationType.COORD && coord == null)
            return null;
        else if (locationType == LocationType.ANY && (name == null || name.isEmpty()))
            return null;
        else if (locationType == LocationType.STATION && convertStationToAddress)
            return new Location(LocationType.ADDRESS,
                    removeIdWhenConverting ? null : id,
                    coord, name != null ? place : null, name);
        else
            return new Location(locationType, id, coord, name != null ? place : null, name);
    }

    private void updateAppearance() {
        final int drawableId;
        if (locationType == LocationType.COORD && coord == null)
            drawableId = R.drawable.ic_location_searching_grey600_24dp;
        else if (locationType == LocationType.STATION && stationAsAddress)
            drawableId = locationTypeIconRes(LocationType.ADDRESS);
        else
            drawableId = locationTypeIconRes(locationType);
        modeButton.setImageDrawable(res.getDrawable(drawableId));

        if (getText() == null) {
            typeButtons.setVisibility(View.VISIBLE);
            clearButton.setVisibility(View.GONE);
        } else {
            typeButtons.setVisibility(View.GONE);
            clearButton.setVisibility(View.VISIBLE);
        }

        if (hint != null)
            textView.setHint(hint);
        else if (hintRes != 0)
            textView.setHint(hintRes);
        else
            textView.setHint(null);
    }

    public void refreshAutoCompleteResults() {
        // only from API 29 ... textView.refreshAutoCompleteResults();
        final int selectionStart = textView.getSelectionStart();
        final int selectionEnd = textView.getSelectionEnd();
        textView.setText(textView.getText());
        textView.setSelection(selectionStart, selectionEnd);
    }

    private void afterLocationViewInput(final boolean blockActionGo) {
        int action = textView.getImeOptions() & EditorInfo.IME_MASK_ACTION;
        if (blockActionGo && action == EditorInfo.IME_ACTION_GO)
            action = 0;
        if (action != 0)
            textView.onEditorAction(action);
    }

    public void exchangeWith(final LocationView other) {
        final Location tempLocation = other.location;
        final LocationType tempLocationType = other.locationType;
        final boolean tempStationAsAddress = other.stationAsAddress;
        final String tempId = other.id;
        final Point tempCoord = other.coord;
        final String tempPlace = other.place;
        final String tempText = other.getText();
        final String tempHint = other.hint;

        other.location = this.location;
        other.locationType = this.locationType;
        other.stationAsAddress = this.stationAsAddress;
        other.id = this.id;
        other.coord = this.coord;
        other.place = this.place;
        other.setText(this.getText());
        other.hint = this.hint;

        this.location = tempLocation;
        this.locationType = tempLocationType;
        this.stationAsAddress = tempStationAsAddress;
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
            listener.changed(this);
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

    public OeffiActivity getActivity() {
        return (OeffiActivity) getContext();
    }
}
