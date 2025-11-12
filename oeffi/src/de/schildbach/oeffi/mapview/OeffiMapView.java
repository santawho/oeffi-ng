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

package de.schildbach.oeffi.mapview;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import java.util.List;

import de.schildbach.oeffi.AreaAware;
import de.schildbach.oeffi.FromViaToAware;
import de.schildbach.oeffi.DeviceLocationAware;
import de.schildbach.oeffi.StationsAware;
import de.schildbach.oeffi.TripAware;
import de.schildbach.oeffi.stations.Station;
import de.schildbach.oeffi.util.ZoomControls;
import de.schildbach.oeffi.util.locationview.LocationView;
import de.schildbach.pte.dto.Location;

public class OeffiMapView extends FrameLayout {
    public static Provider provider = new OsmDroidOeffiMapView.Provider();

    public interface Provider {
        void init();
        Implementation createView(final Context context, final AttributeSet attrs);
    }

    public interface Implementation {
        View getView();
        String getCopyrightNotice();
        void onStart();
        void onResume();
        void onPause();
        void onStop();
        void onDestroy();
        void setVisibility(int visibility);
        void removeAllContent();
        void animateToLocation(final double latitude, final double longitude);
        void setDeviceLocationAware(final DeviceLocationAware locationAware);
        void zoomToAll();
        void setDirectionsOverlay(
                final LocationView viewFromLocation,
                final LocationView viewToLocation);
        void setTripAware(final TripAware tripAware);
        void setAreaAware(final AreaAware areaAware);
        void setStationsAware(final StationsAware stationsAware);
        void zoomToStations(final List<Location> stationLocations);
        void setFromViaToAware(final FromViaToAware fromViaToAware);
        void setZoomControls(final ZoomControls zoom);
    }

    public static void init() {
        provider.init();
    }

    final Implementation viewImplementation;

    public OeffiMapView(final Context context) {
        this(context, null);
    }

    public OeffiMapView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OeffiMapView(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public OeffiMapView(final Context context, final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.viewImplementation = provider.createView(context, attrs);
        final View mapView = viewImplementation.getView();
        mapView.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        this.addView(mapView);
    }

    @Override
    public void setVisibility(final int visibility) {
        super.setVisibility(visibility);
        viewImplementation.setVisibility(visibility);
    }

    public String getCopyrightNotice() {
        return viewImplementation.getCopyrightNotice();
    }

    public void onStart() {
        viewImplementation.onStart();
    }

    public void onResume() {
        viewImplementation.onResume();
    }

    public void onPause() {
        viewImplementation.onPause();
    }

    public void onStop() {
        viewImplementation.onStop();
    }

    public void onDestroy() {
        viewImplementation.onDestroy();
    }

    public void removeAllContent() {
        viewImplementation.removeAllContent();
    }

    public void animateToLocation(final double latitude, final double longitude) {
        viewImplementation.animateToLocation(latitude, longitude);
    }

    public void setDeviceLocationAware(final DeviceLocationAware locationAware) {
        viewImplementation.setDeviceLocationAware(locationAware);
    }

    public void zoomToAll() {
        viewImplementation.zoomToAll();
    }

    public void setDirectionsOverlay(
            final LocationView viewFromLocation,
            final LocationView viewToLocation) {
        viewImplementation.setDirectionsOverlay(viewFromLocation, viewToLocation);
    }

    public void setTripAware(final TripAware tripAware) {
        viewImplementation.setTripAware(tripAware);
    }

    public void setAreaAware(final AreaAware areaAware) {
        viewImplementation.setAreaAware(areaAware);
    }

    public void setStationsAware(final StationsAware stationsAware) {
        viewImplementation.setStationsAware(stationsAware);
    }

    public void zoomToStations(final List<Location> stationLocations) {
        viewImplementation.zoomToStations(stationLocations);
    }

    public void setFromViaToAware(final FromViaToAware fromViaToAware) {
        viewImplementation.setFromViaToAware(fromViaToAware);
    }

    public void setZoomControls(final ZoomControls zoom) {
        viewImplementation.setZoomControls(zoom);
    }
}
