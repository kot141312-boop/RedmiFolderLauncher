
package com.kir.folderlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Bitmap
import android.graphics.Canvas
import org.json.JSONArray
import org.json.JSONObject

private data class AppEntry(val label: String, val packageName: String, val icon: Drawable)
private data class Folder(val id: Long, val name: String, val packages: List<String>, val tileScale: Float = 1f)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FolderLauncher() }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        if (prefs.getBoolean("gesture_enabled", true)) {
            if (Settings.canDrawOverlays(this)) {
                val serviceIntent = Intent(this, BottomSwipeService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
            } else if (!prefs.getBoolean("overlay_permission_prompted", false)) {
                prefs.edit().putBoolean("overlay_permission_prompted", true).apply()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        } else {
            stopService(Intent(this, BottomSwipeService::class.java))
        }
    }
}

@Composable
private fun FolderLauncher() {
    val context = LocalContext.current
    val pm = context.packageManager
    val apps = remember { loadApps(context) }
    val prefs = remember { context.getSharedPreferences("launcher", Context.MODE_PRIVATE) }
    var folders by remember { mutableStateOf(loadFolders(prefs)) }
    var iconSize by remember { mutableFloatStateOf(prefs.getFloat("iconSize", 42f)) }
    var tileSize by remember { mutableFloatStateOf(prefs.getFloat("tileSize", 1f)) }
    var activeFolder by remember { mutableStateOf<Folder?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var gestureEnabled by remember { mutableStateOf(prefs.getBoolean("gesture_enabled", true)) }
    var search by remember { mutableStateOf("") }
    var editFolder by remember { mutableStateOf<Folder?>(null) }

    fun save() { saveFolders(prefs, folders); prefs.edit().putFloat("iconSize", iconSize).putFloat("tileSize", tileSize).apply() }

    BackHandler(activeFolder != null || showSettings || showCreate || editFolder != null || showResetConfirm) {
        when { activeFolder != null -> activeFolder = null; showSettings -> showSettings = false; showCreate -> showCreate = false; else -> editFolder = null }
    }

    MaterialTheme(colorScheme = lightColorScheme(background = Color(0xFFF5F5F7), surface = Color.White, primary = Color(0xFF34495E))) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF5F5F7))) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {

                    Column(Modifier.weight(1f)) {
                        Text("Приложения", fontSize = 27.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF20232A))
                        Text("Твои папки — в твоём порядке", fontSize = 13.sp, color = Color.Gray)
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Настройки") }
                    IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "Создать папку") }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Поиск приложений") }, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(16.dp))
                val visibleFolders = folders.filter { f -> search.isBlank() || f.name.contains(search, true) || f.packages.any { p -> apps.find { it.packageName == p }?.label?.contains(search, true) == true } }
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 104.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    items(visibleFolders, key = { it.id }) { folder ->
                        FolderTile(folder = folder, apps = apps, iconSize = iconSize, tileSize = tileSize,
                            onOpen = { activeFolder = folder }, onEdit = { editFolder = folder },
                            onMove = { from, to ->
                                val a = folders.indexOfFirst { it.id == from }; val b = folders.indexOfFirst { it.id == to }
                                if (a >= 0 && b >= 0 && a != b) { val mutable = folders.toMutableList(); val item = mutable.removeAt(a); mutable.add(b.coerceIn(0, mutable.size), item); folders = mutable; save() }
                            })
                    }
                    if (visibleFolders.isEmpty()) item { Text("Пока нет папок. Нажми +, чтобы создать первую.", color = Color.Gray, modifier = Modifier.padding(12.dp)) }
                }
                Text("Свайп снизу вверх — открыть список всех приложений", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(34.dp).pointerInput(apps) {
                var total = 0f
                detectVerticalDragGestures(onVerticalDrag = { _, amount -> total += amount }, onDragEnd = { if (total < -22f) activeFolder = Folder(-1, "Все приложения", apps.map { it.packageName }); total = 0f })
            })
        }
    }

    if (activeFolder != null) {
        val folder = activeFolder!!
        FolderWindow(folder, apps, iconSize, onClose = { activeFolder = null }, onLaunch = { pkg -> launchApp(context, pkg); activeFolder = null }, onResizeIcon = { iconSize = it; save() }, onEdit = { editFolder = folder })
    }
    if (showCreate) CreateFolderDialog(apps, onDismiss = { showCreate = false }, onCreate = { name, selected ->
        folders = folders + Folder(System.currentTimeMillis(), name, selected); save(); showCreate = false
    })
    if (editFolder != null) {
        val f = editFolder!!
        CreateFolderDialog(apps, initial = f, onDismiss = { editFolder = null }, onCreate = { name, selected ->
            folders = folders.map { if (it.id == f.id) it.copy(name = name, packages = selected) else it }; save(); editFolder = null
        }, onDelete = { folders = folders.filterNot { it.id == f.id }; save(); editFolder = null })
    }
    if (showSettings) SettingsDialog(
        iconSize = iconSize,
        tileSize = tileSize,
        gestureEnabled = gestureEnabled,
        onIcon = { iconSize = it; save() },
        onTile = { tileSize = it; save() },
        onGestureEnabled = { enabled ->
            gestureEnabled = enabled
            prefs.edit().putBoolean("gesture_enabled", enabled).apply()
            val serviceIntent = Intent(context, BottomSwipeService::class.java)
            if (enabled && Settings.canDrawOverlays(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
            } else {
                context.stopService(serviceIntent)
                if (enabled && !Settings.canDrawOverlays(context)) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }
            }
        },
        onReset = { showSettings = false; showResetConfirm = true },
        onDismiss = { showSettings = false }
    )

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Полный откат?") },
            text = { Text("Папки и настройки Folder Drawer будут сброшены. Жест будет выключен. Рабочий стол Xiaomi и системные настройки телефона не изменятся.") },
            confirmButton = {
                TextButton(onClick = {
                    context.stopService(Intent(context, BottomSwipeService::class.java))
                    prefs.edit().remove("folders").remove("iconSize").remove("tileSize")
                        .putBoolean("gesture_enabled", false)
                        .putBoolean("overlay_permission_prompted", true).apply()
                    folders = emptyList()
                    iconSize = 42f
                    tileSize = 1f
                    gestureEnabled = false
                    activeFolder = null
                    editFolder = null
                    showResetConfirm = false
                }) { Text("Сбросить всё") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun FolderTile(folder: Folder, apps: List<AppEntry>, iconSize: Float, tileSize: Float, onOpen: () -> Unit, onEdit: () -> Unit, onMove: (Long, Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().height((112 * tileSize).dp).clip(RoundedCornerShape(20.dp)).background(Color.White).pointerInput(folder.id) {
        detectDragGestures(onDragStart = { dragging = true }, onDragEnd = { dragging = false }, onDragCancel = { dragging = false }, onDrag = { change, _ -> change.consume() })
    }.clickable { onOpen() }.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFE9EDF2)), contentAlignment = Alignment.Center) {
            val previews = folder.packages.take(4).mapNotNull { p -> apps.find { it.packageName == p } }
            if (previews.isEmpty()) Text("＋", fontSize = 26.sp, color = Color.DarkGray)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row { previews.take(2).forEach { AppIcon(it, (iconSize * .38f).coerceAtLeast(15f)) } }
                Row { previews.drop(2).take(2).forEach { AppIcon(it, (iconSize * .38f).coerceAtLeast(15f)) } }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(folder.name, maxLines = 1, fontSize = 12.sp, color = Color(0xFF252932), textAlign = TextAlign.Center)
        Text("${folder.packages.size}", fontSize = 10.sp, color = Color.Gray)
        if (dragging) Text("Перемести", fontSize = 9.sp, color = Color.Gray)
    }
}

@Composable
private fun FolderWindow(folder: Folder, apps: List<AppEntry>, iconSize: Float, onClose: () -> Unit, onLaunch: (String) -> Unit, onResizeIcon: (Float) -> Unit, onEdit: () -> Unit) {
    var width by remember { mutableFloatStateOf(0.88f) }
    var height by remember { mutableFloatStateOf(0.62f) }
    var resizing by remember { mutableStateOf(false) }
    val isAll = folder.id == -1L
    val shown = if (isAll) apps else folder.packages.mapNotNull { p -> apps.find { it.packageName == p } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(width).fillMaxHeight(height).clip(RoundedCornerShape(26.dp)).background(Color(0xFFFCFCFD)).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(folder.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (!isAll) TextButton(onClick = onEdit) { Text("Изменить") }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Закрыть") }
            }
            Divider(color = Color(0xFFE7E7EA))
            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = (iconSize + 24).dp), modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(shown, key = { it.packageName }) { app -> Column(Modifier.clickable { onLaunch(app.packageName) }.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) { AppIcon(app, iconSize); Spacer(Modifier.height(5.dp)); Text(app.label, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center) } }
            }
            if (!isAll) {
                Text("Размер окна", fontSize = 12.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Ширина", fontSize = 11.sp); Slider(width, { width = it }, valueRange = .55f..1f, modifier = Modifier.weight(1f)) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Высота", fontSize = 11.sp); Slider(height, { height = it }, valueRange = .35f..0.88f, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AppIcon(app: AppEntry, size: Float) {
    val bmp = remember(app.packageName) { drawableBitmap(app.icon) }
    androidx.compose.foundation.Image(bitmap = bmp.asImageBitmap(), contentDescription = app.label, modifier = Modifier.size(size.dp))
}
@Composable
private fun CreateFolderDialog(apps: List<AppEntry>, initial: Folder? = null, onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit, onDelete: (() -> Unit)? = null) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    val selected = remember { mutableStateListOf<String>().apply { addAll(initial?.packages ?: emptyList()) } }
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Новая папка" else "Настроить папку") }, text = {
        Column(Modifier.fillMaxWidth()) {
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Название папки") })
            Spacer(Modifier.height(8.dp)); OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Найти приложение") })
            Text("Выбери приложения (${selected.size})", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Column(Modifier.heightIn(max = 330.dp).fillMaxWidth()) {
                apps.filter { it.label.contains(query, true) }.forEach { app ->
                    Row(Modifier.fillMaxWidth().clickable { if (selected.contains(app.packageName)) selected.remove(app.packageName) else selected.add(app.packageName) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(selected.contains(app.packageName), { checked -> if (checked) selected.add(app.packageName) else selected.remove(app.packageName) })
                        AppIcon(app, 28f); Text(app.label, modifier = Modifier.padding(start = 9.dp), fontSize = 13.sp)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim(), selected.toList()) }, enabled = name.isNotBlank()) { Text("Сохранить") } }, dismissButton = {
        Row { if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить") }; TextButton(onClick = onDismiss) { Text("Отмена") } }
    })
}

@Composable
private fun SettingsDialog(
    iconSize: Float,
    tileSize: Float,
    gestureEnabled: Boolean,
    onIcon: (Float) -> Unit,
    onTile: (Float) -> Unit,
    onGestureEnabled: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки Folder Drawer") },
        text = {
            Column {
                Text("Размер значков приложений: ${iconSize.toInt()} dp", fontSize = 13.sp)
                Slider(iconSize, onIcon, valueRange = 28f..64f)
                Spacer(Modifier.height(8.dp))
                Text("Размер карточек папок: ${(tileSize * 100).toInt()}%", fontSize = 13.sp)
                Slider(tileSize, onTile, valueRange = .8f..1.5f)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Жест снизу вверх", fontWeight = FontWeight.Medium)
                        Text(if (gestureEnabled) "Включён — если HyperOS пропускает жест" else "Выключен — свайпы не перехватываются", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = gestureEnabled, onCheckedChange = onGestureEnabled)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onReset) { Text("Полный сброс и откат") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}

private fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapNotNull { info ->
        val pkg = info.activityInfo.packageName
        try { AppEntry(info.loadLabel(pm).toString(), pkg, info.loadIcon(pm)) } catch (_: Exception) { null }
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
}

private fun launchApp(context: Context, pkg: String) {
    try { context.packageManager.getLaunchIntentForPackage(pkg)?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(it) } } catch (_: Exception) { }
}

private fun drawableBitmap(drawable: Drawable): Bitmap {
    val width = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(192)
    val height = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(192)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap); drawable.setBounds(0, 0, width, height); drawable.draw(canvas)
    return bitmap
}

private fun loadFolders(prefs: android.content.SharedPreferences): List<Folder> {
    return try {
        val array = JSONArray(prefs.getString("folders", "[]"))
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i); val pkgs = obj.getJSONArray("packages")
            Folder(obj.getLong("id"), obj.getString("name"), (0 until pkgs.length()).map { pkgs.getString(it) }, obj.optDouble("tileScale", 1.0).toFloat())
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveFolders(prefs: android.content.SharedPreferences, folders: List<Folder>) {
    val array = JSONArray()
    folders.forEach { f ->
        val obj = JSONObject().put("id", f.id).put("name", f.name).put("tileScale", f.tileScale)
        val pkgs = JSONArray(); f.packages.forEach { pkgs.put(it) }; obj.put("packages", pkgs); array.put(obj)
    }
    prefs.edit().putString("folders", array.toString()).apply()
}
