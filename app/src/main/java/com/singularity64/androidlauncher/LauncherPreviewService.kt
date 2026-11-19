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
 private var retries=0

 override fun onAccessibilityEvent(event:AccessibilityEvent?) {
  if(event?.eventType!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
     event?.eventType!=AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
  val pkg=event.packageName?.toString() ?: return
  if(pkg==packageName || pkg=="com.android.systemui") return
  if(currentPackage!=pkg) {
   currentPackage=pkg
   retries=0
   scheduleCapture(pkg,650L)
  } else if(AppPreviewCache.get(pkg)==null && captureTask==null && !capturing) {
   scheduleCapture(pkg,650L)
  }
 }

 private fun scheduleCapture(pkg:String,delay:Long) {
  captureTask?.let(handler::removeCallbacks)
  val task=Runnable {
   captureTask=null
   if(currentPackage!=pkg || capturing || Build.VERSION.SDK_INT<30) return@Runnable
   capturing=true
   try {
    takeScreenshot(Display.DEFAULT_DISPLAY,executor,object:TakeScreenshotCallback {
     override fun onSuccess(result:ScreenshotResult) {
      var saved=false
      try {
       val buffer=result.hardwareBuffer
       try {
        val full=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)
        if(full!=null && currentPackage==pkg && full.width>0) {
         val width=320
         val height=(full.height.toFloat()*width/full.width).toInt().coerceAtLeast(1)
         val small=Bitmap.createScaledBitmap(full,width,height,true).copy(Bitmap.Config.ARGB_8888,false)
         AppPreviewCache.put(pkg,small)
         saved=true
        }
       } finally { buffer.close() }
      } catch (e:Exception) {
       android.util.Log.w("LauncherPreview","Screenshot processing failed",e)
      } finally {
       handler.post {
        capturing=false
        if(!saved) retryCapture(pkg)
       }
      }
     }
     override fun onFailure(errorCode:Int) {
      android.util.Log.w("LauncherPreview","Screenshot failed, code="+errorCode)
      handler.post { capturing=false; retryCapture(pkg) }
     }
    })
   } catch (e:Exception) {
    android.util.Log.w("LauncherPreview","Screenshot request failed",e)
    capturing=false
    retryCapture(pkg)
   }
  }
  captureTask=task
  handler.postDelayed(task,delay)
 }

 private fun retryCapture(pkg:String) {
  if(currentPackage==pkg && AppPreviewCache.get(pkg)==null && retries<3) {
   retries++
   scheduleCapture(pkg,1500L)
  }
 }

 override fun onInterrupt() {}
 override fun onDestroy() {
  captureTask?.let(handler::removeCallbacks)
  executor.shutdown()
  super.onDestroy()
 }
}
