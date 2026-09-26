package com.sf4loader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// One-button loader with staged markers so a single run tells us WHERE it dies.
// Theory to confirm: libsf4's bg thread (sub_15658) calls _exit(0) on maps/dlopen
// checks -> process vanishes with NO dialog. A real native crash shows a dialog
// and a tombstone. Markers below distinguish the two.
class MainActivity : Activity() {
  lateinit var log: TextView
  val handler = Handler(Looper.getMainLooper())
  var busy = false
  val TAG = "SF4Loader"

  fun ui(s: String) { try { handler.post { log.text = s }; Log.i(TAG, s) } catch (e: Exception) {} }
  fun uiAppend(s: String) { try { handler.post { log.append("\n$s") }; Log.i(TAG, s) } catch (e: Exception) {} }

  fun runSu(cmd: String): String = try {
    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
    p.waitFor()
    (p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()).trim()
  } catch (e: Exception) { "" }

  fun findSf4(): String? {
    try {
      val pkgs = packageManager.getInstalledApplications(0).map { it.packageName }
      pkgs.firstOrNull { it == "com.nekki.shadowfightarena" }?.let { return it }
      pkgs.firstOrNull { "shadowfight" in it }?.let { return it }
      pkgs.firstOrNull { "nekki" in it }?.let { return it }
    } catch (e: Exception) { uiAppend("pkg scan err: $e") }
    return null
  }

  override fun onCreate(s: Bundle?) {
    super.onCreate(s)
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
      try { Log.e(TAG, "CRASH: $e") } catch (_: Exception) {}
    }
    val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48,48,48,48) }
    log = TextView(this).apply { textSize = 16f; text = "SF4 Loader\nTap RUN, watch markers. If app vanishes with NO dialog after MARK-2, lib killed us (_exit) - report that." }
    val run = Button(this).apply { text = "RUN"; textSize = 28f; setOnClickListener { runAll() } }
    lay.addView(run)
    val sv = ScrollView(this).apply { addView(log) }
    lay.addView(sv)
    setContentView(lay)
  }

  fun runAll() {
    if (busy) return
    busy = true
    if (!Settings.canDrawOverlays(this)) {
      ui("Grant overlay, then RUN again.")
      try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (e: Exception) { ui("Overlay request failed: $e") }
      busy = false
      return
    }
    ui("MARK-0 RUN pressed, overlay ok")
    Thread({
      try {
        uiAppend("MARK-1 calling loadLibrary(sf4) on bg thread...")
        try {
          System.loadLibrary("sf4")
        } catch (t: Throwable) { uiAppend("MARK-FAIL load: $t"); busy = false; return@Thread }
        uiAppend("MARK-2 loadLibrary RETURNED (JNI_OnLoad done). If you never see MARK-3, process died inside load = _exit or native crash.")
        Thread.sleep(4000)
        uiAppend("MARK-3 alive after 4s. Starting SF4...")
        val pkg = findSf4()
        if (pkg == null) { uiAppend("MARK-4 SF4 not installed here."); busy = false; return@Thread }
        uiAppend("MARK-4 found $pkg, launching...")
        try {
          val it = packageManager.getLaunchIntentForPackage(pkg)
          if (it == null) { uiAppend("MARK-FAIL no launch intent for $pkg"); busy = false; return@Thread }
          startActivity(it)
        } catch (e: Exception) { uiAppend("MARK-FAIL launch: $e"); busy = false; return@Thread }
        Thread.sleep(3000)
        val pid = runSu("pidof $pkg").trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        if (pid.isEmpty()) { uiAppend("MARK-5 no su/pid. Menu (if drawn) is loader-only."); busy = false; return@Thread }
        uiAppend("MARK-5 pid=$pid, injecting...")
        val src = applicationInfo.nativeLibraryDir + "/libsf4.so"
        runSu("cp $src /data/local/tmp/libsf4.so && chmod 755 /data/local/tmp/libsf4.so")
        val inj = applicationInfo.nativeLibraryDir + "/injector"
        uiAppend("MARK-6 inject out: " + runSu("test -x $inj && $inj $pid /data/local/tmp/libsf4.so || echo NO-INJECTOR-BIN"))
        busy = false
      } catch (e: Exception) { uiAppend("MARK-FAIL: $e"); busy = false }
    }).start()
  }
}
