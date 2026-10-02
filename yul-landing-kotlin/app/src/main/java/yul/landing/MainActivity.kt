package yul.landing

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

private val Asphalt = Color(0xFF1D242C)
private val Panel = Color(0xFF262F38)
private val Line = Color(0xFF3A4551)
private val PaintColor = Color(0xFFF3F4EF)
private val Dim = Color(0xFF97A3AE)
private val Taxi = Color(0xFFF2B705)
private val Alert = Color(0xFFEE6A5C)
private val SignBg = Color(0xFF0D1013)
private val SignFg = Color(0xFFFFC933)

data class Runway(val name: String, val id: String, val pair: String, val heading: Double)
private val RUNWAYS = listOf(
    Runway("06 (06L/06R)", "06", "06L / 06R", 46.0),
    Runway("24 (24L/24R)", "24", "24L / 24R", 226.0)
)
private const val AIRPORT_LAT = 45.4706
private const val AIRPORT_LON = -73.7408
private const val AIRPORT_ELEV_M = 36.0
private const val MAX_AGL_M = 1300.0
private const val HDG_TOL = 15.0
private const val CL_TOL = 25.0

data class Aircraft(
    val callsign: String, val runway: Runway, val heading: Int,
    val altFt: Int, val distKm: Double, val speedKt: Int?
)
data class Metar(val dir: Int?, val speed: Int?, val raw: String, val obsMs: Long?)
data class ForecastRow(
    val date: String, val clock: String, val isNow: Boolean, val code: Int,
    val isDay: Int, val temp: Double?, val dir: Double?, val speed: Double?,
    val gust: Double?, val pop: Int?, val visKm: Double?, val fav: Runway?
)

data class UiState(
    val lang: String = "en",
    val arrivals: List<Aircraft>? = null,
    val trafficError: String? = null,
    val updated: Long? = null,
    val metar: Metar? = null,
    val metarLoaded: Boolean = false,
    val metarError: Boolean = false,
    val forecast: List<ForecastRow>? = null,
    val forecastError: Boolean = false,
    val forecastUpdated: Long? = null,
    val intervalSec: Int = 30,
    val radiusKm: Int = 25,
    val autoRefresh: Boolean = false,
    val busy: Boolean = false,
    val nextAt: Long = System.currentTimeMillis(),
    val status: String = ""
)

private class YulRepository {
    private fun get(url: String): String = HttpURLConnection::class.java.let {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.requestMethod = "GET"
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            val code = c.responseCode
            if (code == 429) throw ApiException("RATE")
            if (code !in 200..299) throw ApiException("HTTP:$code")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    suspend fun arrivals(radiusKm: Int): List<Aircraft> = withContext(Dispatchers.IO) {
        val dLat = radiusKm / 111.0
        val dLon = radiusKm / (111.0 * cos(Math.toRadians(AIRPORT_LAT)))
        val url = "https://opensky-network.org/api/states/all?" +
            "lamin=${"%.4f".format(Locale.US, AIRPORT_LAT - dLat)}&" +
            "lamax=${"%.4f".format(Locale.US, AIRPORT_LAT + dLat)}&" +
            "lomin=${"%.4f".format(Locale.US, AIRPORT_LON - dLon)}&" +
            "lomax=${"%.4f".format(Locale.US, AIRPORT_LON + dLon)}"
        val root = JSONObject(get(url))
        val states = root.optJSONArray("states") ?: JSONArray()
        val out = mutableListOf<Aircraft>()
        for (i in 0 until states.length()) {
            val s = states.optJSONArray(i) ?: continue
            val lon = s.optDoubleOrNull(5) ?: continue
            val lat = s.optDoubleOrNull(6) ?: continue
            val track = s.optDoubleOrNull(10) ?: continue
            if (s.optBoolean(8, false)) continue
            val alt = s.optDoubleOrNull(13) ?: s.optDoubleOrNull(7) ?: continue
            val agl = alt - AIRPORT_ELEV_M
            if (agl > MAX_AGL_M) continue
            val vr = s.optDoubleOrNull(11)
            if (vr != null && vr > -0.5) continue
            val dist = distanceKm(lat, lon)
            if (dist > radiusKm) continue
            val rwy = closestRunway(track)
            if (angleDiff(track, rwy.heading) > HDG_TOL) continue
            val brg = bearingDeg(lat, lon)
            if (angleDiff(brg, (rwy.heading + 180) % 360) > CL_TOL) continue
            val call = s.optString(1).trim().ifEmpty { s.optString(0, "?") }
            out += Aircraft(call, rwy, track.roundToInt(), (agl * 3.28084).roundToInt(), dist,
                s.optDoubleOrNull(9)?.let { (it * 1.94384).roundToInt() })
        }
        out.sortedBy { it.distKm }
    }

    suspend fun metar(): Metar? = withContext(Dispatchers.IO) {
        val a = JSONArray(get("https://aviationweather.gov/api/data/metar?ids=CYUL&format=json"))
        if (a.length() == 0) return@withContext null
        val m = a.getJSONObject(0)
        Metar(m.optIntOrNull("wdir"), m.optIntOrNull("wspd"), m.optString("rawOb"),
            if (m.has("obsTime")) m.optLong("obsTime") * 1000 else null)
    }

    suspend fun forecast(): List<ForecastRow> = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$AIRPORT_LAT&longitude=$AIRPORT_LON" +
            "&hourly=is_day,temperature_2m,precipitation_probability,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility" +
            "&wind_speed_unit=kn&timezone=America%2FToronto&forecast_days=2"
        val root = JSONObject(get(url))
        val h = root.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val offset = root.optInt("utc_offset_seconds", -14400)
        val nowShifted = System.currentTimeMillis() + offset * 1000L
        var start = 0
        for (i in 0 until times.length()) {
            val ts = times.getString(i)
            if (LocalDateTime.parse(ts).toInstant(ZoneOffset.UTC).toEpochMilli() + 3_600_000 > nowShifted) { start = i; break }
        }
        val rows = mutableListOf<ForecastRow>()
        for (i in start until min(start + 12, times.length())) {
            val ts = times.getString(i)
            val dir = h.optJSONArray("wind_direction_10m")?.optDoubleOrNull(i)
            val spd = h.optJSONArray("wind_speed_10m")?.optDoubleOrNull(i)
            rows += ForecastRow(ts, ts.substring(11, 16), i == start,
                h.getJSONArray("weather_code").optInt(i),
                h.optJSONArray("is_day")?.optInt(i, 1) ?: 1,
                h.optJSONArray("temperature_2m")?.optDoubleOrNull(i), dir, spd,
                h.optJSONArray("wind_gusts_10m")?.optDoubleOrNull(i),
                h.optJSONArray("precipitation_probability")?.optIntOrNull(i),
                h.optJSONArray("visibility")?.optDoubleOrNull(i)?.div(1000.0),
                favouredRunway(dir, spd))
        }
        rows
    }
}

private class ApiException(val kind: String) : Exception(kind)
private fun JSONArray.optDoubleOrNull(i: Int): Double? = if (isNull(i)) null else optDouble(i).takeUnless { it.isNaN() }
private fun JSONObject.optIntOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)
private fun JSONArray.optIntOrNull(i: Int): Int? = if (isNull(i)) null else optInt(i)
private fun rad(d: Double) = Math.toRadians(d)
private fun angleDiff(a: Double, b: Double) = abs(((a - b + 540) % 360) - 180)
private fun distanceKm(lat2: Double, lon2: Double): Double {
    val dLat = rad(lat2 - AIRPORT_LAT); val dLon = rad(lon2 - AIRPORT_LON)
    val a = sin(dLat / 2).pow(2) + cos(rad(AIRPORT_LAT)) * cos(rad(lat2)) * sin(dLon / 2).pow(2)
    return 2 * 6371.0 * atan2(sqrt(a), sqrt(1 - a))
}
private fun bearingDeg(lat2: Double, lon2: Double): Double {
    val p1 = rad(AIRPORT_LAT); val p2 = rad(lat2); val dl = rad(lon2 - AIRPORT_LON)
    val y = sin(dl) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}
private fun closestRunway(track: Double) = RUNWAYS.minBy { angleDiff(track, it.heading) }
private fun favouredRunway(dir: Double?, speed: Double?): Runway? =
    if (dir == null || speed == null || speed < 3) null else RUNWAYS.minBy { angleDiff(dir, it.heading) }

internal class YulViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = YulRepository()
    private val prefs = app.getSharedPreferences("yulLanding", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UiState(
        lang = prefs.getString("lang", null) ?: if (Locale.getDefault().language == "fr") "fr" else "en",
        intervalSec = prefs.getInt("interval", 30), radiusKm = prefs.getInt("radius", 25),
        autoRefresh = prefs.getBoolean("auto", false)
    ))
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var forecastAt = 0L

    init { refresh(); ticker() }
    private fun ticker() = viewModelScope.launch {
        while (isActive) {
                delay(Duration.parse("0.5s"))
            val s = _state.value
            if (s.autoRefresh && !s.busy && System.currentTimeMillis() >= s.nextAt) refresh()
            else if (s.autoRefresh) _state.value = s.copy(status = "next:${max(0, ceil((s.nextAt - System.currentTimeMillis()) / 1000.0).toInt())}")
        }
    }
    fun setLang(v: String) { prefs.edit { putString("lang", v) }; _state.value = _state.value.copy(lang = v) }
    fun setInterval(v: Int) { prefs.edit { putInt("interval", v) }; _state.value = _state.value.copy(intervalSec = v, nextAt = System.currentTimeMillis() + v * 1000L) }
    fun setRadius(v: Int) { prefs.edit { putInt("radius", v) }; refresh() }
    fun setAuto(v: Boolean) { prefs.edit { putBoolean("auto", v) }; _state.value = _state.value.copy(autoRefresh = v, nextAt = System.currentTimeMillis() + _state.value.intervalSec * 1000L) }

    fun refresh() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, trafficError = null)
            val radius = _state.value.radiusKm
            val arrivals = runCatching { repo.arrivals(radius) }
            val metar = runCatching { repo.metar() }
            val needForecast = System.currentTimeMillis() - forecastAt > 15 * 60 * 1000
            val forecast = if (needForecast || _state.value.forecast == null) runCatching { repo.forecast() } else null
            if (forecast != null && forecast.isSuccess) forecastAt = System.currentTimeMillis()
            _state.value = _state.value.copy(
                busy = false,
                arrivals = arrivals.getOrNull() ?: _state.value.arrivals,
                trafficError = arrivals.exceptionOrNull()?.let { errorText(it) },
                updated = if (arrivals.isSuccess) System.currentTimeMillis() else _state.value.updated,
                metar = metar.getOrNull(), metarLoaded = true, metarError = metar.isFailure,
                forecast = forecast?.getOrNull() ?: _state.value.forecast,
                forecastError = forecast?.isFailure ?: _state.value.forecastError,
                forecastUpdated = if (forecast?.isSuccess == true) System.currentTimeMillis() else _state.value.forecastUpdated,
                nextAt = System.currentTimeMillis() + _state.value.intervalSec * 1000L,
                status = ""
            )
        }
    }
    private fun errorText(e: Throwable): String = when ((e as? ApiException)?.kind) {
        "RATE" -> "rate"
        else -> "network"
    }
}

private fun t(lang: String, key: String, vararg p: Pair<String, String>): String {
    val en = mapOf(
        "sub" to "Runway in use, read from live aircraft on final approach", "landing" to "Landing direction",
        "arrivals" to "Aircraft on final", "wind" to "Wind at the airport (CYUL observation)",
        "forecast" to "Airport forecast, next 12 hours", "waiting" to "Waiting for first update", "checking" to "Checking",
        "updated" to "Updated {time} (Montréal time)",
        "unavailable" to "Unavailable", "no_arrivals" to "No arrivals", "in_use" to "Runway in use", "favours" to "Wind favours",
        "note_none_wind" to "No aircraft on final right now. The wind favours runway {id}, which would mean landing toward the {dir}.",
        "note_none" to "No aircraft on final right now. This can be a quiet spell or a gap in ADS-B coverage.",
        "note_mixed" to "Mixed runway usage: {list}.",
        "note_switch" to "The wind currently favours runway {id}, so traffic may switch soon.",
        "loading" to "Loading.", "noneDetected" to "No aircraft detected on final approach.",
        "trafficUnavailable" to "Live traffic is unavailable right now.",
        "windUnavailable" to "Wind is unavailable right now.", "noMetar" to "No METAR reported.",
        "wind_fav" to "From {dir}° at {spd} kt, which favours runway {rwy}.",
        "calm" to "Wind is calm or variable, so runway choice is not driven by wind.",
        "obs_at" to "Observed {time} at CYUL. ", "obs_none" to "CYUL. ",
        "fcUnavailable" to "Forecast is unavailable right now.", "fcNone" to "No forecast data.",
        "fc_calm" to "Light winds expected: runway choice may not follow the wind.",
        "fc_switch_now" to "Forecast wind favours runway {a} now and is expected to switch to runway {b} around {time}.",
        "fc_switch_later" to "Forecast wind favours runway {a} once the wind picks up and is expected to switch to runway {b} around {time}.",
        "fc_steady_now" to "Forecast wind favours runway {a} now and through the next {n} hours.",
        "fc_steady_later" to "Forecast wind favours runway {a} once the wind picks up, through the next {n} hours.",
        "fc_source" to "Forecast for the airport itself ({lat}, {lon}), from Open-Meteo. Hours are Montréal time ({tz}). Updated {time}.",
        "now" to "Now", "lightWind" to "Light wind",
        "heading" to "Heading", "alt" to "Alt (ft)", "dist" to "Dist (km)", "speed" to "Speed (kt)", "gusts" to "gusts",
        "refresh" to "Refresh now", "auto" to "Auto-refresh", "radius" to "Search radius", "refreshEvery" to "Refresh every",
        "statusNext" to "Next update in {s} s", "updating" to "Updating…",
        "err_network" to "Could not reach OpenSky. Check your connection; if you have refreshed a lot, the daily limit may be used up.",
        "err_rate" to "OpenSky rate limit reached. Choose a longer refresh interval and try again later.",
        "err_generic" to "The live traffic request failed.",
        "fine" to "Live positions come from the OpenSky Network's free ADS-B feed. Anonymous access allows roughly 400 requests a day, so a longer refresh keeps the page working all day. Parallel runways (06L/06R and 24L/24R) share a heading and are reported together. Updates pause while this tab is hidden. All weather is for the airport itself (CYUL observation and a forecast for its coordinates), never for the visitor's location, and every time shown is Montréal time. The forecast comes from Open-Meteo model data and, while auto-refresh is on, refreshes every 15 minutes. Forecast wind only shows which runway it would favour; the runway actually used is chosen by air traffic control.",
        "dirN" to "North", "dirNE" to "Northeast", "dirE" to "East", "dirSE" to "Southeast", "dirS" to "South", "dirSW" to "Southwest", "dirW" to "West", "dirNW" to "Northwest"
    )
    val fr = mapOf(
        "sub" to "Piste en service, déduite des avions en finale en temps réel", "landing" to "Direction d'atterrissage",
        "arrivals" to "Avions en finale", "wind" to "Vent à l'aéroport (observation CYUL)", "forecast" to "Prévisions à l'aéroport, 12 prochaines heures",
        "waiting" to "En attente de la première mise à jour", "checking" to "Vérification",
        "updated" to "Mis à jour à {time} (heure de Montréal)",
        "unavailable" to "Indisponible", "no_arrivals" to "Aucune arrivée", "in_use" to "Piste en service", "favours" to "Vent favorable",
        "note_none_wind" to "Aucun avion en finale pour le moment. Le vent favorise la piste {id}, ce qui signifierait un atterrissage vers le {dir}.",
        "note_none" to "Aucun avion en finale pour le moment. Il peut s'agir d'une période calme ou d'une lacune de couverture ADS-B.",
        "note_mixed" to "Utilisation mixte des pistes : {list}.",
        "note_switch" to "Le vent favorise actuellement la piste {id} : la circulation pourrait bientôt changer de piste.",
        "loading" to "Chargement…", "noneDetected" to "Aucun avion détecté en approche finale.",
        "trafficUnavailable" to "Le trafic en direct est indisponible pour le moment.",
        "windUnavailable" to "Le vent est indisponible pour le moment.", "noMetar" to "Aucun METAR disponible.",
        "wind_fav" to "Vent du {dir}° à {spd} kt, ce qui favorise la piste {rwy}.",
        "calm" to "Vent calme ou variable : le choix de la piste ne dépend pas du vent.",
        "obs_at" to "Observé à {time} à CYUL. ", "obs_none" to "CYUL. ",
        "fcUnavailable" to "Les prévisions sont indisponibles pour le moment.", "fcNone" to "Aucune donnée de prévision.",
        "fc_calm" to "Vents faibles prévus : le choix de la piste pourrait ne pas suivre le vent.",
        "fc_switch_now" to "Le vent prévu favorise la piste {a} en ce moment, avec un changement attendu vers la piste {b} vers {time}.",
        "fc_switch_later" to "Le vent prévu favorisera la piste {a} dès que le vent se lèvera, avec un changement attendu vers la piste {b} vers {time}.",
        "fc_steady_now" to "Le vent prévu favorise la piste {a} en ce moment et pour les {n} prochaines heures.",
        "fc_steady_later" to "Le vent prévu favorisera la piste {a} dès que le vent se lèvera, pour les {n} prochaines heures.",
        "fc_source" to "Prévisions pour l'aéroport lui-même ({lat}, {lon}), d'après Open-Meteo. Les heures sont l'heure de Montréal ({tz}). Mis à jour à {time}.",
        "now" to "Actuel", "lightWind" to "Vent faible",
        "heading" to "Cap", "alt" to "Alt. (ft)", "dist" to "Dist. (km)", "speed" to "Vit. (kt)", "gusts" to "rafales",
        "refresh" to "Actualiser maintenant", "auto" to "Auto-actualisation", "radius" to "Rayon de recherche", "refreshEvery" to "Actualiser toutes les",
        "statusNext" to "Prochaine mise à jour dans {s} s", "updating" to "Mise à jour…",
        "err_network" to "Impossible de joindre OpenSky. Vérifiez votre connexion; si vous avez beaucoup actualisé, la limite quotidienne est peut-être atteinte.",
        "err_rate" to "Limite de requêtes OpenSky atteinte. Choisissez un intervalle d'actualisation plus long et réessayez plus tard.",
        "err_generic" to "La requête de trafic en direct a échoué.",
        "fine" to "Les positions en direct proviennent du flux ADS-B gratuit du réseau OpenSky. L'accès anonyme permet environ 400 requêtes par jour : un intervalle plus long permet à la page de fonctionner toute la journée. Les pistes parallèles (06L/06R et 24L/24R) ont le même cap et sont regroupées. Les mises à jour sont suspendues lorsque cet onglet est masqué. Toute la météo concerne l'aéroport lui-même (observation CYUL et prévisions pour ses coordonnées), jamais l'emplacement du visiteur, et toutes les heures affichées sont l'heure de Montréal. Les prévisions proviennent des données de modèle d'Open-Meteo et, lorsque l'auto-actualisation est activée, sont actualisées toutes les 15 minutes. Le vent prévu indique seulement la piste qu'il favoriserait; la piste réellement utilisée est choisie par le contrôle de la circulation aérienne.",
        "dirN" to "Nord", "dirNE" to "Nord-est", "dirE" to "Est", "dirSE" to "Sud-est", "dirS" to "Sud", "dirSW" to "Sud-ouest", "dirW" to "Ouest", "dirNW" to "Nord-ouest"
    )
    var s = (if (lang == "fr") fr else en)[key] ?: en[key] ?: key
    p.forEach { (k, v) -> s = s.replace("{$k}", v) }
    return s
}
private fun directionWord(lang: String, deg: Double): String {
    val k = ((round(((deg % 360 + 360) % 360) / 45).toInt()) % 8)
    return t(lang, listOf("dirN","dirNE","dirE","dirSE","dirS","dirSW","dirW","dirNW")[k])
}
private fun fmtTime(ms: Long?, seconds: Boolean = false, lang: String = "en"): String {
    if (ms == null) return ""
    val loc = if (lang == "fr") Locale.CANADA_FRENCH else Locale.CANADA
    val f = if (seconds) DateTimeFormatter.ofPattern("HH:mm:ss z", loc) else DateTimeFormatter.ofPattern("HH:mm z", loc)
    return f.format(Instant.ofEpochMilli(ms).atZone(ZoneId.of("America/Toronto")))
}
private fun pad3(d: Double?) = d?.roundToInt()?.toString()?.padStart(3, '0') ?: "–"
private fun forecastSummary(lang: String, rows: List<ForecastRow>): String {
    val known = rows.filter { it.fav != null }
    if (known.isEmpty()) return t(lang, "fc_calm")
    val first = known.first().fav!!
    val change = known.firstOrNull { it.fav!!.id != first.id }
    val now = rows.firstOrNull()?.fav != null
    return if (change != null) t(lang, if (now) "fc_switch_now" else "fc_switch_later", "a" to first.id, "b" to change.fav!!.id, "time" to change.clock)
    else t(lang, if (now) "fc_steady_now" else "fc_steady_later", "a" to first.id, "n" to rows.size.toString())
}
private fun nf(d: Double, digits: Int, lang: String): String = NumberFormat.getNumberInstance(if (lang == "fr") Locale.CANADA_FRENCH else Locale.CANADA).apply { minimumFractionDigits = digits; maximumFractionDigits = digits }.format(d)
private fun weather(code: Int, day: Boolean, lang: String): Pair<String, String> {
    val isFr = lang == "fr"
    fun l(en: String, fr: String) = if (isFr) fr else en
    return when (code) {
        0 -> (if (day) "☀️" else "🌙") to l("Clear", "Dégagé")
        1 -> "🌤️" to l("Mainly clear", "Généralement dégagé")
        2 -> (if (day) "⛅" else "☁️") to l("Partly cloudy", "Partiellement nuageux")
        3 -> "☁️" to l("Overcast", "Couvert")
        45 -> "🌫️" to l("Fog", "Brouillard")
        48 -> "🌫️" to l("Freezing fog", "Brouillard givrant")
        51 -> (if (day) "🌦️" else "🌧️") to l("Light drizzle", "Bruine légère")
        53 -> (if (day) "🌦️" else "🌧️") to l("Drizzle", "Bruine")
        55 -> "🌧️" to l("Heavy drizzle", "Forte bruine")
        56, 57 -> "🧊" to l("Freezing drizzle", "Bruine verglaçante")
        61 -> (if (day) "🌦️" else "🌧️") to l("Light rain", "Pluie légère")
        63 -> "🌧️" to l("Rain", "Pluie")
        65 -> "🌧️" to l("Heavy rain", "Forte pluie")
        66, 67 -> "🧊" to l("Freezing rain", "Pluie verglaçante")
        71 -> "🌨️" to l("Light snow", "Faible neige")
        73 -> "🌨️" to l("Snow", "Neige")
        75 -> "❄️" to l("Heavy snow", "Forte neige")
        77 -> "🌨️" to l("Snow grains", "Neige en grains")
        80 -> (if (day) "🌦️" else "🌧️") to l("Light showers", "Averses légères")
        81 -> "🌧️" to l("Showers", "Averses")
        82 -> "🌧️" to l("Heavy showers", "Fortes averses")
        85 -> "🌨️" to l("Snow showers", "Averses de neige")
        86 -> "❄️" to l("Heavy snow showers", "Fortes averses de neige")
        95 -> "⛈️" to l("Thunderstorm", "Orage")
        96, 99 -> "⛈️" to l("Thunderstorm, hail", "Orage avec grêle")
        else -> "❔" to "–"
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YulApp() }
    }
}

@Composable
private fun YulApp(vm: YulViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = darkColorScheme(background = Asphalt, surface = Panel, primary = Taxi, onBackground = PaintColor, onSurface = PaintColor)) {
        Surface(color = Asphalt, modifier = Modifier.fillMaxSize()) {
            YulScreen(s, vm)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YulScreen(s: UiState, vm: YulViewModel) {
    val lang = s.lang
    val listState = rememberLazyListState()
    BoxWithConstraints {
    val wide = maxWidth >= 600.dp
    PullToRefreshBox(isRefreshing = s.busy, modifier = Modifier.fillMaxSize(), onRefresh = { vm.refresh() }) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Montréal-Trudeau (YUL)", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(t(lang, "sub"), color = Dim, fontSize = 14.sp)
                }
                Row(Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Line)) {
                    LanguageButton("EN", lang == "en") { vm.setLang("en") }
                    LanguageButton("FR", lang == "fr") { vm.setLang("fr") }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(if (s.updated != null) t(lang, "updated", "time" to fmtTime(s.updated, true, lang)) else t(lang, "waiting"), color = Dim, fontSize = 13.sp)
        }
        item { if (s.trafficError != null) Notice(t(lang, if (s.trafficError == "rate") "err_rate" else "err_network")) }
        item { Hero(s, lang) }
        item { SectionTitle(t(lang, "arrivals")) }
        if (s.arrivals == null || s.trafficError != null) item { EmptyText(if (s.trafficError != null) t(lang, "trafficUnavailable") else t(lang, "loading")) }
        else if (s.arrivals.isEmpty()) item { EmptyText(t(lang, "noneDetected")) }
        else items(s.arrivals, key = { it.callsign + it.distKm }) { AircraftCard(it, lang) }
        item { SectionTitle(t(lang, "wind")) }
        item { WindCard(s, lang) }
        item { SectionTitle(t(lang, "forecast")) }
        if (s.forecastError) item { EmptyText(t(lang, "fcUnavailable")) }
        else if (s.forecast == null) item { EmptyText(t(lang, "loading")) }
        else {
            val rows = s.forecast
            item { Text(forecastSummary(lang, rows)) }
            var prevFav: String? = null
            val cards = rows.map { r ->
                val switched = r.fav != null && prevFav != null && r.fav.id != prevFav
                if (r.fav != null) prevFav = r.fav.id
                r to switched
            }
            if (wide) cards.chunked(2).forEach { pair ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (r, sw) -> ForecastCard(r, sw, lang, Modifier.weight(1f)) }
                    }
                }
            } else cards.forEach { (r, sw) -> item { ForecastCard(r, sw, lang) } }
            item {
                val time = fmtTime(s.forecastUpdated, true, lang)
                Text(t(lang, "fc_source", "lat" to "45.4706", "lon" to "-73.7408", "tz" to (time.split(" ").lastOrNull() ?: ""), "time" to time), color = Dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item { Controls(s, vm, lang) }
        item { Text(t(lang, "fine"), color = Dim, fontSize = 12.sp) }
        }
    }
    }
}

@Composable private fun LanguageButton(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(containerColor = if (selected) SignBg else Color.Transparent, contentColor = if (selected) SignFg else Dim), modifier = Modifier.height(40.dp)) { Text(text, fontWeight = FontWeight.Bold) }
}
@Composable private fun Notice(text: String) { Box(Modifier.fillMaxWidth().background(Panel).border(3.dp, Alert).padding(10.dp)) { Text(text, color = PaintColor) } }
@Composable private fun SectionTitle(text: String) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
@Composable private fun EmptyText(text: String) { Text(text, color = Dim, modifier = Modifier.padding(vertical = 2.dp)) }

@Composable private fun Hero(s: UiState, lang: String) {
    val fav = s.metar?.let { favouredRunway(it.dir?.toDouble(), it.speed?.toDouble()) }
    val top = s.arrivals?.groupingBy { it.runway.id }?.eachCount()?.maxByOrNull { it.value }?.key?.let { id -> RUNWAYS.firstOrNull { it.id == id } }
    val active = top ?: fav
    val predicted = top == null
    val dir = when {
        s.arrivals == null -> t(lang, if (s.trafficError != null) "unavailable" else "checking")
        s.arrivals.isEmpty() -> t(lang, "no_arrivals")
        else -> directionWord(lang, top!!.heading)
    }
    Column(Modifier.fillMaxWidth()) {
        Text(t(lang, "landing"), color = Dim, fontSize = 14.sp)
        Text(dir, color = if (s.arrivals.isNullOrEmpty()) Dim else PaintColor, fontSize = if (dir.length > 9) 46.sp else 64.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (active != null) RunwaySign(active, predicted, lang)
            RunwayDiagram(
                active?.id,
                predicted,
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .sizeIn(maxWidth = 300.dp, maxHeight = 300.dp)
            )
        }
        val note = when {
            s.arrivals == null -> ""
            s.arrivals.isEmpty() -> if (fav != null) t(lang, "note_none_wind", "id" to fav.id, "dir" to directionWord(lang, fav.heading).lowercase()) else t(lang, "note_none")
            else -> {
                val counts = s.arrivals.groupingBy { it.runway.id }.eachCount()
                when {
                    counts.size > 1 -> t(lang, "note_mixed", "list" to counts.entries.joinToString(", ") { e -> RUNWAYS.first { it.id == e.key }.name + " ×" + e.value })
                    fav != null && fav.id != top?.id -> t(lang, "note_switch", "id" to fav.id)
                    else -> ""
                }
            }
        }
        if (note.isNotEmpty()) Text(note, color = Dim)
    }
}

@Composable private fun RunwaySign(r: Runway, predicted: Boolean, lang: String) {
    Column(Modifier.padding(4.dp).border(3.dp, if (predicted) Line else SignFg, RoundedCornerShape(12.dp)).background(if (predicted) Color.Transparent else SignBg, RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(t(lang, if (predicted) "favours" else "in_use"), color = if (predicted) Dim else Color(0xFFE6E1C6), fontSize = 12.sp)
        Text(r.id, color = if (predicted) PaintColor else SignFg, fontSize = 60.sp, fontWeight = FontWeight.ExtraBold)
        Text(r.pair, color = if (predicted) Dim else Color(0xFFE6E1C6), fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun RunwayDiagram(activeId: String?, predicted: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "runwayArrows")
    val dashPhase by infiniteTransition.animateFloat(
        initialValue = 18f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arrowDashPhase"
    )

    Canvas(modifier) {
        // Keep the same 244-ish coordinate system as the HTML SVG and scale it
        // to the actual Compose canvas instead of using tiny fixed pixel values.
        val designSize = 260f
        val scale = min(size.width, size.height) / designSize
        val c = Offset(130f * scale, 130f * scale)
        val len = 140f * scale
        val w = 14f * scale
        val off = 12f * scale
        val tagOffset = 38f * scale
        val stroke = max(1f, 1.5f * scale)

        // Parallel runways: same geometry as the HTML version.
        drawContext.canvas.save()
        drawContext.canvas.rotate(46f, c.x, c.y)
        run {
            listOf(-off, off).forEach { parallelOffset ->
                drawRoundRect(
                    color = if (activeId != null) lerp(Panel, Taxi, .30f) else Panel,
                    topLeft = Offset(c.x - w / 2f + parallelOffset, c.y - len / 2f),
                    size = Size(w, len),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale)
                )
                drawRoundRect(
                    color = if (activeId != null) Taxi else Line,
                    topLeft = Offset(c.x - w / 2f + parallelOffset, c.y - len / 2f),
                    size = Size(w, len),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = if (activeId != null) 2.5f * scale else stroke,
                        pathEffect = if (predicted && activeId != null) {
                            PathEffect.dashPathEffect(floatArrayOf(7f * scale, 4f * scale))
                        } else null
                    )
                )
                drawLine(
                    PaintColor.copy(alpha = .6f),
                    Offset(c.x + parallelOffset, c.y - len / 2f + 8f * scale),
                    Offset(c.x + parallelOffset, c.y + len / 2f - 8f * scale),
                    strokeWidth = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * scale, 5f * scale))
                )
            }
        }
        drawContext.canvas.restore()

        // Runway number tags sit beyond each threshold, just like the HTML SVG.
        RUNWAYS.forEach { runway ->
            val th = Math.toRadians(runway.heading)
            val dx = sin(th)
            val dy = -cos(th)
            val px = cos(th)
            val py = sin(th)
            val x = c.x - dx.toFloat() * (len / 2f + 20f * scale) + px.toFloat() * tagOffset
            val y = c.y - dy.toFloat() * (len / 2f + 20f * scale) + py.toFloat() * tagOffset

            textLabel(
                scope = this,
                text = runway.id,
                p = Offset(x, y),
                on = runway.id == activeId,
                scale = scale
            )
        }

        // Animated approach arrow. The HTML uses a 10/8 dash pattern with a
        // 0.9s linear animation of stroke-dashoffset.
        if (activeId != null) {
            val runway = RUNWAYS.first { it.id == activeId }
            val tip = c.y + len / 2f + 10f * scale
            drawContext.canvas.save()
            drawContext.canvas.rotate(runway.heading.toFloat(), c.x, c.y)
            run {
                val arrowAlpha = if (predicted) .55f else 1f
                drawLine(
                    Taxi.copy(alpha = arrowAlpha),
                    Offset(c.x, 252f * scale),
                    Offset(c.x, tip + 14f * scale),
                    strokeWidth = 4.5f * scale,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10f * scale, 8f * scale),
                            if (predicted) 0f else dashPhase * scale
                        )
                )

                val head = Path().apply {
                    moveTo(c.x, tip)
                    lineTo(c.x - 10f * scale, tip + 17f * scale)
                    lineTo(c.x + 10f * scale, tip + 17f * scale)
                    close()
                }
                drawPath(head, Taxi.copy(alpha = arrowAlpha))
            }
            drawContext.canvas.restore()
        }
    }
}

private fun textLabel(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    text: String,
    p: Offset,
    on: Boolean,
    scale: Float
) {
    val background = if (on) SignBg else Color.Transparent
    val border = if (on) SignFg else Line
    val w = 40f * scale
    val h = 28f * scale

    scope.drawRoundRect(
        color = background,
        topLeft = Offset(p.x - w / 2f, p.y - h / 2f),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * scale),
    )
    scope.drawRoundRect(
        color = border,
        topLeft = Offset(p.x - w / 2f, p.y - h / 2f),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * scale),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * scale),
    )

    run {
        val canvas = scope.drawContext.canvas.nativeCanvas
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (on) android.graphics.Color.rgb(255, 201, 51) else android.graphics.Color.rgb(151, 163, 174)
            textSize = 18f * scale
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText(
            text,
            p.x,
            p.y - (paint.ascent() + paint.descent()) / 2f,
            paint
        )
    }
}

@Composable private fun AircraftCard(a: Aircraft, lang: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(a.callsign, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                RunwayBadge(a.runway, stacked = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(t(lang,"heading"), "${a.heading}°"); Metric(t(lang,"alt"), nf(a.altFt.toDouble(),0,lang)); Metric(t(lang,"dist"), nf(a.distKm,1,lang)); Metric(t(lang,"speed"), a.speedKt?.toString() ?: "–")
            }
        }
    }
}
@Composable private fun RunwayBadge(r: Runway, highlight: Boolean = false, stacked: Boolean = false) {
    val content: @Composable () -> Unit = {
        Box(Modifier.then(if (highlight) Modifier.border(2.dp, Taxi, RoundedCornerShape(5.dp)) else Modifier).background(SignBg, RoundedCornerShape(5.dp)).padding(horizontal=9.dp,vertical=2.dp)) { Text(r.id,color=SignFg,fontWeight=FontWeight.ExtraBold) }
        Text(r.pair.replace(" ",""),color=if (highlight) Taxi else Dim,fontSize=12.sp)
    }
    if (stacked) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) { content() }
}
@Composable private fun Metric(label: String, value: String) { Column { Text(label,color=Dim,fontSize=11.sp); Text(value,fontWeight=FontWeight.SemiBold) } }

@Composable private fun WindCard(s: UiState, lang: String) {
    val m = s.metar
    if (!s.metarLoaded) EmptyText(t(lang,"loading"))
    else if (s.metarError) EmptyText(t(lang,"windUnavailable"))
    else if (m == null) EmptyText(t(lang,"noMetar"))
    else {
        val fav = favouredRunway(m.dir?.toDouble(), m.speed?.toDouble())
        Text(if (fav != null) t(lang,"wind_fav","dir" to pad3(m.dir?.toDouble()),"spd" to (m.speed ?: 0).toString(),"rwy" to fav.name) else t(lang,"calm"))
        Text((m.obsMs?.let { t(lang,"obs_at","time" to fmtTime(it, false, lang)) } ?: t(lang,"obs_none")) + m.raw, color=Dim, fontFamily=FontFamily.Monospace, fontSize=12.sp, modifier=Modifier.padding(top=4.dp))
    }
}

@Composable private fun ForecastCard(r: ForecastRow, switched: Boolean, lang: String, modifier: Modifier = Modifier) {
    val (icon, cond) = weather(r.code, r.isDay != 0, lang)
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), border = androidx.compose.foundation.BorderStroke(if (r.isNow) 2.dp else 1.dp, if(r.isNow) Taxi else Line)) {
        Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Text(if(r.isNow) t(lang,"now") else r.clock, color=if(r.isNow) Taxi else PaintColor, fontWeight=if(r.isNow) FontWeight.Bold else FontWeight.SemiBold)
                Text("$icon  $cond", modifier=Modifier.weight(1f).padding(start=12.dp))
                Text(if(r.temp == null) "–" else "${nf(r.temp,0,lang)}°C", fontWeight=FontWeight.SemiBold)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text("☔ ${r.pop?.let { if (lang == "fr") "$it\u00a0%" else "$it%" } ?: "–"}")
                    Text("👁️ ${r.visKm?.let { if(it>=10) "10+" else nf(it,1,lang) } ?: "–"} km")
                }
                Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text("💨 ${if(r.dir==null||r.speed==null) "–" else "${pad3(r.dir)}° / ${r.speed.roundToInt()} kt"}")
                    Text("🌬️ ${t(lang, "gusts")} ${r.gust?.roundToInt() ?: "–"} kt")
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically) { Text("🛬 ", fontSize=16.sp); if(r.fav!=null) { if(switched) Text("🔄 "); RunwayBadge(r.fav, switched) } else Text(t(lang,"lightWind"),color=Dim) }
        }
    }
}

@Composable private fun Controls(s: UiState, vm: YulViewModel, lang: String) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp), modifier=Modifier.fillMaxWidth()) {
        HorizontalDivider(color=Line)
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp), verticalAlignment=Alignment.CenterVertically) {
            Text(t(lang,"refreshEvery"), color=Dim, modifier=Modifier.weight(1f))
            Dropdown(if (s.intervalSec == 120) "2 min" else s.intervalSec.toString()+" s", listOf(15,30,60,120), { if (it == 120) "2 min" else "$it s" }) { vm.setInterval(it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) { Text(t(lang,"auto"),color=Dim); Switch(s.autoRefresh, vm::setAuto) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) { Text(t(lang,"radius"),color=Dim); Dropdown("${s.radiusKm} km", listOf(15,25,35), {"$it km"}) { vm.setRadius(it) } }
        Button(onClick=vm::refresh, enabled=!s.busy, modifier=Modifier.fillMaxWidth(), colors=ButtonDefaults.buttonColors(containerColor=Panel,contentColor=PaintColor)) { Text(t(lang,"refresh")) }
        if(s.autoRefresh) Text(if(s.busy) t(lang,"updating") else t(lang,"statusNext","s" to max(0,ceil((s.nextAt-System.currentTimeMillis())/1000.0).toInt()).toString()),color=Dim,fontSize=13.sp)
    }
}
@Composable private fun <T> Dropdown(current: String, values: List<T>, label: (T)->String, onSelect: (T)->Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick={open=true}) { Text(current) }; DropdownMenu(expanded=open,onDismissRequest={open=false}) { values.forEach { DropdownMenuItem(text={Text(label(it))},onClick={open=false;onSelect(it)}) } } }
}
