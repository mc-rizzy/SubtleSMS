package com.example.subtlesms;

import android.app.Application;

public class Global extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize your Singleton once when the app process starts
        AppRepository.getInstance(this);
    }
}

