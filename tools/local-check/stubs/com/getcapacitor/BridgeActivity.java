package com.getcapacitor; import android.content.Intent; import android.os.Bundle;
public class BridgeActivity extends androidx.appcompat.app.AppCompatActivity { protected Bridge bridge;
  protected void onCreate(Bundle b) {} public void registerPlugin(Class<? extends Plugin> p) {} public Bridge getBridge() { return bridge; }
  public void onResume() {} public void onPause() {} public void onStop() {} public void onDestroy() {} protected void onNewIntent(Intent intent) {} }
