package com.example.santiway;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.santiway.upload_data.MainDatabaseHelper;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
// ИМПОРТЫ GOOGLE MAPS
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

// ДОБАВЛЕНО: Импорт отдельного интерфейса
import com.example.santiway.StatusUpdateListener;


// КЛАСС РЕАЛИЗУЕТ OnMapReadyCallback
public class DeviceDetailsBottomSheet extends BottomSheetDialogFragment implements OnMapReadyCallback {

    public static final String TAG = "DeviceDetailsBottomSheet";
    private static final String ARG_DEVICE = "device_details";
    private static final String ARG_TABLE_NAME = "table_name";
    private String tableName;

    private DeviceListActivity.Device device;
    private MapView mapView; // Google Maps MapView
    private GoogleMap googleMap; // Объект карты

    // НОВЫЕ ПОЛЯ
    private TextView detailsTextView;
    private Button btnAlert, btnSafe, btnClear;
    private MainDatabaseHelper databaseHelper;

    /**
     * Создает новый экземпляр Bottom Sheet, передавая объект Device.
     */
    public static DeviceDetailsBottomSheet newInstance(DeviceListActivity.Device device, String tableName) {
        DeviceDetailsBottomSheet fragment = new DeviceDetailsBottomSheet();
        Bundle args = new Bundle();
        args.putParcelable(ARG_DEVICE, device);
        args.putString(ARG_TABLE_NAME, tableName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device_details_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ИНИЦИАЛИЗАЦИЯ
        if (getContext() != null) {
            databaseHelper = new MainDatabaseHelper(getContext());
        } else {
            Log.e(TAG, "Контекст недоступен. Невозможно инициализировать MainDatabaseHelper.");
        }

        detailsTextView = view.findViewById(R.id.details_text_view);
        mapView = view.findViewById(R.id.google_map_view);

        // ИНИЦИАЛИЗАЦИЯ НОВЫХ КНОПОК
        btnAlert = view.findViewById(R.id.btn_alert);
        btnSafe = view.findViewById(R.id.btn_safe);
        btnClear = view.findViewById(R.id.btn_clear);


        if (getArguments() != null) {
            device = getArguments().getParcelable(ARG_DEVICE);
            tableName = getArguments().getString(ARG_TABLE_NAME);

            if (device != null) {
                String details = formatDeviceDetails(device);
                detailsTextView.setText(details);

                if (mapView != null) {
                    mapView.onCreate(savedInstanceState);
                    mapView.getMapAsync(this);
                } else {
                    Log.e(TAG, "MapView не найден в разметке.");
                }

                // УСТАНОВКА СЛУШАТЕЛЕЙ КЛИКОВ
                if (databaseHelper != null) {
                    setupButtonListeners(device);
                } else {
                    // Скрываем кнопки, если нет возможности обновить БД
                    btnAlert.setVisibility(View.GONE);
                    btnSafe.setVisibility(View.GONE);
                    btnClear.setVisibility(View.GONE);
                }


            } else {
                detailsTextView.setText("Ошибка: Данные об устройстве не найдены.");
                if (mapView != null) mapView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Устанавливает слушателей кликов для кнопок и обрабатывает изменение статуса.
     */
    private void setupButtonListeners(DeviceListActivity.Device currentDevice) {
        View.OnClickListener statusChangeListener = v -> {
            String newStatus;

            if (v.getId() == R.id.btn_alert) {
                newStatus = "TARGET";
            } else if (v.getId() == R.id.btn_safe) {
                newStatus = "SAFE";
            } else if (v.getId() == R.id.btn_clear) {
                newStatus = "GREY";
            } else {
                return;
            }

            if (databaseHelper == null || tableName == null || tableName.trim().isEmpty()) {
                Toast.makeText(getContext(), "Не удалось определить папку устройства", Toast.LENGTH_SHORT).show();
                return;
            }

            int affected = databaseHelper.updateDeviceStatus(tableName, currentDevice.mac, newStatus);

            if (affected >= 0) {
                currentDevice.status = newStatus;
                detailsTextView.setText(formatDeviceDetails(currentDevice));

                Toast.makeText(
                        getContext(),
                        "Статус устройства " + currentDevice.name + " изменен на " + newStatus,
                        Toast.LENGTH_SHORT
                ).show();

                if (getActivity() instanceof StatusUpdateListener) {
                    ((StatusUpdateListener) getActivity()).onStatusUpdated();
                }

                dismiss();
            } else {
                Toast.makeText(getContext(), "Ошибка обновления статуса", Toast.LENGTH_SHORT).show();
            }
        };

        btnAlert.setOnClickListener(statusChangeListener);
        btnSafe.setOnClickListener(statusChangeListener);
        btnClear.setOnClickListener(statusChangeListener);
    }


    /**
     * Вызывается, когда карта Google готова к использованию.
     */
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        // Настройка карты происходит здесь, после получения объекта GoogleMap
        setupGoogleMap(device);
    }


    /**
     * Настраивает Google Map и размещает метку, парся координаты устройства.
     */
    private void setupGoogleMap(DeviceListActivity.Device device) {
        if (googleMap == null || device == null) return;

        String location = device.location;

        if (location == null || location.trim().isEmpty() || !location.contains(",")) {
            mapView.setVisibility(View.GONE);
            Toast.makeText(getContext(), "Местоположение не указано для отображения карты.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            // Устойчивая логика парсинга координат
            String cleanLocation = location.replaceAll("[^0-9\\.,-]", " ").trim();
            cleanLocation = cleanLocation.replaceAll("\\s*,\\s*", ",");
            cleanLocation = cleanLocation.replaceAll(",+", ",");
            String[] parts = cleanLocation.split(",");

            if (parts.length >= 2) {
                String latStr = parts[0].trim().replace(',', '.');
                String lonStr = parts[1].trim().replace(',', '.');

                double latitude = Double.parseDouble(latStr);
                double longitude = Double.parseDouble(lonStr);

                // Используем LatLng для Google Maps
                LatLng deviceLatLng = new LatLng(latitude, longitude);

                // 1. Настройки интерфейса карты
                googleMap.getUiSettings().setZoomControlsEnabled(true);
                googleMap.getUiSettings().setScrollGesturesEnabled(true);

                // 2. Установка начального положения и зума (15 - стандартный зум)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(deviceLatLng, 15));

                // 3. Размещение метки (Marker)
                googleMap.addMarker(new MarkerOptions()
                        .position(deviceLatLng)
                        .title(device.name)
                        .snippet("MAC: " + device.mac));

            } else {
                mapView.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Не удалось извлечь широту и долготу.", Toast.LENGTH_LONG).show();
            }
        } catch (NumberFormatException e) {
            mapView.setVisibility(View.GONE);
            Toast.makeText(getContext(), "Ошибка парсинга координат. Проверьте числовой формат.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "NumberFormatException during coordinate parsing. Raw location: " + location, e);
        } catch (Exception e) {
            Log.e(TAG, "Unknown error during map setup: " + e.getMessage(), e);
            mapView.setVisibility(View.GONE);
        }
    }

    /**
     * Форматирует детали устройства в строку для отображения в TextView.
     */
    private String formatDeviceDetails(DeviceListActivity.Device device) {
        return "Время: " + device.time +
                "\nИмя: " + device.name +
                "\nMAC-адрес: " + device.mac +
                "\nТип: " + device.type +
                "\nМестоположение (Raw): " + device.location +
                "\nСтатус: " + device.status;
    }

    // --- УПРАВЛЕНИЕ ЖИЗНЕННЫМ ЦИКЛОМ MAPVIEW (ОБЯЗАТЕЛЬНО) ---

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    // ИСПРАВЛЕНИЕ: ДОБАВЛЕН МЕТОД onSaveInstanceState для MapView
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // ВАЖНО: Вызываем onDestroy() для Google Maps MapView
        if (mapView != null) {
            mapView.onDestroy();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }
}
