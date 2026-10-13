package de.eltouro.app

import android.content.Context

/** The "stay signed in" token (app_login.php ?r=) that the page stores via setRemember – same preferences as before the port. */
object Remember {
    fun get(ctx: Context): String? =
        ctx.getSharedPreferences("eltouro", Context.MODE_PRIVATE).getString("remember", null)?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
}
