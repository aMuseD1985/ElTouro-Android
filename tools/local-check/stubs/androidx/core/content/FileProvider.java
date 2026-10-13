package androidx.core.content;
import android.content.Context; import android.net.Uri; import java.io.File; import androidx.annotation.*;
public class FileProvider extends android.content.ContentProvider {
  public static Uri getUriForFile(@NonNull Context c, @NonNull String a, @NonNull File f) { return null; }
  public boolean onCreate() { return true; } public android.database.Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
  public String getType(Uri u) { return null; } public Uri insert(Uri u, android.content.ContentValues v) { return null; }
  public int delete(Uri u, String s, String[] a) { return 0; } public int update(Uri u, android.content.ContentValues v, String s, String[] a) { return 0; }
}
