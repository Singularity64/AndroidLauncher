package com.singularity64.androidlauncher
import android.content.Intent
import android.net.Uri
import android.content.Context
import android.content.pm.ResolveInfo
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.combinedClickable
import android.os.Bundle
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.os.Process
import android.provider.Settings
import java.util.Calendar
import kotlin.math.ln
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val bg = Color.Black
private val navy = Color(0xFF263752)
private val blue = Color(0xFF075AAB)
private val purple = Color(0xFF543083)
private val green = Color(0xFF247B26)
private val red = Color(0xFFAE2034)
private fun monochromeIcon(drawable:android.graphics.drawable.Drawable):androidx.compose.ui.graphics.ImageBitmap {
 val bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888)
 drawable.setBounds(0,0,96,96)
 drawable.draw(Canvas(bitmap))
 return bitmap.asImageBitmap()
}

private fun usageAccess(context:Context):Boolean {
 val ops=context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
 return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName)==AppOpsManager.MODE_ALLOWED
}
private fun adaptiveScores(context:Context,packages:List<String>):Map<String,Double> {
 if(!usageAccess(context)) return emptyMap()
 val now=System.currentTimeMillis()
 val manager=context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
 val start=Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR,-35); set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0) }.timeInMillis
 val todayDow=Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
 val stats=manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,start,now) ?: return emptyMap()
 val scores=mutableMapOf<String,Double>()
 val eligible=packages.toSet()
 stats.forEach { item ->
  if(item.packageName !in eligible || item.totalTimeInForeground<=0L) return@forEach
  val ageDays=((now-item.firstTimeStamp)/86400000L).coerceAtLeast(0)
  val day=Calendar.getInstance().apply { timeInMillis=item.firstTimeStamp }.get(Calendar.DAY_OF_WEEK)
  val sameDay=day==todayDow
  val weight=when {
   ageDays==0L -> 2.2
   sameDay -> 2.0
   else -> 0.35
  } * (if(ageDays>21) 0.65 else 1.0)
  val minutes=item.totalTimeInForeground/60000.0
  val contribution=ln(1.0+minutes)*weight
  scores[item.packageName]=(scores[item.packageName]?:0.0)+contribution
 }
 return scores
}
private fun appGroup(app:ResolveInfo,pm:android.content.pm.PackageManager):String {
 val pkg=app.activityInfo.packageName.lowercase(Locale.ROOT)
 val name=app.loadLabel(pm).toString().lowercase(Locale.ROOT)
 return when {
  listOf("airbank","air.bank","revolut","george","csas","kbsmart","mbank","moneta","fio","bank","paypal","wise","finance").any { pkg.contains(it) || name.contains(it) } -> "Bankovnictví a finance"
  listOf("facebook","instagram","messenger","tiktok","snapchat","twitter","reddit","linkedin","threads").any { pkg.contains(it) || name.contains(it) } -> "Sociální sítě"
  listOf("gmail","outlook","calendar","kalend","docs","sheets","drive","office","word","excel","notion","trello","slack","teams","zoom").any { pkg.contains(it) || name.contains(it) } -> "Práce a produktivita"
  listOf("youtube","spotify","netflix","music","hudba","video","twitch","disney").any { pkg.contains(it) || name.contains(it) } -> "Zábava a média"
  listOf("maps","mapy","waze","bolt","uber","wolt","navigation").any { pkg.contains(it) || name.contains(it) } -> "Doprava a mapy"
  listOf("camera","gallery","photos","foto","galerie").any { pkg.contains(it) || name.contains(it) } -> "Fotografie"
  listOf("chrome","firefox","browser","opera","edge").any { pkg.contains(it) || name.contains(it) } -> "Internet"
  else -> when(app.activityInfo.applicationInfo.category) {
   android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Práce a produktivita"
   android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "Sociální sítě"
   android.content.pm.ApplicationInfo.CATEGORY_VIDEO,android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "Zábava a média"
   android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "Doprava a mapy"
   android.content.pm.ApplicationInfo.CATEGORY_GAME -> "Hry"
   else -> "Ostatní aplikace"
  }
 }
}
class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  // Let Android draw the user-selected home wallpaper behind transparent Compose content.
  window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
  window.setBackgroundDrawableResource(android.R.color.transparent)
  window.decorView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
  setContent {
   MaterialTheme(colorScheme=darkColorScheme(background=bg,surface=bg,onSurface=Color.White)) {
    val prefs = remember { getSharedPreferences("mosaic_usage",Context.MODE_PRIVATE) }
    var drawer by remember { mutableStateOf(false) }
    var multitask by remember { mutableStateOf(false) }
    var showFrequent by remember { mutableStateOf(prefs.getBoolean("show_frequent",false)) }
    var smartMode by remember { mutableStateOf(prefs.getBoolean("smart_mode",true)) }
    var search by remember { mutableStateOf("") }
    val homePrefs=remember { getSharedPreferences("mosaic_tiles",Context.MODE_PRIVATE) }
    var homeRevision by remember { mutableIntStateOf(0) }
    var hideHomeLabels by remember { mutableStateOf(homePrefs.getBoolean("hide_home_labels",false)) }
    var showLauncherSettings by remember { mutableStateOf(false) }
    var dimOtherTiles by remember { mutableStateOf(homePrefs.getBoolean("dim_other_tiles",false)) }
    var showNotificationAppPicker by remember { mutableStateOf(false) }
    var notificationAppSearch by remember { mutableStateOf("") }
    val priorityApps=remember { mutableStateListOf<String>().apply { addAll(homePrefs.getString("priority_notification_apps","")!!.split("|").filter { it.isNotBlank() }) } }
    var showNotificationPermission by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
     val enabled=Settings.Secure.getString(contentResolver,"enabled_notification_listeners") ?: ""
     if(!enabled.contains(packageName)) showNotificationPermission=true
    }
    fun addToHome(pkg:String) {
     val pinned=homePrefs.getString("pinned_apps","")!!.split("|").filter { it.isNotBlank() }
     val hidden=homePrefs.getString("hidden_tiles","")!!.split("|").filter { it.isNotBlank() && it!="pinned_$pkg" }.toSet()
     homePrefs.edit().putString("hidden_tiles",hidden.joinToString("|")).putString("pinned_apps",(pinned+pkg).distinct().joinToString("|")).apply()
     homeRevision++
     drawer=false;search=""
    }
    BackHandler(enabled=true) {
     when {
      showLauncherSettings -> showLauncherSettings=false
      multitask -> multitask=false
      drawer -> { drawer=false; search="" }
      else -> { /* Home screen: do not exit the launcher on Back */ }
     }
    }
    var usageRevision by remember { mutableIntStateOf(0) }
    var adaptiveEnabled by remember { mutableStateOf(prefs.getBoolean("adaptive_enabled",true)) }
    var accessRevision by remember { mutableIntStateOf(0) }
    val lifecycleOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
     val observer=androidx.lifecycle.LifecycleEventObserver { _,event -> if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) accessRevision++ }
     lifecycleOwner.lifecycle.addObserver(observer)
     onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val apps = remember {
     packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
      .filter { it.activityInfo.packageName != packageName }
      .distinctBy { it.activityInfo.packageName }
      .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }
    }
    fun today(): String = SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
    fun launch(pkg:String) {
     LauncherNotificationState.markViewed(pkg)
     val intent=packageManager.getLaunchIntentForPackage(pkg) ?: return
     try {
      startActivity(intent)
      val day=today()
      if(prefs.getString("usage_day","") != day) prefs.edit().apply { prefs.all.keys.filter { it.startsWith("count_") }.forEach { remove(it) }; putString("usage_day",day); putBoolean("show_frequent",showFrequent) }.apply()
      prefs.edit().putInt("count_$pkg",prefs.getInt("count_$pkg",0)+1).putInt("week_${day}_$pkg",prefs.getInt("week_${day}_$pkg",0)+1).apply()
      usageRevision++
     } catch (_:Exception) { }
    }
    if(multitask) {
     val todayMs=System.currentTimeMillis()
     val days=(0..6).map { offset -> SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(todayMs-offset*86400000L)) }
     // Prefer Android's actual foreground time, including launches outside this launcher.
     // Keep locally recorded launches as a fallback when usage access is unavailable.
     val revision=usageRevision
     val access=accessRevision
     val ranked=remember(revision,access,apps) {
      val actualUsage=if(usageAccess(this@MainActivity)) {
       try {
        val manager=getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        manager.queryAndAggregateUsageStats(todayMs-7L*86400000L,todayMs)
         .mapValues { it.value.totalTimeInForeground }
       } catch (_:Exception) { emptyMap<String,Long>() }
      } else emptyMap<String,Long>()
      apps.map { app ->
       val pkg=app.activityInfo.packageName
       val local=days.sumOf { d -> prefs.getInt("week_${d}_${pkg}",0) }
       Triple(app,actualUsage[pkg]?:0L,local)
      }.filter { it.second>0L || it.third>0 }
       .sortedWith(compareByDescending<Triple<ResolveInfo,Long,Int>> { it.second }
        .thenByDescending { it.third }
        .thenBy { it.first.loadLabel(packageManager).toString() })
       .map { it.first }
     }
     Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
      Text("‹  Rychlé přepínání",color=Color.White,fontSize=25.sp,modifier=Modifier.clickable { multitask=false }.padding(bottom=8.dp))
      Text("Nejpoužívanější za posledních 7 dní",color=Color.LightGray,fontSize=14.sp,modifier=Modifier.padding(bottom=16.dp))
      if(ranked.isEmpty()) Text("Zatím žádné spuštěné aplikace",color=Color.LightGray)
      else LazyVerticalGrid(columns=GridCells.Fixed(2),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
       gridItems(ranked,key={it.activityInfo.packageName}) { app -> MultitaskCard(app,packageManager,::launch) }
      }
     }
    } else if(drawer) {
     val day=today()
     if(prefs.getString("usage_day","") != day) {
      prefs.edit().apply { prefs.all.keys.filter { it.startsWith("count_") }.forEach { remove(it) }; putString("usage_day",day); putBoolean("show_frequent",showFrequent) }.apply()
     }
     val revision=usageRevision
     val scores=remember(revision,day,apps,accessRevision,adaptiveEnabled) { if(adaptiveEnabled) adaptiveScores(this@MainActivity,apps.map { it.activityInfo.packageName }) else emptyMap() }
     val popular=remember(revision,day,apps,scores,adaptiveEnabled) {
      apps.filter { (scores[it.activityInfo.packageName]?:0.0)>0.0 || prefs.getInt("count_${it.activityInfo.packageName}",0)>0 }
       .sortedWith(compareByDescending<ResolveInfo> { (scores[it.activityInfo.packageName]?:0.0) + ln(1.0+prefs.getInt("count_${it.activityInfo.packageName}",0))*1.5 }
        .thenBy { it.loadLabel(packageManager).toString() }).take(8)
     }
     val filtered=apps.filter { it.loadLabel(packageManager).toString().startsWith(search,ignoreCase=true) }
     Column(Modifier.fillMaxSize().background(Color.Transparent).statusBarsPadding().navigationBarsPadding().padding(horizontal=12.dp,vertical=12.dp)) {
      Row(Modifier.fillMaxWidth().padding(bottom=12.dp),verticalAlignment=Alignment.CenterVertically,
       horizontalArrangement=Arrangement.SpaceBetween) {
       Text("‹ Aplikace",color=Color.White,fontSize=26.sp,
        modifier=Modifier.clickable { drawer=false })
       Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
       Text("⚙",color=Color.White,fontSize=24.sp,modifier=Modifier.clickable { showLauncherSettings=true }.padding(6.dp))
       Row(Modifier.background(Color(0xFF1A1A1F),RoundedCornerShape(24.dp)).padding(3.dp),
        horizontalArrangement=Arrangement.spacedBy(3.dp)) {
        Text("A–Z",color=if(!smartMode) Color.White else Color.LightGray,
         fontSize=13.sp,fontWeight=FontWeight.Medium,
         modifier=Modifier.background(if(!smartMode) Color(0xFF40434D) else Color.Transparent,RoundedCornerShape(20.dp))
          .clickable { smartMode=false;prefs.edit().putBoolean("smart_mode",false).apply() }
          .padding(horizontal=13.dp,vertical=9.dp))
        Text("✦ Smart",color=if(smartMode) Color.White else Color.LightGray,
         fontSize=13.sp,fontWeight=FontWeight.Medium,
         modifier=Modifier.background(if(smartMode) Color(0xFF40434D) else Color.Transparent,RoundedCornerShape(20.dp))
          .clickable { smartMode=true;prefs.edit().putBoolean("smart_mode",true).apply() }
          .padding(horizontal=13.dp,vertical=9.dp))
       }
       }
      }
      OutlinedTextField(value=search,onValueChange={search=it},placeholder={Text("Hledat aplikace…")},singleLine=true,
       leadingIcon={Icon(Icons.Default.Search,contentDescription=null)},
       trailingIcon={if(search.isNotEmpty()) IconButton(onClick={search=""}) { Icon(Icons.Default.Close,contentDescription="Vymazat") }},
       shape=RoundedCornerShape(18.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Color(0xAA181818),unfocusedContainerColor=Color(0xAA181818),focusedBorderColor=Color.White,unfocusedBorderColor=Color.Transparent),
       modifier=Modifier.fillMaxWidth().padding(horizontal=4.dp).padding(bottom=14.dp))
      val collapsed=remember { mutableStateMapOf<String,Boolean>() }
      val rankedApps=filtered.sortedWith(compareByDescending<ResolveInfo> {
       (scores[it.activityInfo.packageName]?:0.0)+ln(1.0+prefs.getInt("count_${it.activityInfo.packageName}",0))*1.5
      }.thenBy { it.loadLabel(packageManager).toString() })
      val grouped=rankedApps.groupBy { appGroup(it,packageManager) }.toList()
       .sortedWith(compareByDescending<Pair<String,List<ResolveInfo>>> { group ->
        group.second.sumOf { (scores[it.activityInfo.packageName]?:0.0)+ln(1.0+prefs.getInt("count_${it.activityInfo.packageName}",0))*1.5 }
       }.thenBy { it.first })
      LazyColumn(modifier=Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),
       contentPadding=PaddingValues(bottom=20.dp)) {
       if(!smartMode) {
        item(key="alphabetical_apps") {
         val alphabetic=filtered.sortedWith(compareBy<ResolveInfo> {
          it.loadLabel(packageManager).toString().lowercase(Locale.getDefault())
         }.thenBy { it.activityInfo.packageName })
         Column(Modifier.fillMaxWidth().background(Color(0xB817191E),RoundedCornerShape(20.dp))
          .border(1.dp,Color(0xFF424751),RoundedCornerShape(20.dp)).padding(12.dp),
          verticalArrangement=Arrangement.spacedBy(10.dp)) {
          Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
           Text("Všechny aplikace",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
           Text("A–Z",color=Color.LightGray,fontSize=13.sp)
          }
          alphabetic.chunked(4).forEach { rowApps ->
           Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            rowApps.forEach { app -> Box(Modifier.weight(1f)) {
             AppGridItem(app,packageManager,::launch,glass=true,onPin=::addToHome)
            } }
            repeat(4-rowApps.size) { Spacer(Modifier.weight(1f)) }
           }
          }
         }
        }
       } else {
       if(search.isBlank()) {
        item(key="frequent_glass") {
         GlassAppSection("Nejčastěji používané aplikace",Color(0xFF3FA86C),
          Color(0xFF10261A),showFrequent,{showFrequent=!showFrequent;prefs.edit().putBoolean("show_frequent",showFrequent).apply()}) {
          if(adaptiveEnabled && !usageAccess(this@MainActivity)) {
           Text("Pro automatické řazení povol přístup k údajům o používání.",color=Color.LightGray,fontSize=12.sp)
           Text("Povolit přístup",color=Color(0xFF8CCBFF),modifier=Modifier.clickable { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }.padding(vertical=8.dp))
          }
          if(popular.isEmpty()) Text("Zatím žádné spuštěné aplikace",color=Color.LightGray,fontSize=12.sp)
          else popular.chunked(4).forEach { rowApps ->
           Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            rowApps.forEach { app -> Box(Modifier.weight(1f)) { AppGridItem(app,packageManager,::launch,glass=true,onPin=::addToHome) } }
            repeat(4-rowApps.size) { Spacer(Modifier.weight(1f)) }
           }
          }
         }
        }
       }
       grouped.forEach { (category,groupApps) ->
        item(key="glass_$category") {
         val (accent,tint)=glassColors(category)
         GlassAppSection(category,accent,tint,collapsed[category]!=true,
          {collapsed[category]=collapsed[category]!=true}) {
          groupApps.chunked(5).forEach { rowApps ->
           Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            rowApps.forEach { app -> Box(Modifier.weight(1f)) { AppGridItem(app,packageManager,::launch,glass=true,onPin=::addToHome) } }
            repeat(5-rowApps.size) { Spacer(Modifier.weight(1f)) }
           }
          }
         }
        }
       }
       }
      }
     }
    } else Home(onApps={search="";drawer=true},onMultitask={multitask=true},onLaunch={launch(it)},apps=apps.map { it.activityInfo.packageName },
      homeRevision=homeRevision,hideLabels=hideHomeLabels,dimOtherTiles=dimOtherTiles,priorityApps=priorityApps.toSet(),onTileRemoved={homeRevision++})
    if(showNotificationPermission) AlertDialog(
     onDismissRequest={showNotificationPermission=false},
     title={Text("Oznámení na dlaždicích")},
     text={Text("Pro malé červené tečky u aplikací povol launcheru přístup k oznámením. Obsah oznámení na ploše nezobrazujeme.")},
     confirmButton={TextButton(onClick={showNotificationPermission=false;startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}) { Text("Povolit") }},
     dismissButton={TextButton(onClick={showNotificationPermission=false}) { Text("Později") }}
    )
    if(showNotificationAppPicker) AlertDialog(
     onDismissRequest={showNotificationAppPicker=false;notificationAppSearch=""},
     title={Text("Vybrané aplikace")},
     text={
      Column(Modifier.heightIn(max=460.dp)) {
       OutlinedTextField(value=notificationAppSearch,onValueChange={notificationAppSearch=it},
        placeholder={Text("Hledat aplikaci…")},singleLine=true,
        leadingIcon={Icon(Icons.Default.Search,contentDescription=null)},
        modifier=Modifier.fillMaxWidth().padding(bottom=8.dp))
       Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())) {
       apps.filter { it.loadLabel(packageManager).toString().contains(notificationAppSearch,ignoreCase=true) }.forEach { app ->
        val pkg=app.activityInfo.packageName
        Row(Modifier.fillMaxWidth().clickable {
         if(pkg in priorityApps) priorityApps.remove(pkg) else priorityApps.add(pkg)
         homePrefs.edit().putString("priority_notification_apps",priorityApps.joinToString("|")).apply()
        }.padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
         Checkbox(checked=pkg in priorityApps,onCheckedChange={ checked ->
          if(checked) { if(pkg !in priorityApps) priorityApps.add(pkg) } else priorityApps.remove(pkg)
          homePrefs.edit().putString("priority_notification_apps",priorityApps.joinToString("|")).apply()
         })
         val icon=remember(pkg) { try { monochromeIcon(app.loadIcon(packageManager)) } catch (_:Exception) { null } }
         if(icon!=null) Image(bitmap=icon,contentDescription=null,modifier=Modifier.size(23.dp).padding(end=3.dp))
         Text(app.loadLabel(packageManager).toString(),fontSize=14.sp,modifier=Modifier.padding(start=6.dp))
        }
       }
       }
      }
     },
     confirmButton={TextButton(onClick={showNotificationAppPicker=false;notificationAppSearch=""}) { Text("Hotovo") }}
    )
    if(showLauncherSettings) AlertDialog(
     onDismissRequest={showLauncherSettings=false},
     title={Text("Nastavení launcheru")},
     text={
      Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
       Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text("Skrýt názvy dlaždic na ploše",modifier=Modifier.weight(1f))
        Switch(checked=hideHomeLabels,onCheckedChange={hideHomeLabels=it;homePrefs.edit().putBoolean("hide_home_labels",it).apply()})
       }
       Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        Column(Modifier.weight(1f)) {
         Text("Zesednout ostatní dlaždice při oznámení",fontSize=14.sp)
         if(dimOtherTiles) Text("Vybrat aplikace (${priorityApps.size})",color=Color(0xFF8CCBFF),fontSize=12.sp,
          modifier=Modifier.clickable { showLauncherSettings=false;notificationAppSearch="";showNotificationAppPicker=true }.padding(top=5.dp))
        }
        Switch(checked=dimOtherTiles,onCheckedChange={dimOtherTiles=it;homePrefs.edit().putBoolean("dim_other_tiles",it).apply()})
       }
      }
     },
     confirmButton={TextButton(onClick={showLauncherSettings=false}) { Text("Hotovo") }}
    )
   }
  }
 }
}

@Composable private fun MultitaskCard(app:ResolveInfo,pm:android.content.pm.PackageManager,onLaunch:(String)->Unit) {
 val pkg=app.activityInfo.packageName
 val icon=remember(pkg) {
  try { monochromeIcon(app.loadIcon(pm)) } catch (_:Exception) { null }
 }
 val shape=RoundedCornerShape(18.dp)
 Column(Modifier.fillMaxWidth().height(92.dp)
  .background(Brush.linearGradient(listOf(Color(0xFF3CAEE8).copy(alpha=0.52f),Color(0xFF102D58).copy(alpha=0.55f),Color(0xFFAE40D9).copy(alpha=0.48f))),shape)
  .border(1.dp,Brush.linearGradient(listOf(Color(0xFFB9F7FF),Color(0xFF18BFFF),Color(0xFFF044F7))),shape)
  .clickable { onLaunch(pkg) }.padding(horizontal=8.dp,vertical=9.dp),
  horizontalAlignment=Alignment.CenterHorizontally,
  verticalArrangement=Arrangement.spacedBy(8.dp)) {
  if(icon!=null) Image(bitmap=icon,contentDescription=null,modifier=Modifier.size(36.dp))
  else Text("▦",color=Color.White,fontSize=30.sp)
  Text(app.loadLabel(pm).toString(),color=Color.White,fontSize=12.sp,maxLines=1,
   overflow=TextOverflow.Ellipsis)
 }
}
private fun glassColors(category:String):Pair<Color,Color> = when(category) {
 "Bankovnictví a finance" -> Color(0xFF258D84) to Color(0xFF0C2020)
 "Doprava a mapy" -> Color(0xFF347AC2) to Color(0xFF101D32)
 "Práce a produktivita" -> Color(0xFFD58C39) to Color(0xFF292015)
 "Zábava a média" -> Color(0xFFB74C64) to Color(0xFF28151E)
 "Fotografie" -> Color(0xFFC05DA3) to Color(0xFF281725)
 "Sociální sítě" -> Color(0xFF8461C7) to Color(0xFF21182F)
 "Internet" -> Color(0xFF4D91C2) to Color(0xFF14232E)
 "Hry" -> Color(0xFF5BA96C) to Color(0xFF15251B)
 else -> Color(0xFF64748B) to Color(0xFF1A2029)
}
@Composable private fun GlassAppSection(
 title:String,accent:Color,tint:Color,expanded:Boolean,onToggle:()->Unit,
 content:@Composable ColumnScope.()->Unit
) {
 Column(Modifier.fillMaxWidth()
  .background(Brush.verticalGradient(listOf(tint.copy(alpha=0.68f),tint.copy(alpha=0.42f))),RoundedCornerShape(20.dp))
  .border(1.dp,accent.copy(alpha=0.78f),RoundedCornerShape(20.dp))
  .padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Row(Modifier.fillMaxWidth().clickable(onClick=onToggle).padding(vertical=3.dp),
   verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
   Text(title,color=accent.copy(alpha=1f).let { Color(
    (it.red*0.48f+0.52f),(it.green*0.48f+0.52f),(it.blue*0.48f+0.52f),1f) },
    fontSize=15.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
   Text(if(expanded) "⌃" else "⌄",color=Color.White,fontSize=21.sp)
  }
  if(expanded) content()
 }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun AppGridItem(app:ResolveInfo,pm:android.content.pm.PackageManager,onLaunch:(String)->Unit,tile:Boolean=false,glass:Boolean=false,onPin:((String)->Unit)?=null) {
 val pkg=app.activityInfo.packageName
 val icon=remember(pkg) {
  try {
   val drawable=app.loadIcon(pm)
   monochromeIcon(drawable)
  } catch (_:Exception) { null }
 }
 var pinMenu by remember { mutableStateOf(false) }
 Box {
 Column(Modifier.fillMaxWidth().then(if(tile) Modifier.background(Color(0xFF263448),RoundedCornerShape(16.dp)).padding(vertical=13.dp,horizontal=4.dp) else Modifier.then(if(glass) Modifier else Modifier.background(Color.Black,RoundedCornerShape(15.dp))).padding(horizontal=2.dp,vertical=11.dp)).combinedClickable(onClick={onLaunch(pkg)},onLongClick={if(onPin!=null) pinMenu=true}),horizontalAlignment=Alignment.CenterHorizontally) {
  if(icon!=null) Image(bitmap=icon,contentDescription=null,modifier=Modifier.size(34.dp))
  else Text("▦",color=Color.White,fontSize=28.sp)
  Text(app.loadLabel(pm).toString(),color=Color.White,fontSize=10.sp,maxLines=2,
   overflow=TextOverflow.Ellipsis,lineHeight=10.sp)
 }
 }
 if(pinMenu && onPin!=null) {
  AlertDialog(
   onDismissRequest={pinMenu=false},
   title={Text(app.loadLabel(pm).toString())},
   text={Text("Přidat tuto aplikaci na hlavní plochu?")},
   confirmButton={TextButton(onClick={pinMenu=false;onPin(pkg)}) { Text("Přidat na plochu") }},
   dismissButton={TextButton(onClick={pinMenu=false}) { Text("Zrušit") }}
  )
 }
}private data class HomeTileSpec(
 val id:String,val label:String,val symbol:String,val start:Color,val end:Color,
 val defaultSpan:Int,val defaultHeight:Int,val appPackage:String?=null,val big:Boolean=false
)
@OptIn(ExperimentalFoundationApi::class)
private fun packWithPriority(
 first:String,positions:Map<String,Int>,tiles:List<HomeTileSpec>,
 dimensions:(HomeTileSpec)->Pair<Int,Int>
):Map<String,Int> {
 val taken=mutableSetOf<Int>()
 val result=mutableMapOf<String,Int>()
 val sequence=tiles.sortedWith(compareBy<HomeTileSpec> { if(it.id==first) -1 else positions[it.id]?:Int.MAX_VALUE })
 sequence.forEach { tile ->
  val (w,h)=dimensions(tile)
  var cell=(positions[tile.id]?:0).coerceAtLeast(0)
  fun fits(p:Int)=p%4+w<=4 && (0 until h).all { y -> (0 until w).all { x -> (p/4+y)*4+p%4+x !in taken } }
  while(!fits(cell)) cell++
  result[tile.id]=cell
  repeat(h) { y -> repeat(w) { x -> taken.add((cell/4+y)*4+cell%4+x) } }
 }
 val rows=taken.map { it/4 }.distinct().sorted()
 val rowMap=rows.withIndex().associate { it.value to it.index }
 return result.mapValues { (_,p) -> rowMap.getValue(p/4)*4+p%4 }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Home(onApps:()->Unit,onMultitask:()->Unit,onLaunch:(String)->Unit,apps:List<String>,homeRevision:Int,hideLabels:Boolean,dimOtherTiles:Boolean,priorityApps:Set<String>,onTileRemoved:()->Unit) {
 val context=LocalContext.current
 val prefs=remember { context.getSharedPreferences("mosaic_tiles",Context.MODE_PRIVATE) }
 val time=remember { SimpleDateFormat("HH:mm",Locale("cs","CZ")).format(Date()) }
 val date=remember { SimpleDateFormat("EEEE, d. MMMM",Locale("cs","CZ")).format(Date()) }
 val tiles=remember(time,date,homeRevision) { val defaults=listOf(
  HomeTileSpec("clock","$time\n$date","◷",navy,purple,2,145,big=true),
  HomeTileSpec("mail","Pošta","✉",blue,Color(0xFF028AD6),2,145,"com.google.android.gm"),
  HomeTileSpec("weather","Počasí","☁",Color(0xFF244D81),blue,2,115),
  HomeTileSpec("calendar","Kalendář","▦",purple,Color(0xFF7037A0),1,115,"com.google.android.calendar"),
  HomeTileSpec("gallery","Galerie","▧",green,Color(0xFF39A927),1,115,"com.google.android.apps.photos"),
  HomeTileSpec("apps","Aplikace","▦",navy,Color(0xFF00B9DC),2,116),
  HomeTileSpec("youtube","YouTube","▶",red,Color(0xFFED3038),2,116,"com.google.android.youtube"),
  HomeTileSpec("phone","Telefon","☎",green,Color(0xFF42B91D),1,72,"com.google.android.dialer"),
  HomeTileSpec("messages","Zprávy","▣",blue,navy,1,72,"com.google.android.apps.messaging"),
  HomeTileSpec("chrome","Chrome","◉",Color(0xFF1648A2),blue,1,72,"com.android.chrome"),
  HomeTileSpec("settings","Nastavení","⚙",Color(0xFF353945),Color(0xFF545A6B),1,72,"com.android.settings"),
  HomeTileSpec("multitask","Multitasking","▤",Color(0xFF273B52),Color(0xFF38617B),4,94),
  HomeTileSpec("music","Hudba","♫",purple,navy,4,118,"com.google.android.apps.youtube.music")
 )
 val hidden=prefs.getString("hidden_tiles","")!!.split("|").filter { it.isNotBlank() }.toSet()
 val pinned=prefs.getString("pinned_apps","")!!.split("|").filter { it.isNotBlank() && it in apps }
 (defaults+pinned.filter { pkg -> defaults.none { it.appPackage==pkg } }.map { pkg ->
  val label=try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg,0)).toString() } catch (_:Exception) { pkg }
  HomeTileSpec("pinned_$pkg",label,"▦",Color(0xFF263752),Color(0xFF365879),1,72,pkg)
 }).filter { it.id !in hidden }
 }
 val orderedIds=remember(tiles) {
  val saved=prefs.getString("tile_order","")!!.split("|").filter { it.isNotBlank() }
  mutableStateListOf<String>().apply {
   addAll(saved.filter { id -> tiles.any { it.id==id } })
   addAll(tiles.map { it.id }.filter { it !in this })
  }
 }
 val cellPositions=remember(tiles) { mutableStateMapOf<String,Int>().apply {
  tiles.forEach { tile -> if(prefs.contains("cell_"+tile.id)) put(tile.id,prefs.getInt("cell_"+tile.id,0)) }
 } }
 val orderedTiles=orderedIds.mapNotNull { id -> tiles.firstOrNull { it.id==id } }
 val tileBounds=remember { mutableStateMapOf<String,Rect>() }
 val slotBounds=remember { mutableStateMapOf<Int,Rect>() }
 var draggingTile by remember { mutableStateOf<String?>(null) }
 var dragDelta by remember { mutableStateOf(Offset.Zero) }
 var dragOrigin by remember { mutableStateOf<Offset?>(null) }
 fun moveTile(id:String,delta:Int) {
  val from=orderedIds.indexOf(id)
  if(from<0) return
  val to=(from+delta).coerceIn(0,orderedIds.lastIndex)
  if(to==from) return
  orderedIds.removeAt(from)
  orderedIds.add(to,id)
  prefs.edit().putString("tile_order",orderedIds.joinToString("|")).apply()
 }
 val sizes=remember { mutableStateMapOf<String,Int>().apply {
  tiles.forEach { put(it.id,prefs.getInt("span_"+it.id,it.defaultSpan).coerceIn(1,4)) }
 } }
 val rowSizes=remember { mutableStateMapOf<String,Int>().apply { tiles.forEach { put(it.id,prefs.getInt("rows_"+it.id,if(it.id=="clock" || it.id=="mail") 2 else 1)) } } }
 val heights=remember { mutableStateMapOf<String,Int>().apply {
  tiles.forEach { put(it.id,prefs.getInt("height_"+it.id,it.defaultHeight)) }
 } }
 // Real two-dimensional grid. Only completely empty rows are removed.
 fun dimensions(tile:HomeTileSpec):Pair<Int,Int> {
  val w=(sizes[tile.id]?:tile.defaultSpan).coerceIn(1,4)
  return w to if(w==2) (rowSizes[tile.id]?:1).coerceIn(1,2) else 1
 }
 fun pack(preferred:Map<String,Int>):Map<String,Int> {
  val taken=mutableSetOf<Int>()
  val placed=mutableMapOf<String,Int>()
  orderedTiles.sortedWith(compareBy<HomeTileSpec> { preferred[it.id]?:Int.MAX_VALUE }.thenBy { orderedIds.indexOf(it.id) }).forEach { tile ->
   val (w,h)=dimensions(tile)
   var cell=(preferred[tile.id]?:0).coerceAtLeast(0)
   fun fits(pos:Int):Boolean=pos%4+w<=4 && (0 until h).all { dy -> (0 until w).all { dx -> (pos/4+dy)*4+pos%4+dx !in taken } }
   while(!fits(cell)) cell++
   placed[tile.id]=cell
   repeat(h) { dy -> repeat(w) { dx -> taken.add((cell/4+dy)*4+cell%4+dx) } }
  }
  val rows=taken.map { it/4 }.distinct().sorted()
  val rowMap=rows.withIndex().associate { it.value to it.index }
  return placed.mapValues { (_,cell) -> rowMap.getValue(cell/4)*4+cell%4 }
 }
 val layout=pack(cellPositions)
 var gestureStartNearBottom by remember { mutableStateOf(false) }
 var swipeDistance by remember { mutableFloatStateOf(0f) }
 BoxWithConstraints(Modifier.fillMaxSize()) {
  val screenHeightPx=with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
  val gap=10.dp
  val side=(maxWidth-50.dp)/4
  val step=side+gap
  val density=androidx.compose.ui.platform.LocalDensity.current
  val stepPx=with(density) { step.toPx() }
  val gapPx=with(density) { gap.toPx() }
  val maxRow=orderedTiles.maxOfOrNull { tile ->
   val start=layout[tile.id]?:0
   start/4+dimensions(tile).second
  }?:1
  Column(Modifier.fillMaxSize().pointerInput(Unit) {
    // Observe at Initial pass so child tiles cannot swallow the drawer gesture.
    awaitEachGesture {
     val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
     val start=down.position
     var last=start
     var finished=false
     while(!finished) {
      val event=awaitPointerEvent(PointerEventPass.Initial)
      val pointer=event.changes.firstOrNull { it.id==down.id }
      if(pointer==null) {
       finished=true
      } else {
       last=pointer.position
       if(!pointer.pressed) finished=true
      }
     }
     val dx=last.x-start.x
     val dy=last.y-start.y
     if(draggingTile==null && dx < -75.dp.toPx() && kotlin.math.abs(dx)>kotlin.math.abs(dy)*1.25f) onApps()
    }
   }.pointerInput(screenHeightPx) {
    detectVerticalDragGestures(
     onDragStart={ offset -> gestureStartNearBottom=offset.y>=screenHeightPx-110.dp.toPx();swipeDistance=0f },
     onVerticalDrag={ change,amount -> if(gestureStartNearBottom && draggingTile==null) { swipeDistance+=amount;change.consume() } },
     onDragEnd={ if(gestureStartNearBottom && swipeDistance < -160.dp.toPx()) onMultitask();gestureStartNearBottom=false },
     onDragCancel={ gestureStartNearBottom=false }
    )
   }.statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=10.dp,vertical=12.dp)) {
   Box(Modifier.fillMaxWidth().height(step*maxRow-gap)) {
    orderedTiles.forEach { tile ->
     val span=dimensions(tile).first
     val rows=dimensions(tile).second
     val start=layout[tile.id]?:0
     val cellX=step*(start%4)
     val cellY=step*(start/4)
     val cellW=step*span-gap
     val cellH=step*rows-gap
    var menu by remember { mutableStateOf(false) }
    val activePriority=dimOtherTiles && priorityApps.any { LauncherNotificationState.isUnseen(it) }
    val dimThisTile=activePriority && (tile.appPackage==null || !LauncherNotificationState.isUnseen(tile.appPackage) || tile.appPackage !in priorityApps)
    Box(Modifier.offset(x=cellX,y=cellY).width(cellW).height(cellH).then(
     if(dimThisTile) Modifier.drawWithContent {
      val paint=Paint().apply { colorFilter=ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
      drawContext.canvas.saveLayer(Rect(Offset.Zero,size),paint)
      drawContent()
      drawContext.canvas.restore()
     } else Modifier
    ).onGloballyPositioned { tileBounds[tile.id]=it.boundsInRoot() }
     .graphicsLayer {
      if(draggingTile==tile.id) {
       translationX=dragDelta.x
       translationY=dragDelta.y
       alpha=0.85f
       shadowElevation=16.dp.toPx()
      }
     }.pointerInput(tile.id) {
      detectDragGesturesAfterLongPress(
       onDragStart={ draggingTile=tile.id;dragDelta=Offset.Zero;dragOrigin=tileBounds[tile.id]?.center },
       onDrag={ change,amount ->
        change.consume()
        dragDelta+=amount
       },
       onDragEnd={
        if(dragDelta.getDistance() < 12.dp.toPx()) menu=true
        val origin=dragOrigin
        if(origin!=null && dragDelta.getDistance() >= 12.dp.toPx()) {
         val drop=origin+dragDelta
         val density=context.resources.displayMetrics.density
         val gridOriginX=tileBounds.values.minOfOrNull { it.left }?:0f
         val gridOriginY=tileBounds.values.minOfOrNull { it.top }?:0f
         val (w,h)=dimensions(tile)
         val left=drop.x-(w*stepPx-gapPx)/2f
         val top=drop.y-(h*stepPx-gapPx)/2f
         val col=kotlin.math.round((left-gridOriginX)/stepPx).toInt().coerceIn(0,4-w)
         val row=kotlin.math.round((top-gridOriginY)/stepPx).toInt().coerceAtLeast(0)
         val desired=row*4+col
         val updated=cellPositions.toMutableMap()
         // Reserve the dragged tile first, then relocate all collisions.
         updated[tile.id]=desired
         val packed=packWithPriority(tile.id,updated,orderedTiles,::dimensions)
         cellPositions.clear()
         cellPositions.putAll(packed)
         val edit=prefs.edit()
         packed.forEach { (id,cell) -> edit.putInt("cell_"+id,cell) }
         edit.apply()
        }
        draggingTile=null;dragDelta=Offset.Zero;dragOrigin=null
       },
       onDragCancel={draggingTile=null;dragDelta=Offset.Zero;dragOrigin=null}
      )
     }) {
     Tile(tile.label,tile.symbol,tile.start,tile.end,
      Modifier.fillMaxSize(),big=tile.big,appPackage=tile.appPackage,hideLabel=hideLabels,compact=span==1,
      onLongClick={}) {
       when(tile.id) {
        "apps" -> onApps()
        "multitask" -> onMultitask()
        else -> tile.appPackage?.let(onLaunch)
       }
      }
      if(tile.appPackage!=null && LauncherNotificationState.activePackages[tile.appPackage]==true) {
       Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(7.dp).background(Color(0xFFFF5252),androidx.compose.foundation.shape.CircleShape))
      }
     
     DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
      DropdownMenuItem(text={Text("Odebrat z plochy",color=Color(0xFFFF5252))},onClick={
       val hidden=prefs.getString("hidden_tiles","")!!.split("|").filter { it.isNotBlank() }.toMutableSet()
       hidden.add(tile.id)
       val edit=prefs.edit().putString("hidden_tiles",hidden.joinToString("|"))
       if(tile.id.startsWith("pinned_")) {
        val pkg=tile.id.removePrefix("pinned_")
        val pinned=prefs.getString("pinned_apps","")!!.split("|").filter { it.isNotBlank() && it!=pkg }
        edit.putString("pinned_apps",pinned.joinToString("|"))
       }
       edit.apply()
       menu=false
       onTileRemoved()
      })
      HorizontalDivider()
      listOf(Triple(1,1,"Malá 1 × 1"),Triple(2,1,"Široká 2 × 1"),Triple(2,2,"Velká 2 × 2"),Triple(4,1,"Pruh 4 × 1")).forEach { (newSpan,newRows,title) ->
       DropdownMenuItem(text={Text(title)},onClick={
        sizes[tile.id]=newSpan
        rowSizes[tile.id]=newRows
        val newHeight=when(newSpan) { 1 -> 108; 2 -> tile.defaultHeight.coerceAtLeast(112); else -> tile.defaultHeight.coerceAtLeast(118) }
        heights[tile.id]=newHeight
        prefs.edit().putInt("span_"+tile.id,newSpan).putInt("rows_"+tile.id,newRows).putInt("height_"+tile.id,newHeight).apply()
        menu=false
       })
      }
     }
    }
   }
  } }
}
}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Tile(label:String,symbol:String,start:Color,end:Color,modifier:Modifier=Modifier,big:Boolean=false,appPackage:String?=null,hideLabel:Boolean=false,compact:Boolean=false,onLongClick:()->Unit={},click:()->Unit) {
 val glassShape=RoundedCornerShape(if(compact) 16.dp else 20.dp)
 Box(modifier
  .background(Brush.linearGradient(listOf(
   Color(0xFF3CAEE8).copy(alpha=0.52f),
   Color(0xFF102D58).copy(alpha=0.55f),
   Color(0xFFAE40D9).copy(alpha=0.48f)
  )),glassShape)
  .border(1.dp,Brush.linearGradient(listOf(
   Color(0xFFB9F7FF),Color(0xFF18BFFF),Color(0xFF5977FF),Color(0xFFF044F7)
  )),glassShape)
  .clickable(onClick=click).padding(if(compact) 5.dp else 12.dp)) {
  if(big && !hideLabel) Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally) {
   Text(label.substringBefore("\n"),color=Color.White,fontSize=42.sp,fontWeight=FontWeight.Light)
   Text(label.substringAfter("\n"),color=Color.White,fontSize=13.sp)
  } else if(!compact) Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
   val context=LocalContext.current
   val icon=remember(appPackage) { appPackage?.let { pkg -> try { monochromeIcon(context.packageManager.getApplicationIcon(pkg)) } catch (_:Exception) { null } } }
   if(icon!=null) Image(bitmap=icon,contentDescription=null,modifier=Modifier.size(36.dp))
   else Text(symbol,color=Color.White,fontSize=30.sp)
   if(!hideLabel) { Spacer(Modifier.width(12.dp));Text(label,color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis) }
  } else Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
   val context=LocalContext.current
   val icon=remember(appPackage) {
    appPackage?.let { pkg ->
     try { monochromeIcon(context.packageManager.getApplicationIcon(pkg)) } catch (_:Exception) { null }
    }
   }
   if(icon!=null) Image(bitmap=icon,contentDescription=null,modifier=Modifier.size(if(compact) 30.dp else 36.dp))
   else Text(symbol,color=Color.White,fontSize=if(compact) 25.sp else 30.sp)
   if(!hideLabel) { Spacer(Modifier.height(if(compact) 3.dp else 7.dp));Text(label,color=Color.White,fontSize=if(compact) 10.sp else 11.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis,lineHeight=13.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center) }
  }
 }
}
