package com.houston.weatherwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

public class WeatherWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH =
            "com.houston.weatherwidget.ACTION_REFRESH";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WeatherScheduler.schedulePeriodic(context);
        enqueueRefresh(context);
    }

    @Override
    public void onEnabled(Context context) {
        WeatherScheduler.schedulePeriodic(context);
        enqueueRefresh(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            enqueueRefresh(context);
        }
    }

    private void enqueueRefresh(Context context) {
        WorkManager.getInstance(context).enqueue(
                new OneTimeWorkRequest.Builder(WeatherWorker.class).build()
        );
    }
}
