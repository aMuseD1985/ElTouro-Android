package de.eltouro.app;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.webkit.CookieManager;

import androidx.core.app.NotificationCompat;

import com.getcapacitor.JSObject;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the position coming while the display is off: a foreground service (visible notification) with fused location.
 * While a recording session is set, the points go straight to /api/track from here – independent of the web page, which
 * Android may pause in the background. The page gets every fix as an event as long as it is alive.
 */
public class TrackerService extends Service {
    static final String CHANNEL = "eltouro_ride";
    private static final int NOTIFICATION_ID = 4711;
    private static final long UPLOAD_EVERY_MS = 15000;
    private static final int MAX_BUFFER = 6000;

    private FusedLocationProviderClient client;
    private LocationCallback callback;
    private HandlerThread thread;
    private Handler handler;
    private PowerManager.WakeLock wakeLock;
    private final List<JSONArray> buffer = new ArrayList<>();
    private Location lastKept = null;
    private long lastKeptAt = 0;

    private final Runnable uploadTick = new Runnable() {
        @Override public void run() {
            flushNow();
            if (handler != null) handler.postDelayed(this, UPLOAD_EVERY_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("eltouro-upload");
        thread.start();
        handler = new Handler(thread.getLooper());
        TrackerState.service = this;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (wakeLock == null && pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "eltouro:ride");
            wakeLock.acquire(8 * 60 * 60 * 1000L);   // a long ride at most
        }
        startLocation();
        handler.removeCallbacks(uploadTick);
        handler.postDelayed(uploadTick, UPLOAD_EVERY_MS);
        return START_STICKY;
    }

    @SuppressLint("MissingPermission")
    private void startLocation() {
        if (client != null) return;
        client = LocationServices.getFusedLocationProviderClient(this);
        callback = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                for (Location l : result.getLocations()) onLocation(l);
            }
        };
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(1000).build();
        try {
            client.requestLocationUpdates(req, callback, thread.getLooper());
        } catch (SecurityException e) {
            stopSelf();
        }
    }

    private void onLocation(Location l) {
        TrackerPlugin p = TrackerState.plugin;
        if (p != null) {
            JSObject o = new JSObject();
            o.put("lat", l.getLatitude());
            o.put("lng", l.getLongitude());
            o.put("acc", l.getAccuracy());
            o.put("heading", l.hasBearing() ? l.getBearing() : null);
            o.put("speedMs", l.hasSpeed() ? l.getSpeed() : null);
            o.put("t", l.getTime());
            p.emitLocation(o);
        }
        if (TrackerState.session <= 0) return;
        // like the web recorder: at most one point every 2 s, and only when we have moved – standing at lights fills nothing
        long now = l.getTime();
        if (now - lastKeptAt < 2000) return;
        if (lastKept != null && l.distanceTo(lastKept) < 5f) return;
        lastKept = l; lastKeptAt = now;
        try {
            JSONArray pt = new JSONArray();
            pt.put(now);
            pt.put(Math.round(l.getLatitude() * 1e6) / 1e6);
            pt.put(Math.round(l.getLongitude() * 1e6) / 1e6);
            pt.put(Math.round(l.getAccuracy()));
            if (l.hasSpeed()) pt.put(Math.round(l.getSpeed() * 3.6 * 10) / 10.0); else pt.put(JSONObject.NULL);
            synchronized (buffer) {
                buffer.add(pt);
                if (buffer.size() > MAX_BUFFER) buffer.remove(0);
            }
        } catch (Exception ignored) { /* skip the point */ }
    }

    /** Sends what is buffered. Safe to call from any thread, blocks. Returns true when nothing is left behind. */
    boolean flushNow() {
        long session = TrackerState.session;
        List<JSONArray> batch;
        synchronized (buffer) {
            if (session <= 0 || buffer.isEmpty()) return true;
            batch = new ArrayList<>(buffer);
            buffer.clear();
        }
        boolean ok = false;
        try {
            JSONArray pts = new JSONArray();
            for (JSONArray a : batch) pts.put(a);
            JSONObject body = new JSONObject();
            body.put("action", "points");
            body.put("session_id", session);
            body.put("points", pts);
            URL url = new URL(TrackerState.baseUrl + "/api/track");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("X-CSRF", TrackerState.csrf);
            String cookie = CookieManager.getInstance().getCookie(TrackerState.baseUrl);
            if (cookie != null) c.setRequestProperty("Cookie", cookie);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            ok = code >= 200 && code < 300;
            c.disconnect();
        } catch (Exception ignored) { /* retried with the next round */ }
        if (!ok) {
            synchronized (buffer) {
                buffer.addAll(0, batch);
                while (buffer.size() > MAX_BUFFER) buffer.remove(0);
            }
        }
        return ok;
    }

    void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "ElTouro Fahrt", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Läuft, solange eine Fahrt aufgezeichnet oder navigiert wird");
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(TrackerState.title)
                .setContentText(TrackerState.text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setContentIntent(pi)
                .build();
    }

    @Override public void onDestroy() {
        if (client != null && callback != null) client.removeLocationUpdates(callback);
        client = null;
        if (handler != null) handler.removeCallbacks(uploadTick);
        flushNow();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (thread != null) thread.quitSafely();
        TrackerState.service = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
