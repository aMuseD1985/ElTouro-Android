package com.getcapacitor;
import android.webkit.RenderProcessGoneDetail; import android.webkit.WebView;
public abstract class WebViewListener {
  public void onPageLoaded(WebView webView) {} public void onReceivedError(WebView webView) {} public void onReceivedHttpError(WebView webView) {}
  public void onPageStarted(WebView webView) {} public boolean onRenderProcessGone(WebView webView, RenderProcessGoneDetail detail) { return false; }
  public void onPageCommitVisible(WebView view, String url) {} }
