package com.example.shoppinglist

// KEEP the "package ..." line that Android Studio generated at the top of YOUR file,
// then replace everything below it with this code.

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.json.JSONArray
import org.json.JSONObject

// ---------- Colours (taken from your screenshot) ----------

val Green = Color(0xFF659D32)          // header, buttons, checkboxes
val DarkGreen = Color(0xFF3E6B1C)
val PageBg = Color(0xFFF1F6E9)         // very light green behind everything
val CardBorder = Color(0xFFCBD9B8)
val OnListTint = Color(0xFFE3EFD2)
val NavBg = Color(0xFFE8F0DC)

private val AppColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFD3E6B8),   // highlight behind the selected tab icon
    onSecondaryContainer = DarkGreen,
    background = PageBg,
    surface = PageBg,
    onSurface = Color(0xFF1F2A14),
    surfaceVariant = Color(0xFFE3EBD6),
    outline = Color(0xFF7C8A6C)
)

val CardShape = RoundedCornerShape(12.dp)

// ---------- Custom icons (drawn in code, no files needed) ----------

private fun strokeIcon(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    paths.forEach { p ->
        builder.addPath(
            pathData = PathParser().parsePathString(p).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        )
    }
    return builder.build()
}

// A fridge, for the Pantry tab
val FridgeIcon = strokeIcon(
    "fridge",
    "M6,2 H18 A2,2 0 0 1 20,4 V20 A2,2 0 0 1 18,22 H6 A2,2 0 0 1 4,20 V4 A2,2 0 0 1 6,2 Z",
    "M4,10 H20",
    "M8,5.5 V7.5",
    "M8,13 V16"
)

// Two horizontal lines = drag handle
val DragIcon = strokeIcon("drag", "M5,9 H19", "M5,15 H19")

// ---------- Data ----------

data class Item(val id: Long, val name: String, val checked: Boolean = false)

// ---------- Saving (simple, no database needed) ----------

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("shopping", Context.MODE_PRIVATE)

    fun loadItems(): List<Item> {
        val arr = JSONArray(prefs.getString("list_items", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Item(o.getLong("id"), o.getString("name"), o.optBoolean("checked", false))
        }
    }

    // Saved in the order shown on screen, so your drag-and-drop order is remembered
    fun saveItems(items: List<Item>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("checked", it.checked)
            )
        }
        prefs.edit().putString("list_items", arr.toString()).apply()
    }

    fun loadPantry(): List<String> {
        val arr = JSONArray(prefs.getString("pantry", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun savePantry(pantry: List<String>) {
        val arr = JSONArray()
        pantry.forEach { arr.put(it) }
        prefs.edit().putString("pantry", arr.toString()).apply()
    }
}

// ---------- Activity ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this)
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = PageBg) {
                    ShoppingApp(store)
                }
            }
        }
    }
}

// ---------- App (holds the data, switches between the two tabs) ----------

@Composable
fun ShoppingApp(store: Store) {
    val items = remember { mutableStateListOf<Item>().apply { addAll(store.loadItems()) } }
    val pantry = remember { mutableStateListOf<String>().apply { addAll(store.loadPantry()) } }
    var tab by remember { mutableIntStateOf(0) }   // 0 = List, 1 = Pantry
    val keyboard = LocalSoftwareKeyboardController.current

    fun save() {
        store.saveItems(items)
        store.savePantry(pantry)
    }

    fun addToList(name: String) {
        val n = name.trim()
        if (n.isEmpty() || items.any { it.name.equals(n, ignoreCase = true) }) return
        items.add(Item(System.currentTimeMillis(), n))
        save()
    }

    // Tap a pantry item: add it to the list, tap again: take it off the list
    fun toggleInList(name: String) {
        val i = items.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (i >= 0) items.removeAt(i) else items.add(Item(System.currentTimeMillis(), name))
        save()
    }

    fun toggleChecked(item: Item) {
        val i = items.indexOfFirst { it.id == item.id }
        if (i >= 0) {
            items[i] = items[i].copy(checked = !items[i].checked)
            save()
        }
    }

    fun moveItem(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        save()
    }

    // Removes the given items from the list; optionally saves them to the pantry first
    fun finishItems(targets: List<Item>, toPantry: Boolean) {
        if (toPantry) {
            targets.forEach { t ->
                if (pantry.none { it.equals(t.name, ignoreCase = true) }) pantry.add(t.name)
            }
            pantry.sortBy { it.lowercase() }
        }
        val ids = targets.map { it.id }.toSet()
        items.removeAll { it.id in ids }
        save()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .imePadding()
    ) {
        // Green header bar (extends up behind the status bar)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Green)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = if (tab == 0) "Shopping List" else "Pantry",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) {
                ListTab(
                    items = items,
                    onAdd = { addToList(it) },
                    onToggle = { toggleChecked(it) },
                    onMove = { from, to -> moveItem(from, to) },
                    onFinish = { targets, toPantry -> finishItems(targets, toPantry) }
                )
            } else {
                PantryTab(
                    pantry = pantry,
                    items = items,
                    onToggle = { toggleInList(it) },
                    onRemove = { name ->
                        pantry.removeAll { it == name }
                        save()
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavBg)
                .navigationBarsPadding()
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
                    label = { Text("List") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { keyboard?.hide(); tab = 1 },
                    icon = { Icon(FridgeIcon, contentDescription = null) },
                    label = { Text("Pantry") }
                )
            }
        }
    }
}

// ---------- Tab 1: the shopping list ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListTab(
    items: List<Item>,
    onAdd: (String) -> Unit,
    onToggle: (Item) -> Unit,
    onMove: (Int, Int) -> Unit,
    onFinish: (List<Item>, Boolean) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var menuItemId by remember { mutableStateOf<Long?>(null) }   // which row's menu is open
    var draggingId by remember { mutableStateOf<Long?>(null) }   // which row is being dragged
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current

    // Open straight into the list with the keyboard up
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    fun submit() {
        if (text.isNotBlank()) onAdd(text)
        text = ""
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) {
                Text(
                    "Nothing on the list yet",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        val isDragging = draggingId == item.id
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)          // the small gap between cards
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CardShape)
                                    .background(if (isDragging) OnListTint else Color.White)
                                    .border(1.dp, CardBorder, CardShape)
                                    .combinedClickable(
                                        onClick = { onToggle(item) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuItemId = item.id
                                        }
                                    )
                                    .padding(start = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // The row handles the tap, so the checkbox itself is display-only
                                Checkbox(checked = item.checked, onCheckedChange = null)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = item.name,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 14.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                    color = if (item.checked) MaterialTheme.colorScheme.outline
                                    else MaterialTheme.colorScheme.onSurface
                                )

                                // Drag handle: press and drag the two lines to move the item
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .pointerInput(item.id) {
                                            awaitEachGesture {
                                                // Grabbing the handle starts the drag straight away, and
                                                // stops the row's own tap / long-press menu from firing
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                down.consume()
                                                draggingId = item.id
                                                dragOffset = 0f
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                try {
                                                    drag(down.id) { change ->
                                                        // read the movement BEFORE consume(), which zeroes it
                                                        dragOffset += change.positionChange().y
                                                        change.consume()
                                                        val visible = listState.layoutInfo.visibleItemsInfo
                                                        val info = visible.firstOrNull { it.key == item.id }
                                                        val currentIndex = items.indexOfFirst { it.id == item.id }
                                                        // (info.index != currentIndex means the screen hasn't caught up yet)
                                                        if (info != null && info.index == currentIndex) {
                                                            val centre = info.offset + info.size / 2f + dragOffset
                                                            val target = visible.firstOrNull {
                                                                it.key != item.id &&
                                                                        centre >= it.offset &&
                                                                        centre <= it.offset + it.size
                                                            }
                                                            if (target != null) {
                                                                onMove(info.index, target.index)
                                                                dragOffset += (info.offset - target.offset)
                                                            }
                                                        }
                                                    }
                                                } finally {
                                                    // Always reset, even if the gesture is interrupted
                                                    draggingId = null
                                                    dragOffset = 0f
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        DragIcon,
                                        contentDescription = "Drag to reorder",
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            // The long-press menu. If this item is ticked, it acts on ALL ticked
                            // items; otherwise only on this one.
                            DropdownMenu(
                                expanded = menuItemId == item.id,
                                onDismissRequest = { menuItemId = null }
                            ) {
                                val targets = if (item.checked) items.filter { it.checked }
                                else listOf(item)
                                val suffix = if (targets.size > 1) " (${targets.size})" else ""
                                DropdownMenuItem(
                                    text = { Text("Pantry$suffix") },
                                    onClick = {
                                        menuItemId = null
                                        onFinish(targets, true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete$suffix") },
                                    onClick = {
                                        menuItemId = null
                                        onFinish(targets, false)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Add an item") },
                singleLine = true,
                shape = CardShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    unfocusedBorderColor = CardBorder
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() })
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { submit() }) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
    }
}

// ---------- Tab 2: the pantry ----------

@Composable
fun PantryTab(
    pantry: List<String>,
    items: List<Item>,
    onToggle: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Tap an item to add it to your list. Tap again to take it off.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
        )
        if (pantry.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    "Your pantry is empty.\nLong-press an item on your list and choose Pantry to save it here.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(pantry, key = { it }) { name ->
                    val onList = items.any { it.name.equals(name, ignoreCase = true) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(CardShape)
                            .background(if (onList) OnListTint else Color.White)
                            .border(1.dp, CardBorder, CardShape)
                            .clickable { onToggle(name) }
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 14.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (onList) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "On list",
                                tint = Green
                            )
                            Text(
                                " On list",
                                style = MaterialTheme.typography.labelMedium,
                                color = DarkGreen
                            )
                        }
                        IconButton(onClick = { onRemove(name) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove from pantry")
                        }
                    }
                }
            }
        }
    }
}