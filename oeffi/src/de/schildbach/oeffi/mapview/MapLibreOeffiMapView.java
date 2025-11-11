package de.schildbach.oeffi.mapview;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import org.maplibre.android.maps.MapView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.AreaAware;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.FromViaToAware;
import de.schildbach.oeffi.LocationAware;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.StationsAware;
import de.schildbach.oeffi.TripAware;
import de.schildbach.oeffi.stations.LineView;
import de.schildbach.oeffi.stations.Station;
import de.schildbach.oeffi.util.GeoUtils;
import de.schildbach.oeffi.util.GeocoderThread;
import de.schildbach.oeffi.util.ViewUtils;
import de.schildbach.oeffi.util.ZoomControls;
import de.schildbach.oeffi.util.locationview.LocationTextView;
import de.schildbach.oeffi.util.locationview.LocationView;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.Trip;

//public class MapLibreOeffiMapView extends MapView implements OeffiMapView.Implementation{
//    private static final Logger log = LoggerFactory.getLogger(MapLibreOeffiMapView.class);
//
//    private static final String PREF_KEY_MAP_TILE_RESOLUTION = "user_interface_map_tile_resolution";
//
//    public static class Provider implements OeffiMapView.Provider {
//        @Override
//        public void init() {
//            final Application application = Application.getInstance();
//            final IConfigurationProvider config = Configuration.getInstance();
//            config.setMapLibreBasePath(new File(application.getCacheDir(), "org.mapLibre"));
//            config.setUserAgentValue(application.getPackageName());
//        }
//
//        @Override
//        public OeffiMapView.Implementation createView(final Context context, final AttributeSet attrs) {
//            return new MapLibreOeffiMapView(context, attrs);
//        }
//    }
//
//    @Override
//    public View getView() {
//        return this;
//    }
//
//    private ZoomControls zoomControls = null;
//    private FromViaToAware fromViaToAware = null;
//    private TripAware tripAware = null;
//    private StationsAware stationsAware = null;
//    private LocationAware locationAware = null;
//    private AreaAware areaAware = null;
//    private boolean firstLocation = true;
//    private boolean zoomLocked = true;
//
//    private final int AREA_FILL_COLOR = Color.parseColor("#22000000");
//    private final Animation zoomControlsAnimation;
//
//    public MapLibreOeffiMapView(final Context context) {
//        this(context, null);
//    }
//
//    public MapLibreOeffiMapView(final Context context, final AttributeSet attrs) {
//        super(context, attrs);
//
//        zoomControlsAnimation = AnimationUtils.loadAnimation(context, R.anim.zoom_controls);
//        zoomControlsAnimation.setFillAfter(true); // workaround: set through code because XML does not work
//
//        setBuiltInZoomControls(false);
//        setMultiTouchControls(true);
//
//        final float scaleRange = 2.0f;
//        final int mapTileResolutionPref = Application.getInstance().getSharedPreferences().getInt(PREF_KEY_MAP_TILE_RESOLUTION, 100);
//        final float mapTileResolution = (float) (mapTileResolutionPref - 100) / 100f * scaleRange;
//        final float mapTileScaleFactor = mapTileResolution > 0 ? (1f / (1f + mapTileResolution)) : (1f - mapTileResolution);
//        setTilesScaleFactor(mapTileScaleFactor);
//        setTilesScaledToDpi(true);
//
//        getController().setZoom(Constants.INITIAL_MAP_ZOOM_LEVEL);
//        setMinZoomLevel(Constants.MAP_MIN_ZOOM_LEVEL);
//        setMaxZoomLevel(Constants.MAP_MAX_ZOOM_LEVEL);
//
//        getOverlays().add(new MapLibreOeffiMapView.DefaultOverlay());
//    }
//
//    @Override
//    public void removeAllContent() {
//        removeAllViews();
//    }
//
//    public void setZoomControls(final ZoomControls zoomControls) {
//        this.zoomControls = zoomControls;
//        zoomControls.setOnZoomInClickListener(v -> {
//            showZoomControls();
//            getController().zoomIn();
//        });
//        zoomControls.setOnZoomOutClickListener(v -> {
//            showZoomControls();
//            getController().zoomOut();
//        });
//    }
//
//    public void setFromViaToAware(final FromViaToAware fromViaToAware) {
//        this.fromViaToAware = fromViaToAware;
//        invalidate();
//    }
//
//    public void setTripAware(final TripAware tripAware) {
//        this.tripAware = tripAware;
//        invalidate();
//    }
//
//    public void setStationsAware(final StationsAware stationsAware) {
//        this.stationsAware = stationsAware;
//        invalidate();
//    }
//
//    public void setLocationAware(final LocationAware locationAware) {
//        this.locationAware = locationAware;
//        invalidate();
//    }
//
//    public void setAreaAware(final AreaAware areaAware) {
//        this.areaAware = areaAware;
//        invalidate();
//    }
//
//    public void setDirectionsOverlay(
//            final LocationView viewFromLocation,
//            final LocationView viewToLocation) {
//        getOverlays().add(new MapLibreOeffiMapView.DirectionsMapLibreOverlay(viewFromLocation, viewToLocation));
//    }
//
//    public void animateToLocation(final double lat, final double lon) {
//        if (lat == 0 && lon == 0)
//            return;
//
//        final GeoPoint point = new GeoPoint(lat, lon);
//
//        if (firstLocation || !ViewUtils.isVisible(this))
//            getController().setCenter(point);
//        else
//            getController().animateTo(point);
//
//        firstLocation = false;
//    }
//
//    public void zoomToAll() {
//        if (!ViewUtils.isVisible(this))
//            return;
//
//        zoomLocked = true;
//
//        final boolean hasLegSelection = tripAware != null && tripAware.hasSelection();
//        final List<IGeoPoint> points = new LinkedList<>();
//
//        if (areaAware != null) {
//            final Point[] area = areaAware.getArea();
//            if (area != null) {
//                for (final Point p : area)
//                    points.add(new GeoPoint(p.getLatAsDouble(), p.getLonAsDouble()));
//            }
//        }
//
//        if (fromViaToAware != null) {
//            final Point from = fromViaToAware.getFrom();
//            if (from != null)
//                points.add(new GeoPoint(from.getLatAsDouble(), from.getLonAsDouble()));
//            final Point via = fromViaToAware.getVia();
//            if (via != null)
//                points.add(new GeoPoint(via.getLatAsDouble(), via.getLonAsDouble()));
//            final Point to = fromViaToAware.getTo();
//            if (to != null)
//                points.add(new GeoPoint(to.getLatAsDouble(), to.getLonAsDouble()));
//        }
//
//        if (tripAware != null) {
//            for (final Trip.Leg leg : tripAware.getTrip().legs) {
//                if (!hasLegSelection || tripAware.isSelectedLeg(leg)) {
//                    for (final Point p : leg.path)
//                        points.add(new GeoPoint(p.getLatAsDouble(), p.getLonAsDouble()));
//                }
//            }
//        }
//
//        if (locationAware != null && !hasLegSelection) {
//            final Location referenceLocation = locationAware.getReferenceLocation();
//            if (referenceLocation != null) {
//                points.add(new GeoPoint(referenceLocation.getLatAsDouble(), referenceLocation.getLonAsDouble()));
//            } else {
//                final Point location = locationAware.getDeviceLocation();
//                if (location != null)
//                    points.add(new GeoPoint(location.getLatAsDouble(), location.getLonAsDouble()));
//            }
//        }
//
//        if (!points.isEmpty()) {
//            final BoundingBox boundingBox;
//            if (points.size() == 1)
//                boundingBox = BoundingBox.fromGeoPoints(Arrays.asList(points.get(0), getMapCenter()));
//            else
//                boundingBox = BoundingBox.fromGeoPoints(points);
//            zoomToBoundingBox(boundingBox.increaseByScale(1.3f), !firstLocation);
//
//            firstLocation = false;
//        }
//    }
//
//    public void zoomToStations(final List<Station> stations) {
//        if (!ViewUtils.isVisible(this))
//            return;
//
//        if (stations.isEmpty())
//            return;
//
//        // show at most 16 stations
//        final List<GeoPoint> points = new LinkedList<>();
//        for (final Station station : stations) {
//            final Location location = station.location;
//            if (location.hasCoord()) {
//                points.add(new GeoPoint(location.getLatAsDouble(), location.getLonAsDouble()));
//                if (points.size() >= 16)
//                    break;
//            }
//        }
//
//        // make sure a minimum area is shown
//        final GeoPoint centerPoint = points.get(0);
//        final float delta = 0.002f;
//        points.add(new GeoPoint(centerPoint.getLatitude() - delta, centerPoint.getLongitude() - delta));
//        points.add(new GeoPoint(centerPoint.getLatitude() + delta, centerPoint.getLongitude() + delta));
//        final BoundingBox boundingBox = BoundingBox.fromGeoPoints(points).increaseByScale(1.05f);
//
//        // zoom
//        zoomToBoundingBox(boundingBox, !firstLocation);
//        firstLocation = false;
//    }
//
//    private void showZoomControls() {
//        zoomControls.clearAnimation();
//        zoomControls.startAnimation(zoomControlsAnimation);
//    }
//
//    @Override
//    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
//        if (zoomLocked)
//            zoomToAll();
//
//        super.onSizeChanged(w, h, oldw, oldh);
//    }
//
//    @Override
//    public boolean onTouchEvent(final MotionEvent ev) {
//        zoomLocked = false;
//        if (zoomControls != null)
//            showZoomControls();
//
//        return super.onTouchEvent(ev);
//    }
//
//    private Drawable drawablePointer(final int resId, final int sizeDivider) {
//        final Resources res = getResources();
//        final Drawable drawable = res.getDrawable(resId);
//        drawable.setBounds(-drawable.getIntrinsicWidth() / sizeDivider, -drawable.getIntrinsicHeight(),
//                drawable.getIntrinsicWidth() / sizeDivider, 0);
//        return drawable;
//    }
//
//    private Drawable drawableCenter(final int resId, final int sizeDivider) {
//        final Resources res = getResources();
//        final Drawable drawable = res.getDrawable(resId);
//        drawable.setBounds(-drawable.getIntrinsicWidth() / sizeDivider, -drawable.getIntrinsicHeight() / sizeDivider,
//                drawable.getIntrinsicWidth() / sizeDivider, drawable.getIntrinsicHeight() / sizeDivider);
//        return drawable;
//    }
//
//    @Override
//    public String getCopyrightNotice() {
//        return getTileProvider().getTileSource().getCopyrightNotice();
//    }
//
//    public class DefaultOverlay extends Overlay {
//        final float stationFontSize;
//        final float tripStrokeWidth;
//        final float tripStrokeWidthSelected;
//        final float tripStrokeWidthSelectedGlow;
//        final int bubbleTextColor;
//
//        final Drawable startIcon = drawablePointer(R.drawable.ic_maps_indicator_startpoint_list, 2);
//        final Drawable pointIcon = drawableCenter(R.drawable.ic_maps_product_default, 2);
//        final Drawable endIcon = drawablePointer(R.drawable.ic_maps_indicator_endpoint_list, 2);
//
//        final Drawable deviceLocationIcon = drawableCenter(R.drawable.location_on, 2);
//        final Drawable referenceLocationIcon = drawablePointer(R.drawable.da_marker_red, 2);
//
//        final Drawable stationDefaultIcon = drawableCenter(R.drawable.ic_maps_product_default, 2);
//        final Drawable stationHighspeedIcon = drawableCenter(R.drawable.product_highspeed_color_22dp, 2);
//        final Drawable stationTrainIcon = drawableCenter(R.drawable.product_train_color_22dp, 2);
//        final Drawable stationSuburbanIcon = drawableCenter(R.drawable.product_suburban_color_22dp, 2);
//        final Drawable stationSubwayIcon = drawableCenter(R.drawable.product_subway_color_22dp, 2);
//        final Drawable stationTramIcon = drawableCenter(R.drawable.product_tram_color_22dp, 2);
//        final Drawable stationBusIcon = drawableCenter(R.drawable.product_bus_color_22dp, 2);
//        final Drawable stationFerryIcon = drawableCenter(R.drawable.product_ferry_color_22dp, 2);
//        final Drawable stationCablecarIcon = drawableCenter(R.drawable.product_cablecar_color_22dp, 2);
//        final Drawable stationCallIcon = drawableCenter(R.drawable.product_call_color_22dp, 2);
//
//        private DefaultOverlay() {
//            final Resources res = getContext().getResources();
//
//            stationFontSize = res.getDimension(R.dimen.font_size_normal);
//            tripStrokeWidth = res.getDimension(R.dimen.map_trip_stroke_width);
//            tripStrokeWidthSelected = res.getDimension(R.dimen.map_trip_stroke_width_selected);
//            tripStrokeWidthSelectedGlow = res.getDimension(R.dimen.map_trip_stroke_width_selected_glow);
//            bubbleTextColor = res.getColor(R.color.fg_significant_on_light);
//        }
//        @Override
//        public void draw(final Canvas canvas, final MapView mapView, final boolean shadow) {
//            if (!shadow) {
//                final Projection projection = mapView.getProjection();
//                final android.graphics.Point point = new android.graphics.Point();
//
//                if (areaAware != null) {
//                    final Point[] area = areaAware.getArea();
//                    if (area != null) {
//                        final Paint paint = new Paint();
//                        paint.setAntiAlias(true);
//                        paint.setStyle(Paint.Style.FILL);
//                        paint.setColor(AREA_FILL_COLOR);
//
//                        final Path path = pointsToPath(projection, area);
//                        path.close();
//
//                        path.setFillType(Path.FillType.INVERSE_WINDING);
//
//                        canvas.drawPath(path, paint);
//                    }
//                }
//
//                if (fromViaToAware != null) {
//                    final List<Point> path = new ArrayList<>(3);
//                    final Point from = fromViaToAware.getFrom();
//                    if (from != null)
//                        path.add(from);
//                    final Point via = fromViaToAware.getVia();
//                    if (via != null)
//                        path.add(via);
//                    final Point to = fromViaToAware.getTo();
//                    if (to != null)
//                        path.add(to);
//
//                    if (path.size() >= 2) {
//                        final Paint paint = new Paint();
//                        paint.setAntiAlias(true);
//                        paint.setStyle(Paint.Style.STROKE);
//                        paint.setStrokeJoin(Paint.Join.ROUND);
//                        paint.setStrokeCap(Paint.Cap.ROUND);
//
//                        paint.setColor(Color.DKGRAY);
//                        paint.setAlpha(92);
//                        paint.setStrokeWidth(tripStrokeWidth);
//                        canvas.drawPath(pointsToPath(projection, path), paint);
//                    }
//
//                    if (from != null) {
//                        projection.toPixels(new GeoPoint(from.getLatAsDouble(), from.getLonAsDouble()), point);
//                        drawAt(canvas, startIcon, point.x, point.y, false, 0);
//                    }
//
//                    if (to != null) {
//                        projection.toPixels(new GeoPoint(to.getLatAsDouble(), to.getLonAsDouble()), point);
//                        drawAt(canvas, endIcon, point.x, point.y, false, 0);
//                    }
//                }
//
//                if (tripAware != null) {
//                    final Trip trip = tripAware.getTrip();
//
//                    final Paint paint = new Paint();
//                    paint.setAntiAlias(true);
//                    paint.setStyle(Paint.Style.STROKE);
//                    paint.setStrokeJoin(Paint.Join.ROUND);
//                    paint.setStrokeCap(Paint.Cap.ROUND);
//
//                    // first paint all unselected legs
//                    for (final Trip.Leg leg : trip.legs) {
//                        if (!tripAware.isSelectedLeg(leg)) {
//                            final Path path = pointsToPath(projection, leg.path);
//
//                            paint.setColor(leg instanceof Trip.Public ? Color.MAGENTA : Color.DKGRAY);
//                            paint.setAlpha(92);
//                            paint.setStrokeWidth(tripStrokeWidth);
//                            canvas.drawPath(path, paint);
//                        }
//                    }
//
//                    // then paint selected legs
//                    for (final Trip.Leg leg : trip.legs) {
//                        if (tripAware.isSelectedLeg(leg)) {
//                            final List<Point> points = leg.path;
//                            final Path path = pointsToPath(projection, points);
//
//                            paint.setColor(Color.GREEN);
//                            paint.setAlpha(92);
//                            paint.setStrokeWidth(tripStrokeWidthSelectedGlow);
//                            canvas.drawPath(path, paint);
//
//                            paint.setColor(leg instanceof Trip.Public ? Color.RED : Color.DKGRAY);
//                            paint.setAlpha(128);
//                            paint.setStrokeWidth(tripStrokeWidthSelected);
//                            canvas.drawPath(path, paint);
//
//                            if (leg instanceof Trip.Public && !points.isEmpty()) {
//                                final Trip.Public publicLeg = (Trip.Public) leg;
//
//                                final double lat;
//                                final double lon;
//
//                                final int size = points.size();
//                                if (size >= 2) {
//                                    final int pivot = size / 2;
//                                    final Point p1 = points.get(pivot - 1);
//                                    final Point p2 = points.get(pivot);
//                                    lat = (p1.getLatAsDouble() + p2.getLatAsDouble()) / 2.0;
//                                    lon = (p1.getLonAsDouble() + p2.getLonAsDouble()) / 2.0;
//                                } else if (size == 1) {
//                                    final Point p = points.get(0);
//                                    lat = p.getLatAsDouble();
//                                    lon = p.getLonAsDouble();
//                                } else {
//                                    lat = 0;
//                                    lon = 0;
//                                }
//                                projection.toPixels(new GeoPoint(lat, lon), point);
//
//                                final LineView lineView = (LineView) LayoutInflater.from(getContext()).inflate(R.layout.map_trip_line, null);
//                                lineView.setLine(publicLeg.line);
//                                lineView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
//                                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
//                                final int width = lineView.getMeasuredWidth();
//                                final int height = lineView.getMeasuredHeight();
//                                lineView.layout(point.x - width / 2, point.y - height / 2, point.x + width / 2,
//                                        point.y + height / 2);
//                                // since point.x is ignored in layout (why?), we need to translate canvas
//                                // ourselves
//                                canvas.save();
//                                canvas.translate(point.x - width / 2, point.y - height / 2);
//                                lineView.draw(canvas);
//                                canvas.restore();
//                            }
//                        }
//                    }
//
//                    // then paint decorators
//                    final Trip.Leg firstLeg = trip.legs.get(0);
//                    final Trip.Leg lastLeg = trip.legs.get(trip.legs.size() - 1);
//
//                    for (final Trip.Leg leg : trip.legs) {
//                        if (!leg.path.isEmpty()) {
//                            final Point firstPoint = leg.path.get(0);
//                            final Point lastPoint = leg.path.get(leg.path.size() - 1);
//
//                            if (firstPoint == lastPoint) {
//                                projection.toPixels(
//                                        new GeoPoint(firstPoint.getLatAsDouble(), firstPoint.getLonAsDouble()),
//                                        point);
//                                drawAt(canvas, startIcon, point.x, point.y, false, 0);
//                            } else if (leg == firstLeg || leg == lastLeg) {
//                                if (leg == firstLeg) {
//                                    projection.toPixels(
//                                            new GeoPoint(firstPoint.getLatAsDouble(), firstPoint.getLonAsDouble()),
//                                            point);
//                                    drawAt(canvas, startIcon, point.x, point.y, false, 0);
//                                }
//
//                                if (leg == lastLeg) {
//                                    projection.toPixels(
//                                            new GeoPoint(lastPoint.getLatAsDouble(), lastPoint.getLonAsDouble()),
//                                            point);
//                                    drawAt(canvas, endIcon, point.x, point.y, false, 0);
//                                }
//                            } else {
//                                projection.toPixels(
//                                        new GeoPoint(firstPoint.getLatAsDouble(), firstPoint.getLonAsDouble()),
//                                        point);
//                                drawAt(canvas, pointIcon, point.x, point.y, false, 0);
//                                projection.toPixels(
//                                        new GeoPoint(lastPoint.getLatAsDouble(), lastPoint.getLonAsDouble()),
//                                        point);
//                                drawAt(canvas, pointIcon, point.x, point.y, false, 0);
//                            }
//                        }
//                    }
//                }
//
//                if (locationAware != null) {
//                    final Point deviceLocation = locationAware.getDeviceLocation();
//                    if (deviceLocation != null) {
//                        projection.toPixels(
//                                new GeoPoint(deviceLocation.getLatAsDouble(), deviceLocation.getLonAsDouble()),
//                                point);
//                        drawAt(canvas, deviceLocationIcon, point.x, point.y, false, 0);
//                    }
//
//                    final Location referenceLocation = locationAware.getReferenceLocation();
//                    if (referenceLocation != null) {
//                        projection.toPixels(new GeoPoint(referenceLocation.getLatAsDouble(),
//                                referenceLocation.getLonAsDouble()), point);
//                        drawAt(canvas, referenceLocationIcon, point.x, point.y, false, 0);
//                    }
//                }
//
//                if (stationsAware != null) {
//                    final List<Station> stations = stationsAware.getStations();
//                    if (stations != null) {
//                        Station selectedStation = null;
//
//                        for (final Station station : stations) {
//                            if (station.location.hasCoord()) {
//                                projection.toPixels(new GeoPoint(station.location.getLatAsDouble(),
//                                        station.location.getLonAsDouble()), point);
//
//                                if (stationsAware.isSelectedStation(station.location.id))
//                                    selectedStation = station;
//
//                                final Drawable iconDrawable;
//                                final Product product = station.getRelevantProduct();
//                                if (product == null)
//                                    iconDrawable = stationDefaultIcon;
//                                else if (product == Product.HIGH_SPEED_TRAIN)
//                                    iconDrawable = stationHighspeedIcon;
//                                else if (product == Product.REGIONAL_TRAIN)
//                                    iconDrawable = stationTrainIcon;
//                                else if (product == Product.SUBURBAN_TRAIN)
//                                    iconDrawable = stationSuburbanIcon;
//                                else if (product == Product.SUBWAY)
//                                    iconDrawable = stationSubwayIcon;
//                                else if (product == Product.TRAM)
//                                    iconDrawable = stationTramIcon;
//                                else if (product == Product.BUS)
//                                    iconDrawable = stationBusIcon;
//                                else if (product == Product.FERRY)
//                                    iconDrawable = stationFerryIcon;
//                                else if (product == Product.CABLECAR)
//                                    iconDrawable = stationCablecarIcon;
//                                else if (product == Product.ON_DEMAND)
//                                    iconDrawable = stationCallIcon;
//                                else if (product == Product.REPLACEMENT_SERVICE)
//                                    iconDrawable = stationBusIcon;
//                                else
//                                    iconDrawable = stationDefaultIcon;
//                                drawAt(canvas, iconDrawable, point.x, point.y, false, 0);
//                            }
//                        }
//
//                        if (selectedStation != null) {
//                            projection.toPixels(new GeoPoint(selectedStation.location.getLatAsDouble(),
//                                    selectedStation.location.getLonAsDouble()), point);
//                            final TextView bubble = new TextView(getContext());
//                            bubble.setBackgroundResource(R.drawable.popup_dir_pointer_button);
//                            bubble.setText(selectedStation.location.name);
//                            bubble.setTypeface(Typeface.DEFAULT_BOLD);
//                            bubble.setTextSize(TypedValue.COMPLEX_UNIT_PX, stationFontSize);
//                            bubble.setTextColor(bubbleTextColor);
//                            bubble.setIncludeFontPadding(false);
//                            bubble.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
//                                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
//                            final int width = bubble.getMeasuredWidth();
//                            final int height = bubble.getMeasuredHeight();
//                            bubble.layout(point.x - width / 2, point.y - height / 2, point.x + width / 2,
//                                    point.y + height / 2);
//                            // since point.x is ignored in layout (why?), we need to translate canvas
//                            // ourselves
//                            canvas.save();
//                            canvas.translate(point.x - width / 2,
//                                    point.y - height - stationDefaultIcon.getIntrinsicHeight() / 2.5f);
//                            bubble.draw(canvas);
//                            canvas.restore();
//                        }
//                    }
//                }
//            }
//        }
//
//        private Path pointsToPath(final Projection projection, final List<Point> points) {
//            final Path path = new Path();
//            path.incReserve(points.size());
//
//            final android.graphics.Point point = new android.graphics.Point();
//
//            for (final Point p : points) {
//                projection.toPixels(new GeoPoint(p.getLatAsDouble(), p.getLonAsDouble()), point);
//                if (path.isEmpty())
//                    path.moveTo(point.x, point.y);
//                else
//                    path.lineTo(point.x, point.y);
//            }
//
//            return path;
//        }
//
//        private Path pointsToPath(final Projection projection, final Point[] points) {
//            final Path path = new Path();
//            path.incReserve(points.length);
//
//            final android.graphics.Point point = new android.graphics.Point();
//
//            for (final Point p : points) {
//                projection.toPixels(new GeoPoint(p.getLatAsDouble(), p.getLonAsDouble()), point);
//                if (path.isEmpty())
//                    path.moveTo(point.x, point.y);
//                else
//                    path.lineTo(point.x, point.y);
//            }
//
//            return path;
//        }
//
//        @Override
//        public boolean onSingleTapConfirmed(final MotionEvent e, final MapView mapView) {
//            final IGeoPoint p = mapView.getProjection().fromPixels((int) e.getX(), (int) e.getY());
//            final double tappedLat = p.getLatitude();
//            final double tappedLon = p.getLongitude();
//            boolean consumed = false;
//
//            if (tripAware != null) {
//                int tappedLegIndex = -1;
//                float tappedPointDistance = 0;
//
//                int iRoute = 0;
//                for (final Trip.Leg leg : tripAware.getTrip().legs) {
//                    for (final Point point : leg.path) {
//                        final float distance = GeoUtils.distanceBetween(tappedLat, tappedLon, point).distanceInMeters;
//                        if (tappedLegIndex == -1 || distance < tappedPointDistance) {
//                            tappedLegIndex = iRoute;
//                            tappedPointDistance = distance;
//                        }
//                    }
//
//                    iRoute++;
//                }
//
//                if (tappedLegIndex != -1) {
//                    tripAware.selectLeg(tappedLegIndex);
//                    consumed = true;
//                }
//            }
//
//            if (stationsAware != null) {
//                Station tappedStation = null;
//                float tappedStationDistance = 0;
//
//                for (final Station station : stationsAware.getStations()) {
//                    final float distance = GeoUtils.distanceBetween(tappedLat, tappedLon, station.location.coord).distanceInMeters;
//                    if (tappedStation == null || distance < tappedStationDistance) {
//                        tappedStation = station;
//                        tappedStationDistance = distance;
//                    }
//                }
//
//                if (locationAware != null) {
//                    if (tappedStation == null) {
//                        stationsAware.selectStation(null);
//                        consumed = true;
//                    } else {
//                        final Point deviceLocation = locationAware.getDeviceLocation();
//                        if (deviceLocation != null) {
//                            final float distance = GeoUtils.distanceBetween(tappedLat, tappedLon, deviceLocation).distanceInMeters;
//                            if (distance < tappedStationDistance) {
//                                stationsAware.selectStation(null);
//                                consumed = true;
//                            }
//                        }
//                    }
//                }
//
//                if (!consumed && tappedStation != null) {
//                    stationsAware.selectStation(tappedStation);
//                    consumed = true;
//                }
//            }
//
//            return consumed;
//        }
//    }
//
//    public class DirectionsMapLibreOverlay extends Overlay {
//        final LocationView viewFromLocation, viewToLocation;
//        private Location pinLocation;
//        private View pinView;
//
//        public DirectionsMapLibreOverlay(
//                final LocationView viewFromLocation, final LocationView viewToLocation) {
//            this.viewFromLocation = viewFromLocation;
//            this.viewToLocation = viewToLocation;
//        }
//
//        @Override
//        public void draw(final Canvas canvas, final MapView mapView, final boolean shadow) {
//            if (pinView != null)
//                pinView.requestLayout();
//        }
//
//        @Override
//        public boolean onSingleTapConfirmed(final MotionEvent e, final MapView mapView) {
//            final IGeoPoint p = mapView.getProjection().fromPixels((int) e.getX(), (int) e.getY());
//            pinLocation = Location.coord(Point.fromDouble(p.getLatitude(), p.getLongitude()));
//
//            final View view = LayoutInflater.from(getContext()).inflate(R.layout.directions_map_pin, null);
//            final LocationTextView locationView = view
//                    .findViewById(R.id.directions_map_pin_location);
//            final View buttonGroup = view.findViewById(R.id.directions_map_pin_buttons);
//            buttonGroup.findViewById(R.id.directions_map_pin_button_from).setOnClickListener(v -> {
//                viewFromLocation.setLocation(pinLocation);
//                mapView.removeAllViews();
//            });
//            buttonGroup.findViewById(R.id.directions_map_pin_button_to).setOnClickListener(v -> {
//                viewToLocation.setLocation(pinLocation);
//                mapView.removeAllViews();
//            });
//            locationView.setLocation(pinLocation);
//            locationView.setShowLocationType(false);
//
//            // exchange view for the pin
//            if (pinView != null)
//                mapView.removeView(pinView);
//            pinView = view;
//            mapView.addView(pinView, new MapView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
//                    ViewGroup.LayoutParams.WRAP_CONTENT, p, MapView.LayoutParams.BOTTOM_CENTER, 0, 0));
//
//            new GeocoderThread(getContext(), p.getLatitude(), p.getLongitude(),
//                    new GeocoderThread.Callback() {
//                        public void onGeocoderResult(final Address address) {
//                            pinLocation = GeocoderThread.addressToLocation(address);
//                            locationView.setLocation(pinLocation);
//                            locationView.setShowLocationType(false);
//                        }
//
//                        public void onGeocoderFail(final Exception exception) {
//                            log.info("Problem in geocoder: {}", exception.getMessage());
//                        }
//                    });
//
//            final IMapController controller = mapView.getController();
//            controller.animateTo(p);
//
//            return false;
//        }
//    }
//}
