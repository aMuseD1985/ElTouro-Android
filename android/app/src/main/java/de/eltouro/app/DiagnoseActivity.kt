package de.eltouro.app

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import de.eltouro.app.diag.DeviceStatus
import de.eltouro.app.diag.Diag
import de.eltouro.app.diag.Vendor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Diagnose": what a ride with the display off needs (with buttons into the system settings), the live state of
 * the ride service and the log, which the rider can share as a text file. Reachable from the ride notification,
 * by long-pressing the app icon, and from the page via EltouroTracker.diagnostics().
 */
class DiagnoseActivity : AppCompatActivity() {
    private lateinit var setupBox: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private val h = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            renderStatus()
            h.postDelayed(this, 2000)
        }
    }

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        Diag.log("setup: location answer $r")
        renderSetup()
    }

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Diag.log("setup: permission answer $ok")
        renderSetup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.diag_title)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        root.addView(heading(R.string.setup_heading))
        root.addView(text(getString(R.string.setup_intro)))
        setupBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(setupBox)

        root.addView(heading(R.string.diag_status))
        statusView = mono(13f)
        root.addView(statusView)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(button(R.string.diag_share) { shareLog() })
        buttons.addView(button(R.string.diag_clear) {
            AlertDialog.Builder(this)
                .setMessage(R.string.diag_clear_confirm)
                .setPositiveButton(R.string.diag_clear) { _, _ -> Diag.clear(); Diag.log("log cleared"); renderStatus() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        })
        root.addView(buttons)

        root.addView(heading(R.string.diag_log))
        logView = mono(11f)
        root.addView(logView)

        setContentView(ScrollView(this).apply { addView(root) })
        Diag.log("diagnostics opened")
    }

    override fun onResume() {
        super.onResume()
        renderSetup()
        h.removeCallbacks(refresh)
        h.post(refresh)
    }

    override fun onPause() {
        h.removeCallbacks(refresh)
        super.onPause()
    }

    // ---- setup steps

    private fun renderSetup() {
        setupBox.removeAllViews()
        step(Setup.hasPreciseLocation(this), R.string.setup_precise, R.string.setup_allow) {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            step(Setup.hasNotifications(this), R.string.setup_notifications, R.string.setup_allow) {
                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            step(Setup.hasBackgroundLocation(this), R.string.setup_background, R.string.setup_allow) { askBackground() }
        }
        step(Setup.ignoresBatteryOptimization(this), R.string.setup_battery, R.string.setup_switch_off) {
            Setup.requestIgnoreBatteryOptimization(this)
        }
        if (Build.VERSION.SDK_INT >= 28) {
            step(!Setup.backgroundRestricted(this), R.string.setup_restricted, R.string.setup_open) { Setup.openBatteryForApp(this) }
        }

        setupBox.addView(heading(when (Setup.vendor) {
            Vendor.XIAOMI -> R.string.guide_title_xiaomi
            Vendor.SAMSUNG -> R.string.guide_title_samsung
            Vendor.HUAWEI -> R.string.guide_title_huawei
            Vendor.ONEPLUS -> R.string.guide_title_oneplus
            Vendor.OTHER -> R.string.guide_title_other
        }))
        setupBox.addView(text(getString(when (Setup.vendor) {
            Vendor.XIAOMI -> R.string.guide_xiaomi
            Vendor.SAMSUNG -> R.string.guide_samsung
            Vendor.HUAWEI -> R.string.guide_huawei
            Vendor.ONEPLUS -> R.string.guide_oneplus
            Vendor.OTHER -> R.string.guide_other
        })))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (Setup.vendor != Vendor.OTHER) row.addView(button(R.string.guide_autostart) { Setup.openAutostart(this) })
        row.addView(button(R.string.guide_app_settings) { Setup.openAppDetails(this) })
        setupBox.addView(row)
    }

    /** "Allow all the time" needs its own request, after the normal location permission; Android 11+ opens the settings page for it. */
    private fun askBackground() {
        if (!Setup.hasAnyLocation(this)) {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.setup_background)
            .setMessage(R.string.setup_background_why)
            .setPositiveButton(R.string.setup_continue) { _, _ ->
                if (Build.VERSION.SDK_INT >= 29) askPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNeutralButton(R.string.guide_app_settings) { _, _ -> Setup.openAppDetails(this) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun step(ok: Boolean, label: Int, action: Int, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val t = TextView(this).apply {
            text = (if (ok) "✓  " else "✗  ") + getString(label)
            setTextColor(if (ok) Color.rgb(46, 125, 50) else Color.rgb(198, 40, 40))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        }
        row.addView(t)
        if (!ok) row.addView(button(action, onClick))
        setupBox.addView(row)
    }

    // ---- status and log

    private fun renderStatus() {
        statusView.text = DeviceStatus.snapshot(this).joinToString("\n") { it.asText() }
        logView.text = Diag.tail(80).reversed().joinToString("\n")
    }

    private fun shareLog() {
        try {
            val dir = File(filesDir, "diag").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
            dir.listFiles { f -> f.name.startsWith("eltouro-diagnose-") }?.forEach { it.delete() }
            val f = File(dir, "eltouro-diagnose-$stamp.txt")
            val sb = StringBuilder()
            sb.append("ElTouro – Diagnose ").append(Date()).append("\n\n")
            DeviceStatus.snapshot(this).forEach { sb.append(it.asText()).append('\n') }
            sb.append("\n---- Log ----\n").append(Diag.readAll())
            f.writeText(sb.toString(), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "ElTouro Diagnose $stamp")
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Diag.log("log shared (${f.length() / 1024} kB)")
            startActivity(Intent.createChooser(send, getString(R.string.diag_share)))
        } catch (e: Exception) {
            Diag.log("log share failed – ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // ---- tiny view helpers

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun heading(res: Int) = TextView(this).apply {
        text = getString(res)
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(16), 0, dp(6))
    }

    private fun text(s: String) = TextView(this).apply {
        text = s
        textSize = 14f
        setPadding(0, 0, 0, dp(6))
    }

    private fun mono(size: Float) = TextView(this).apply {
        typeface = Typeface.MONOSPACE
        textSize = size
        setTextIsSelectable(true)
    }

    private fun button(label: Int, onClick: () -> Unit) = Button(this).apply {
        text = getString(label)
        isAllCaps = false
        setOnClickListener(View.OnClickListener { onClick() })
    }
}
