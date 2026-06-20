package com.houston.weatherwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WeatherWorker extends Worker {

    public WeatherWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
        String latitude = prefs.getString("latitude", null);
        String longitude = prefs.getString("longitude", null);

        if (latitude == null || longitude == null) {
            updateWidget(context, "--°", "📍", "ОТКРОЙ ПРИЛОЖЕНИЕ", "нужна геопозиция");
            return Result.success();
        }

        try {
            String endpoint = "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + latitude
                    + "&longitude=" + longitude
                    + "&current=temperature_2m,apparent_temperature,weather_code"
                    + "&timezone=auto";

            HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "WeatherWithoutCensorship/1.0");

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("HTTP " + responseCode);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            } finally {
                connection.disconnect();
            }

            JSONObject current = new JSONObject(response.toString()).getJSONObject("current");
            double temperature = current.getDouble("temperature_2m");
            double apparent = current.optDouble("apparent_temperature", temperature);
            int weatherCode = current.optInt("weather_code", -1);

            int roundedTemperature = (int) Math.round(temperature);
            int roundedApparent = (int) Math.round(apparent);
            String phrase = phraseFor(roundedApparent);
            String emoji = emojiFor(weatherCode, roundedTemperature);
            String updatedAt = "обновлено " + new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(new Date());

            updateWidget(
                    context,
                    roundedTemperature + "°",
                    emoji,
                    phrase,
                    updatedAt + " · ощущается " + roundedApparent + "°"
            );
            return Result.success();
        } catch (Exception error) {
            updateWidget(context, "--°", "⚠️", "ПОГОДА ПРОЕБАЛАСЬ", "нажми ↻");
            return Result.retry();
        }
    }

    private static String phraseFor(int temperature) {
        if (temperature <= -30) return "ЕБАТЬ КОЛОТИТ";
        if (temperature <= -20) return "ДУБАК ПИЗДЕЦ";
        if (temperature <= -10) return "ХОЛОДНО, БЛЯ";
        if (temperature <= 0) return "ЗЯБКО, СУКА";
        if (temperature <= 9) return "ПРОХЛАДНО";
        if (temperature <= 19) return "НОРМАЛЬНО";
        if (temperature <= 27) return "ТЕПЛО, ЗАЕБИСЬ";
        if (temperature <= 34) return "ЖАРКОВАТО, БЛЯ";
        if (temperature <= 39) return "ЖАРА ПИЗДЕЦ";
        return "АДСКАЯ ЖАРА";
    }

    private static String emojiFor(int weatherCode, int temperature) {
        if (weatherCode == 0) return temperature >= 30 ? "🥵" : "☀️";
        if (weatherCode == 1 || weatherCode == 2) return "🌤️";
        if (weatherCode == 3) return "☁️";
        if (weatherCode == 45 || weatherCode == 48) return "🌫️";
        if ((weatherCode >= 51 && weatherCode <= 67)
                || (weatherCode >= 80 && weatherCode <= 82)) return "🌧️";
        if ((weatherCode >= 71 && weatherCode <= 77)
                || (weatherCode >= 85 && weatherCode <= 86)) return "❄️";
        if (weatherCode >= 95) return "⛈️";
        return temperature >= 35 ? "🥵" : "🌡️";
    }

    private static void updateWidget(
            Context context,
            String temperature,
            String emoji,
            String phrase,
            String subtitle
    ) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather_widget);
        views.setTextViewText(R.id.temperatureText, temperature);
        views.setTextViewText(R.id.weatherEmoji, emoji);
        views.setTextViewText(R.id.phraseText, phrase);
        views.setTextViewText(R.id.updateText, subtitle);

        Intent refreshIntent = new Intent(context, WeatherWidgetProvider.class)
                .setAction(WeatherWidgetProvider.ACTION_REFRESH);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.refreshButton, refreshPendingIntent);

        Intent openIntent = new Intent(context, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widgetRoot, openPendingIntent);

        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, WeatherWidgetProvider.class);
        manager.updateAppWidget(widget, views);
    }
}
