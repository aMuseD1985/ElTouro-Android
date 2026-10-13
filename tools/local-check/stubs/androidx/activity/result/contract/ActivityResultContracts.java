package androidx.activity.result.contract;
import java.util.Map;
public final class ActivityResultContracts {
  public static final class RequestMultiplePermissions extends ActivityResultContract<String[], Map<String, Boolean>> {}
  public static final class RequestPermission extends ActivityResultContract<String, Boolean> {}
}
