package com.example.maintenancereportapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ============================================================================
// LIGHT BLUE COLOR THEME DEFINITIONS
// ============================================================================

val LightBluePrimary = Color(0xFF0284C7)    // Sky Blue 600
val LightBlueDark = Color(0xFF0369A1)       // Deep Sky Blue 700
val LightBlueBackground = Color(0xFFF0F9FF) // Sky Blue 50
val LightBlueCardBg = Color(0xFFE0F2FE)     // Sky Blue 100
val LightBlueSurface = Color(0xFFFFFFFF)    // Pure White
val EmeraldGreen = Color(0xFF10B981)        // OK status
val CrimsonRed = Color(0xFFEF4444)          // Issue status

// ============================================================================
// DATA STRUCTURES
// ============================================================================

data class EquipmentCategory(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var description: String = "",
    val checklistItems: MutableList<String> = mutableListOf()
)

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val no: Int,
    val name: String,
    var isOk: Boolean = true,
    var notes: String = "",
    var base64Image: String = "", // Direct photo captured for this item!
    var caption: String = ""
)

data class ReportAttachment(
    val id: String = UUID.randomUUID().toString(),
    val base64Image: String,
    var caption: String = ""
)

data class MaintenanceReport(
    val id: String = UUID.randomUUID().toString(),
    var facilityName: String = "",
    var locationAddress: String = "",
    var date: String = "",
    var timeRange: String = "",
    var categoryName: String = "",
    var equipmentIdOrSerial: String = "",
    var inspectorName: String = "",
    var inspectorTitle: String = "",
    var inspectorSignatureBase64: String = "",
    var overallStatus: String = "ALL_OK", // "ALL_OK", "ISSUES_FOUND"
    val checklistItems: MutableList<ChecklistItem> = mutableListOf(),
    val attachments: MutableList<ReportAttachment> = mutableListOf()
)

// ============================================================================
// PERSISTENCE MANAGER (SharedPreferences + Gson)
// ============================================================================

object StorageManager {
    private const val PREFS_NAME = "MaintenanceAppPrefs"
    private const val KEY_REPORTS = "key_reports_json"
    private const val KEY_CATEGORIES = "key_categories_json"
    private const val KEY_PROFILE_NAME = "key_inspector_name"
    private const val KEY_PROFILE_TITLE = "key_inspector_title"
    private const val KEY_PROFILE_COMPANY = "key_company_name"
    private const val KEY_PROFILE_SIGNATURE = "key_signature_base64"

    fun saveReports(context: Context, reports: List<MaintenanceReport>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(reports)
        prefs.edit().putString(KEY_REPORTS, json).apply()
    }

    fun loadReports(context: Context): List<MaintenanceReport> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_REPORTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<MaintenanceReport>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCategories(context: Context, categories: List<EquipmentCategory>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(categories)
        prefs.edit().putString(KEY_CATEGORIES, json).apply()
    }

    fun loadCategories(context: Context): List<EquipmentCategory> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CATEGORIES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<EquipmentCategory>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveProfile(context: Context, name: String, title: String, company: String, signatureBase64: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_PROFILE_NAME, name)
            putString(KEY_PROFILE_TITLE, title)
            putString(KEY_PROFILE_COMPANY, company)
            putString(KEY_PROFILE_SIGNATURE, signatureBase64)
            apply()
        }
    }

    fun loadProfile(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return mapOf(
            "name" to (prefs.getString(KEY_PROFILE_NAME, "") ?: ""),
            "title" to (prefs.getString(KEY_PROFILE_TITLE, "") ?: ""),
            "company" to (prefs.getString(KEY_PROFILE_COMPANY, "") ?: ""),
            "signature" to (prefs.getString(KEY_PROFILE_SIGNATURE, "") ?: "")
        )
    }
}

// ============================================================================
// HELPER UTILITIES
// ============================================================================

fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
}

fun base64ToBitmap(base64Str: String): Bitmap? {
    return try {
        val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        null
    }
}

fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        null
    }
}

fun generateSignatureBitmap(strokes: List<List<Offset>>, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 7f
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }
    for (stroke in strokes) {
        if (stroke.size > 1) {
            for (i in 0 until (stroke.size - 1)) {
                canvas.drawLine(stroke[i].x, stroke[i].y, stroke[i + 1].x, stroke[i + 1].y, paint)
            }
        }
    }
    return bitmap
}

// ============================================================================
// NAVIGATION ENUM
// ============================================================================

enum class AppScreen {
    DASHBOARD,
    CREATE_REPORT,
    VIEW_REPORT,
    PROFILE_SETTINGS,
    CATEGORY_MANAGER
}

// ============================================================================
// MAIN ACTIVITY
// ============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = LightBluePrimary,
                    secondary = LightBlueDark,
                    background = LightBlueBackground,
                    surface = LightBlueSurface
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainAppController()
                }
            }
        }
    }
}

// ============================================================================
// MAIN APP CONTROLLER
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppController() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }

    // Persistent State Lists
    val reportsList = remember { mutableStateListOf<MaintenanceReport>() }
    val categoriesList = remember { mutableStateListOf<EquipmentCategory>() }

    // Initial Load
    LaunchedEffect(Unit) {
        reportsList.clear()
        reportsList.addAll(StorageManager.loadReports(context))
        categoriesList.clear()
        categoriesList.addAll(StorageManager.loadCategories(context))
    }

    var selectedReport by remember { mutableStateOf<MaintenanceReport?>(null) }
    var activeReport by remember { mutableStateOf(MaintenanceReport()) }

    when (currentScreen) {
        AppScreen.DASHBOARD -> {
            DashboardScreen(
                reports = reportsList,
                categories = categoriesList,
                onNewReport = {
                    val profile = StorageManager.loadProfile(context)
                    val today = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date())

                    activeReport = MaintenanceReport(
                        id = UUID.randomUUID().toString(),
                        date = today,
                        timeRange = SimpleDateFormat("HH:mm", Locale.US).format(Date()),
                        inspectorName = profile["name"] ?: "",
                        inspectorTitle = profile["title"] ?: "",
                        inspectorSignatureBase64 = profile["signature"] ?: "",
                        categoryName = ""
                    )

                    currentScreen = AppScreen.CREATE_REPORT
                },
                onEditReport = { report ->
                    activeReport = Gson().fromJson(Gson().toJson(report), MaintenanceReport::class.java)
                    currentScreen = AppScreen.CREATE_REPORT
                },
                onViewReport = { report ->
                    selectedReport = report
                    currentScreen = AppScreen.VIEW_REPORT
                },
                onDeleteReport = { report ->
                    reportsList.remove(report)
                    StorageManager.saveReports(context, reportsList)
                    Toast.makeText(context, "Report deleted successfully", Toast.LENGTH_SHORT).show()
                },
                onOpenProfile = { currentScreen = AppScreen.PROFILE_SETTINGS },
                onOpenCategories = { currentScreen = AppScreen.CATEGORY_MANAGER }
            )
        }

        AppScreen.CREATE_REPORT -> {
            ReportFormScreen(
                report = activeReport,
                onBack = { currentScreen = AppScreen.DASHBOARD },
                onSubmit = { finalizedReport ->
                    val existingIdx = reportsList.indexOfFirst { it.id == finalizedReport.id }
                    if (existingIdx >= 0) {
                        reportsList[existingIdx] = finalizedReport
                    } else {
                        reportsList.add(0, finalizedReport)
                    }
                    StorageManager.saveReports(context, reportsList)

                    // Auto-save category if new
                    if (finalizedReport.categoryName.isNotBlank() && categoriesList.none { it.name.equals(finalizedReport.categoryName, ignoreCase = true) }) {
                        val newCat = EquipmentCategory(
                            name = finalizedReport.categoryName.trim(),
                            description = "User created category",
                            checklistItems = finalizedReport.checklistItems.map { it.name }.toMutableList()
                        )
                        categoriesList.add(newCat)
                        StorageManager.saveCategories(context, categoriesList)
                    }

                    Toast.makeText(context, "Maintenance Log Saved Successfully!", Toast.LENGTH_SHORT).show()
                    currentScreen = AppScreen.DASHBOARD
                }
            )
        }

        AppScreen.VIEW_REPORT -> {
            selectedReport?.let { report ->
                ReportSummaryScreen(
                    report = report,
                    onBack = { currentScreen = AppScreen.DASHBOARD }
                )
            }
        }

        AppScreen.PROFILE_SETTINGS -> {
            ProfileSettingsScreen(
                context = context,
                onBack = { currentScreen = AppScreen.DASHBOARD }
            )
        }

        AppScreen.CATEGORY_MANAGER -> {
            CategoryManagerScreen(
                categories = categoriesList,
                onBack = { currentScreen = AppScreen.DASHBOARD },
                onCategoriesUpdated = { updatedList ->
                    categoriesList.clear()
                    categoriesList.addAll(updatedList)
                    StorageManager.saveCategories(context, categoriesList)
                }
            )
        }
    }
}

// ============================================================================
// DASHBOARD SCREEN (LIGHT BLUE THEMED, EDITABLE LOGS, NO OVERVIEW CARD)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    reports: List<MaintenanceReport>,
    categories: List<EquipmentCategory>,
    onNewReport: () -> Unit,
    onEditReport: (MaintenanceReport) -> Unit,
    onViewReport: (MaintenanceReport) -> Unit,
    onDeleteReport: (MaintenanceReport) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCategories: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    // Filter Logic
    val filteredReports = reports.filter { report ->
        val matchesSearch = report.facilityName.contains(searchQuery, ignoreCase = true) ||
                report.categoryName.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategoryFilter == "All" || report.categoryName == selectedCategoryFilter
        matchesSearch && matchesCategory
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Maintenance Report App", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightBluePrimary),
                actions = {
                    IconButton(onClick = onOpenCategories) {
                        Icon(Icons.Default.Settings, contentDescription = "Equipment Categories", tint = Color.White)
                    }
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile & Signature", tint = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewReport,
                containerColor = LightBluePrimary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Log", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(LightBlueBackground)) {

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Search logs by facility or category...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LightBluePrimary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = LightBluePrimary
                )
            )

            // Category Filter Chips Row
            if (categories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == "All",
                            onClick = { selectedCategoryFilter = "All" },
                            label = { Text("All Categories") }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat.name,
                            onClick = { selectedCategoryFilter = cat.name },
                            label = { Text(cat.name) }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Reports Feed List
            if (filteredReports.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(64.dp), tint = LightBluePrimary.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (searchQuery.isNotEmpty()) "No matching maintenance logs found."
                            else "No maintenance logs created yet.\nTap '+ New Log' to create your first log!",
                            color = Color.DarkGray,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredReports, key = { it.id }) { report ->
                        ReportCardItem(
                            report = report,
                            onClick = { onViewReport(report) },
                            onEdit = { onEditReport(report) },
                            onDelete = { onDeleteReport(report) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportCardItem(
    report: MaintenanceReport,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LightBlueCardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = report.facilityName.ifBlank { "Unnamed Log" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = LightBlueDark
                )

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Log", tint = LightBluePrimary)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonRed.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (report.categoryName.isNotBlank()) {
                Text("Category: ${report.categoryName}", fontSize = 14.sp, color = Color.DarkGray)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = LightBlueCardBg)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = LightBluePrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${report.date} | ${report.timeRange}", fontSize = 13.sp, color = Color.DarkGray)
                }

                // Count photos from checklist items + attachments
                val itemPhotosCount = report.checklistItems.count { it.base64Image.isNotBlank() }
                val totalPhotos = itemPhotosCount + report.attachments.size

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = LightBluePrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$totalPhotos Photos", fontSize = 13.sp, color = Color.DarkGray)
                }
            }
        }
    }
}

// ============================================================================
// CREATE/EDIT REPORT FORM SCREEN (EACH ITEM HAS ITS OWN CAMERA BUTTON)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportFormScreen(
    report: MaintenanceReport,
    onBack: () -> Unit,
    onSubmit: (MaintenanceReport) -> Unit
) {
    val context = LocalContext.current

    var facilityName by remember { mutableStateOf(report.facilityName) }
    var date by remember { mutableStateOf(report.date) }
    var timeRange by remember { mutableStateOf(report.timeRange) }
    var selectedCategoryName by remember { mutableStateOf(report.categoryName) }

    val checklistState = remember { mutableStateListOf<ChecklistItem>().apply { addAll(report.checklistItems) } }
    val capturedPhotos = remember { mutableStateListOf<ReportAttachment>().apply { addAll(report.attachments) } }

    var newChecklistItemInput by remember { mutableStateOf("") }

    // Active item index tracking for item-level camera button
    var activeItemIndexForCamera by remember { mutableStateOf<Int?>(null) }

    // Item Camera Launcher (Captures photo directly for a specific item row!)
    val itemCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let { bmp ->
            val idx = activeItemIndexForCamera
            if (idx != null && idx in checklistState.indices) {
                val base64 = bitmapToBase64(bmp)
                checklistState[idx] = checklistState[idx].copy(base64Image = base64)
                Toast.makeText(context, "Photo attached to Item #${idx + 1}!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // General Camera Launcher for additional photos
    val generalCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            val base64String = bitmapToBase64(it)
            capturedPhotos.add(ReportAttachment(base64Image = base64String, caption = ""))
            Toast.makeText(context, "General photo captured!", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bmp = uriToBitmap(context, it)
            bmp?.let { bitmap ->
                val base64String = bitmapToBase64(bitmap)
                capturedPhotos.add(ReportAttachment(base64Image = base64String, caption = ""))
                Toast.makeText(context, "Photo added from Gallery!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (report.facilityName.isNotBlank()) "Edit Maintenance Log" else "Create Maintenance Log", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightBluePrimary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(LightBlueBackground)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Facility & Equipment Information (Date & Time preserved)
            item {
                Text("1. Facility Information", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LightBlueDark)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = facilityName,
                    onValueChange = { facilityName = it },
                    label = { Text("Facility / Business Name") },
                    placeholder = { Text("e.g. Main Machine Facility / Computer Room") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = selectedCategoryName,
                    onValueChange = { selectedCategoryName = it },
                    label = { Text("Category / Equipment Name") },
                    placeholder = { Text("e.g. 1. Main Machine or 2. Computer") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                    )
                    OutlinedTextField(
                        value = timeRange,
                        onValueChange = { timeRange = it },
                        label = { Text("Time") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                    )
                }
            }

            // Section 2: Custom Inspection Items (EVERY ITEM HAS ITS OWN CAMERA BUTTON)
            item {
                HorizontalDivider(color = LightBlueCardBg, modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("2. Inspection Checklist Items", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LightBlueDark)
                    Text("${checklistState.size} Items", color = LightBluePrimary, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input card to add new checklist item from scratch
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = LightBlueCardBg)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Add Inspection Item to this Log:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LightBlueDark)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newChecklistItemInput,
                                onValueChange = { newChecklistItemInput = it },
                                placeholder = { Text("e.g. Check motor vibration, voltage...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newChecklistItemInput.isNotBlank()) {
                                        checklistState.add(
                                            ChecklistItem(
                                                no = checklistState.size + 1,
                                                name = newChecklistItemInput.trim(),
                                                isOk = true,
                                                notes = ""
                                            )
                                        )
                                        newChecklistItemInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LightBluePrimary)
                            ) {
                                Text("+ Add")
                            }
                        }
                    }
                }
            }

            if (checklistState.isEmpty()) {
                item {
                    Text("No inspection items added yet. Type an item above and tap '+ Add' to build your log!", fontSize = 13.sp, color = Color.Gray)
                }
            }

            itemsIndexed(checklistState) { idx, item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LightBlueCardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Title + Delete Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${idx + 1}. ${item.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = LightBlueDark, modifier = Modifier.weight(1f))
                            IconButton(onClick = { checklistState.removeAt(idx) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Item", tint = CrimsonRed)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Status Chips Row + Dedicated Item Camera Button!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = item.isOk,
                                    onClick = { checklistState[idx] = item.copy(isOk = true) },
                                    label = { Text("OK") },
                                    leadingIcon = { if (item.isOk) Icon(Icons.Default.Check, null) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                FilterChip(
                                    selected = !item.isOk,
                                    onClick = { checklistState[idx] = item.copy(isOk = false) },
                                    label = { Text("Issue") },
                                    leadingIcon = { if (!item.isOk) Icon(Icons.Default.Close, null) }
                                )
                            }

                            // DEDICATED CAMERA BUTTON FOR THIS SPECIFIC ITEM!
                            Button(
                                onClick = {
                                    activeItemIndexForCamera = idx
                                    itemCameraLauncher.launch(null)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LightBluePrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Camera", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (item.base64Image.isNotBlank()) "Retake Photo" else "Snap Photo", fontSize = 12.sp)
                            }
                        }

                        // Direct Item Photo Preview if captured
                        if (item.base64Image.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth()) {
                                base64ToBitmap(item.base64Image)?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                IconButton(
                                    onClick = { checklistState[idx] = item.copy(base64Image = "") },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove Photo", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = item.notes,
                            onValueChange = { newNotes ->
                                checklistState[idx] = item.copy(notes = newNotes)
                            },
                            label = { Text("Notes / Maintenance Action") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = LightBlueBackground, unfocusedContainerColor = LightBlueBackground)
                        )
                    }
                }
            }

            // Section 3: Additional General Photo Evidence Attachments
            item {
                HorizontalDivider(color = LightBlueCardBg, modifier = Modifier.padding(vertical = 8.dp))
                Text("3. Additional General Photos", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LightBlueDark)

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { generalCameraLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = LightBluePrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Camera", tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Extra Photo")
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Gallery", tint = LightBluePrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Gallery", color = LightBluePrimary)
                    }
                }
            }

            itemsIndexed(capturedPhotos) { photoIdx, attachment ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LightBlueCardBg)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            base64ToBitmap(attachment.base64Image)?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            IconButton(
                                onClick = { capturedPhotos.removeAt(photoIdx) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        var captionText by remember { mutableStateOf(attachment.caption) }
                        OutlinedTextField(
                            value = captionText,
                            onValueChange = {
                                captionText = it
                                attachment.caption = it
                            },
                            label = { Text("Photo Caption / Label") },
                            placeholder = { Text("e.g. General overview photo") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // Save & Commit Button
            item {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (facilityName.isBlank()) {
                            Toast.makeText(context, "Please enter Facility/Business Name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        report.facilityName = facilityName
                        report.date = date
                        report.timeRange = timeRange
                        report.categoryName = selectedCategoryName

                        report.checklistItems.clear()
                        report.checklistItems.addAll(checklistState)

                        report.attachments.clear()
                        report.attachments.addAll(capturedPhotos)

                        onSubmit(report)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LightBluePrimary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Maintenance Log", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ============================================================================
// CATEGORY MANAGER SCREEN (LIGHT BLUE THEMED, FULLY EDITABLE AT ALL TIMES)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerScreen(
    categories: List<EquipmentCategory>,
    onBack: () -> Unit,
    onCategoriesUpdated: (List<EquipmentCategory>) -> Unit
) {
    val categoryListState = remember { mutableStateListOf<EquipmentCategory>().apply { addAll(categories) } }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<EquipmentCategory?>(null) }

    var catNameInput by remember { mutableStateOf("") }
    var catDescInput by remember { mutableStateOf("") }
    var catItemsInput by remember { mutableStateOf("") }

    fun openAddDialog() {
        editingCategory = null
        catNameInput = ""
        catDescInput = ""
        catItemsInput = ""
        showAddDialog = true
    }

    fun openEditDialog(cat: EquipmentCategory) {
        editingCategory = cat
        catNameInput = cat.name
        catDescInput = cat.description
        catItemsInput = cat.checklistItems.joinToString(", ")
        showAddDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Equipment Categories", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightBluePrimary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { openAddDialog() },
                containerColor = LightBluePrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Category", tint = Color.White)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(LightBlueBackground)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Manage Categories & Checklists", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LightBlueDark)
                Text(
                    "Configure equipment categories (e.g., 1. Main Machine, 2. Computer) and default inspection items at any time.",
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (categoryListState.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, LightBlueCardBg)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = LightBluePrimary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No Equipment Categories Defined Yet", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LightBlueDark)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Tap the '+' button below to create your first category from scratch.", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
            }

            itemsIndexed(categoryListState) { idx, category ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, LightBlueCardBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(category.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LightBlueDark)
                            Row {
                                IconButton(onClick = { openEditDialog(category) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Category", tint = LightBluePrimary)
                                }
                                IconButton(onClick = {
                                    categoryListState.removeAt(idx)
                                    onCategoriesUpdated(categoryListState)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonRed)
                                }
                            }
                        }

                        if (category.description.isNotBlank()) {
                            Text(category.description, fontSize = 13.sp, color = Color.Gray)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${category.checklistItems.size} Default Checklist Items:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LightBluePrimary)

                        category.checklistItems.forEachIndexed { itemIdx, item ->
                            Text(" • ${itemIdx + 1}. $item", fontSize = 12.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Category Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (editingCategory == null) "Add Equipment Category" else "Edit Equipment Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = catNameInput,
                        onValueChange = { catNameInput = it },
                        label = { Text("Category Name (e.g. 1. Main Machine)") }
                    )
                    OutlinedTextField(
                        value = catDescInput,
                        onValueChange = { catDescInput = it },
                        label = { Text("Description") }
                    )
                    OutlinedTextField(
                        value = catItemsInput,
                        onValueChange = { catItemsInput = it },
                        label = { Text("Checklist Items (comma separated)") },
                        placeholder = { Text("Check voltage, motor noise, fan cleaning") },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (catNameInput.isNotBlank()) {
                            val itemsList = catItemsInput.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toMutableList()

                            val currentEditing = editingCategory
                            if (currentEditing != null) {
                                val idx = categoryListState.indexOfFirst { it.id == currentEditing.id }
                                if (idx >= 0) {
                                    categoryListState[idx] = currentEditing.copy(
                                        name = catNameInput.trim(),
                                        description = catDescInput.trim(),
                                        checklistItems = if (itemsList.isEmpty()) mutableListOf("General Inspection") else itemsList
                                    )
                                }
                            } else {
                                val newCategory = EquipmentCategory(
                                    name = catNameInput.trim(),
                                    description = catDescInput.trim(),
                                    checklistItems = if (itemsList.isEmpty()) mutableListOf("General Inspection") else itemsList
                                )
                                categoryListState.add(newCategory)
                            }

                            onCategoriesUpdated(categoryListState)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LightBluePrimary)
                ) {
                    Text("Save Category")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ============================================================================
// PROFILE SETTINGS & DIGITAL SIGNATURE SCREEN
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(context: Context, onBack: () -> Unit) {
    val initialProfile = StorageManager.loadProfile(context)

    var nameInput by remember { mutableStateOf(initialProfile["name"] ?: "") }
    var titleInput by remember { mutableStateOf(initialProfile["title"] ?: "") }
    var companyInput by remember { mutableStateOf(initialProfile["company"] ?: "") }
    var signatureBase64 by remember { mutableStateOf(initialProfile["signature"] ?: "") }

    val strokesList = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inspector Profile & Signature", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightBluePrimary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(LightBlueBackground)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Inspector Configuration", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = LightBlueDark)

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Inspector Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Title / Position") },
                    placeholder = { Text("e.g. Lead Maintenance Engineer") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = companyInput,
                    onValueChange = { companyInput = it },
                    label = { Text("Company / Organization Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                )
            }

            item {
                Text("Digital Signature Pad", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LightBlueDark)
                Text("Draw your authorized signature below for report export certification.", fontSize = 12.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.White)
                        .border(2.dp, LightBluePrimary, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { currentStroke = listOf(it) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    strokesList.add(currentStroke)
                                    currentStroke = emptyList()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        strokesList.forEach { stroke ->
                            if (stroke.size > 1) {
                                for (i in 0 until (stroke.size - 1)) {
                                    drawLine(Color.Black, stroke[i], stroke[i + 1], strokeWidth = 6f, cap = StrokeCap.Round)
                                }
                            }
                        }
                        if (currentStroke.size > 1) {
                            for (i in 0 until (currentStroke.size - 1)) {
                                drawLine(Color.Black, currentStroke[i], currentStroke[i + 1], strokeWidth = 6f, cap = StrokeCap.Round)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { strokesList.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Signature Pad", color = CrimsonRed)
                    }

                    Button(
                        onClick = {
                            if (strokesList.isNotEmpty()) {
                                val bmp = generateSignatureBitmap(strokesList, 400, 200)
                                signatureBase64 = bitmapToBase64(bmp)
                                Toast.makeText(context, "Signature captured & locked!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LightBluePrimary)
                    ) {
                        Text("Capture Signature")
                    }
                }
            }

            if (signatureBase64.isNotEmpty()) {
                item {
                    Text("Saved Signature Preview:", fontSize = 13.sp, color = Color.Gray)
                    base64ToBitmap(signatureBase64)?.let { signBmp ->
                        Image(
                            bitmap = signBmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(Color.White)
                                .border(1.dp, LightBlueCardBg, RoundedCornerShape(6.dp))
                                .padding(4.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        StorageManager.saveProfile(context, nameInput, titleInput, companyInput, signatureBase64)
                        Toast.makeText(context, "Profile Settings Saved!", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LightBluePrimary)
                ) {
                    Text("Save Inspector Profile", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================================
// REPORT REVIEW & PDF EXPORT SCREEN
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSummaryScreen(report: MaintenanceReport, onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Review", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightBluePrimary),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        shareReportSummaryText(context, report)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(LightBlueBackground)) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column {
                        Text(report.facilityName.ifBlank { "Facility Report" }, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = LightBlueDark)
                        if (report.categoryName.isNotBlank()) {
                            Text("Category: ${report.categoryName}", fontSize = 14.sp, color = LightBluePrimary, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Date: ${report.date} | Time: ${report.timeRange}", fontSize = 13.sp, color = Color.Gray)

                    HorizontalDivider(color = LightBlueCardBg, modifier = Modifier.padding(vertical = 12.dp))
                }

                item {
                    Text("Checklist Inspection Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LightBlueDark)
                }

                itemsIndexed(report.checklistItems) { idx, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, LightBlueCardBg)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${idx + 1}. ${item.name}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LightBlueDark)
                                    if (item.notes.isNotBlank()) {
                                        Text("Notes: ${item.notes}", fontSize = 12.sp, color = Color.DarkGray)
                                    }
                                }

                                Text(
                                    text = if (item.isOk) "☑ OK" else "⚠️ Issue",
                                    color = if (item.isOk) EmeraldGreen else CrimsonRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            if (item.base64Image.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                base64ToBitmap(item.base64Image)?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }

                if (report.attachments.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Additional Photo Evidence (${report.attachments.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LightBlueDark)
                    }

                    itemsIndexed(report.attachments) { _, photo ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, LightBlueCardBg)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                base64ToBitmap(photo.base64Image)?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(70.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    photo.caption.ifBlank { "Photo Evidence Log" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Bottom PDF Action Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Button(
                    onClick = { generatePdfDocument(context, report) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LightBluePrimary)
                ) {
                    Icon(Icons.Default.Done, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export & Print English PDF Report", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================================
// SHARE & PDF GENERATION ENGINE
// ============================================================================

fun shareReportSummaryText(context: Context, report: MaintenanceReport) {
    val summary = StringBuilder()
    summary.append("MAINTENANCE REPORT\n")
    summary.append("Facility: ${report.facilityName}\n")
    if (report.categoryName.isNotBlank()) {
        summary.append("Category: ${report.categoryName}\n")
    }
    summary.append("Date: ${report.date} | Time: ${report.timeRange}\n\n")
    summary.append("Checklist Items:\n")
    for (item in report.checklistItems) {
        summary.append("- ${item.name}: ${if (item.isOk) "OK" else "ISSUE"}\n")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Maintenance Report - ${report.facilityName}")
        putExtra(Intent.EXTRA_TEXT, summary.toString())
    }
    context.startActivity(Intent.createChooser(intent, "Share Maintenance Report"))
}

fun generatePdfDocument(context: Context, report: MaintenanceReport) {
    val webView = WebView(context)
    val htmlBuilder = StringBuilder()

    htmlBuilder.append("""
        <!DOCTYPE html>
        <html>
        <head>
        <style>
            body { font-family: 'Segoe UI', Helvetica, Arial, sans-serif; margin: 25px; color: #0369a1; }
            h1 { text-align: center; color: #0284c7; font-size: 20px; margin-bottom: 5px; text-transform: uppercase; letter-spacing: 1px; }
            .subtitle { text-align: center; color: #0369a1; font-size: 13px; font-weight: bold; margin-bottom: 20px; }
            table { width: 100%; border-collapse: collapse; margin-top: 10px; margin-bottom: 20px; }
            th, td { border: 1px solid #bae6fd; padding: 8px; text-align: left; font-size: 11px; }
            th { background-color: #0284c7; color: #ffffff; text-transform: uppercase; font-size: 10px; letter-spacing: 0.5px; }
            .header-table td { border: none; padding: 5px; color: #1e293b; }
            .section-title { font-weight: bold; background-color: #e0f2fe; color: #0369a1; padding: 8px; margin-top: 15px; font-size: 13px; border-left: 4px solid #0284c7; }
            .status-ok { color: #10b981; font-weight: bold; }
            .status-issue { color: #ef4444; font-weight: bold; }
            .photo-grid-table { width: 100%; border: none; margin-top: 10px; }
            .photo-grid-table td { width: 50%; border: 1px solid #e0f2fe; padding: 10px; text-align: center; vertical-align: top; background: #f0f9ff; }
            .photo-grid-table img { width: 100%; max-height: 220px; object-fit: cover; border-radius: 4px; }
            .photo-caption { font-size: 11px; margin-top: 6px; font-weight: bold; text-align: left; color: #0369a1; }
            .footer-sign-table { width: 100%; border: none; margin-top: 35px; }
            .footer-sign-table td { border: none; text-align: center; width: 50%; font-size: 12px; color: #1e293b; }
            .sign-img { max-height: 70px; width: auto; margin-top: 5px; margin-bottom: 5px; }
            .item-photo { max-height: 120px; width: auto; margin-top: 5px; border-radius: 4px; display: block; }
        </style>
        </head>
        <body>
            <h1>MAINTENANCE REPORT</h1>
            <div class='subtitle'>FACILITY & EQUIPMENT LOG</div>

            <table class='header-table'>
                <tr><td><b>Facility / Business:</b> ${report.facilityName}</td><td><b>Date:</b> ${report.date}</td></tr>
                <tr><td><b>Category:</b> ${report.categoryName.ifBlank { "General Inspection" }}</td><td><b>Time:</b> ${report.timeRange}</td></tr>
            </table>

            <div class='section-title'>INSPECTION CHECKLIST RESULTS</div>
            <table>
                <tr>
                    <th style='width:6%;'>No</th>
                    <th style='width:50%;'>Inspection Item</th>
                    <th style='width:14%;'>Condition</th>
                    <th>Notes & Photo Evidence</th>
                </tr>
    """.trimIndent())

    for ((idx, item) in report.checklistItems.withIndex()) {
        val statusHtml = if (item.isOk) "<span class='status-ok'>OK</span>" else "<span class='status-issue'>ISSUE</span>"
        val itemPhotoHtml = if (item.base64Image.isNotBlank()) {
            "<br/><img class='item-photo' src='data:image/jpeg;base64,${item.base64Image.trim()}' />"
        } else ""

        htmlBuilder.append("<tr><td>${idx + 1}</td><td>${item.name}</td><td>$statusHtml</td><td>${item.notes.ifBlank { "-" }}$itemPhotoHtml</td></tr>")
    }

    htmlBuilder.append("</table>")

    // Additional General Photo Attachments Section
    if (report.attachments.isNotEmpty()) {
        htmlBuilder.append("<div class='section-title' style='page-break-before: auto;'>ADDITIONAL PHOTO EVIDENCE</div>")
        htmlBuilder.append("<table class='photo-grid-table'>")
        for (i in report.attachments.indices step 2) {
            htmlBuilder.append("<tr>")

            val leftPhoto = report.attachments[i]
            htmlBuilder.append("<td>")
            htmlBuilder.append("<img src='data:image/jpeg;base64,${leftPhoto.base64Image.trim()}' />")
            htmlBuilder.append("<div class='photo-caption'>${leftPhoto.caption.ifBlank { "Photo Evidence #${i + 1}" }}</div>")
            htmlBuilder.append("</td>")

            if (i + 1 < report.attachments.size) {
                val rightPhoto = report.attachments[i + 1]
                htmlBuilder.append("<td>")
                htmlBuilder.append("<img src='data:image/jpeg;base64,${rightPhoto.base64Image.trim()}' />")
                htmlBuilder.append("<div class='photo-caption'>${rightPhoto.caption.ifBlank { "Photo Evidence #${i + 2}" }}</div>")
                htmlBuilder.append("</td>")
            } else {
                htmlBuilder.append("<td style='border:none;'></td>")
            }
            htmlBuilder.append("</tr>")
        }
        htmlBuilder.append("</table>")
    }

    // Signature Footer Section
    val signatureImgTag = if (report.inspectorSignatureBase64.isNotEmpty()) {
        "<img class='sign-img' src='data:image/png;base64,${report.inspectorSignatureBase64.trim()}' /><br/>"
    } else {
        "<br/><br/><br/>"
    }

    htmlBuilder.append("""
            <table class='footer-sign-table'>
                <tr>
                    <td>
                        Inspector Signature,<br/>
                        $signatureImgTag
                        <b>${report.inspectorName.ifBlank { "Authorized Inspector" }}</b>
                    </td>
                    <td>
                        Facility Manager Approval,<br/><br/><br/><br/>
                        <b>( _______________________ )</b>
                    </td>
                </tr>
            </table>
        </body>
        </html>
    """.trimIndent())

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val jobName = "Maintenance_Report_${report.facilityName.replace(" ", "_")}"
            val printAdapter = view.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        }
    }

    webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "utf-8", null)
}
