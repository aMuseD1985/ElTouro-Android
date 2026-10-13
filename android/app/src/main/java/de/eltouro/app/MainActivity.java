package de.eltouro.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(TrackerPlugin.class);
        super.onCreate(savedInstanceState);
        openLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        openLink(intent);
    }

    /** A tapped ElTouro link (flyer QR code, mail, chat) opens inside the app. */
    private void openLink(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri data = intent.getData();
        if (data == null || data.getHost() == null || !data.getHost().endsWith("eltouro.de")) return;
        final String url = data.toString();
        getBridge().getWebView().post(() -> getBridge().getWebView().loadUrl(url));
    }
}
