package com.singularity64.androidlauncher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateMapOf

object LauncherNotificationState {
 val activePackages = mutableStateMapOf<String, Boolean>()
 fun refresh(notifications: Array<StatusBarNotification>?) {
  val packages = notifications?.filter { it.isClearable && !it.isOngoing }?.map { it.packageName }?.toSet() ?: emptySet()
  activePackages.keys.toList().forEach { if(it !in packages) activePackages.remove(it) }
  packages.forEach { activePackages[it] = true }
 }
}
class LauncherNotificationListener : NotificationListenerService() {
 override fun onListenerConnected() { super.onListenerConnected(); refreshSafely() }
 override fun onNotificationPosted(sbn: StatusBarNotification?) { refreshSafely() }
 override fun onNotificationRemoved(sbn: StatusBarNotification?) { refreshSafely() }
 override fun onListenerDisconnected() { LauncherNotificationState.activePackages.clear(); super.onListenerDisconnected() }
 private fun refreshSafely() {
  try { LauncherNotificationState.refresh(activeNotifications) } catch (_: Exception) { LauncherNotificationState.activePackages.clear() }
 }
}
