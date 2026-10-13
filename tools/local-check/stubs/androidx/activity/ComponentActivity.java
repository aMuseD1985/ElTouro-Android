package androidx.activity;
import android.content.Intent; import androidx.annotation.*; import androidx.activity.result.*; import androidx.activity.result.contract.*;
public class ComponentActivity extends android.app.Activity {
  public final @NonNull <I, O> ActivityResultLauncher<I> registerForActivityResult(@NonNull ActivityResultContract<I, O> c, @NonNull ActivityResultCallback<O> cb) { return null; }
  @Override protected void onNewIntent(@NonNull Intent intent) {}
}
