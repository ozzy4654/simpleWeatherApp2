package com.example.ozankalan.simpleweatherapp2.app;

import android.app.Application;
import com.squareup.leakcanary.LeakCanary;

/**
 * Created by ozan.kalan on 3/29/17.
 */

public class WeatherApp extends Application {

    @Override
    public void onCreate() {

        // Parent.
        super.onCreate();

        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }

        LeakCanary.install(this);

    }
}
