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

// Accessibility-first big-button loader.
// IMPORTANT LIMIT (no-root): Android sandbox blocks cross-process dlopen.
// This APK therefore does BOTH, in order:
//  1) loads libsf4.so in its OWN process -> menu UI draws here (proves lib+dex+overlay work, game patches no-op without SF4 memory)
//  2) launches SF4 via intent (user plays there)
//  3) if su exists (rooted VM), attempts real inject into SF4 pid via /data/local/tmp + injector (same as SuInject/)
class MainActivity : Activity() {
  lateinit var log: TextView
  val SF4_PKG = "com.nekki.shadowfight4"
  val handler = Handler(Looper.getMainLooper())

  fun runSu(cmd: String): String = try {
    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
    p.waitFor()
    p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()
  } catch (e: Exception) { "no-su: $e" }

  override fun onCreate(s: Bundle?) {
    super.onCreate(s)
    val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48,48,48,48) }
    log = TextView(this).apply { textSize = 18f; text = "SF4 Loader\n" }
    val b1 = Button(this).apply { text = "1. LOAD MENU HERE (no root)"; textSize = 22f; setOnClickListener { loadLocal() } }
    val b2 = Button(this).apply { text = "2. START SF4"; textSize = 22f; setOnClickListener { startSf4() } }
    val b3 = Button(this).apply { text = "3. INJECT SF4 (needs su)"; textSize = 22f; setOnClickListener { injectSf4() } }
    lay.addView(b1); lay.addView(b2); lay.addView(b3); lay.addView(log)
    setContentView(lay)
    if (!Settings.canDrawOverlays(this)) {
      log.text = "Grant overlay first, then reopen."
      startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }
  }

  // (1) same-process load: triggers libsf4 JNI_OnLoad -> AttachCurrentThread ok (we are an app),
  // currentApplication = OUR app, dex loads, RegisterNatives ok, CreateMenu(our ctx) draws overlay HERE.
  fun loadLocal() {
    try {
      System.loadLibrary("sf4")
      log.text = "libsf4 loaded in loader process.\nWait 3s (sleep in JNI_OnLoad)...\nMenu overlay should appear HERE.\nGame patches will no-op (no SF4 memory) - expected."
    } catch (e: UnsatisfiedLinkError) { log.text = "load fail: $e" }
  }

  // (2) start real game
  fun startSf4() {
    try {
      startActivity(packageManager.getLaunchIntentForPackage(SF4_PKG)!!)
      log.text = "SF4 launched. Now tap 3 (needs su) within VM."
    } catch (e: Exception) { log.text = "SF4 not installed: $e" }
  }

  // (3) root cross-process inject, same as SuInject/inject.sh
  fun injectSf4() {
    log.text = "finding SF4..."
    handler.postDelayed({
      val pid = runSu("pidof $SF4_PKG").trim().split(" ").firstOrNull().orEmpty()
      if (pid.isEmpty()) { log.text = "SF4 not running. Tap 2 first."; return@postDelayed }
      // lib already in apk's jniLibs, copy out to tmp where target can read
      val src = applicationInfo.nativeLibraryDir + "/libsf4.so"
      var out = runSu("cp $src /data/local/tmp/libsf4.so && chmod 755 /data/local/tmp/libsf4.so && echo ok")
      log.text = "copy: $out\npid=$pid injecting..."
      out = runSu("/data/local/tmp/injector $pid /data/local/tmp/libsf4.so 2>&1 || echo NO-INJECTOR")
      log.text = "inject: $out\nIf NO-INJECTOR: push SuInject/injector to /data/local/tmp and retry, or use frida Module.load."
    }, 500)
  }
}
