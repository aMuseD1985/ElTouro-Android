package de.eltouro.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(TrackerPlugin.class);
        super.onCreate(savedInstanceState);
        CookieManager.getInstance().setAcceptCookie(true);
        openLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        openLink(intent);
    }

    // The WebView keeps cookies in memory first – write them to disk so the sign-in survives closing the app
    @Override
    public void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    public void onStop() {
        CookieManager.getInstance().flush();
        super.onStop();
    }

    /**
     * 1. de.eltouro.app://login?t=…&next=… : back from "Sign in with Google" in the system browser – sign the WebView in with the token
     * 2. a tapped ElTouro link (flyer QR code, mail, chat): opens inside the app
     */
    private void openLink(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri data = intent.getData();
        if (data == null) return;
        String url = null;
        if ("de.eltouro.app".equals(data.getScheme()) && "login".equals(data.getHost())) {
            String t = data.getQueryParameter("t");
            String next = data.getQueryParameter("next");
            if (t == null || !t.matches("[0-9a-f]{64}")) return;
            String base = getBridge().getConfig().getServerUrl();
            if (base == null) return;
            url = base.replaceAll("/+$", "") + "/auth/app?t=" + t + "&next=" + Uri.encode(next == null ? "/" : next);
        } else if (data.getHost() != null && data.getHost().endsWith("eltouro.de")) {
            url = data.toString();
        }
        if (url == null) return;
        final String target = url;
        getBridge().getWebView().post(() -> getBridge().getWebView().loadUrl(target));
    }
}
