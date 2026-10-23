package de.myweather

import kotlin.math.roundToInt
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.max

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
        scheduleAlertWorker(this)
        setContent { MyWeatherApp() }
    }
}

fun scheduleAlertWorker(context: Context) {
    val req = PeriodicWorkRequestBuilder<AlertPollWorker>(15, TimeUnit.MINUTES)
        .setInitialDelay(1, TimeUnit.MINUTES)
        .build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork("myweather_alerts", ExistingPeriodicWorkPolicy.UPDATE, req)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyWeatherApp() {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var locationName by remember { mutableStateOf("Köln, Deutschland") }
    var lat by remember { mutableDoubleStateOf(50.9375) }
    var lon by remember { mutableDoubleStateOf(6.9603) }
    var provider by remember { mutableStateOf(Provider.MIX) }
    var enabled by remember { mutableStateOf(Provider.values().filter { it != Provider.MIX }.toSet()) }
    var weights by remember { mutableStateOf(defaultWeights()) }
    var bg by remember { mutableStateOf(Color(0xFF064766)) }
    var forecast by remember { mutableStateOf<Forecast?>(null) }
    var alerts by remember { mutableStateOf<List<WeatherAlert>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var tornado by remember { mutableStateOf(true) }
    var frost by remember { mutableStateOf(true) }
    var ice by remember { mutableStateOf(true) }
    var storm by remember { mutableStateOf(true) }
    var wind by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun persist() {
        context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
            .edit()
            .putFloat("lat", lat.toFloat())
            .putFloat("lon", lon.toFloat())
            .putBoolean("tornado", tornado)
            .apply()
    }
    LaunchedEffect(lat, lon, tornado) { persist() }

    fun reload() {
        loading = true
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                if (provider == Provider.MIX) ensembleForecast(lat, lon, enabled, weights)
                else WeatherEngine.forecast(lat, lon, provider)
            }
            val a = withContext(Dispatchers.IO) {
                runCatching { WeatherEngine.alerts(lat, lon) }.getOrDefault(emptyList())
            }
            forecast = f.copy(location = locationName)
            alerts = a
            loading = false
        }
    }
    LaunchedEffect(provider, enabled, weights, lat, lon) { reload() }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF06658C),
            secondary = Color(0xFFFFC400),
            background = bg,
            surface = Color(0xFF003D58),
            onSurface = Color.White,
            onBackground = Color.White
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (searchOpen) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Ort suchen…") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                        } else {
                            Text("MyWeather", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { tab = 3 }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menü")
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchOpen = !searchOpen }) {
                            Icon(Icons.Default.Search, contentDescription = "Ort suchen")
                        }
                        if (searchOpen) {
                            IconButton(onClick = {
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { WeatherEngine.geocode(query) }
                                    r.firstOrNull()?.let {
                                        locationName = it.first
                                        lat = it.second.first
                                        lon = it.second.second
                                        searchOpen = false
                                        query = ""
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "Suchen")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF06658C),
                        titleContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF00557A)) {
                    NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Home, null) }, label = { Text("Wetter") })
                    NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.Map, null) }, label = { Text("Radar") })
                    NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Warning, null) }, label = { Text("Warnungen") })
                    NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Einstellungen") })
                }
            }
        ) { pad ->
            when (tab) {
                0 -> WeatherHome(Modifier.padding(pad), forecast, loading, bg, alerts)
                1 -> RadarScreen(Modifier.padding(pad), lat, lon)
                2 -> AlertsScreen(Modifier.padding(pad), alerts, tornado)
                else -> SettingsScreen(
                    Modifier.padding(pad),
                    provider,
                    { provider = it },
                    enabled,
                    { enabled = it },
                    weights,
                    { weights = it },
                    tornado,
                    { tornado = it },
                    frost,
                    { frost = it },
                    ice,
                    { ice = it },
                    storm,
                    { storm = it },
                    wind,
                    { wind = it },
                    bg,
                    { bg = it }
                )
            }
        }
    }
}

fun defaultWeights(): Map<Provider, Int> =
    Provider.values().filter { it != Provider.MIX }.associateWith { 5 }.toMutableMap().apply {
        this[Provider.DWD] = 15
        this[Provider.DWD_EU] = 10
        this[Provider.ECMWF] = 15
        this[Provider.AIFS] = 10
        this[Provider.NOAA] = 8
        this[Provider.GEM] = 7
        this[Provider.JMA] = 5
        this[Provider.KMA] = 5
        this[Provider.METNO] = 5
        this[Provider.KNMI] = 5
        this[Provider.UKMO] = 5
        this[Provider.DMI] = 5
        this[Provider.METEOSWISS] = 5
        this[Provider.GEOSPHERE] = 5
    }

suspend fun ensembleForecast(
    lat: Double,
    lon: Double,
    selected: Set<Provider>,
    weights: Map<Provider, Int>
): Forecast {
    val models = selected.filter { it != Provider.MIX }
    if (models.isEmpty()) return WeatherEngine.forecast(lat, lon, Provider.DWD)
    val forecasts = coroutineScope {
        models.map { p ->
            async(Dispatchers.IO) {
                runCatching { p to WeatherEngine.forecast(lat, lon, p) }.getOrNull()
            }
        }.awaitAll().filterNotNull()
    }
    if (forecasts.isEmpty()) return WeatherEngine.forecast(lat, lon, Provider.DWD)
    val totalW = forecasts.sumOf { weights[it.first] ?: 5 }.coerceAtLeast(1)
    fun w(p: Provider) = (weights[p] ?: 5).toDouble() / totalW
    val best = forecasts.maxBy { weights[it.first] ?: 5 }.second
    val current = forecasts.sumOf { w(it.first) * it.second.currentTemp }
    val rain = forecasts.sumOf { w(it.first) * it.second.currentRainProb }.toInt().coerceIn(0, 100)
    val wind = forecasts.sumOf { w(it.first) * it.second.currentWind }
    val hours = best.hours.indices.map { i ->
        val vals = forecasts.mapNotNull { (p, f) -> f.hours.getOrNull(i)?.let { p to it } }
        if (vals.isEmpty()) best.hours[i] else Hour(
            vals.first().second.time,
            vals.sumOf { w(it.first) * it.second.temp },
            vals.sumOf { w(it.first) * it.second.rainProb }.toInt().coerceIn(0, 100),
            vals.sumOf { w(it.first) * it.second.wind },
            vals.maxBy { weights[it.first] ?: 5 }.second.code,
            vals.maxBy { weights[it.first] ?: 5 }.second.isDay
        )
    }
    val days = best.days.indices.map { i ->
        val vals = forecasts.mapNotNull { (p, f) -> f.days.getOrNull(i)?.let { p to it } }
        if (vals.isEmpty()) best.days[i] else Day(
            vals.first().second.date,
            vals.sumOf { w(it.first) * it.second.min },
            vals.sumOf { w(it.first) * it.second.max },
            vals.sumOf { w(it.first) * it.second.rainProb }.toInt().coerceIn(0, 100),
            vals.maxBy { weights[it.first] ?: 5 }.second.code
        )
    }
    return Forecast(best.location, current, best.currentCode, best.currentIsDay, wind, rain, hours, days)
}

@Composable
fun WeatherHome(modifier: Modifier, f: Forecast?, loading: Boolean, bg: Color, alerts: List<WeatherAlert>) {
    LazyColumn(modifier.fillMaxSize().background(bg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            if (loading && f == null) {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (f != null) {
                Column(Modifier.padding(16.dp)) {
                    Text(f.location, fontSize = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${f.currentTemp.roundToInt()}°",
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Light,
                            color = Color(WeatherEngine.tempColorHex(f.currentTemp))
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(WeatherEngine.icon(f.currentCode, !f.currentIsDay), fontSize = 52.sp)
                    }
                    Text("Wind ${f.currentWind.roundToInt()} km/h")
                    Text("Regen ${f.currentRainProb} %")
                    if (f.currentWind > 30) {
                        Text("⚑ Windsack · starker Wind", color = Color(0xFFFFD54F))
                    }
                }
            }
        }
        if (alerts.isNotEmpty()) {
            item {
                Card(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7D2B2B))
                ) {
                    Text("⚠ ${alerts.first().event}", Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            SectionTitle("Heute")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                f?.hours?.take(24)?.forEach { HourCard(it) }
            }
        }
        item {
            SectionTitle("14-Tage-Prognose")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                f?.days?.take(14)?.forEach { DayCard(it) }
            }
        }
        item {
            SectionTitle("Temperaturverlauf")
            f?.let { TemperatureChart(it.days.take(14)) }
        }
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t, Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun HourCard(h: Hour) {
    Card(
        Modifier.padding(end = 8.dp).width(105.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF06415C))
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(h.time.substringAfter("T").take(5))
            Text("${h.temp.roundToInt()}°", fontSize = 24.sp, color = Color(WeatherEngine.tempColorHex(h.temp)))
            Text(WeatherEngine.icon(h.code, !h.isDay), fontSize = 30.sp)
            Text("💧 ${h.rainProb}%")
            Text("${h.wind.roundToInt()} km/h")
            if (h.wind > 30) Text("⚑", color = Color(0xFFFFD54F))
        }
    }
}

@Composable
fun DayCard(d: Day) {
    Card(
        Modifier.padding(end = 8.dp).width(125.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF06415C))
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(d.date)
            Text("${d.max.roundToInt()}°", fontSize = 25.sp, color = Color(WeatherEngine.tempColorHex(d.max)))
            Text("${d.min.roundToInt()}°", color = Color(WeatherEngine.tempColorHex(d.min)))
            Text(WeatherEngine.icon(d.code, false), fontSize = 32.sp)
            Text("💧 ${d.rainProb}%")
        }
    }
}

@Composable
fun TemperatureChart(days: List<Day>) {
    Canvas(Modifier.fillMaxWidth().height(190.dp).padding(16.dp)) {
        if (days.isEmpty()) return@Canvas
        val all = days.flatMap { listOf(it.min, it.max) }
        val lo = all.minOrNull() ?: 0.0
        val hi = all.maxOrNull() ?: 1.0
        val range = max(1.0, hi - lo)
        fun x(i: Int) = i * size.width / max(1, days.size - 1)
        fun y(v: Double) = size.height - ((v - lo) / range).toFloat() * size.height
        val maxP = Path()
        val minP = Path()
        days.forEachIndexed { i, d ->
            val xx = x(i)
            val ym = y(d.max)
            val yn = y(d.min)
            if (i == 0) {
                maxP.moveTo(xx, ym)
                minP.moveTo(xx, yn)
            } else {
                maxP.lineTo(xx, ym)
                minP.lineTo(xx, yn)
            }
            drawCircle(Color.Red, 4f, androidx.compose.ui.geometry.Offset(xx, ym))
            drawCircle(Color.Blue, 4f, androidx.compose.ui.geometry.Offset(xx, yn))
        }
        drawPath(maxP, Color.Red, style = Stroke(5f, cap = StrokeCap.Round))
        drawPath(minP, Color.Blue, style = Stroke(5f, cap = StrokeCap.Round))
    }
}

@Composable
fun RadarScreen(modifier: Modifier, lat: Double, lon: Double) {
    Column(modifier.fillMaxSize().background(Color(0xFF003D58)).padding(10.dp)) {
        Text("OPERA Radar", Modifier.padding(8.dp), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("EUMETNET Open Radar Data · echte Live-Radardaten", Modifier.padding(horizontal = 8.dp), color = Color.LightGray)
        Spacer(Modifier.height(8.dp))
        AndroidViewWeb(WeatherEngine.operaRadarUrl(lat, lon), Modifier.fillMaxWidth().weight(1f))
        Text("OPERA-Daten werden über die öffentliche MeteoGate/ORD-Schnittstelle bezogen. Lizenzhinweise werden in der App angezeigt.", Modifier.padding(8.dp), fontSize = 11.sp, color = Color.LightGray)
    }
}

@Composable
fun AndroidViewWeb(url: String, modifier: Modifier) {
    val context = LocalContext.current
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                webViewClient = WebViewClient()
                loadUrl("https://www.eumetnet.eu/observations/opera-radar-animation/")
            }
        }
    )
}

@Composable
fun AlertsScreen(modifier: Modifier, alerts: List<WeatherAlert>, tornado: Boolean) {
    LazyColumn(modifier.fillMaxSize().background(Color(0xFF003D58)), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Warnungen", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
        }
        if (tornado) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF8B3D12)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Tornadobildung möglich", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Nicht amtliche warnung", fontSize = 12.sp, color = Color(0xFFFFCC80))
                        Text("Ermittelt aus DWD-Mesocyclone-Detection-Daten.")
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        if (alerts.isEmpty()) {
            item { Text("Keine aktuellen DWD-Warnungen im Feed gefunden.") }
        }
        items(alerts) { a ->
            Card(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF06415C))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("⚠ ${a.event}", fontWeight = FontWeight.Bold)
                    Text(a.description)
                    Text("Amtliche Warnung · DWD", fontSize = 12.sp, color = Color(0xFFB8E4FF))
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier,
    provider: Provider,
    onProvider: (Provider) -> Unit,
    enabled: Set<Provider>,
    onEnabled: (Set<Provider>) -> Unit,
    weights: Map<Provider, Int>,
    onWeights: (Map<Provider, Int>) -> Unit,
    tornado: Boolean,
    setTornado: (Boolean) -> Unit,
    frost: Boolean,
    setFrost: (Boolean) -> Unit,
    ice: Boolean,
    setIce: (Boolean) -> Unit,
    storm: Boolean,
    setStorm: (Boolean) -> Unit,
    wind: Boolean,
    setWind: (Boolean) -> Unit,
    bg: Color,
    setBg: (Color) -> Unit
) {
    val providersWithoutMix = remember { Provider.values().filter { it != Provider.MIX } }

    LazyColumn(modifier = modifier.fillMaxSize().background(bg), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Einstellungen", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Vorhersagequelle", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        items(providersWithoutMix) { p ->
            if (p in enabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onProvider(p) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (p == provider),
                        onClick = { onProvider(p) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(p.label)
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Modelle im Mischmodus", fontWeight = FontWeight.SemiBold)
        }
        items(providersWithoutMix) { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = (p in enabled),
                    onCheckedChange = { v ->
                        onEnabled(if (v) enabled + p else enabled - p)
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(p.label)
                Spacer(Modifier.weight(1f))
                Text("${weights[p] ?: 5}%")
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Gewichtung (jedes aktive Modell mindestens 5 %)", fontWeight = FontWeight.SemiBold)
        }
        items(providersWithoutMix) { p ->
            if (p in enabled) {
                val currentWeight = weights[p] ?: 5
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(p.label)
                            Text("$currentWeight%")
                        }
                        Slider(
                            value = currentWeight.toFloat(),
                            onValueChange = { newWeight ->
                                onWeights(weights + (p to newWeight.toInt().coerceIn(5, 70)))
                            },
                            valueRange = 5f..70f,
                            steps = 64
                        )
                    }
                }
            }
        }
        item {
            Divider(Modifier.padding(vertical = 12.dp))
            Text("Warnungen", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        item { ToggleRow("Tornado – nicht amtlich", tornado, setTornado) }
        item { ToggleRow("Frost", frost, setFrost) }
        item { ToggleRow("Glätte", ice, setIce) }
        item { ToggleRow("Gewitter", storm, setStorm) }
        item { ToggleRow("Sturm / Wind", wind, setWind) }
        item {
            Divider(Modifier.padding(vertical = 12.dp))
            Text("Design", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Color(0xFF064766),
                    Color(0xFF1C2733),
                    Color(0xFF244D2C),
                    Color(0xFF4B274E),
                    Color(0xFF263238)
                ).forEach { c ->
                    Box(
                        Modifier.size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c)
                            .clickable { setBg(c) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "DWD-Warnungen und die nicht amtliche Rotationswarnung werden im Hintergrund regelmäßig geprüft und als Benachrichtigung ausgegeben.",
                fontSize = 12.sp,
                color = Color.LightGray
            )
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
