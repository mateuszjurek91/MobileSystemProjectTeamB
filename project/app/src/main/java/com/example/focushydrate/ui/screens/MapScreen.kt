package com.example.focushydrate.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.focushydrate.R
import com.example.focushydrate.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapScreen(profile: AccountProfile) {
    val resources = LocalResources.current
    val context = LocalContext.current
    val connectivity = remember(context) { context.getSystemService(ConnectivityManager::class.java) }
    fun hasInternet(): Boolean =
        connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

    val store = remember { FirestoreWaterSpotStore() }
    var dataAttempt by remember { mutableIntStateOf(0) }
    var spotsReady by remember { mutableStateOf(false) }
    var feedbackReady by remember { mutableStateOf(false) }
    val loaded = spotsReady && feedbackReady
    var loadError by remember { mutableStateOf(false) }
    var spots by remember { mutableStateOf(emptyList<WaterSpot>()) }
    var reports by remember { mutableStateOf(emptyList<SpotFeedback>()) }

    LaunchedEffect(profile.id, dataAttempt) {
        loadError = false
        try {
            coroutineScope {
                launch {
                    store.observeSpots().collect { points ->
                        spots = points
                        spotsReady = true
                    }
                }
                launch {
                    store.observeFeedback().collect { feedback ->
                        reports = feedback
                        feedbackReady = true
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = true
        }
    }

    var feedbackSpot by remember { mutableStateOf<WaterSpot?>(null) }
    val translations = remember(resources, resources.configuration.locales[0]) { mapTranslations(resources) }
    var message by remember { mutableStateOf<String?>(null) }
    var demo by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var selectedPoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var spotToDelete by remember { mutableStateOf<WaterSpot?>(null) }
    var pointName by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var pendingVotes by remember { mutableStateOf(emptySet<Pair<String, String>>()) }
    var ready by remember { mutableStateOf(false) }
    var mapState by remember { mutableStateOf("loading") }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var online by remember(connectivity) { mutableStateOf(hasInternet()) }
    DisposableEffect(connectivity) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                scope.launch { online = hasInternet() }
            }

            override fun onLost(network: Network) {
                scope.launch { online = hasInternet() }
            }
        }
        connectivity?.registerDefaultNetworkCallback(callback)
        onDispose { connectivity?.unregisterNetworkCallback(callback) }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var pointsJson by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(spotsReady, feedbackReady, spots, reports) {
        if (spotsReady || feedbackReady) {
            pointsJson = withContext(Dispatchers.Default) { WaterSpotStore.toJson(spots, reports) }
        }
    }

    fun requireInternet(): Boolean {
        message = if (hasInternet()) null else resources.getString(R.string.error_offline)
        return message == null
    }

    suspend fun writeToMap(write: suspend () -> Unit): Boolean {
        return try {
            write()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = resources.getString(if (hasInternet()) R.string.error_storage else R.string.error_offline)
            false
        }
    }

    val filterHidesUserSpots = demo && filter != "all" && !adding
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.map_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.point_count, if (filterHidesUserSpots) 0 else spots.size), style = MaterialTheme.typography.labelMedium)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = demo,
                onClick = { demo = !demo },
                label = { Text(stringResource(R.string.demo_areas)) }
            )
            if (demo) {
                listOf(
                    "all" to R.string.filter_all,
                    "drinkable" to R.string.filter_drinkable,
                    "not_drinkable" to R.string.filter_not_drinkable
                ).forEach { (value, title) ->
                    FilterChip(
                        selected = filter == value,
                        onClick = { filter = value },
                        label = { Text(stringResource(title)) }
                    )
                }
            }
        }
        if (demo) {
            Text(
                stringResource(R.string.demo_note),
                Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (loaded && filterHidesUserSpots) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.map_no_filtered_points),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { filter = "all" }) {
                    Text(stringResource(R.string.map_show_all))
                }
            }
        } else if (loaded && spots.isEmpty() && !adding) {
            Text(
                stringResource(R.string.map_no_points),
                Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        message?.let { text ->
            Text(
                text,
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (loadError) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.error_load),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { dataAttempt++ }) { Text(stringResource(R.string.retry)) }
            }
        }
        if (mapState == "error") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.map_error), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { ready = false; mapState = "loading"; reload++ }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(reload) {
                val generation = reload
                AndroidView(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).clip(MaterialTheme.shapes.large),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.cacheMode = if (online) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.userAgentString = settings.userAgentString + " FocusHydrate/1.0 (MobileSystemProjectTeamB)"
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    if (generation != reload) return true
                                    val url = request.url
                                    if (!request.isForMainFrame) return true
                                    if (url.scheme == "focushydrate") {
                                        when (url.host) {
                                            "ready" -> ready = true
                                            "loaded" -> mapState = "loaded"
                                            "error" -> mapState = "error"
                                            "add" -> {
                                                val lat = url.getQueryParameter("latitude")?.toDoubleOrNull()
                                                val lon = url.getQueryParameter("longitude")?.toDoubleOrNull()
                                                if (adding && lat != null && lon != null && validCoordinates(lat, lon)) {
                                                    if (!requireInternet()) {
                                                        adding = false
                                                    } else if (hasNearbySpot(spots, lat, lon)) {
                                                        message = resources.getString(R.string.point_too_close)
                                                    } else {
                                                        message = null
                                                        pointName = ""
                                                        selectedPoint = lat to lon
                                                    }
                                                }
                                            }
                                            "delete" -> {
                                                message = null
                                                spotToDelete = spots.find {
                                                    it.id == url.getQueryParameter("id") && (it.ownerId == profile.id || it.ownerId.isBlank())
                                                }
                                            }
                                            "feedback" -> {
                                                message = null
                                                feedbackSpot = spots.find { it.id == url.getQueryParameter("id") }
                                            }
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
                        view.settings.cacheMode = if (online) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
                        val points = pointsJson?.let { if (filterHidesUserSpots) "[]" else it }
                        if (ready) {
                            val previous = view.tag as? MapRenderState
                            val areaFilter = if (demo) filter else "hidden"
                            val state = MapRenderState(points, translations, areaFilter, adding)
                            if (previous?.filter != areaFilter || previous.adding != adding || previous.translations != translations) {
                                view.evaluateJavascript("window.updateControls('$areaFilter', $adding, $translations)", null)
                            }
                            if (points != null && (previous == null || previous.points != points || previous.translations != translations)) {
                                view.evaluateJavascript("window.updateSpots($points, $translations, ${JSONObject.quote(profile.id)})", null)
                            }
                            view.tag = state
                        }
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
                    onClick = {
                        if (requireInternet()) {
                            if (loaded) adding = true
                            else message = resources.getString(if (loadError) R.string.error_load else R.string.map_loading)
                        }
                    },
                    expanded = true,
                    shape = MaterialTheme.shapes.large,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_point)) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 40.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
            if (adding) {
                ElevatedCard(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Text(
                            text = stringResource(R.string.choose_point),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        TextButton(onClick = { adding = false; selectedPoint = null; message = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 104.dp)
            ) { data ->
                Snackbar(
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Text(
            stringResource(R.string.map_legend),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }

    selectedPoint?.let { point ->
        AlertDialog(
            onDismissRequest = { if (!saving) selectedPoint = null },
            title = { Text(stringResource(R.string.new_point)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.point_body))
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(
                        value = pointName,
                        onValueChange = { if (it.length <= 80) pointName = it },
                        colors = inputColors(),
                        enabled = !saving,
                        label = { Text(stringResource(R.string.point_name)) },
                        singleLine = true
                    )
                    Text(
                        java.lang.String.format(java.util.Locale.US, "%.5f, %.5f", point.first, point.second),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pointName.isNotBlank() && !saving,
                    onClick = {
                        if (!requireInternet()) return@TextButton
                        val spot = WaterSpot(UUID.randomUUID().toString(), pointName.trim(), point.first, point.second, profile.id)
                        saving = true
                        scope.launch {
                            val ok = writeToMap { store.addSpot(spot) }
                            saving = false
                            if (ok) {
                                message = null
                                selectedPoint = null
                                adding = false
                            }
                        }
                    }
                ) { Text(stringResource(if (saving) R.string.saving else R.string.save)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { selectedPoint = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    spotToDelete?.let { spot ->
        AlertDialog(
            onDismissRequest = { if (!saving) spotToDelete = null },
            title = { Text(stringResource(R.string.delete_point)) },
            text = {
                Column {
                    Text(spot.name)
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        if (!requireInternet()) return@TextButton
                        saving = true
                        scope.launch {
                            val ok = writeToMap { store.deleteSpot(spot.id) }
                            saving = false
                            if (ok) spotToDelete = null
                        }
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { spotToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    feedbackSpot?.let { spot ->
        FeedbackDialog(
            spot, reports, profile, saving, message, pendingVotes,
            onSave = save@{ status, note ->
                if (!requireInternet()) return@save
                saving = true
                val report = SpotFeedback(spot.id, profile.id, profile.username, status, note, System.currentTimeMillis())
                scope.launch {
                    val ok = writeToMap { store.saveFeedback(report) }
                    saving = false
                    if (ok) {
                        feedbackSpot = null
                        snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.feedback_saved),
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            },
            onVote = { report, vote ->
                val key = report.spotId to report.authorId
                if (key !in pendingVotes && requireInternet()) {
                    pendingVotes = pendingVotes + key
                    scope.launch {
                        try {
                            writeToMap { store.toggleFeedbackVote(report, profile.id, vote) }
                        } finally {
                            pendingVotes = pendingVotes - key
                        }
                    }
                }
            },
            onDelete = delete@{
                if (!requireInternet()) return@delete
                saving = true
                scope.launch {
                    try {
                        if (writeToMap { store.deleteFeedback(spot.id, profile.id) }) {
                            feedbackSpot = null
                            message = resources.getString(R.string.feedback_deleted)
                        }
                    } finally {
                        saving = false
                    }
                }
            },
            onDismiss = { feedbackSpot = null; message = null }
        )
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
    put("pointCount", resources.getString(R.string.point_count))
    put("remove", resources.getString(R.string.delete_point_button))
    put("feedback", resources.getString(R.string.feedback))
    put("WORKING", resources.getString(R.string.feedback_working))
    put("NOT_WORKING", resources.getString(R.string.feedback_broken))
    put("NO_WATER", resources.getString(R.string.feedback_no_water))
    put("WRONG_LOCATION", resources.getString(R.string.feedback_wrong_location))
}.toString()

private data class MapRenderState(
    val points: String?,
    val translations: String,
    val filter: String,
    val adding: Boolean
)
