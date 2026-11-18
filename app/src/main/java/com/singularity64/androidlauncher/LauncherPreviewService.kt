package com.singularity64.androidlauncher

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.Executors

object AppPreviewCache {
 private val handler=Handler(Looper.getMainLooper())
 private val images=androidx.compose.runtime.mutableStateMapOf<String,Bitmap>()
 @Synchronized fun put(pkg:String,bitmap:Bitmap) { handler.post { images[pkg]=bitmap; if(images.size>12) images.remove(images.keys.first()) } }
 @Synchronized fun get(pkg:String):Bitmap? = images[pkg]
}

class LauncherPreviewService:AccessibilityService() {
 private val handler=Handler(Looper.getMainLooper())
 private val executor=Executors.newSingleThreadExecutor()
 private var currentPackage:String?=null
 private var captureTask:Runnable?=null
 private var capturing=false
 override fun onAccessibilityEvent(event:AccessibilityEvent?) {
  if(event?.eventType!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
     event?.eventType!=AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
  val pkg=event.packageName?.toString() ?: return
  if(pkg==packageName || pkg=="com.android.systemui") return
  if(currentPackage==pkg && captureTask!=null) return
  currentPackage=pkg
  captureTask?.let(handler::removeCallbacks)
  val task=Runnable {
   if(currentPackage!=pkg || capturing || Build.VERSION.SDK_INT<30) return@Runnable
   capturing=true
   takeScreenshot(Display.DEFAULT_DISPLAY,executor,object:TakeScreenshotCallback {
    override fun onSuccess(result:ScreenshotResult) {
     try {
      val buffer=result.hardwareBuffer
      try {
       val full=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)
       if(full!=null && currentPackage==pkg) {
        val width=320
        val height=(full.height.toFloat()*width/full.width).toInt().coerceAtLeast(1)
        val small=Bitmap.createScaledBitmap(full,width,height,true).copy(Bitmap.Config.ARGB_8888,false)
        AppPreviewCache.put(pkg,small)
       }
      } finally { buffer.close() }
     } catch (_:Exception) {} finally { capturing=false }
    }
    override fun onFailure(errorCode:Int) { capturing=false }
   })
  }
  captureTask=task
  handler.postDelayed(task,650L)
 }
 override fun onInterrupt() {}
 override fun onDestroy() {
  captureTask?.let(handler::removeCallbacks)
  executor.shutdown()
  super.onDestroy()
 }
}
