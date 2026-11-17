package com.singularity64.androidlauncher
import android.content.Intent
import android.os.Bundle
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

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  setContent { MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = bg, onSurface = Color.White)) {
   var drawer by remember { mutableStateOf(false) }
   val apps = remember { packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).filter { it.activityInfo.packageName != packageName }.sortedBy { it.loadLabel(packageManager).toString().lowercase() } }
   fun launch(pkg: String) { packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) } }
   if (drawer) { Column(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
     Text("‹  Aplikace", color=Color.White, fontSize=27.sp, modifier=Modifier.clickable { drawer=false }.padding(bottom=16.dp))
     LazyColumn { items(apps) { app -> Text(app.loadLabel(packageManager).toString(), color=Color.White, fontSize=18.sp, modifier=Modifier.fillMaxWidth().clickable { launch(app.activityInfo.packageName) }.padding(14.dp)) } }
   } } else Home(onApps={drawer=true}, onLaunch={launch(it)}, apps=apps.map { it.activityInfo.packageName })
  } } }
}
@Composable private fun Home(onApps:()->Unit,onLaunch:(String)->Unit,apps:List<String>) {
 val time = remember { SimpleDateFormat("HH:mm",Locale("cs","CZ")).format(Date()) }
 val date = remember { SimpleDateFormat("EEEE, d. MMMM",Locale("cs","CZ")).format(Date()) }
 Column(Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState()).padding(horizontal=10.dp, vertical=28.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Tile("$"+"time\n$"+"date", "◷", navy, purple, Modifier.weight(1f).height(145.dp), big=true) {}
   Tile("Pošta", "✉", blue, Color(0xFF028AD6), Modifier.weight(1f).height(145.dp)) { onLaunch("com.google.android.gm") }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Tile("Počasí", "☁", Color(0xFF244D81), blue, Modifier.weight(2f).height(115.dp)) {}
   Tile("Kalendář", "▦", purple, Color(0xFF7037A0), Modifier.weight(1f).height(115.dp)) { onLaunch("com.google.android.calendar") }
   Tile("Galerie", "▧", green, Color(0xFF39A927), Modifier.weight(1f).height(115.dp)) { onLaunch("com.google.android.apps.photos") }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Tile("Aplikace", "▦", navy, Color(0xFF00B9DC), Modifier.weight(1f).height(116.dp), onApps)
   Tile("YouTube", "▶", red, Color(0xFFED3038), Modifier.weight(1f).height(116.dp)) { onLaunch("com.google.android.youtube") }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Tile("Telefon", "☎", green, Color(0xFF42B91D), Modifier.weight(1f).height(112.dp)) { onLaunch("com.google.android.dialer") }
   Tile("Zprávy", "▣", blue, navy, Modifier.weight(1f).height(112.dp)) { onLaunch("com.google.android.apps.messaging") }
   Tile("Chrome", "◉", Color(0xFF1648A2), blue, Modifier.weight(1f).height(112.dp)) { onLaunch("com.android.chrome") }
   Tile("Nastavení", "⚙", Color(0xFF353945), Color(0xFF545A6B), Modifier.weight(1f).height(112.dp)) { onLaunch("com.android.settings") }
  }
  Tile("Hudba", "♫", purple, navy, Modifier.fillMaxWidth().height(118.dp)) { onLaunch("com.google.android.apps.youtube.music") }
 }
}
@Composable private fun Tile(label:String, symbol:String, start:Color, end:Color, modifier:Modifier=Modifier, big:Boolean=false, click:()->Unit) {
 Box(modifier.background(Brush.linearGradient(listOf(start,end)),RoundedCornerShape(15.dp)).clickable(onClick=click).padding(15.dp)) {
  if(big) Column(Modifier.align(Alignment.CenterStart)) { Text(label.substringBefore("\n"),color=Color.White,fontSize=42.sp,fontWeight=FontWeight.Light); Text(label.substringAfter("\n"),color=Color.White,fontSize=13.sp) }
  else Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.SpaceBetween) { Text(symbol,color=Color.White,fontSize=37.sp); Text(label,color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Medium) }
 }
}