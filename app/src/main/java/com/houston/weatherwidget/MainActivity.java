package com.houston.weatherwidget;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private TextView statusText;
    private LocationManager locationManager;
    private LocationListener locationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        Button locationButton = findViewById(R.id.locationButton);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        locationButton.setOnClickListener(v -> ensureLocationPermission());

        boolean configured = getSharedPreferences("weather", MODE_PRIVATE)
                .contains("latitude");
        if (configured) {
            statusText.setText("Место уже сохранено. Нажми кнопку, чтобы обновить геопозицию.");
            WeatherScheduler.schedulePeriodic(this);
            requestWidgetRefresh();
        }
    }

    private void ensureLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, LOCATION_PERMISSION_REQUEST);
            return;
        }
        acquireLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            acquireLocation();
        } else {
            statusText.setText("Без геопозиции виджет не знает, откуда брать погоду.");
        }
    }

    @SuppressWarnings("MissingPermission")
    private void acquireLocation() {
        statusText.setText("Определяю местоположение…");

        Location best = getBestLastKnownLocation();
        if (best != null && System.currentTimeMillis() - best.getTime() < 6 * 60 * 60 * 1000L) {
            saveLocation(best);
            return;
        }

        locationListener = location -> {
            if (location != null) {
                locationManager.removeUpdates(locationListener);
                saveLocation(location);
            }
        };

        try {
            locationManager.requestSingleUpdate(
                    LocationManager.NETWORK_PROVIDER,
                    locationListener,
                    Looper.getMainLooper()
            );
        } catch (Exception networkError) {
            try {
                locationManager.requestSingleUpdate(
                        LocationManager.GPS_PROVIDER,
                        locationListener,
                        Looper.getMainLooper()
                );
            } catch (Exception gpsError) {
                statusText.setText("Не удалось получить координаты. Включи геолокацию и повтори.");
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private Location getBestLastKnownLocation() {
        try {
            List<String> providers = locationManager.getProviders(true);
            return providers.stream()
                    .map(provider -> {
                        try {
                            return locationManager.getLastKnownLocation(provider);
                        } catch (SecurityException ignored) {
                            return null;
                        }
                    })
                    .filter(location -> location != null)
                    .max(Comparator.comparingLong(Location::getTime))
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveLocation(Location location) {
        getSharedPreferences("weather", MODE_PRIVATE)
                .edit()
                .putString("latitude", Double.toString(location.getLatitude()))
                .putString("longitude", Double.toString(location.getLongitude()))
                .apply();

        statusText.setText(String.format(
                "Готово. Координаты сохранены: %.4f, %.4f",
                location.getLatitude(),
                location.getLongitude()
        ));

        WeatherScheduler.schedulePeriodic(this);
        requestWidgetRefresh();
    }

    private void requestWidgetRefresh() {
        WorkManager.getInstance(this).enqueue(
                new OneTimeWorkRequest.Builder(WeatherWorker.class).build()
        );
    }
}
