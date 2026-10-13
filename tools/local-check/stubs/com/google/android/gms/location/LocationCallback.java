package com.google.android.gms.location; import androidx.annotation.*;
public abstract class LocationCallback { public void onLocationResult(@NonNull LocationResult r) {} public void onLocationAvailability(@NonNull LocationAvailability a) {} }
