package com.getcapacitor;
import android.webkit.WebResourceRequest; import android.webkit.WebView; import android.webkit.WebViewClient;
public class BridgeWebViewClient extends WebViewClient { public BridgeWebViewClient(Bridge bridge) {}
  @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; } }
