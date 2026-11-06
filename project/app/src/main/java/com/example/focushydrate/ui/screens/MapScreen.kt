package com.example.focushydrate.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.focushydrate.data.*
import com.example.focushydrate.R
import androidx.compose.ui.res.stringResource
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapScreen(profile: AccountProfile) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val store = remember { WaterSpotStore(context.applicationContext) }
    var dataAttempt by remember { mutableIntStateOf(0) }
    val initial = remember(dataAttempt) { runCatching { store.load() to store.loadFeedback() } }
    if (initial.isFailure) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.error_load))
            Button(onClick = { dataAttempt++ }) { Text(stringResource(R.string.retry)) }
        }
        return
    }
    var spots by remember { mutableStateOf(initial.getOrNull()?.first ?: emptyList()) }
    var reports by remember { mutableStateOf(initial.getOrNull()?.second ?: emptyList()) }
    var feedbackSpot by remember { mutableStateOf<WaterSpot?>(null) }
    val translations = mapTranslations(resources)
    var message by remember { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var selectedPoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var spotToDelete by remember { mutableStateOf<WaterSpot?>(null) }
    var pointName by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var mapState by remember { mutableStateOf("loading") }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun save(next: List<WaterSpot>, onSaved: () -> Unit) {
        saving = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { store.save(next) }.getOrDefault(false) }
            saving = false
            if (saved) { spots = next; message = null; onSaved() }
            else message = resources.getString(R.string.error_storage)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.map_title), Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.point_count, spots.size), style = MaterialTheme.typography.labelMedium)
        }
        Text(stringResource(R.string.demo_note),
            Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to stringResource(R.string.filter_all), "drinkable" to stringResource(R.string.filter_drinkable), "not_drinkable" to stringResource(R.string.filter_not_drinkable)).forEach { (value, title) ->
                FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(title) })
            }
        }
        message?.let { text ->
            Text(text, Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (mapState == "error") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.map_error), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { ready = false; mapState = "loading"; reload++ }) { Text(stringResource(R.string.retry)) }
            }
        }
        if (adding) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.choose_point), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { adding = false; selectedPoint = null }) { Text(stringResource(R.string.cancel)) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(reload) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.userAgentString = settings.userAgentString + " FocusHydrate/1.0 (MobileSystemProjectTeamB)"
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val url = request.url
                                    if (!request.isForMainFrame) return true
                                    if (url.scheme == "hydrotracker") {
                                        when (url.host) {
                                            "ready" -> ready = true
                                            "loaded" -> mapState = "loaded"
                                            "error" -> mapState = "error"
                                            "add" -> {
                                                val lat = url.getQueryParameter("latitude")?.toDoubleOrNull()
                                                val lon = url.getQueryParameter("longitude")?.toDoubleOrNull()
                                                if (adding && lat != null && lon != null && validCoordinates(lat, lon)) {
                                                    pointName = ""
                                                    selectedPoint = lat to lon
                                                }
                                            }
                                            "delete" -> spotToDelete = spots.find { it.id == url.getQueryParameter("id") && (it.ownerId == profile.id || it.ownerId.isBlank()) }
                                            "feedback" -> { message = null; feedbackSpot = spots.find { it.id == url.getQueryParameter("id") } }
                                        }
                                        return true
                                    }
                                    if (url.scheme == "https" && url.host == "www.openstreetmap.org") {
                                        runCatching { viewContext.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                                    }
                                    return true
                                }
                            }
                            loadUrl("file:///android_asset/map/index.html")
                        }
                    },
                    update = { view ->
                        if (ready) view.evaluateJavascript(
                            "window.updateMap && window.updateMap(${WaterSpotStore.toJson(spots, reports)}, '$filter', $adding, $translations, ${JSONObject.quote(profile.id)});", null)
                    },
                    onRelease = { view -> view.stopLoading(); view.destroy() }
                )
            }
            if (mapState == "loading") {
                Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.map_loading), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (!adding) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true }, expanded = true,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_point)) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 40.dp),
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Text(stringResource(R.string.map_legend),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
    }

    selectedPoint?.let { point ->
        AlertDialog(
            onDismissRequest = { if (!saving) selectedPoint = null },
            title = { Text(stringResource(R.string.new_point)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.point_body))
                    OutlinedTextField(value = pointName, onValueChange = { if (it.length <= 80) pointName = it },
                        enabled = !saving, label = { Text(stringResource(R.string.point_name)) }, singleLine = true)
                    Text(java.lang.String.format(java.util.Locale.US, "%.5f, %.5f", point.first, point.second),
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = pointName.isNotBlank() && !saving, onClick = {
                    val spot = WaterSpot(UUID.randomUUID().toString(), pointName.trim(), point.first, point.second, profile.id)
                    save(spots + spot) { selectedPoint = null; adding = false }
                }) { Text(stringResource(if (saving) R.string.saving else R.string.save)) }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { selectedPoint = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    spotToDelete?.let { spot ->
        AlertDialog(onDismissRequest = { if (!saving) spotToDelete = null },
            title = { Text(stringResource(R.string.delete_point)) }, text = { Text(spot.name) },
            confirmButton = { TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    val saved = withContext(Dispatchers.IO) { runCatching { store.deleteSpot(spot.id) }.getOrDefault(false) }
                    saving = false
                    if (saved) {
                        spots = spots.filterNot { it.id == spot.id }; reports = reports.filterNot { it.spotId == spot.id }; spotToDelete = null
                    } else message = resources.getString(R.string.error_storage)
                }
            }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(enabled = !saving, onClick = { spotToDelete = null }) { Text(stringResource(R.string.cancel)) } })
    }
    feedbackSpot?.let { spot ->
        FeedbackDialog(spot, reports, profile, saving, message,
            onSave = { status, note ->
                saving = true; message = null
                val next = replaceFeedback(reports, SpotFeedback(spot.id, profile.id, profile.username, status, note, System.currentTimeMillis()))
                scope.launch {
                    val saved = withContext(Dispatchers.IO) { runCatching { store.saveFeedback(next) }.getOrDefault(false) }
                    saving = false
                    if (saved) { reports = next; feedbackSpot = null; message = resources.getString(R.string.feedback_saved) }
                    else message = resources.getString(R.string.error_storage)
                }
            }, onDismiss = { feedbackSpot = null; message = null })
    }

}

private fun mapTranslations(resources: android.content.res.Resources): String = JSONObject().apply {
    put("zoomIn", resources.getString(R.string.zoom_in))
    put("zoomOut", resources.getString(R.string.zoom_out))
    put("language", resources.configuration.locales[0].language)
    put("mapLabel", resources.getString(R.string.map_title))
    put("demoArea", resources.getString(R.string.demo_area))
    put("demoGreen", resources.getString(R.string.demo_green))
    put("demoRed", resources.getString(R.string.demo_red))
    put("demoPopup", resources.getString(R.string.demo_popup))
    put("pointNote", resources.getString(R.string.local_point_note))
    put("remove", resources.getString(R.string.delete_point_button))
    put("feedback", resources.getString(R.string.feedback))
    put("WORKING", resources.getString(R.string.feedback_working))
    put("NOT_WORKING", resources.getString(R.string.feedback_broken))
    put("NO_WATER", resources.getString(R.string.feedback_no_water))
    put("WRONG_LOCATION", resources.getString(R.string.feedback_wrong_location))
}.toString()
