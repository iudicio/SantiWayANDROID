package com.example.santiway.activity_map;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.santiway.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.UrlTileProvider;

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

/**
 * Единый менеджер карт:
 * - хранит выбранный слой карты;
 * - применяет слой к OSM и Google Maps;
 * - создаёт общие кнопки + / -;
 * - создаёт маркеры нужного размера;
 * - хранит API key для Yandex Tiles.
 */
public final class MapLayerManager {
    private static final String PREFS_NAME = "AppSettings";

    private static final String KEY_LAYER_INDEX = "map_layer_index";
    private static final String KEY_MARKER_SIZE_DP = "map_marker_size_dp";
    private static final String KEY_YANDEX_API_KEY = "yandex_tiles_api_key";

    private static final int LAYER_OSM = 0;
    private static final int LAYER_GOOGLE_NORMAL = 1;
    private static final int LAYER_GOOGLE_SATELLITE = 2;
    private static final int LAYER_GOOGLE_HYBRID = 3;
    private static final int LAYER_YANDEX = 4;

    private static final int DEFAULT_MARKER_SIZE_DP = 34;

    private MapLayerManager() {
    }

    public interface DrawerStateListener {
        void onDrawerStateChanged(boolean open);
    }

    public static String[] layerLabels(Context context) {
        return new String[]{
                "OpenStreetMap",
                "Google Normal",
                "Google Satellite",
                "Google Hybrid",
                "Yandex Tiles"
        };
    }

    public static int savedLayerIndex(Context context) {
        if (context == null) return LAYER_OSM;

        int value = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_LAYER_INDEX, LAYER_OSM);

        if (value < 0 || value > LAYER_YANDEX) return LAYER_OSM;
        return value;
    }

    public static void saveLayerIndex(Context context, int layerIndex) {
        if (context == null) return;

        if (layerIndex < 0 || layerIndex > LAYER_YANDEX) {
            layerIndex = LAYER_OSM;
        }

        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAYER_INDEX, layerIndex)
                .apply();
    }

    public static int markerSizeDp(Context context) {
        if (context == null) return DEFAULT_MARKER_SIZE_DP;

        int value = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_MARKER_SIZE_DP, DEFAULT_MARKER_SIZE_DP);

        if (value < 12) return 12;
        if (value > 96) return 96;
        return value;
    }

    public static void saveMarkerSizeDp(Context context, int sizeDp) {
        if (context == null) return;

        if (sizeDp < 12) sizeDp = 12;
        if (sizeDp > 96) sizeDp = 96;

        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_MARKER_SIZE_DP, sizeDp)
                .apply();
    }

    public static String yandexApiKey(Context context) {
        if (context == null) return "";

        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_YANDEX_API_KEY, "");
    }

    public static void saveYandexApiKey(Context context, String apiKey) {
        if (context == null) return;

        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_YANDEX_API_KEY, apiKey == null ? "" : apiKey.trim())
                .apply();
    }

    public static void applySavedLayer(Context context, MapView mapView) {
        if (context == null || mapView == null) return;

        int layer = savedLayerIndex(context);

        if (layer == LAYER_YANDEX) {
            String apiKey = yandexApiKey(context);

            if (!apiKey.isEmpty()) {
                mapView.setTileSource(createYandexTileSource(apiKey));
            } else {
                mapView.setTileSource(TileSourceFactory.MAPNIK);
            }
        } else {
            mapView.setTileSource(TileSourceFactory.MAPNIK);
        }

        mapView.invalidate();
    }

    private static OnlineTileSourceBase createYandexTileSource(String apiKey) {
        return new OnlineTileSourceBase(
                "YandexTiles",
                1,
                20,
                256,
                ".png",
                new String[]{"https://tiles.api-maps.yandex.ru/v1/tiles/"}
        ) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                int zoom = MapTileIndex.getZoom(pMapTileIndex);
                int x = MapTileIndex.getX(pMapTileIndex);
                int y = MapTileIndex.getY(pMapTileIndex);

                return getBaseUrl()
                        + "?apikey=" + apiKey
                        + "&lang=ru_RU"
                        + "&l=map"
                        + "&x=" + x
                        + "&y=" + y
                        + "&z=" + zoom;
            }
        };
    }

    public static TileOverlay applyGoogleLayer(Context context, GoogleMap googleMap, TileOverlay currentOverlay) {
        if (googleMap == null) return currentOverlay;

        if (currentOverlay != null) {
            currentOverlay.remove();
            currentOverlay = null;
        }

        int layer = savedLayerIndex(context);

        switch (layer) {
            case LAYER_GOOGLE_SATELLITE:
                googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                break;

            case LAYER_GOOGLE_HYBRID:
                googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                break;

            case LAYER_YANDEX:
                googleMap.setMapType(GoogleMap.MAP_TYPE_NONE);

                String apiKey = yandexApiKey(context);
                if (!apiKey.isEmpty()) {
                    currentOverlay = googleMap.addTileOverlay(
                            new TileOverlayOptions().tileProvider(new UrlTileProvider(256, 256) {
                                @Override
                                public URL getTileUrl(int x, int y, int zoom) {
                                    String url = "https://tiles.api-maps.yandex.ru/v1/tiles/"
                                            + "?apikey=" + apiKey
                                            + "&lang=ru_RU"
                                            + "&l=map"
                                            + "&x=" + x
                                            + "&y=" + y
                                            + "&z=" + zoom;

                                    try {
                                        return new URL(url);
                                    } catch (MalformedURLException e) {
                                        return null;
                                    }
                                }
                            })
                    );
                } else {
                    googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                }
                break;

            case LAYER_GOOGLE_NORMAL:
            case LAYER_OSM:
            default:
                googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                break;
        }

        return currentOverlay;
    }

    public static View createOsmControls(
            Context context,
            MapView mapView,
            int topMarginDp,
            Runnable onLayerChanged,
            View... viewsToHide
    ) {
        return createOsmControls(context, mapView, topMarginDp, onLayerChanged, null, viewsToHide);
    }

    public static View createOsmControls(
            Context context,
            MapView mapView,
            int topMarginDp,
            Runnable onLayerChanged,
            DrawerStateListener drawerStateListener,
            View... viewsToHide
    ) {
        FrameLayout root = new FrameLayout(context);
        root.setClickable(false);

        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        root.setLayoutParams(rootParams);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setClickable(true);

        FrameLayout.LayoutParams buttonsParams = new FrameLayout.LayoutParams(
                dp(context, 46),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonsParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        buttonsParams.rightMargin = dp(context, 12);
        buttons.setLayoutParams(buttonsParams);

        TextView zoomIn = makeMapButton(context, "+");
        TextView zoomOut = makeMapButton(context, "−");
        TextView layer = makeMapButton(context, "☰");

        zoomIn.setOnClickListener(v -> {
            if (mapView != null) {
                mapView.getController().setZoom(mapView.getZoomLevelDouble() + 1.0);
            }
        });

        zoomOut.setOnClickListener(v -> {
            if (mapView != null) {
                mapView.getController().setZoom(mapView.getZoomLevelDouble() - 1.0);
            }
        });

        layer.setOnClickListener(v -> {
            cycleLayer(context);

            if (mapView != null) {
                applySavedLayer(context, mapView);
            }

            if (onLayerChanged != null) {
                onLayerChanged.run();
            }
        });

        buttons.addView(zoomIn);
        buttons.addView(zoomOut);
        buttons.addView(layer);

        root.addView(buttons);

        return root;
    }

    public static View createGoogleControls(
            Context context,
            GoogleMap googleMap,
            Runnable onLayerChanged,
            View unusedAnchor,
            int topMarginDp,
            DrawerStateListener drawerStateListener,
            View... viewsToHide
    ) {
        FrameLayout root = new FrameLayout(context);
        root.setClickable(false);

        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        root.setLayoutParams(rootParams);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setClickable(true);

        FrameLayout.LayoutParams buttonsParams = new FrameLayout.LayoutParams(
                dp(context, 46),
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonsParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        buttonsParams.rightMargin = dp(context, 12);
        buttons.setLayoutParams(buttonsParams);

        TextView zoomIn = makeMapButton(context, "+");
        TextView zoomOut = makeMapButton(context, "−");
        TextView layer = makeMapButton(context, "☰");

        zoomIn.setOnClickListener(v -> {
            if (googleMap != null) {
                googleMap.animateCamera(CameraUpdateFactory.zoomIn());
            }
        });

        zoomOut.setOnClickListener(v -> {
            if (googleMap != null) {
                googleMap.animateCamera(CameraUpdateFactory.zoomOut());
            }
        });

        layer.setOnClickListener(v -> {
            cycleLayer(context);

            if (onLayerChanged != null) {
                onLayerChanged.run();
            }
        });

        buttons.addView(zoomIn);
        buttons.addView(zoomOut);
        buttons.addView(layer);

        root.addView(buttons);

        return root;
    }

    private static void cycleLayer(Context context) {
        int current = savedLayerIndex(context);
        int next = current + 1;

        if (next > LAYER_YANDEX) {
            next = LAYER_OSM;
        }

        saveLayerIndex(context, next);
    }

    private static TextView makeMapButton(Context context, String text) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(22);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(context, 42),
                dp(context, 42)
        );
        params.setMargins(0, dp(context, 4), 0, dp(context, 4));
        button.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#172A46"));
        bg.setCornerRadius(dp(context, 12));
        bg.setStroke(dp(context, 1), Color.parseColor("#5D708E"));
        button.setBackground(bg);

        return button;
    }

    public static Drawable markerDrawable(Context context, int color, int scale) {
        int sizeDp = markerSizeDp(context) * Math.max(1, scale);
        int sizePx = dp(context, sizeDp);

        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        float center = sizePx / 2f;
        float radius = sizePx * 0.34f;

        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(center, center, radius, paint);

        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, sizePx * 0.06f));
        canvas.drawCircle(center, center, radius, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(center, center, Math.max(3f, sizePx * 0.09f), paint);

        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }

    public static BitmapDescriptor googleMarkerDescriptor(Context context, int color, int scale) {
        int sizeDp = markerSizeDp(context) * Math.max(1, scale);
        int sizePx = dp(context, sizeDp);

        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        float cx = sizePx / 2f;
        float cy = sizePx * 0.42f;
        float radius = sizePx * 0.28f;

        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius, paint);

        Path tail = new Path();
        tail.moveTo(cx - radius * 0.45f, cy + radius * 0.65f);
        tail.lineTo(cx + radius * 0.45f, cy + radius * 0.65f);
        tail.lineTo(cx, sizePx * 0.92f);
        tail.close();
        canvas.drawPath(tail, paint);

        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, sizePx * 0.05f));
        canvas.drawCircle(cx, cy, radius, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, Math.max(3f, sizePx * 0.08f), paint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    public static void styleOsmMarkerInfoWindow(Context context, MapView mapView, Marker marker) {
        if (marker == null) return;

        marker.setOnMarkerClickListener((clickedMarker, clickedMapView) -> {
            if (clickedMarker.isInfoWindowShown()) {
                clickedMarker.closeInfoWindow();
            } else {
                clickedMarker.showInfoWindow();
                if (clickedMapView != null) {
                    clickedMapView.getController().animateTo(clickedMarker.getPosition());
                }
            }
            return true;
        });
    }

    private static int dp(Context context, int value) {
        if (context == null) return value;
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
