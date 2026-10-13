package com.google.android.gms.location;
import android.os.Looper; import androidx.annotation.*; import com.google.android.gms.tasks.Task;
public interface FusedLocationProviderClient {
  @NonNull Task<Void> requestLocationUpdates(@NonNull LocationRequest r, @NonNull LocationCallback cb, @Nullable Looper l);
  @NonNull Task<Void> removeLocationUpdates(@NonNull LocationCallback cb);
}
