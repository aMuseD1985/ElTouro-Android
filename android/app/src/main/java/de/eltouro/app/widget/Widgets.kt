package de.eltouro.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import de.eltouro.app.MainActivity
import de.eltouro.app.R
import de.eltouro.app.RideState
import de.eltouro.app.web.Summary
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** Builds the four home-screen widgets from [WidgetData] and keeps them up to date. */
object Widgets {
    const val ACTION_REFRESH = "de.eltouro.app.WIDGET_REFRESH"
    const val FREE = 1
    const val NEAR = 2
    const val HERDS = 3
    const val NEWS = 4

    private fun providerOf(kind: Int): Class<out AppWidgetProvider> = when (kind) {
        FREE -> FreeRideWidget::class.java
        NEAR -> NearbyWidget::class.java
        HERDS -> HerdsWidget::class.java
        else -> NewsWidget::class.java
    }

    fun renderAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (kind in listOf(FREE, NEAR, HERDS, NEWS)) {
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, providerOf(kind)))
            if (ids.isNotEmpty()) render(ctx, mgr, ids, kind)
        }
    }

    fun renderAllAsync(ctx: Context) {
        val app = ctx.applicationContext
        Thread { try { renderAll(app) } catch (_: Exception) { } }.start()
    }

    /** Fetch again if the last try is older than [maxAgeMs] and widgets are placed, then redraw. */
    fun refreshAsync(ctx: Context, maxAgeMs: Long) {
        val app = ctx.applicationContext
        Thread {
            try {
                if (anyPlaced(app) && WidgetData.ageMs(app) >= maxAgeMs) WidgetData.refresh(app)
                renderAll(app)
            } catch (_: Exception) { }
        }.start()
    }

    private fun anyPlaced(ctx: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(ctx)
        return listOf(NEAR, HERDS, NEWS).any { mgr.getAppWidgetIds(ComponentName(ctx, providerOf(it))).isNotEmpty() }
    }

    fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray, kind: Int) {
        val st = WidgetData.state(ctx)
        for (id in ids) mgr.updateAppWidget(id, if (kind == FREE) freeRide(ctx, id) else list(ctx, id, kind, st))
    }

    // ---- free ride: one tap starts recording – or brings the rider back to the running ride

    private fun freeRide(ctx: Context, id: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_free_ride)
        val running = RideState.resumablePath()
        if (running != null) {
            v.setTextViewText(R.id.w_action, ctx.getString(R.string.widget_free_resume))
            v.setTextViewText(R.id.w_sub, ctx.getString(R.string.widget_free_running, DateUtils.getRelativeTimeSpanString(RideState.startedAt)))
            v.setOnClickPendingIntent(R.id.w_root, open(ctx, running, code(FREE, id, 0)))
        } else {
            v.setTextViewText(R.id.w_action, ctx.getString(R.string.widget_free_action))
            v.setTextViewText(R.id.w_sub, ctx.getString(R.string.widget_free_sub))
            v.setOnClickPendingIntent(R.id.w_root, open(ctx, "/free?autostart=1", code(FREE, id, 0)))
        }
        return v
    }

    // ---- lists: near me, herds, news

    private data class Row(val text: String, val path: String)

    private fun list(ctx: Context, id: Int, kind: Int, st: WidgetData.State): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_list)
        val s = st.summary
        val (title, rows, empty, all) = when (kind) {
            NEAR -> Quad(
                s?.nearLabel?.let { ctx.getString(R.string.widget_near_title_at, it) } ?: ctx.getString(R.string.widget_near_title),
                (s?.rides.orEmpty().map { Row(rideLine(ctx, it), it.url) } + s?.tours.orEmpty().map { Row(tourLine(ctx, it), it.url) }).take(Summary.MAX),
                R.string.widget_empty_near, "/rides")
            HERDS -> Quad(
                if ((s?.unreadTotal ?: 0) > 0) ctx.getString(R.string.widget_herds_title_n, s!!.unreadTotal) else ctx.getString(R.string.widget_herds_title),
                s?.herds.orEmpty().map { Row(herdLine(ctx, it), it.url) },
                R.string.widget_empty_herds, "/crews")
            else -> Quad(
                ctx.getString(R.string.widget_news_title),
                s?.news.orEmpty().map { Row(newsLine(it), it.url) },
                R.string.widget_empty_news, "/notifications")
        }
        v.setTextViewText(R.id.w_title, title)
        v.setOnClickPendingIntent(R.id.w_title, open(ctx, all, code(kind, id, 9)))
        val rowIds = intArrayOf(R.id.w_row1, R.id.w_row2, R.id.w_row3)
        val message = when (st.status) {
            WidgetData.Status.NO_KEY -> ctx.getString(R.string.widget_need_login)
            WidgetData.Status.NOT_AVAILABLE -> ctx.getString(R.string.widget_not_yet)
            WidgetData.Status.NEVER -> ctx.getString(R.string.widget_loading)
            else -> null
        }
        rowIds.forEachIndexed { i, rid ->
            val r = rows.getOrNull(i)
            when {
                r != null -> {
                    v.setViewVisibility(rid, View.VISIBLE)
                    v.setTextViewText(rid, r.text)
                    v.setOnClickPendingIntent(rid, open(ctx, r.path, code(kind, id, i + 1)))
                }
                i == 0 && s == null -> {
                    v.setViewVisibility(rid, View.VISIBLE)
                    v.setTextViewText(rid, message ?: ctx.getString(empty))
                    v.setOnClickPendingIntent(rid, open(ctx, if (st.status == WidgetData.Status.NO_KEY) "/" else all, code(kind, id, 1)))
                }
                i == 0 && rows.isEmpty() -> {
                    v.setViewVisibility(rid, View.VISIBLE)
                    v.setTextViewText(rid, ctx.getString(empty))
                    v.setOnClickPendingIntent(rid, open(ctx, all, code(kind, id, 1)))
                }
                else -> v.setViewVisibility(rid, View.GONE)
            }
        }
        val foot = when {
            st.fetchedAt <= 0 -> ""
            st.status == WidgetData.Status.OFFLINE -> ctx.getString(R.string.widget_offline, DateUtils.getRelativeTimeSpanString(st.fetchedAt))
            else -> ctx.getString(R.string.widget_updated, DateUtils.getRelativeTimeSpanString(st.fetchedAt))
        }
        v.setTextViewText(R.id.w_foot, foot)
        val refresh = Intent(ctx, providerOf(kind)).setAction(ACTION_REFRESH)
        v.setOnClickPendingIntent(R.id.w_refresh, PendingIntent.getBroadcast(ctx, code(kind, id, 8), refresh,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        return v
    }

    private data class Quad(val title: String, val rows: List<Row>, val empty: Int, val all: String)

    private fun km(d: Double?) = d?.let { String.format(Locale.getDefault(), if (it < 10) "%.1f" else "%.0f", it) }

    private fun rideLine(ctx: Context, r: Summary.NearRide): String =
        "🛴 " + r.title + " · " + whenText(r.startsAt) + (km(r.distanceKm)?.let { " · " + ctx.getString(R.string.widget_km_away, it) } ?: "")

    private fun tourLine(ctx: Context, t: Summary.NearTour): String =
        "🗺 " + t.title + (km(t.lengthKm)?.let { " · " + ctx.getString(R.string.widget_km, it) } ?: "") +
            (km(t.distanceKm)?.let { " · " + ctx.getString(R.string.widget_km_away, it) } ?: "")

    private fun herdLine(ctx: Context, h: Summary.Herd): String =
        h.name + (if (h.unread > 0) " · " + ctx.getString(R.string.widget_unread, h.unread) else "") + (if (h.last.isNotEmpty()) " – " + h.last else "")

    private fun newsLine(n: Summary.News): String {
        val t = parseIso(n.at)
        return n.text + (if (t > 0) " · " + DateUtils.getRelativeTimeSpanString(t) else "")
    }

    private fun whenText(iso: String): String {
        val t = parseIso(iso)
        if (t <= 0) return ""
        return if (DateUtils.isToday(t)) DateFormat.getTimeInstance(DateFormat.SHORT).format(t)
        else SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(t)
    }

    private fun parseIso(iso: String): Long = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).parse(iso.replace("Z", "+00:00"))?.time ?: 0L
    } catch (_: Exception) { 0L }

    private fun code(kind: Int, id: Int, slot: Int) = kind * 1_000_000 + id * 10 + slot

    private fun open(ctx: Context, path: String, code: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN).putExtra(MainActivity.EXTRA_PATH, path)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** Shared by the four providers: draw from the cache right away, then fetch in the background if it is older than 15 min. */
abstract class BaseWidget(private val kind: Int) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.render(context, appWidgetManager, appWidgetIds, kind)
        if (kind == Widgets.FREE) return
        val pending = goAsync()
        Thread {
            try {
                if (WidgetData.ageMs(context) > 15 * 60_000L) WidgetData.refresh(context)
                Widgets.renderAll(context)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != Widgets.ACTION_REFRESH) return
        val pending = goAsync()
        Thread {
            try {
                WidgetData.refresh(context)
                Widgets.renderAll(context)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}

class FreeRideWidget : BaseWidget(Widgets.FREE)
class NearbyWidget : BaseWidget(Widgets.NEAR)
class HerdsWidget : BaseWidget(Widgets.HERDS)
class NewsWidget : BaseWidget(Widgets.NEWS)
