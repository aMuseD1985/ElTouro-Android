package de.eltouro.app;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * Bridge between the ElTouro web app and the foreground service.
 *   start({baseUrl, csrf, session?, title, text})  asks for location (and notification) permission, starts the service
 *   setSession({session, csrf}) / clearSession()   switch the recording on / off while the service keeps running
 *   stop()                                         flushes what is left and stops the service
 *   batterySettings()                              opens the system page for battery optimisation
 * Event "location": {lat, lng, acc, heading, speedMs, t}
 */
@CapacitorPlugin(
        name = "EltouroTracker",
        permissions = {
                @Permission(strings = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, alias = "location"),
                @Permission(strings = {Manifest.permission.POST_NOTIFICATIONS}, alias = "notifications")
        })
public class TrackerPlugin extends Plugin {

    @Override public void load() {
        TrackerState.plugin = this;
    }

    void emitLocation(JSObject o) {
        notifyListeners("location", o);
    }

    @PluginMethod
    public void start(PluginCall call) {
        TrackerState.baseUrl = call.getString("baseUrl", "");
        TrackerState.csrf = call.getString("csrf", "");
        TrackerState.title = call.getString("title", "ElTouro");
        TrackerState.text = call.getString("text", "");
        Long s = call.getLong("session", 0L);
        TrackerState.session = s == null ? 0 : s;
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "afterLocation");
            return;
        }
        afterLocation(call);
    }

    @PermissionCallback
    private void afterLocation(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            call.reject("location_denied");
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notifications") != PermissionState.GRANTED) {
            requestPermissionForAlias("notifications", call, "afterNotifications");
            return;
        }
        begin(call);
    }

    @PermissionCallback
    private void afterNotifications(PluginCall call) {
        begin(call);   // without the notification permission the service still runs, only the status entry is hidden
    }

    private void begin(PluginCall call) {
        try {
            Intent i = new Intent(getContext(), TrackerService.class);
            ContextCompat.startForegroundService(getContext(), i);
            call.resolve();
        } catch (Exception e) {
            call.reject("service_failed", e);
        }
    }

    @PluginMethod
    public void setSession(PluginCall call) {
        String csrf = call.getString("csrf");
        if (csrf != null && !csrf.isEmpty()) TrackerState.csrf = csrf;
        Long s = call.getLong("session", 0L);
        TrackerState.session = s == null ? 0 : s;
        call.resolve();
    }

    @PluginMethod
    public void clearSession(PluginCall call) {
        final TrackerService svc = TrackerState.service;
        new Thread(() -> {
            if (svc != null) svc.flushNow();
            TrackerState.session = 0;
            call.resolve();
        }).start();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        final TrackerService svc = TrackerState.service;
        new Thread(() -> {
            if (svc != null) svc.flushNow();
            TrackerState.session = 0;
            getContext().stopService(new Intent(getContext(), TrackerService.class));
            call.resolve();
        }).start();
    }

    @PluginMethod
    public void batterySettings(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Exception e) {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        }
        call.resolve();
    }
}
