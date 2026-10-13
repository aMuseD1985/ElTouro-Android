package com.getcapacitor; import androidx.annotation.*;
public class JSObject extends org.json.JSONObject {
  public JSObject put(String k, boolean v) { return this; } public JSObject put(String k, int v) { return this; } public JSObject put(String k, long v) { return this; }
  public JSObject put(String k, double v) { return this; } public JSObject put(String k, Object v) { return this; } public JSObject put(String k, String v) { return this; } }
