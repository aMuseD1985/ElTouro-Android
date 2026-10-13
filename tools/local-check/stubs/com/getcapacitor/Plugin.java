package com.getcapacitor; import androidx.annotation.*;
public class Plugin { protected Bridge bridge;
  public android.content.Context getContext() { return null; } public androidx.appcompat.app.AppCompatActivity getActivity() { return null; } public Bridge getBridge() { return null; }
  public void load() {} protected void notifyListeners(String e, JSObject d) {} public PermissionState getPermissionState(String a) { return null; }
  protected void requestPermissionForAlias(@NonNull String a, @NonNull PluginCall c, @NonNull String cb) {} }
