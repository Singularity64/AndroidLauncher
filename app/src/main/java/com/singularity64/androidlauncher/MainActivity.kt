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

private val bg = Color(0xFF10131B)
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
class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent {
   MaterialTheme(colorScheme=darkColorScheme(background=bg,surface=bg,onSurface=Color.White)) {
    val prefs = remember { getSharedPreferences("mosaic_usage",Context.MODE_PRIVATE) }
    var drawer by remember { mutableStateOf(false) }
    var multitask by remember { mutableStateOf(false) }
    var showFrequent by remember { mutableStateOf(prefs.getBoolean("show_frequent",true)) }
    var search by remember { mutableStateOf("") }
    BackHandler(enabled=true) {
     when {
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
     val ranked=apps.map { app -> app to days.sumOf { d -> prefs.getInt("week_${d}_${app.activityInfo.packageName}",0) } }
      .filter { it.second>0 }.sortedWith(compareByDescending<Pair<ResolveInfo,Int>> { it.second }.thenBy { it.first.loadLabel(packageManager).toString() }).map { it.first }
     val revision=usageRevision
     Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
      Text("‹  Rychlé přepínání",color=Color.White,fontSize=25.sp,modifier=Modifier.clickable { multitask=false }.padding(bottom=8.dp))
      Text("Nejpoužívanější za posledních 7 dní",color=Color.LightGray,fontSize=14.sp,modifier=Modifier.padding(bottom=16.dp))
      if(ranked.isEmpty()) Text("Zatím žádné spuštěné aplikace",color=Color.LightGray)
      else LazyVerticalGrid(columns=GridCells.Fixed(3),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
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
     val filtered=apps.filter { it.loadLabel(packageManager).toString().contains(search,ignoreCase=true) }
     Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding().padding(horizontal=12.dp,vertical=12.dp)) {
      Text("‹  Aplikace",color=Color.White,fontSize=26.sp,modifier=Modifier.clickable { drawer=false }.padding(bottom=12.dp))
      OutlinedTextField(value=search,onValueChange={search=it},placeholder={Text("Hledat aplikace…")},singleLine=true,
       leadingIcon={Icon(Icons.Default.Search,contentDescription=null)},
       trailingIcon={if(search.isNotEmpty()) IconButton(onClick={search=""}) { Icon(Icons.Default.Close,contentDescription="Vymazat") }},
       shape=RoundedCornerShape(18.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Color(0xFF283447),unfocusedContainerColor=Color(0xFF222C3B),focusedBorderColor=Color(0xFF6B94C4),unfocusedBorderColor=Color.Transparent),
       modifier=Modifier.fillMaxWidth().padding(horizontal=4.dp).padding(bottom=14.dp))
      Row(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
       Text(if(adaptiveEnabled && scores.isNotEmpty()) "Chytrý výběr pro dnešek" else "Nejčastěji používané dnes",color=Color.White,fontSize=15.sp)
       Text(if(showFrequent) "Skrýt" else "Zobrazit",color=Color(0xFF8CCBFF),modifier=Modifier.clickable {
        showFrequent=!showFrequent
        prefs.edit().putBoolean("show_frequent",showFrequent).apply()
       })
      }
      if(search.isBlank()) {
       Row(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text("Adaptivní řazení",color=Color.LightGray,fontSize=12.sp)
        Switch(checked=adaptiveEnabled,onCheckedChange={ adaptiveEnabled=it; prefs.edit().putBoolean("adaptive_enabled",it).apply() },modifier=Modifier.height(28.dp))
       }
       if(adaptiveEnabled && !usageAccess(this@MainActivity)) {
        Text("Pro učení podle času a dnů povol přístup k údajům o používání.",color=Color.LightGray,fontSize=12.sp)
        Text("Povolit přístup",color=Color(0xFF8CCBFF),modifier=Modifier.clickable { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }.padding(vertical=8.dp))
       }
      }
      if(showFrequent && search.isBlank()) {
       if(popular.isEmpty()) Text("Zatím žádné spuštěné aplikace",color=Color.LightGray,fontSize=12.sp,modifier=Modifier.padding(bottom=10.dp))
       else {
        Column(Modifier.padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
         popular.chunked(4).forEach { group ->
          Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
           group.forEach { app ->
            Box(Modifier.weight(1f)) { AppGridItem(app,packageManager,::launch) }
           }
           repeat(4-group.size) { Spacer(Modifier.weight(1f)) }
          }
         }
        }
       }
      }
      Text("Všechny aplikace",color=Color.White,fontSize=16.sp,modifier=Modifier.padding(bottom=8.dp))
      LazyVerticalGrid(columns=GridCells.Fixed(5),horizontalArrangement=Arrangement.spacedBy(3.dp),
       verticalArrangement=Arrangement.spacedBy(9.dp),modifier=Modifier.fillMaxSize()) {
       gridItems(filtered,key={it.activityInfo.packageName}) { app -> AppGridItem(app,packageManager,::launch) }
      }
     }
    } else Home(onApps={search="";drawer=true},onMultitask={multitask=true},onLaunch={launch(it)},apps=apps.map { it.activityInfo.packageName },
      onSelfUninstall={ startActivity(Intent(Intent.ACTION_DELETE,Uri.parse("package:$packageName"))) })
   }
  }
 }
}

@Composable private fun MultitaskCard(app:ResolveInfo,pm:android.content.pm.PackageManager,onLaunch:(String)->Unit) {
 val pkg=app.activityInfo.packageName
 val icon=remember(pkg) {
  try {
   val drawable=app.loadIcon(pm)
   monochromeIcon(drawable)
  } catch (_:Exception) { null }
 }
 Column(Modifier.fillMaxWidth().background(Color(0xFF263448),RoundedCornerShape(16.dp)).clickable { onLaunch(pkg) }.padding(7.dp),horizontalAlignment=Alignment.CenterHorizontally) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
   if(icon!=null) Image(bitmap=icon,contentDescription=null,modifier=Modifier.size(20.dp))
   Text(app.loadLabel(pm).toString(),color=Color.White,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
  }
  Box(Modifier.fillMaxWidth().padding(top=7.dp).aspectRatio(0.72f).background(Color(0xFF17212E),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center) {
   if(icon!=null) Image(bitmap=icon,contentDescription=app.loadLabel(pm).toString(),modifier=Modifier.size(58.dp))
   else Text("▦",color=Color.White,fontSize=48.sp)
  }
 }
}
@Composable private fun AppGridItem(app:ResolveInfo,pm:android.content.pm.PackageManager,onLaunch:(String)->Unit,tile:Boolean=false) {
 val pkg=app.activityInfo.packageName
 val icon=remember(pkg) {
  try {
   val drawable=app.loadIcon(pm)
   monochromeIcon(drawable)
  } catch (_:Exception) { null }
 }
 Column(Modifier.fillMaxWidth().then(if(tile) Modifier.background(Color(0xFF263448),RoundedCornerShape(16.dp)).padding(vertical=13.dp,horizontal=4.dp) else Modifier.background(Color(0xFF202A39),RoundedCornerShape(15.dp)).padding(horizontal=4.dp,vertical=11.dp)).clickable { onLaunch(pkg) },horizontalAlignment=Alignment.CenterHorizontally) {
  if(icon!=null) Image(bitmap=icon,contentDescription=null,modifier=Modifier.size(34.dp))
  else Text("▦",color=Color.White,fontSize=28.sp)
  Text(app.loadLabel(pm).toString(),color=Color.White,fontSize=10.sp,maxLines=2,
   overflow=TextOverflow.Ellipsis,lineHeight=10.sp)
 }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Home(onApps:()->Unit,onMultitask:()->Unit,onLaunch:(String)->Unit,apps:List<String>,onSelfUninstall:()->Unit) {
 val time = remember { SimpleDateFormat("HH:mm",Locale("cs","CZ")).format(Date()) }
 val date = remember { SimpleDateFormat("EEEE, d. MMMM",Locale("cs","CZ")).format(Date()) }
 var gestureStartNearBottom by remember { mutableStateOf(false) }
 var swipeDistance by remember { mutableFloatStateOf(0f) }
 BoxWithConstraints(Modifier.fillMaxSize().background(bg)) {
  val screenHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
  Column(Modifier.fillMaxSize().pointerInput(screenHeightPx) {
   detectVerticalDragGestures(onDragStart={ offset -> gestureStartNearBottom=offset.y >= screenHeightPx-110.dp.toPx(); swipeDistance=0f },
    onVerticalDrag={ change,dragAmount -> if(gestureStartNearBottom) { swipeDistance+=dragAmount; change.consume() } },
    onDragEnd={ if(gestureStartNearBottom && swipeDistance < -75.dp.toPx()) onMultitask(); gestureStartNearBottom=false },
    onDragCancel={ gestureStartNearBottom=false })
  }.verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal=10.dp, vertical=12.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Tile("$time\n$date", "◷", navy, purple, Modifier.weight(1f).height(145.dp), big=true) {}
   Tile("Pošta", "✉", blue, Color(0xFF028AD6), Modifier.weight(1f).height(145.dp), appPackage="com.google.android.gm") { onLaunch("com.google.android.gm") }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Tile("Počasí", "☁", Color(0xFF244D81), blue, Modifier.weight(2f).height(115.dp)) {}
   Tile("Kalendář", "▦", purple, Color(0xFF7037A0), Modifier.weight(1f).height(115.dp), appPackage="com.google.android.calendar") { onLaunch("com.google.android.calendar") }
   Tile("Galerie", "▧", green, Color(0xFF39A927), Modifier.weight(1f).height(115.dp), appPackage="com.google.android.apps.photos") { onLaunch("com.google.android.apps.photos") }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Tile("Aplikace", "▦", navy, Color(0xFF00B9DC), Modifier.weight(1f).height(116.dp), click=onApps)
   Tile("YouTube", "▶", red, Color(0xFFED3038), Modifier.weight(1f).height(116.dp), appPackage="com.google.android.youtube") { onLaunch("com.google.android.youtube") }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Tile("Telefon", "☎", green, Color(0xFF42B91D), Modifier.weight(1f).height(112.dp), appPackage="com.google.android.dialer") { onLaunch("com.google.android.dialer") }
   Tile("Zprávy", "▣", blue, navy, Modifier.weight(1f).height(112.dp), appPackage="com.google.android.apps.messaging") { onLaunch("com.google.android.apps.messaging") }
   Tile("Chrome", "◉", Color(0xFF1648A2), blue, Modifier.weight(1f).height(112.dp), appPackage="com.android.chrome") { onLaunch("com.android.chrome") }
   Tile("Nastavení", "⚙", Color(0xFF353945), Color(0xFF545A6B), Modifier.weight(1f).height(112.dp), appPackage="com.android.settings") { onLaunch("com.android.settings") }
  }
  Tile("Multitasking", "▤", Color(0xFF273B52), Color(0xFF38617B), Modifier.fillMaxWidth().height(94.dp), click=onMultitask)
  Tile("Hudba", "♫", purple, navy, Modifier.fillMaxWidth().height(118.dp), appPackage="com.google.android.apps.youtube.music") { onLaunch("com.google.android.apps.youtube.music") }
  Box(Modifier.fillMaxWidth().height(82.dp).background(Color(0xFF283447),RoundedCornerShape(15.dp))
   .combinedClickable(onClick={},onLongClick=onSelfUninstall).padding(15.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
    Text("▦",fontSize=32.sp,color=Color.White)
    Column { Text("Mosaic Launcher",color=Color.White,fontSize=16.sp)
     Text("Podržet pro odinstalaci",color=Color.LightGray,fontSize=11.sp) }
   }
  }
  }
 }
}
@Composable private fun Tile(label:String, symbol:String, start:Color, end:Color, modifier:Modifier=Modifier, big:Boolean=false, appPackage:String?=null, click:()->Unit) {
 Box(modifier.background(Brush.linearGradient(listOf(start,end)),RoundedCornerShape(15.dp)).clickable(onClick=click).padding(15.dp)) {
  if(big) Column(Modifier.align(Alignment.CenterStart)) { Text(label.substringBefore("\n"),color=Color.White,fontSize=42.sp,fontWeight=FontWeight.Light); Text(label.substringAfter("\n"),color=Color.White,fontSize=13.sp) }
  else Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.SpaceBetween) {
   val context = LocalContext.current
   val icon = remember(appPackage) {
    appPackage?.let { pkg ->
     try {
      val drawable = context.packageManager.getApplicationIcon(pkg)
      monochromeIcon(drawable)
     } catch (_: Exception) { null }
    }
   }
   if (icon != null) Image(bitmap=icon,contentDescription=null,modifier=Modifier.size(39.dp))
   else Text(symbol,color=Color.White,fontSize=37.sp)
   Text(label,color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Medium)
  }
 }
}