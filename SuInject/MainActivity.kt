package com.sf4injector

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

// Big-button injector like AMODSVM, but local + su. No server, no key.
// Put vip/libsf4.so in app/src/main/assets/libsf4.so before building.
class MainActivity : Activity() {
  lateinit var log: TextView
  fun runSu(cmd: String): String = try {
    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
    p.waitFor()
    p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()
  } catch (e: Exception) { "su fail: $e" }

  override fun onCreate(s: Bundle?) {
    super.onCreate(s)
    val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40,40,40,40) }
    log = TextView(this).apply { textSize = 16f; text = "SF4 injector (root VM)\n1. start SF4\n2. tap INJECT\n" }
    val btn = Button(this).apply { text = "INJECT libsf4.so"; textSize = 24f; setOnClickListener { inject() } }
    lay.addView(btn); lay.addView(log)
    setContentView(lay)
  }

  fun inject() {
    log.text = "copying lib..."
    try {
      // assets -> files/libsf4.so -> /data/local/tmp (target can read)
      val dst = File(filesDir, "libsf4.so")
      assets.open("libsf4.so").use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
      var out = runSu("cp ${dst.absolutePath} /data/local/tmp/libsf4.so && chmod 755 /data/local/tmp/libsf4.so && chcon u:object_r:shell_data_file:s0 /data/local/tmp/libsf4.so 2>/dev/null; echo ok")
      log.text = "copy: $out\nfinding SF4..."
      val pid = runSu("pidof com.nekki.shadowfight4").trim().split(" ").firstOrNull().orEmpty()
      if (pid.isEmpty()) { log.text = "start SF4 first"; return }
      log.text = "SF4 pid=$pid, injecting (3s sleep in JNI_OnLoad)..."
      // injector binary bundled in assets too (build injector.c via NDK first)
      val inj = File(filesDir, "injector")
      assets.open("injector").use { i -> inj.outputStream().use { o -> i.copyTo(o) } }
      out = runSu("chmod 755 ${inj.absolutePath}; ${inj.absolutePath} $pid /data/local/tmp/libsf4.so")
      log.text = "done:\n$out\nGrant overlay to SF4 if menu doesn't show."
    } catch (e: Exception) { log.text = "err: $e" }
  }
}
