package de.eltouro.app.diag

/** Readable names for system codes in the diagnostics log. The numbers are the platform constants (stable API). */
object Names {
    /** ApplicationExitInfo.REASON_* */
    fun exitReason(code: Int): String = when (code) {
        0 -> "unknown"
        1 -> "exit_self"
        2 -> "signaled"
        3 -> "low_memory"
        4 -> "crash"
        5 -> "crash_native"
        6 -> "anr"
        7 -> "initialization_failure"
        8 -> "permission_change"
        9 -> "excessive_resource_usage"
        10 -> "user_requested"
        11 -> "user_stopped"
        12 -> "dependency_died"
        13 -> "other"
        14 -> "freezer"
        15 -> "package_state_change"
        16 -> "package_updated"
        else -> "reason_$code"
    }

    /** RunningAppProcessInfo.IMPORTANCE_* – how important the process was when it died */
    fun importance(code: Int): String = when {
        code <= 100 -> "foreground"
        code <= 125 -> "foreground_service"
        code <= 200 -> "visible"
        code <= 230 -> "perceptible"
        code <= 300 -> "service"
        code <= 325 -> "top_sleeping"
        code <= 350 -> "cant_save_state"
        code <= 400 -> "cached"
        code <= 500 -> "empty"
        else -> "gone"
    }

    /** UsageStatsManager.STANDBY_BUCKET_* */
    fun standbyBucket(code: Int): String = when (code) {
        5 -> "exempted"
        10 -> "active"
        20 -> "working_set"
        30 -> "frequent"
        40 -> "rare"
        45 -> "restricted"
        50 -> "never"
        else -> "bucket_$code"
    }

    /** Buckets in which background work is throttled noticeably */
    fun bucketIsBad(code: Int): Boolean = code >= 40
}

/** Phone makers with their own app killers; each gets its own instructions and shortcuts. */
enum class Vendor {
    XIAOMI, SAMSUNG, HUAWEI, ONEPLUS, OTHER;

    companion object {
        fun detect(manufacturer: String?, brand: String?): Vendor {
            val s = ((manufacturer ?: "") + " " + (brand ?: "")).lowercase()
            return when {
                listOf("xiaomi", "redmi", "poco").any { it in s } -> XIAOMI
                "samsung" in s -> SAMSUNG
                listOf("huawei", "honor").any { it in s } -> HUAWEI
                listOf("oneplus", "oppo", "realme").any { it in s } -> ONEPLUS
                else -> OTHER
            }
        }
    }
}
