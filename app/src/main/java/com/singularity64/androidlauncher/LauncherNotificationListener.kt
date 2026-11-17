package com.singularity64.androidlauncher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateMapOf

object LauncherNotificationState {
 val activePackages = mutableStateMapOf<String, Boolean>()
 val latestNotification = mutableStateMapOf<String, Long>()
 val viewedNotification = mutableStateMapOf<String, Long>()
 fun markViewed(pkg:String) { viewedNotification[pkg] = latestNotification[pkg] ?: 0L }
 fun isUnseen(pkg:String):Boolean = activePackages[pkg] == true &&
  (latestNotification[pkg] ?: 0L) > (viewedNotification[pkg] ?: 0L)
 fun refresh(notifications: Array<StatusBarNotification>?) {
  val relevant = notifications?.filter { it.isClearable && !it.isOngoing } ?: emptyList()
  val packages = relevant.map { it.packageName }.toSet()
  activePackages.keys.toList().forEach { if(it !in packages) { activePackages.remove(it);latestNotification.remove(it);viewedNotification.remove(it) } }
  relevant.groupBy { it.packageName }.forEach { (pkg,items) ->
   activePackages[pkg] = true
   latestNotification[pkg] = items.maxOf { it.postTime }
  }
 }
}
class LauncherNotificationListener : NotificationListenerService() {
 override fun onListenerConnected() { super.onListenerConnected(); refreshSafely() }
 override fun onNotificationPosted(sbn: StatusBarNotification?) { refreshSafely() }
 override fun onNotificationRemoved(sbn: StatusBarNotification?) { refreshSafely() }
 override fun onListenerDisconnected() { LauncherNotificationState.activePackages.clear();LauncherNotificationState.latestNotification.clear(); super.onListenerDisconnected() }
 private fun refreshSafely() {
  try { LauncherNotificationState.refresh(activeNotifications) } catch (_: Exception) { LauncherNotificationState.activePackages.clear();LauncherNotificationState.latestNotification.clear() }
 }
}
