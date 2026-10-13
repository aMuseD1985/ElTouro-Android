package androidx.core.content;
import android.content.*; import androidx.annotation.*;
public class ContextCompat {
  public static final int RECEIVER_NOT_EXPORTED = 4;
  public static int checkSelfPermission(@NonNull Context c, @NonNull String p) { return 0; }
  public static void startForegroundService(@NonNull Context c, @NonNull Intent i) {}
  public static @Nullable Intent registerReceiver(@NonNull Context c, @Nullable BroadcastReceiver r, @NonNull IntentFilter f, int flags) { return null; }
}
