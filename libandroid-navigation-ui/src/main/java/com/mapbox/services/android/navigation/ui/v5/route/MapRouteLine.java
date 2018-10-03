package com.mapbox.services.android.navigation.ui.v5.route;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.StyleRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.content.res.AppCompatResources;

import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.api.directions.v5.models.RouteLeg;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.style.expressions.Expression;
import com.mapbox.mapboxsdk.style.layers.Layer;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.Property;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonOptions;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.services.android.navigation.ui.v5.R;
import com.mapbox.services.android.navigation.ui.v5.utils.MapImageUtils;
import com.mapbox.services.android.navigation.ui.v5.utils.MapUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static com.mapbox.mapboxsdk.style.expressions.Expression.color;
import static com.mapbox.mapboxsdk.style.expressions.Expression.exponential;
import static com.mapbox.mapboxsdk.style.expressions.Expression.get;
import static com.mapbox.mapboxsdk.style.expressions.Expression.interpolate;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;
import static com.mapbox.mapboxsdk.style.expressions.Expression.match;
import static com.mapbox.mapboxsdk.style.expressions.Expression.stop;
import static com.mapbox.mapboxsdk.style.expressions.Expression.switchCase;
import static com.mapbox.mapboxsdk.style.expressions.Expression.zoom;
import static com.mapbox.mapboxsdk.style.layers.Property.NONE;
import static com.mapbox.mapboxsdk.style.layers.Property.VISIBLE;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.visibility;
import static com.mapbox.services.android.navigation.ui.v5.route.RouteConstants.ROUTE_LAYER_ID;
import static com.mapbox.services.android.navigation.ui.v5.route.RouteConstants.ROUTE_SHIELD_LAYER_ID;
import static com.mapbox.services.android.navigation.ui.v5.route.RouteConstants.ROUTE_SOURCE_ID;
import static com.mapbox.services.android.navigation.ui.v5.route.RouteConstants.WAYPOINT_LAYER_ID;
import static com.mapbox.services.android.navigation.ui.v5.route.RouteConstants.WAYPOINT_SOURCE_ID;

class MapRouteLine {

  @StyleRes
  private final int styleRes;
  @ColorInt
  private int routeDefaultColor;
  @ColorInt
  private int routeModerateColor;
  @ColorInt
  private int routeSevereColor;
  @ColorInt
  private int alternativeRouteDefaultColor;
  @ColorInt
  private int alternativeRouteModerateColor;
  @ColorInt
  private int alternativeRouteSevereColor;
  @ColorInt
  private int alternativeRouteShieldColor;
  @ColorInt
  private int routeShieldColor;
  @DrawableRes
  private int originWaypointIcon;
  @DrawableRes
  private int destinationWaypointIcon;
  private float routeScale;
  private float alternativeRouteScale;

  private final HashMap<LineString, DirectionsRoute> routeLineStrings = new HashMap<>();
  private final List<FeatureCollection> routeFeatureCollections = new ArrayList<>();
  private final List<DirectionsRoute> directionsRoutes = new ArrayList<>();
  private final MapboxMap mapboxMap;
  private final Drawable originIcon;
  private final Drawable destinationIcon;
  private final GeoJsonSource wayPointSource;
  private final GeoJsonSource routeLineSource;
  private final List<Layer> layers = new ArrayList<>();

  private FeatureCollection wayPointFeatureCollection;
  private String belowLayer;
  private int primaryRouteIndex;
  private boolean alternativesVisible = true;

  MapRouteLine(Context context, MapboxMap mapboxMap, int styleRes, String belowLayer) {
    this.mapboxMap = mapboxMap;
    this.styleRes = styleRes;
    this.belowLayer = belowLayer;

    obtainStyledAttributes(context);
    originIcon = AppCompatResources.getDrawable(context, originWaypointIcon);
    destinationIcon = AppCompatResources.getDrawable(context, destinationWaypointIcon);
    findRouteBelowLayerId();

    GeoJsonOptions wayPointGeoJsonOptions = new GeoJsonOptions().withMaxZoom(16);
    FeatureCollection emptyWayPointFeatureCollection = FeatureCollection.fromFeatures(new Feature[] {});
    wayPointSource = new GeoJsonSource(WAYPOINT_SOURCE_ID, emptyWayPointFeatureCollection, wayPointGeoJsonOptions);
    mapboxMap.addSource(wayPointSource);

    GeoJsonOptions routeLineGeoJsonOptions = new GeoJsonOptions().withMaxZoom(16);
    FeatureCollection emptyRouteLineFeatureCollection = FeatureCollection.fromFeatures(new Feature[] {});
    routeLineSource = new GeoJsonSource(ROUTE_SOURCE_ID, emptyRouteLineFeatureCollection, routeLineGeoJsonOptions);
    mapboxMap.addSource(routeLineSource);

    initializeLayers(mapboxMap);
  }

  void draw(DirectionsRoute directionsRoute) {
    List<DirectionsRoute> route = new ArrayList<>();
    route.add(directionsRoute);
    draw(route);
  }

  void draw(List<DirectionsRoute> directionsRoutes) {
    clearRouteData();
    this.directionsRoutes.addAll(directionsRoutes);
    primaryRouteIndex = 0;
    alternativesVisible = directionsRoutes.size() > 1;
    generateRouteFeatureCollectionsFrom(directionsRoutes);
  }

  void redraw() {
    findRouteBelowLayerId();
    drawRoutes();
    wayPointSource.setGeoJson(wayPointFeatureCollection);
    toggleAlternativeVisibilityWith(alternativesVisible);
  }

  void toggleAlternativeVisibilityWith(boolean alternativesVisible) {
    this.alternativesVisible = alternativesVisible;
    updateAlternativeLayersVisibilityTo(alternativesVisible);
  }

  void updateVisibilityTo(boolean isVisible) {
    updateAllLayersVisibilityTo(isVisible);
  }

  List<DirectionsRoute> retrieveDirectionsRoutes() {
    return directionsRoutes;
  }

  boolean updatePrimaryRouteIndex(int primaryRouteIndex) {
    boolean isNewIndex = this.primaryRouteIndex != primaryRouteIndex;
    if (isNewIndex) {
      this.primaryRouteIndex = primaryRouteIndex;
      updateRoutesForNewIndex();
    }
    return isNewIndex;
  }

  int retrievePrimaryRouteIndex() {
    return primaryRouteIndex;
  }

  HashMap<LineString, DirectionsRoute> retrieveRouteLineStrings() {
    return routeLineStrings;
  }

  void onDestroy() {
    removeAllLayers();
  }

  private void drawRoutes() {
    for (int i = routeFeatureCollections.size() - 1; i >= 0; i--) {
      routeLineSource.setGeoJson(routeFeatureCollections.get(i));
    }
  }

  private void clearRouteData() {
    removeAllLayers();
    clearRouteListData();
    resetSource(wayPointSource);
    resetSource(routeLineSource);
  }

  private void removeAllLayers() {
    if (!layers.isEmpty()) {
      for (Layer layer : layers) {
        mapboxMap.removeLayer(layer);
      }
    }
    layers.clear();
  }

  private void clearRouteListData() {
    if (!directionsRoutes.isEmpty()) {
      directionsRoutes.clear();
    }
    if (!routeLineStrings.isEmpty()) {
      routeLineStrings.clear();
    }
    if (!routeFeatureCollections.isEmpty()) {
      routeFeatureCollections.clear();
    }
  }

  private void generateRouteFeatureCollectionsFrom(List<DirectionsRoute> routes) {
    new FeatureProcessingTask(routes, new OnRouteFeaturesProcessedCallback() {
      @Override
      public void onRouteFeaturesProcessed(List<FeatureCollection> routeFeatureCollections,
                                           HashMap<LineString, DirectionsRoute> routeLineStrings) {
        MapRouteLine.this.routeFeatureCollections.addAll(routeFeatureCollections);
        MapRouteLine.this.routeLineStrings.putAll(routeLineStrings);
        drawRoutes();
        drawWayPoints();
      }
    }).execute();
  }

  // Way Points

  private void drawWayPoints() {
    DirectionsRoute primaryRoute = directionsRoutes.get(primaryRouteIndex);
    wayPointFeatureCollection = buildWayPointFeatureCollectionFrom(primaryRoute);
    wayPointSource.setGeoJson(wayPointFeatureCollection);
  }

  private FeatureCollection buildWayPointFeatureCollectionFrom(DirectionsRoute route) {
    final List<Feature> wayPointFeatures = new ArrayList<>();
    for (RouteLeg leg : route.legs()) {
      wayPointFeatures.add(buildWayPointFeatureFromLeg(leg, 0));
      wayPointFeatures.add(buildWayPointFeatureFromLeg(leg, leg.steps().size() - 1));
    }
    return FeatureCollection.fromFeatures(wayPointFeatures);
  }

  private Feature buildWayPointFeatureFromLeg(RouteLeg leg, int index) {
    Feature feature = Feature.fromGeometry(Point.fromLngLat(
      leg.steps().get(index).maneuver().location().longitude(),
      leg.steps().get(index).maneuver().location().latitude()
    ));
    feature.addStringProperty("waypoint", index == 0 ? "origin" : "destination");
    return feature;
  }

  private void updateRoutesForNewIndex() {
    for (int i = 0; i < routeFeatureCollections.size(); i++) {
      String shieldLayerId = String.format(
        Locale.US, RouteConstants.ID_FORMAT, ROUTE_SHIELD_LAYER_ID, i
      );
      updatePrimaryShieldRoute(shieldLayerId, i);

      String routeLayerId = String.format(
        Locale.US, RouteConstants.ID_FORMAT, ROUTE_LAYER_ID, i
      );
      updatePrimaryRoute(routeLayerId, i);
    }
  }

  private void updatePrimaryRoute(String layerId, int index) {
    Layer layer = mapboxMap.getLayer(layerId);
    if (layer != null) {
      layer.setProperties(
        PropertyFactory.lineColor(match(
          Expression.toString(get(RouteConstants.CONGESTION_KEY)),
          color(index == primaryRouteIndex ? routeDefaultColor : alternativeRouteDefaultColor),
          stop("moderate", color(index == primaryRouteIndex ? routeModerateColor : alternativeRouteModerateColor)),
          stop("heavy", color(index == primaryRouteIndex ? routeSevereColor : alternativeRouteSevereColor)),
          stop("severe", color(index == primaryRouteIndex ? routeSevereColor : alternativeRouteSevereColor))
          )
        )
      );
      if (index == primaryRouteIndex) {
        mapboxMap.removeLayer(layer);
        mapboxMap.addLayerBelow(layer, WAYPOINT_LAYER_ID);
      }
    }
  }

  private void updatePrimaryShieldRoute(String layerId, int index) {
    Layer layer = mapboxMap.getLayer(layerId);
    if (layer != null) {
      layer.setProperties(
        PropertyFactory.lineColor(index == primaryRouteIndex ? routeShieldColor : alternativeRouteShieldColor)
      );
      if (index == primaryRouteIndex) {
        mapboxMap.removeLayer(layer);
        mapboxMap.addLayerBelow(layer, WAYPOINT_LAYER_ID);
      }
    }
  }

  private void findRouteBelowLayerId() {
    if (belowLayer == null || belowLayer.isEmpty()) {
      List<Layer> styleLayers = mapboxMap.getLayers();
      for (int i = 0; i < styleLayers.size(); i++) {
        if (!(styleLayers.get(i) instanceof SymbolLayer)
          // Avoid placing the route on top of the user location layer
          && !styleLayers.get(i).getId().contains(RouteConstants.MAPBOX_LOCATION_ID)) {
          belowLayer = styleLayers.get(i).getId();
        }
      }
    }
  }

  private void obtainStyledAttributes(Context context) {
    TypedArray typedArray = context.obtainStyledAttributes(styleRes, R.styleable.NavigationMapRoute);

    // Primary Route attributes
    routeDefaultColor = typedArray.getColor(R.styleable.NavigationMapRoute_routeColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_layer_blue));
    routeModerateColor = typedArray.getColor(
      R.styleable.NavigationMapRoute_routeModerateCongestionColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_layer_congestion_yellow));
    routeSevereColor = typedArray.getColor(
      R.styleable.NavigationMapRoute_routeSevereCongestionColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_layer_congestion_red));
    routeShieldColor = typedArray.getColor(R.styleable.NavigationMapRoute_routeShieldColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_shield_layer_color));
    routeScale = typedArray.getFloat(R.styleable.NavigationMapRoute_routeScale, 1.0f);

    // Secondary Routes attributes
    alternativeRouteDefaultColor = typedArray.getColor(
      R.styleable.NavigationMapRoute_alternativeRouteColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_alternative_color));
    alternativeRouteModerateColor = typedArray.getColor(
      R.styleable.NavigationMapRoute_alternativeRouteModerateCongestionColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_alternative_congestion_yellow));
    alternativeRouteSevereColor = typedArray.getColor(
      R.styleable.NavigationMapRoute_alternativeRouteSevereCongestionColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_alternative_congestion_red));
    alternativeRouteShieldColor = typedArray.getColor(
      R.styleable.NavigationMapRoute_alternativeRouteShieldColor,
      ContextCompat.getColor(context, R.color.mapbox_navigation_route_alternative_shield_color));
    alternativeRouteScale = typedArray.getFloat(
      R.styleable.NavigationMapRoute_alternativeRouteScale, 1.0f);

    // Waypoint attributes
    originWaypointIcon = typedArray.getResourceId(
      R.styleable.NavigationMapRoute_originWaypointIcon, R.drawable.ic_route_origin);
    destinationWaypointIcon = typedArray.getResourceId(
      R.styleable.NavigationMapRoute_destinationWaypointIcon, R.drawable.ic_route_destination);

    typedArray.recycle();
  }

  private void initializeLayers(MapboxMap mapboxMap) {
    SymbolLayer wayPointLayer = initializeWayPointLayer(mapboxMap);
    layers.add(wayPointLayer);

    LineLayer routeShieldLayer = initializeRouteShieldLayer(mapboxMap);
    layers.add(routeShieldLayer);

    LineLayer routeLayer = initializeRouteLayer(mapboxMap);
    layers.add(routeLayer);
  }

  private SymbolLayer initializeWayPointLayer(@NonNull MapboxMap mapboxMap) {
    SymbolLayer waypointLayer = mapboxMap.getLayerAs(WAYPOINT_LAYER_ID);
    if (waypointLayer != null) {
      mapboxMap.removeLayer(waypointLayer);
    }

    Bitmap bitmap = MapImageUtils.getBitmapFromDrawable(originIcon);
    mapboxMap.addImage("originMarker", bitmap);
    bitmap = MapImageUtils.getBitmapFromDrawable(destinationIcon);
    mapboxMap.addImage("destinationMarker", bitmap);

    waypointLayer = new SymbolLayer(WAYPOINT_LAYER_ID, WAYPOINT_SOURCE_ID)
      .withProperties(PropertyFactory.iconImage(match(
        Expression.toString(get("waypoint")), literal("originMarker"),
        stop("origin", literal("originMarker")),
        stop("destination", literal("destinationMarker"))
        )
        ),
        PropertyFactory.iconSize(interpolate(
          exponential(1.5f), zoom(),
          stop(0f, 0.6f),
          stop(10f, 0.8f),
          stop(12f, 1.3f),
          stop(22f, 2.8f)
        )),
        PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true)
      );
    MapUtils.addLayerToMap(mapboxMap, waypointLayer, belowLayer);
    return waypointLayer;
  }

  private LineLayer initializeRouteShieldLayer(MapboxMap mapboxMap) {
    LineLayer shieldLayer = mapboxMap.getLayerAs(ROUTE_SHIELD_LAYER_ID);
    if (shieldLayer != null) {
      mapboxMap.removeLayer(shieldLayer);
    }

    shieldLayer = new LineLayer(ROUTE_SHIELD_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
      PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
      PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
      PropertyFactory.lineColor(
        switchCase(
          get("primary-route"), interpolate(
            exponential(1.5f), zoom(),
            stop(10f, 7f),
            stop(14f, 10.5f * routeScale),
            stop(16.5f, 15.5f * routeScale),
            stop(19f, 24f * routeScale),
            stop(22f, 29f * routeScale)
          ),
          interpolate(
            exponential(1.5f), zoom(),
            stop(10f, 7f),
            stop(14f, 10.5f * alternativeRouteScale),
            stop(16.5f, 15.5f * alternativeRouteScale),
            stop(19f, 24f * alternativeRouteScale),
            stop(22f, 29f * alternativeRouteScale)
          )
        )
      ),
      // TODO fix switch here
//      PropertyFactory.lineColor(
//        switchCase(
//          get("primary-route"), color(routeShieldColor),
//          color(alternativeRouteShieldColor)
//        )
//      )
      PropertyFactory.lineColor(color(routeShieldColor))
    );
    MapUtils.addLayerToMap(mapboxMap, shieldLayer, belowLayer);
    return shieldLayer;
  }

  private LineLayer initializeRouteLayer(MapboxMap mapboxMap) {
    LineLayer routeLayer = mapboxMap.getLayerAs(ROUTE_LAYER_ID);
    if (routeLayer != null) {
      mapboxMap.removeLayer(routeLayer);
    }

    routeLayer = new LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
      PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
      PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
      PropertyFactory.lineColor(
        switchCase(
          get("primary-route"), interpolate(
            exponential(1.5f), zoom(),
            stop(4f, 3f * routeScale),
            stop(10f, 4f * routeScale),
            stop(13f, 6f * routeScale),
            stop(16f, 10f * routeScale),
            stop(19f, 14f * routeScale),
            stop(22f, 18f * routeScale)
          ),
          interpolate(
            exponential(1.5f), zoom(),
            stop(10f, 7f),
            stop(14f, 10.5f * alternativeRouteScale),
            stop(16.5f, 15.5f * alternativeRouteScale),
            stop(19f, 24f * alternativeRouteScale),
            stop(22f, 29f * alternativeRouteScale)
          )
        )
      ),
      PropertyFactory.lineColor(
        switchCase(
          get("primary-route"), match(
            Expression.toString(get(RouteConstants.CONGESTION_KEY)),
            color(routeDefaultColor),
            stop("moderate", color(routeModerateColor)),
            stop("heavy", color(routeSevereColor)),
            stop("severe", color(routeSevereColor))
          ),
          match(
            Expression.toString(get(RouteConstants.CONGESTION_KEY)),
            color(alternativeRouteDefaultColor),
            stop("moderate", color(alternativeRouteModerateColor)),
            stop("heavy", color(alternativeRouteSevereColor)),
            stop("severe", color(alternativeRouteSevereColor))
          )
        )
      )
    );
    MapUtils.addLayerToMap(mapboxMap, routeLayer, belowLayer);
    return routeLayer;
  }

  private void updateAlternativeLayersVisibilityTo(boolean isVisible) {
    for (Layer layer : layers) {
      // TODO detect alternative
//      if (layerId.contains(String.valueOf(primaryRouteIndex))
//        || layerId.contains(WAYPOINT_LAYER_ID)) {
//        continue;
//      }
      layer.setProperties(
        visibility(isVisible ? VISIBLE : NONE)
      );
    }
  }

  private void updateAllLayersVisibilityTo(boolean isVisible) {
    for (Layer layer : layers) {
      layer.setProperties(
        visibility(isVisible ? VISIBLE : NONE)
      );
    }
  }

  private void resetSource(GeoJsonSource source) {
    source.setGeoJson(FeatureCollection.fromFeatures(new Feature[]{}));
  }
}