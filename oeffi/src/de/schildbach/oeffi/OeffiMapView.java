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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.FillType;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import de.schildbach.oeffi.stations.LineView;
import de.schildbach.oeffi.stations.Station;
import de.schildbach.oeffi.util.ViewUtils;
import de.schildbach.oeffi.util.ZoomControls;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.dto.Trip.Leg;
import de.schildbach.pte.dto.Trip.Public;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class OeffiMapView extends MapView {
    public static final String PREF_KEY_MAP_TILE_RESOLUTION = "user_interface_map_tile_resolution";

    private ZoomControls zoomControls = null;
    private FromViaToAware fromViaToAware = null;
    private TripAware tripAware = null;
    private StationsAware stationsAware = null;
    private LocationAware locationAware = null;
    private AreaAware areaAware = null;
    private boolean firstLocation = true;
    private boolean zoomLocked = true;

    private final int AREA_FILL_COLOR = Color.parseColor("#22000000");
    private final Animation zoomControlsAnimation;

    public OeffiMapView(final Context context) {
        this(context, null);
    }

    public OeffiMapView(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        final Resources res = context.getResources();
        final LayoutInflater inflater = LayoutInflater.from(context);

        final float stationFontSize = res.getDimension(R.dimen.font_size_normal);
        final float tripStrokeWidth = res.getDimension(R.dimen.map_trip_stroke_width);
        final float tripStrokeWidthSelected = res.getDimension(R.dimen.map_trip_stroke_width_selected);
        final float tripStrokeWidthSelectedGlow = res.getDimension(R.dimen.map_trip_stroke_width_selected_glow);

        final Drawable startIcon = drawablePointer(R.drawable.ic_maps_indicator_startpoint_list, 2);
        final Drawable pointIcon = drawableCenter(R.drawable.ic_maps_product_default, 2);
        final Drawable endIcon = drawablePointer(R.drawable.ic_maps_indicator_endpoint_list, 2);

        final Drawable deviceLocationIcon = drawableCenter(R.drawable.location_on, 2);
        final Drawable referenceLocationIcon = drawablePointer(R.drawable.da_marker_red, 2);

        final Drawable stationDefaultIcon = drawableCenter(R.drawable.ic_maps_product_default, 2);
        final Drawable stationHighspeedIcon = drawableCenter(R.drawable.product_highspeed_color_22dp, 2);
        final Drawable stationTrainIcon = drawableCenter(R.drawable.product_train_color_22dp, 2);
        final Drawable stationSuburbanIcon = drawableCenter(R.drawable.product_suburban_color_22dp, 2);
        final Drawable stationSubwayIcon = drawableCenter(R.drawable.product_subway_color_22dp, 2);
        final Drawable stationTramIcon = drawableCenter(R.drawable.product_tram_color_22dp, 2);
        final Drawable stationBusIcon = drawableCenter(R.drawable.product_bus_color_22dp, 2);
        final Drawable stationFerryIcon = drawableCenter(R.drawable.product_ferry_color_22dp, 2);
        final Drawable stationCablecarIcon = drawableCenter(R.drawable.product_cablecar_color_22dp, 2);
        final Drawable stationCallIcon = drawableCenter(R.drawable.product_call_color_22dp, 2);

        zoomControlsAnimation = AnimationUtils.loadAnimation(context, R.anim.zoom_controls);
        zoomControlsAnimation.setFillAfter(true); // workaround: set through code because XML does not work

        setBuiltInZoomControls(false);
        setMultiTouchControls(true);

        final float scaleRange = 2.0f;
        final int mapTileResolutionPref = Application.getInstance().getSharedPreferences().getInt(PREF_KEY_MAP_TILE_RESOLUTION, 100);
        final float mapTileResolution = (float) (mapTileResolutionPref - 100) / 100f * scaleRange;
        final float mapTileScaleFactor = mapTileResolution > 0 ? (1f / (1f + mapTileResolution)) : (1f - mapTileResolution);
        setTilesScaleFactor(mapTileScaleFactor);
        setTilesScaledToDpi(true);

        getController().setZoom(Constants.INITIAL_MAP_ZOOM_LEVEL);
        setMinZoomLevel(Constants.MAP_MIN_ZOOM_LEVEL);
        setMaxZoomLevel(Constants.MAP_MAX_ZOOM_LEVEL);

        getOverlays().add(new Overlay() {
            @Override
            public void draw(final Canvas canvas, final MapView mapView, final boolean shadow) {
                if (!shadow) {
                    final Projection projection = mapView.getProjection();
                    final android.graphics.Point point = new android.graphics.Point();

                    if (areaAware != null) {
                        final Point[] area = areaAware.getArea();
                        if (area != null) {
                            final Paint paint = new Paint();
                            paint.setAntiAlias(true);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(AREA_FILL_COLOR);

                            final Path path = pointsToPath(projection, area);
                            path.close();

                            path.setFillType(FillType.INVERSE_WINDING);

                            canvas.drawPath(path, paint);
                        }
                    }

                    if (fromViaToAware != null) {
                        final List<Point> path = new ArrayList<>(3);
                        final Point from = fromViaToAware.getFrom();
                        if (from != null)
                            path.add(from);
                        final Point via = fromViaToAware.getVia();
                        if (via != null)
                            path.add(via);
                        final Point to = fromViaToAware.getTo();
                        if (to != null)
                            path.add(to);

                        if (path.size() >= 2) {
                            final Paint paint = new Paint();
                            paint.setAntiAlias(true);
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setStrokeJoin(Paint.Join.ROUND);
                            paint.setStrokeCap(Paint.Cap.ROUND);

                            paint.setColor(Color.DKGRAY);
                            paint.setAlpha(92);
                            paint.setStrokeWidth(tripStrokeWidth);
                            canvas.drawPath(pointsToPath(projection, path), paint);
                        }

                        if (from != null) {
                            projection.toPixels(new GeoPoint(from.getLatAsDouble(), from.getLonAsDouble()), point);
                            drawAt(canvas, startIcon, point.x, point.y, false, 0);
                        }

                        if (to != null) {
                            projection.toPixels(new GeoPoint(to.getLatAsDouble(), to.getLonAsDouble()), point);
                            drawAt(canvas, endIcon, point.x, point.y, false, 0);
                        }
                    }

                    if (tripAware != null) {
                        final Trip trip = tripAware.getTrip();

                        final Paint paint = new Paint();
                        paint.setAntiAlias(true);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeJoin(Paint.Join.ROUND);
                        paint.setStrokeCap(Paint.Cap.ROUND);

                        // first paint all unselected legs
                        for (final Trip.Leg leg : trip.legs) {
                            if (!tripAware.isSelectedLeg(leg)) {
                                final Path path = pointsToPath(projection, leg.path);

                                paint.setColor(leg instanceof Public ? Color.MAGENTA : Color.DKGRAY);
                                paint.setAlpha(92);
                                paint.setStrokeWidth(tripStrokeWidth);
                                canvas.drawPath(path, paint);
                            }
                        }

                        // then paint selected legs
                        for (final Trip.Leg leg : trip.legs) {
                            if (tripAware.isSelectedLeg(leg)) {
                                final List<Point> points = leg.path;
                                final Path path = pointsToPath(projection, points);

                                paint.setColor(Color.GREEN);
                                paint.setAlpha(92);
                                paint.setStrokeWidth(tripStrokeWidthSelectedGlow);
                                canvas.drawPath(path, paint);

                                paint.setColor(leg instanceof Public ? Color.RED : Color.DKGRAY);
                                paint.setAlpha(128);
                                paint.setStrokeWidth(tripStrokeWidthSelected);
                                canvas.drawPath(path, paint);

                                if (leg instanceof Public && !points.isEmpty()) {
                                    final Public publicLeg = (Public) leg;

                                    final double lat;
                                    final double lon;

                                    final int size = points.size();
                                    if (size >= 2) {
                                        final int pivot = size / 2;
                                        final Point p1 = points.get(pivot - 1);
                                        final Point p2 = points.get(pivot);
                                        lat = (p1.getLatAsDouble() + p2.getLatAsDouble()) / 2.0;
                                        lon = (p1.getLonAsDouble() + p2.getLonAsDouble()) / 2.0;
                                    } else if (size == 1) {
                                        final Point p = points.get(0);
                                        lat = p.getLatAsDouble();
                                        lon = p.getLonAsDouble();
                                    } else {
                                        lat = 0;
                                        lon = 0;
                                    }
                                    projection.toPixels(new GeoPoint(lat, lon), point);

                                    final LineView lineView = (LineView) inflater.inflate(R.layout.map_trip_line, null);
                                    lineView.setLine(publicLeg.line);
                                    lineView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                                            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                                    final int width = lineView.getMeasuredWidth();
                                    final int height = lineView.getMeasuredHeight();
                                    lineView.layout(point.x - width / 2, point.y - height / 2, point.x + width / 2,
                                            point.y + height / 2);
                                    // since point.x is ignored in layout (why?), we need to translate canvas
                                    // ourselves
                                    canvas.save();
                                    canvas.translate(point.x - width / 2, point.y - height / 2);
                                    lineView.draw(canvas);
                                    canvas.restore();
                                }
                            }
                        }

                        // then paint decorators
                        final Leg firstLeg = trip.legs.get(0);
                        final Leg lastLeg = trip.legs.get(trip.legs.size() - 1);

                        for (final Trip.Leg leg : trip.legs) {
                            if (!leg.path.isEmpty()) {
                                final Point firstPoint = leg.path.get(0);
                                final Point lastPoint = leg.path.get(leg.path.size() - 1);

                                if (firstPoint == lastPoint) {
                                    projection.toPixels(
                                            new GeoPoint(firstPoint.getLatAsDouble(), firstPoint.getLonAsDouble()),
                                            point);
                                    drawAt(canvas, startIcon, point.x, point.y, false, 0);
                                } else if (leg == firstLeg || leg == lastLeg) {
                                    if (leg == firstLeg) {
                                        projection.toPixels(
                                                new GeoPoint(firstPoint.getLatAsDouble(), firstPoint.getLonAsDouble()),
                                                point);
                                        drawAt(canvas, startIcon, point.x, point.y, false, 0);
                                    }

                                    if (leg == lastLeg) {
                                        projection.toPixels(
                                                new GeoPoint(lastPoint.getLatAsDouble(), lastPoint.getLonAsDouble()),
                                                point);
                                        drawAt(canvas, endIcon, point.x, point.y, false, 0);
                                    }
                                } else {
                                    projection.toPixels(
                                            new GeoPoint(firstPoint.getLatAsDouble(), firstPoint.getLonAsDouble()),
                                            point);
                                    drawAt(canvas, pointIcon, point.x, point.y, false, 0);
                                    projection.toPixels(
                                            new GeoPoint(lastPoint.getLatAsDouble(), lastPoint.getLonAsDouble()),
                                            point);
                                    drawAt(canvas, pointIcon, point.x, point.y, false, 0);
                                }
                            }
                        }
                    }

                    if (locationAware != null) {
                        final Point deviceLocation = locationAware.getDeviceLocation();
                        if (deviceLocation != null) {
                            projection.toPixels(
                                    new GeoPoint(deviceLocation.getLatAsDouble(), deviceLocation.getLonAsDouble()),
                                    point);
                            drawAt(canvas, deviceLocationIcon, point.x, point.y, false, 0);
                        }

                        final Location referenceLocation = locationAware.getReferenceLocation();
                        if (referenceLocation != null) {
                            projection.toPixels(new GeoPoint(referenceLocation.getLatAsDouble(),
                                    referenceLocation.getLonAsDouble()), point);
                            drawAt(canvas, referenceLocationIcon, point.x, point.y, false, 0);
                        }
                    }

                    if (stationsAware != null) {
                        final List<Station> stations = stationsAware.getStations();
                        if (stations != null) {
                            Station selectedStation = null;

                            for (final Station station : stations) {
                                if (station.location.hasCoord()) {
                                    projection.toPixels(new GeoPoint(station.location.getLatAsDouble(),
                                            station.location.getLonAsDouble()), point);

                                    if (stationsAware.isSelectedStation(station.location.id))
                                        selectedStation = station;

                                    final Product product = station.getRelevantProduct();
                                    if (product == null)
                                        drawAt(canvas, stationDefaultIcon, point.x, point.y, false, 0);
                                    else if (product == Product.HIGH_SPEED_TRAIN)
                                        drawAt(canvas, stationHighspeedIcon, point.x, point.y, false, 0);
                                    else if (product == Product.REGIONAL_TRAIN)
                                        drawAt(canvas, stationTrainIcon, point.x, point.y, false, 0);
                                    else if (product == Product.SUBURBAN_TRAIN)
                                        drawAt(canvas, stationSuburbanIcon, point.x, point.y, false, 0);
                                    else if (product == Product.SUBWAY)
                                        drawAt(canvas, stationSubwayIcon, point.x, point.y, false, 0);
                                    else if (product == Product.TRAM)
                                        drawAt(canvas, stationTramIcon, point.x, point.y, false, 0);
                                    else if (product == Product.BUS)
                                        drawAt(canvas, stationBusIcon, point.x, point.y, false, 0);
                                    else if (product == Product.FERRY)
                                        drawAt(canvas, stationFerryIcon, point.x, point.y, false, 0);
                                    else if (product == Product.CABLECAR)
                                        drawAt(canvas, stationCablecarIcon, point.x, point.y, false, 0);
                                    else if (product == Product.ON_DEMAND)
                                        drawAt(canvas, stationCallIcon, point.x, point.y, false, 0);
                                    else
                                        drawAt(canvas, stationDefaultIcon, point.x, point.y, false, 0);
                                }
                            }

                            if (selectedStation != null) {
                                projection.toPixels(new GeoPoint(selectedStation.location.getLatAsDouble(),
                                        selectedStation.location.getLonAsDouble()), point);
                                final TextView bubble = new TextView(getContext());
                                bubble.setBackgroundResource(R.drawable.popup_dir_pointer_button);
                                bubble.setText(selectedStation.location.name);
                                bubble.setTypeface(Typeface.DEFAULT_BOLD);
                                bubble.setTextSize(TypedValue.COMPLEX_UNIT_PX, stationFontSize);
                                bubble.setTextColor(res.getColor(R.color.fg_significant_on_light));
                                bubble.setIncludeFontPadding(false);
                                bubble.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                                final int width = bubble.getMeasuredWidth();
                                final int height = bubble.getMeasuredHeight();
                                bubble.layout(point.x - width / 2, point.y - height / 2, point.x + width / 2,
                                        point.y + height / 2);
                                // since point.x is ignored in layout (why?), we need to translate canvas
                                // ourselves
                                canvas.save();
                                canvas.translate(point.x - width / 2,
                                        point.y - height - stationDefaultIcon.getIntrinsicHeight() / 2.5f);
                                bubble.draw(canvas);
                                canvas.restore();
                            }
                        }
                    }
                }
            }

            private Path pointsToPath(final Projection projection, final List<Point> points) {
                final Path path = new Path();
                path.incReserve(points.size());

                final android.graphics.Point point = new android.graphics.Point();

                for (final Point p : points) {
                    projection.toPixels(new GeoPoint(p.getLatAsDouble(), p.getLonAsDouble()), point);
                    if (path.isEmpty())
                        path.moveTo(point.x, point.y);
                    else
                        path.lineTo(point.x, point.y);
                }

                return path;
            }

            private Path pointsToPath(final Projection projection, final Point[] points) {
                final Path path = new Path();
                path.incReserve(points.length);

                final android.graphics.Point point = new android.graphics.Point();

                for (final Point p : points) {
                    projection.toPixels(new GeoPoint(p.getLatAsDouble(), p.getLonAsDouble()), point);
                    if (path.isEmpty())
                        path.moveTo(point.x, point.y);
                    else
                        path.lineTo(point.x, point.y);
                }

                return path;
            }

            @Override
            public boolean onSingleTapConfirmed(final MotionEvent e, final MapView mapView) {
                final IGeoPoint p = mapView.getProjection().fromPixels((int) e.getX(), (int) e.getY());
                final double tappedLat = p.getLatitude();
                final double tappedLon = p.getLongitude();
                boolean consumed = false;

                final float[] distanceBetweenResults = new float[1];

                if (tripAware != null) {
                    int tappedLegIndex = -1;
                    float tappedPointDistance = 0;

                    int iRoute = 0;
                    for (final Leg leg : tripAware.getTrip().legs) {
                        for (final Point point : leg.path) {
                            android.location.Location.distanceBetween(tappedLat, tappedLon, point.getLatAsDouble(),
                                    point.getLonAsDouble(), distanceBetweenResults);
                            final float distance = distanceBetweenResults[0];
                            if (tappedLegIndex == -1 || distance < tappedPointDistance) {
                                tappedLegIndex = iRoute;
                                tappedPointDistance = distance;
                            }
                        }

                        iRoute++;
                    }

                    if (tappedLegIndex != -1) {
                        tripAware.selectLeg(tappedLegIndex);
                        consumed = true;
                    }
                }

                if (stationsAware != null) {
                    Station tappedStation = null;
                    float tappedStationDistance = 0;

                    for (final Station station : stationsAware.getStations()) {
                        android.location.Location.distanceBetween(tappedLat, tappedLon,
                                station.location.getLatAsDouble(), station.location.getLonAsDouble(),
                                distanceBetweenResults);
                        final float distance = distanceBetweenResults[0];
                        if (tappedStation == null || distance < tappedStationDistance) {
                            tappedStation = station;
                            tappedStationDistance = distance;
                        }
                    }

                    if (locationAware != null) {
                        if (tappedStation == null) {
                            stationsAware.selectStation(null);
                            consumed = true;
                        } else {
                            final Point deviceLocation = locationAware.getDeviceLocation();
                            if (deviceLocation != null) {
                                android.location.Location.distanceBetween(tappedLat, tappedLon,
                                        deviceLocation.getLatAsDouble(), deviceLocation.getLonAsDouble(),
                                        distanceBetweenResults);
                                final float distance = distanceBetweenResults[0];
                                if (distance < tappedStationDistance) {
                                    stationsAware.selectStation(null);
                                    consumed = true;
                                }
                            }
                        }
                    }

                    if (!consumed && tappedStation != null) {
                        stationsAware.selectStation(tappedStation);
                        consumed = true;
                    }
                }

                return consumed;
            }
        });
    }

    public void setZoomControls(final ZoomControls zoomControls) {
        this.zoomControls = zoomControls;
        zoomControls.setOnZoomInClickListener(v -> {
            showZoomControls();
            getController().zoomIn();
        });
        zoomControls.setOnZoomOutClickListener(v -> {
            showZoomControls();
            getController().zoomOut();
        });
    }

    public void setFromViaToAware(final FromViaToAware fromViaToAware) {
        this.fromViaToAware = fromViaToAware;
        invalidate();
    }

    public void setTripAware(final TripAware tripAware) {
        this.tripAware = tripAware;
        invalidate();
    }

    public void setStationsAware(final StationsAware stationsAware) {
        this.stationsAware = stationsAware;
        invalidate();
    }

    public void setLocationAware(final LocationAware locationAware) {
        this.locationAware = locationAware;
        invalidate();
    }

    public void setAreaAware(final AreaAware areaAware) {
        this.areaAware = areaAware;
        invalidate();
    }

    public void animateToLocation(final double lat, final double lon) {
        if (!ViewUtils.isVisible(this))
            return;

        if (lat == 0 && lon == 0)
            return;

        final GeoPoint point = new GeoPoint(lat, lon);

        if (firstLocation)
            getController().setCenter(point);
        else
            getController().animateTo(point);

        firstLocation = false;
    }

    public void zoomToAll() {
        if (!ViewUtils.isVisible(this))
            return;

        zoomLocked = true;

        final boolean hasLegSelection = tripAware != null && tripAware.hasSelection();
        final List<IGeoPoint> points = new LinkedList<>();

        if (areaAware != null) {
            final Point[] area = areaAware.getArea();
            if (area != null) {
                for (final Point p : area)
                    points.add(new GeoPoint(p.getLatAsDouble(), p.getLonAsDouble()));
            }
        }

        if (fromViaToAware != null) {
            final Point from = fromViaToAware.getFrom();
            if (from != null)
                points.add(new GeoPoint(from.getLatAsDouble(), from.getLonAsDouble()));
            final Point via = fromViaToAware.getVia();
            if (via != null)
                points.add(new GeoPoint(via.getLatAsDouble(), via.getLonAsDouble()));
            final Point to = fromViaToAware.getTo();
            if (to != null)
                points.add(new GeoPoint(to.getLatAsDouble(), to.getLonAsDouble()));
        }

        if (tripAware != null) {
            for (final Leg leg : tripAware.getTrip().legs) {
                if (!hasLegSelection || tripAware.isSelectedLeg(leg)) {
                    for (final Point p : leg.path)
                        points.add(new GeoPoint(p.getLatAsDouble(), p.getLonAsDouble()));
                }
            }
        }

        if (locationAware != null && !hasLegSelection) {
            final Location referenceLocation = locationAware.getReferenceLocation();
            if (referenceLocation != null) {
                points.add(new GeoPoint(referenceLocation.getLatAsDouble(), referenceLocation.getLonAsDouble()));
            } else {
                final Point location = locationAware.getDeviceLocation();
                if (location != null)
                    points.add(new GeoPoint(location.getLatAsDouble(), location.getLonAsDouble()));
            }
        }

        if (!points.isEmpty()) {
            final BoundingBox boundingBox;
            if (points.size() == 1)
                boundingBox = BoundingBox.fromGeoPoints(Arrays.asList(points.get(0), getMapCenter()));
            else
                boundingBox = BoundingBox.fromGeoPoints(points);
            zoomToBoundingBox(boundingBox.increaseByScale(1.3f), !firstLocation);

            firstLocation = false;
        }
    }

    public void zoomToStations(final List<Station> stations) {
        if (!ViewUtils.isVisible(this))
            return;

        if (stations.isEmpty())
            return;

        // show at most 16 stations
        final List<GeoPoint> points = new LinkedList<>();
        for (final Station station : stations) {
            final Location location = station.location;
            if (location.hasCoord()) {
                points.add(new GeoPoint(location.getLatAsDouble(), location.getLonAsDouble()));
                if (points.size() >= 16)
                    break;
            }
        }

        // make sure a minimum area is shown
        final GeoPoint centerPoint = points.get(0);
        final float delta = 0.002f;
        points.add(new GeoPoint(centerPoint.getLatitude() - delta, centerPoint.getLongitude() - delta));
        points.add(new GeoPoint(centerPoint.getLatitude() + delta, centerPoint.getLongitude() + delta));
        final BoundingBox boundingBox = BoundingBox.fromGeoPoints(points).increaseByScale(1.05f);

        // zoom
        zoomToBoundingBox(boundingBox, !firstLocation);
        firstLocation = false;
    }

    private void showZoomControls() {
        zoomControls.clearAnimation();
        zoomControls.startAnimation(zoomControlsAnimation);
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        if (zoomLocked)
            zoomToAll();

        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent ev) {
        zoomLocked = false;
        if (zoomControls != null)
            showZoomControls();

        return super.onTouchEvent(ev);
    }

    private Drawable drawablePointer(final int resId, final int sizeDivider) {
        final Resources res = getResources();
        final Drawable drawable = res.getDrawable(resId);
        drawable.setBounds(-drawable.getIntrinsicWidth() / sizeDivider, -drawable.getIntrinsicHeight(),
                drawable.getIntrinsicWidth() / sizeDivider, 0);
        return drawable;
    }

    private Drawable drawableCenter(final int resId, final int sizeDivider) {
        final Resources res = getResources();
        final Drawable drawable = res.getDrawable(resId);
        drawable.setBounds(-drawable.getIntrinsicWidth() / sizeDivider, -drawable.getIntrinsicHeight() / sizeDivider,
                drawable.getIntrinsicWidth() / sizeDivider, drawable.getIntrinsicHeight() / sizeDivider);
        return drawable;
    }
}
