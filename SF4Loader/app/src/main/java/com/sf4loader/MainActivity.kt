package com.sf4loader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

// One-button loader. No ordering, no crashes: every step null-checked,
// heavy work off the main thread (lib JNI_OnLoad sleeps 3s).
class MainActivity : Activity() {
  lateinit var log: TextView
  val handler = Handler(Looper.getMainLooper())
  var busy = false

  fun ui(s: String) { handler.post { log.text = s } }
  fun uiAppend(s: String) { handler.post { log.text = "${log.text}\n$s" } }

  fun runSu(cmd: String): String = try {
    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
    p.waitFor()
    (p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()).trim()
  } catch (e: Exception) { "" }

  // Find SF4 even if package renames: prefer arena, else any nekki/shadowfight pkg.
  fun findSf4(): String? {
    val pm = packageManager
    try {
      val pkgs = pm.getInstalledApplications(0).map { it.packageName }
      pkgs.firstOrNull { it == "com.nekki.shadowfightarena" }?.let { return it }
      pkgs.firstOrNull { "shadowfight" in it }?.let { return it }
      pkgs.firstOrNull { "nekki" in it }?.let { return it }
    } catch (e: Exception) { /* ignore, fall through */ }
    return try {
      pm.getLaunchIntentForPackage("com.nekki.shadowfightarena")?.let { "com.nekki.shadowfightarena" }
    } catch (e: Exception) { null }
  }

  override fun onCreate(s: Bundle?) {
    super.onCreate(s)
    val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48,48,48,48) }
    log = TextView(this).apply { textSize = 18f; text = "SF4 Loader\nTap RUN, then wait. Grant overlay if asked." }
    val run = Button(this).apply { text = "RUN"; textSize = 28f; setOnClickListener { runAll() } }
    lay.addView(run); lay.addView(log)
    setContentView(lay)
  }

  fun runAll() {
    if (busy) return
    busy = true
    if (!Settings.canDrawOverlays(this)) {
      ui("Grant overlay, then tap RUN again.")
      try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (e: Exception) { ui("Overlay request failed: $e") }
      busy = false
      return
    }
    ui("Working... (takes ~10s)")
    Thread({
      try {
        // 1. load menu lib in our own process (menu UI check)
        try {
          System.loadLibrary("sf4")
          uiAppend("Menu lib loaded, waiting 4s for dex...")
        } catch (e: UnsatisfiedLinkError) { uiAppend("load fail: ${e.message}") ; busy = false; return@Thread }
        Thread.sleep(4000)
        // 2. start game
        val pkg = findSf4()
        if (pkg == null) { uiAppend("SF4 not found (looked for nekki/shadowfight pkgs). Install it first."); busy = false; return@Thread }
        uiAppend("Found: $pkg, launching...")
        try {
          val it = packageManager.getLaunchIntentForPackage(pkg)
          if (it == null) { uiAppend("No launch intent for $pkg"); busy = false; return@Thread }
          startActivity(it)
        } catch (e: Exception) { uiAppend("Launch failed: $e"); busy = false; return@Thread }
        Thread.sleep(3000)
        // 3. root inject if su exists (real patches need same-process lib)
        val pid = runSu("pidof $pkg").trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        if (pid.isEmpty()) { uiAppend("No su or SF4 pid not visible. Menu runs in loader only."); busy = false; return@Thread }
        uiAppend("SF4 pid=$pid, injecting...")
        val src = applicationInfo.nativeLibraryDir + "/libsf4.so"
        runSu("cp $src /data/local/tmp/libsf4.so && chmod 755 /data/local/tmp/libsf4.so")
        val inj = applicationInfo.nativeLibraryDir + "/injector"
        val out = runSu("test -x $inj && $inj $pid /data/local/tmp/libsf4.so || echo NO-INJECTOR-BIN")
        uiAppend("inject: $out")
        busy = false
      } catch (e: Exception) { uiAppend("Error: $e"); busy = false }
    }).start()
  }
}
