package com.example.maintenancereportapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.io.ByteArrayOutputStream
import java.util.UUID

// ==========================================
// DATA STRUCTURES (Upgraded with Signatures)
// ==========================================

data class MaintenanceReport(
    val id: String = UUID.randomUUID().toString(),
    var buildingName: String = "",
    var date: String = "",
    var address: String = "",
    var timeRange: String = "",
    var floorCount: String = "",
    var liftBrand: String = "",
    var liftType: String = "",
    var doorCount: String = "",
    var totalStops: String = "",
    val ruangMesinItems: List<ChecklistItem> = getDefaultRuangMesin(),
    val atasSangkarItems: List<ChecklistItem> = getDefaultAtasSangkar(),
    val pitLiftItems: List<ChecklistItem> = getDefaultPitLift(),
    val attachments: MutableList<ReportAttachment> = mutableListOf(),
    var reporterName: String = "",
    var reporterSignatureBase64: String = ""
)

data class ChecklistItem(
    val no: Int,
    val name: String,
    var isOk: Boolean = true,
    var notes: String = ""
)

data class ReportAttachment(
    val id: String = UUID.randomUUID().toString(),
    val base64Image: String,
    var caption: String = ""
)

fun getDefaultRuangMesin() = listOf(
    ChecklistItem(1, "Kondisi suhu ruang mesin"),
    ChecklistItem(2, "Kondisi lantai ruang mesin"),
    ChecklistItem(3, "Kondisi penerangan ruang mesin"),
    ChecklistItem(4, "Pembersihan ruang mesin"),
    ChecklistItem(5, "Pengecekan voltase"),
    ChecklistItem(6, "Cek kondisi mesin"),
    ChecklistItem(7, "Cek balancing sangkar"),
    ChecklistItem(8, "Steel rope / Steel belt"),
    ChecklistItem(9, "Governor rope"),
    ChecklistItem(10, "Steel rope governor")
)

fun getDefaultAtasSangkar() = listOf(
    ChecklistItem(1, "Limit switch"),
    ChecklistItem(2, "Box panel/panel atas sangkar"),
    ChecklistItem(3, "Power supply"),
    ChecklistItem(4, "Intercom"),
    ChecklistItem(5, "Door sensor"),
    ChecklistItem(6, "Roller/Sliding guide shoe (sangkar)"),
    ChecklistItem(7, "Sliding guide shoe (CWT)"),
    ChecklistItem(8, "Door lock / door contact"),
    ChecklistItem(9, "Door deterrent"),
    ChecklistItem(10, "Fan sangkar"),
    ChecklistItem(11, "V Belt door motor"),
    ChecklistItem(12, "Door motor"),
    ChecklistItem(13, "Door control / door operator"),
    ChecklistItem(14, "Door shoe"),
    ChecklistItem(15, "Roller landing door"),
    ChecklistItem(16, "Roller sangkar"),
    ChecklistItem(17, "Penerangan atas sangkar"),
    ChecklistItem(18, "Pembersihan atas sangkar"),
    ChecklistItem(19, "Pengecekan dan pengencangan baut"),
    ChecklistItem(20, "Steel rope landing door"),
    ChecklistItem(21, "Pulley sangkar"),
    ChecklistItem(22, "TEST Fire alarm / ARD")
)

fun getDefaultPitLift() = listOf(
    ChecklistItem(1, "Pembersihan PIT"),
    ChecklistItem(2, "Penerangan pit"),
    ChecklistItem(3, "Control pit"),
    ChecklistItem(4, "Tangga pit"),
    ChecklistItem(5, "Safety CWT"),
    ChecklistItem(6, "Kondisi PIT"),
    ChecklistItem(7, "Tatakan oli"),
    ChecklistItem(8, "Buffer sangkar"),
    ChecklistItem(9, "Buffer counterweight"),
    ChecklistItem(10, "Jarak runbay")
)

// Global Bitmaps and Converters
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

// Vector-to-Bitmap conversion engine for our handwritten canvas strokes
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
            for (i in 0 until stroke.size - 1) {
                canvas.drawLine(stroke[i].x, stroke[i].y, stroke[i+1].x, stroke[i+1].y, paint)
            }
        }
    }
    return bitmap
}

// ==========================================
// NAVIGATION AND SCREEN CONTROLLER
// ==========================================

enum class AppScreen { DASHBOARD, CREATE_REPORT, VIEW_REPORT, PROFILE_SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainAppController()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppController() {
    var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }
    val reportsList = remember { mutableStateListOf<MaintenanceReport>() }
    var selectedReport by remember { mutableStateOf<MaintenanceReport?>(null) }
    var activeReport by remember { mutableStateOf(MaintenanceReport()) }

    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("UserProfilePrefs", Context.MODE_PRIVATE) }

    when (currentScreen) {
        AppScreen.DASHBOARD -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Laporan Maintenance Lift", color = Color.White, fontWeight = FontWeight.Bold) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E3A8A)),
                        actions = {
                            IconButton(onClick = { currentScreen = AppScreen.PROFILE_SETTINGS }) {
                                Icon(Icons.Default.Person, contentDescription = "Profile Setup", tint = Color.White)
                            }
                        }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(onClick = {
                        // Create log payload and map saved persistent technician configurations directly
                        val savedName = sharedPrefs.getString("technician_name", "") ?: ""
                        val savedSign = sharedPrefs.getString("technician_signature", "") ?: ""
                        
                        activeReport = MaintenanceReport().apply {
                            reporterName = savedName
                            reporterSignatureBase64 = savedSign
                        }
                        currentScreen = AppScreen.CREATE_REPORT
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "New Report")
                    }
                }
            ) { padding ->
                LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                    if (reportsList.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No reports submitted yet.", color = Color.Gray)
                            }
                        }
                    } else {
                        itemsIndexed(reportsList) { _, report ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                onClick = {
                                    selectedReport = report
                                    currentScreen = AppScreen.VIEW_REPORT
                                }
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(report.buildingName.ifBlank { "Unnamed Project" }, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("Date: ${report.date.ifBlank { "N/A" }}", fontSize = 14.sp)
                                    Text("Reporter: ${report.reporterName.ifBlank { "Unknown" }} | Photos: ${report.attachments.size}", fontSize = 14.sp, color = Color(0xFF1E3A8A))
                                }
                            }
                        }
                    }
                }
            }
        }

        AppScreen.CREATE_REPORT -> {
            ReportFormScreen(
                report = activeReport,
                onBack = { currentScreen = AppScreen.DASHBOARD },
                onSubmit = { finalizedReport ->
                    val jsonString = Gson().toJson(finalizedReport)
                    val parsedReport = Gson().fromJson(jsonString, MaintenanceReport::class.java)
                    reportsList.add(parsedReport)
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
    }
}

// ==========================================
// NEW SCREEN: PROFILE SETTINGS & SIGN CANVAS
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(context: Context, onBack: () -> Unit) {
    val sharedPrefs = remember { context.getSharedPreferences("UserProfilePrefs", Context.MODE_PRIVATE) }
    var nameInput by remember { mutableStateOf(sharedPrefs.getString("technician_name", "") ?: "") }
    var signatureBase64 by remember { mutableStateOf(sharedPrefs.getString("technician_signature", "") ?: "") }

    val strokesList = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Signature Setup") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            item {
                Text("Technician Identity", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Full Name / Vendor Title") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
                
                Text("Digital Signature Pad (Draw Below)", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
                
                // Pure touch signature interface tracking coordinates
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.White)
                        .border(2.dp, Color.Gray)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { currentStroke = listOf(it) },
                                onDrag = { change, dragAmount ->
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
                                for (i in 0 until stroke.size - 1) {
                                    drawLine(Color.Black, stroke[i], stroke[i + 1], strokeWidth = 6f, cap = StrokeCap.Round)
                                }
                            }
                        }
                        if (currentStroke.size > 1) {
                            for (i in 0 until currentStroke.size - 1) {
                                drawLine(Color.Black, currentStroke[i], currentStroke[i + 1], strokeWidth = 6f, cap = StrokeCap.Round)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { strokesList.clear() }) {
                        Icon(Icons.Default.Delete, "")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Pad")
                    }
                    
                    Button(onClick = {
                        if (strokesList.isNotEmpty()) {
                            val bmp = generateSignatureBitmap(strokesList, 400, 200)
                            signatureBase64 = bitmapToBase64(bmp)
                            Toast.makeText(context, "Signature drawing verified!", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Lock Signature")
                    }
                }
            }

            if (signatureBase64.isNotEmpty()) {
                item {
                    Text("Saved Signature Preview:", modifier = Modifier.padding(top = 12.dp), fontSize = 14.sp, color = Color.Gray)
                    base64ToBitmap(signatureBase64)?.let { signBmp ->
                        Image(
                            bitmap = signBmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(80.dp).background(Color.LightGray).padding(4.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    onClick = {
                        sharedPrefs.edit().apply {
                            putString("technician_name", nameInput)
                            putString("technician_signature", signatureBase64)
                            apply()
                        }
                        Toast.makeText(context, "Profile configurations synchronized!", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                ) {
                    Text("Save & Commit Profile Changes")
                }
            }
        }
    }
}

// ==========================================
// SCREEN: CREATE/EDIT REPORT FORM
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportFormScreen(
    report: MaintenanceReport,
    onBack: () -> Unit,
    onSubmit: (MaintenanceReport) -> Unit
) {
    var buildingName by remember { mutableStateOf(report.buildingName) }
    var date by remember { mutableStateOf(report.date) }
    var address by remember { mutableStateOf(report.address) }
    var timeRange by remember { mutableStateOf(report.timeRange) }
    var floorCount by remember { mutableStateOf(report.floorCount) }
    var liftBrand by remember { mutableStateOf(report.liftBrand) }
    var liftType by remember { mutableStateOf(report.liftType) }
    var doorCount by remember { mutableStateOf(report.doorCount) }
    var totalStops by remember { mutableStateOf(report.totalStops) }

    val capturedPhotos = remember { mutableStateListOf<ReportAttachment>().apply { addAll(report.attachments) } }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            val base64String = bitmapToBase64(it)
            capturedPhotos.add(ReportAttachment(base64Image = base64String, caption = ""))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fill Maintenance Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            item {
                Text("General Specifications", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(vertical = 8.dp))
                OutlinedTextField(value = buildingName, onValueChange = { buildingName = it }, label = { Text("Nama Gedung") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Tanggal (e.g. 31 Maret 2026)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Alamat") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = timeRange, onValueChange = { timeRange = it }, label = { Text("Waktu Kerja") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = floorCount, onValueChange = { floorCount = it }, label = { Text("Jumlah Lantai") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = liftBrand, onValueChange = { liftBrand = it }, label = { Text("Merk Lift") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = liftType, onValueChange = { liftType = it }, label = { Text("Tipe Lift (e.g. Passenger PL 2)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = doorCount, onValueChange = { doorCount = it }, label = { Text("Jumlah Pintu") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = totalStops, onValueChange = { totalStops = it }, label = { Text("Jumlah Stop") }, modifier = Modifier.fillMaxWidth())
                
                Spacer(modifier = Modifier.height(24.dp))
            }

            item { Text("1. Ruang Mesin Checklist", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            itemsIndexed(report.ruangMesinItems) { idx, item -> ChecklistInputRow(idx, item) }

            item { Text("2. Atas Sangkar / Shaft Checklist", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp)) }
            itemsIndexed(report.atasSangkarItems) { idx, item -> ChecklistInputRow(idx, item) }

            item { Text("3. Pit Lift Checklist", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp)) }
            itemsIndexed(report.pitLiftItems) { idx, item -> ChecklistInputRow(idx, item) }

            // Camera Attachment Layout
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
                Text("4. Lampiran Foto (Evidence)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Button(
                    onClick = { cameraLauncher.launch(null) },
                    modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth()
                ) {
                    Text("Snap Photo with Camera")
                }
            }

            itemsIndexed(capturedPhotos) { _, attachment ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        base64ToBitmap(attachment.base64Image)?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        var captionText by remember { mutableStateOf(attachment.caption) }
                        OutlinedTextField(
                            value = captionText,
                            onValueChange = { captionText = it; attachment.caption = it },
                            label = { Text("Keterangan Foto / Label") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    onClick = {
                        report.buildingName = buildingName
                        report.date = date
                        report.address = address
                        report.timeRange = timeRange
                        report.floorCount = floorCount
                        report.liftBrand = liftBrand
                        report.liftType = liftType
                        report.doorCount = doorCount
                        report.totalStops = totalStops
                        report.attachments.clear()
                        report.attachments.addAll(capturedPhotos)
                        onSubmit(report)
                    }
                ) {
                    Text("Submit Report Data (JSON Payload)")
                }
            }
        }
    }
}

@Composable
fun ChecklistInputRow(index: Int, item: ChecklistItem) {
    var statusOk by remember { mutableStateOf(item.isOk) }
    var notesText by remember { mutableStateOf(item.notes) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${item.no}. ${item.name}", fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Condition: ")
                FilterChip(
                    selected = statusOk,
                    onClick = { statusOk = true; item.isOk = true },
                    label = { Text("OK") },
                    leadingIcon = { if (statusOk) Icon(Icons.Default.Check, "") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = !statusOk,
                    onClick = { statusOk = false; item.isOk = false },
                    label = { Text("Not OK") },
                    leadingIcon = { if (!statusOk) Icon(Icons.Default.Close, "") }
                )
            }
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it; item.notes = it },
                label = { Text("Keterangan / Notes") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==========================================
// SCREEN: ADMIN VIEW & ON-DEVICE PDF EXPORT
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSummaryScreen(report: MaintenanceReport, onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Mode (Admin)") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                item {
                    Text("Laporan: ${report.buildingName}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Tipe Lift: ${report.liftType}", fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }
                
                item { Text("Summary Checklist Status", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                
                item { Text("\n[Ruang Mesin Items]", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                itemsIndexed(report.ruangMesinItems) { _, item -> StaticSummaryRow(item) }

                item { Text("\n[Atas Sangkar Items]", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                itemsIndexed(report.atasSangkarItems) { _, item -> StaticSummaryRow(item) }

                item { Text("\n[Pit Lift Items]", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                itemsIndexed(report.pitLiftItems) { _, item -> StaticSummaryRow(item) }

                if (report.attachments.isNotEmpty()) {
                    item { Text("\n[Lampiran Foto]", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                    itemsIndexed(report.attachments) { _, photo ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                base64ToBitmap(photo.base64Image)?.let { bmp ->
                                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(60.dp), contentScale = ContentScale.Crop)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(photo.caption.ifBlank { "Tidak ada keterangan" }, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { generatePdfDocument(context, report) }
                ) {
                    Text("Export & Print Local PDF Document")
                }
            }
        }
    }
}

@Composable
fun StaticSummaryRow(item: ChecklistItem) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${item.no}. ${item.name}", modifier = Modifier.weight(0.6f), fontSize = 14.sp)
        Text(if (item.isOk) "☑ OK" else "⚠️ Not OK", modifier = Modifier.weight(0.2f), color = if (item.isOk) Color(0xFF388E3C) else Color.Red, fontWeight = FontWeight.Bold)
        Text(item.notes.ifBlank { "-" }, modifier = Modifier.weight(0.2f), fontSize = 12.sp, color = Color.DarkGray)
    }
}

// ==========================================
// ON-DEVICE PRINT ENGINE: HTML -> PDF Canvas
// ==========================================

fun generatePdfDocument(context: Context, report: MaintenanceReport) {
    val webView = WebView(context)
    val htmlBuilder = StringBuilder()
    
    htmlBuilder.append("""
        <html>
        <head>
        <style>
            body { font-family: sans-serif; margin: 20px; }
            table { width: 100%; border-collapse: collapse; margin-top: 15px; margin-bottom: 25px; }
            th, td { border: 1px solid #333; padding: 6px; text-align: left; font-size: 12px; }
            th { background-color: #f2f2f2; }
            .header-table td { border: none; padding: 4px; }
            .section-title { font-weight: bold; background-color: #ddd; padding: 5px; margin-top: 20px; font-size: 14px; }
            .photo-grid-table { width: 100%; border: none; }
            .photo-grid-table td { width: 50%; border: 1px solid #ccc; padding: 10px; text-align: center; vertical-align: top; }
            .photo-grid-table img { width: 100%; max-height: 250px; object-fit: cover; }
            .photo-caption { font-size: 11px; margin-top: 5px; font-weight: bold; text-align: left; }
            .footer-sign-table { width: 100%; border: none; margin-top: 40px; }
            .footer-sign-table td { border: none; text-align: center; width: 50%; font-size: 13px; }
            .sign-img { max-height: 75px; width: auto; margin-top: 5px; margin-bottom: 5px; }
        </style>
        </head>
        <body>
            <h2 style='text-align:center;'>LAPORAN MAINTENANCE LIFT</h2>
            <table class='header-table'>
                <tr><td><b>Nama Gedung:</b> ${report.buildingName}</td><td><b>Jumlah Lantai:</b> ${report.floorCount}</td></tr>
                <tr><td><b>Tanggal:</b> ${report.date}</td><td><b>Merk Lift:</b> ${report.liftBrand}</td></tr>
                <tr><td><b>Alamat:</b> ${report.address}</td><td><b>Tipe Lift:</b> ${report.liftType}</td></tr>
                <tr><td><b>Waktu:</b> ${report.timeRange}</td><td><b>Jumlah Stop:</b> ${report.totalStops}</td></tr>
            </table>
    """.trimIndent())

    fun appendSectionTable(title: String, items: List<ChecklistItem>) {
        htmlBuilder.append("<div class='section-title'>$title</div>")
        htmlBuilder.append("<table><tr><th style='width:5%;'>No</th><th style='width:50%;'>Nama Pekerjaan / Spare Part</th><th style='width:15%;'>Kondisi</th><th>Keterangan</th></tr>")
        for (item in items) {
            val statusStr = if (item.isOk) "OK" else "not OK"
            htmlBuilder.append("<tr><td>${item.no}</td><td>${item.name}</td><td>$statusStr</td><td>${item.notes}</td></tr>")
        }
        htmlBuilder.append("</table>")
    }

    appendSectionTable("RUANG MESIN", report.ruangMesinItems)
    appendSectionTable("ATAS SANGKAR / SHAFT", report.atasSangkarItems)
    appendSectionTable("PIT LIFT", report.pitLiftItems)

    // Dynamic photo grid rendering section
    if (report.attachments.isNotEmpty()) {
        htmlBuilder.append("<div class='section-title' style='page-break-before: always;'>LAMPIRAN FOTO DOKUMENTASI</div>")
        htmlBuilder.append("<table class='photo-grid-table'>")
        for (i in report.attachments.indices step 2) {
            htmlBuilder.append("<tr>")
            
            val leftPhoto = report.attachments[i]
            htmlBuilder.append("<td>")
            htmlBuilder.append("<img src='data:image/jpeg;base64,${leftPhoto.base64Image.trim()}' />")
            htmlBuilder.append("<div class='photo-caption'>${leftPhoto.caption.ifBlank { "Evidence Log" }}</div>")
            htmlBuilder.append("</td>")
            
            if (i + 1 < report.attachments.size) {
                val rightPhoto = report.attachments[i + 1]
                htmlBuilder.append("<td>")
                htmlBuilder.append("<img src='data:image/jpeg;base64,${rightPhoto.base64Image.trim()}' />")
                htmlBuilder.append("<div class='photo-caption'>${rightPhoto.caption.ifBlank { "Evidence Log" }}</div>")
                htmlBuilder.append("</td>")
            } else {
                htmlBuilder.append("<td style='border:none;'></td>")
            }
            htmlBuilder.append("</tr>")
        }
        htmlBuilder.append("</table>")
    }

    // Append authorized legal signature validation row at the base of the doc canvas
    val signatureImgTag = if (report.reporterSignatureBase64.isNotEmpty()) {
        "<img class='sign-img' src='data:image/png;base64,${report.reporterSignatureBase64.trim()}' /><br/>"
    } else {
        "<br/><br/><br/><br/>"
    }

    htmlBuilder.append("""
            <table class='footer-sign-table'>
                <tr>
                    <td>
                        Pelaksana,<br/>
                        $signatureImgTag
                        <b>(${report.reporterName.ifBlank { "PT. PEDEA SUKSES MAKMUR" }})</b>
                    </td>
                    <td>
                        Pengawas,<br/><br/><br/><br/><br/>
                        <b>(Perkumpulan Bakti Insan Olive)</b>
                    </td>
                </tr>
            </table>
        </body>
        </html>
    """.trimIndent())

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = view.createPrintDocumentAdapter("Laporan_Maintenance_${report.buildingName.replace(" ", "_")}")
            val jobName = "Maintenance Report Document"
            
            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        }
    }
    
    webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "utf-8", null)
}
