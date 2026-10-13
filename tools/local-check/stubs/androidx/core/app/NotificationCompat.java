package androidx.core.app;
import android.app.Notification; import android.app.PendingIntent; import android.content.Context; import androidx.annotation.*;
public class NotificationCompat {
  public static final int PRIORITY_HIGH = 1; public static final String CATEGORY_NAVIGATION = "navigation"; public static final int FOREGROUND_SERVICE_IMMEDIATE = 1;
  public static class Builder {
    public Builder(@NonNull Context c, @NonNull String channelId) {}
    public @NonNull Builder setSmallIcon(int i) { return this; }
    public @NonNull Builder setContentTitle(@Nullable CharSequence t) { return this; }
    public @NonNull Builder setContentText(@Nullable CharSequence t) { return this; }
    public @NonNull Builder setOngoing(boolean b) { return this; }
    public @NonNull Builder setOnlyAlertOnce(boolean b) { return this; }
    public @NonNull Builder setSilent(boolean b) { return this; }
    public @NonNull Builder setPriority(int p) { return this; }
    public @NonNull Builder setCategory(@Nullable String c) { return this; }
    public @NonNull Builder setForegroundServiceBehavior(int b) { return this; }
    public @NonNull Builder setContentIntent(@Nullable PendingIntent p) { return this; }
    public @NonNull Builder addAction(int icon, @Nullable CharSequence title, @Nullable PendingIntent p) { return this; }
    public @NonNull Notification build() { return null; }
  }
}
