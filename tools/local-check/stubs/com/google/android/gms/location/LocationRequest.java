package com.google.android.gms.location; import androidx.annotation.*;
public final class LocationRequest { public static final class Builder { public Builder(int priority, long interval) {}
  public @NonNull Builder setMinUpdateIntervalMillis(long m) { return this; } public @NonNull Builder setMaxUpdateDelayMillis(long m) { return this; }
  public @NonNull Builder setWaitForAccurateLocation(boolean b) { return this; } public @NonNull LocationRequest build() { return null; } } }
