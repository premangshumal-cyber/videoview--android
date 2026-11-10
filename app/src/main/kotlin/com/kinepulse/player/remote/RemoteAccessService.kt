package com.kinepulse.player.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.kinepulse.player.KinePulseApp
import com.kinepulse.player.ui.remote.RemoteAccessActivity
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Experimental, opt-in local HTTP catalog. No file reads, uploads or playback
 * commands are permitted through the network in this milestone.
 * Never enable on untrusted Wi-Fi; HTTPS and device-level access policies are
 * required before supporting write operations.
 */
object RemoteAccessState {
    @Volatile var running: Boolean = false
        internal set
    @Volatile var pin: String = ""
        internal set
    @Volatile var error: String = ""
        internal set

    fun localAddress(): String {
        return try {
            val nets = NetworkInterface.getNetworkInterfaces()
            while (nets.hasMoreElements()) {
                val network = nets.nextElement()
                if (!network.isUp || network.isLoopback) continue
                val addresses = network.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && address.isSiteLocalAddress) {
                        return "http://${address.hostAddress}:4242"
                    }
                }
            }
            "http://127.0.0.1:4242"
        } catch (_: Exception) {
            "http://127.0.0.1:4242"
        }
    }
}

class RemoteAccessService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: ApplicationEngine? = null
    private val sessions = ConcurrentHashMap<String, Long>()
    private val failures = AtomicInteger(0)
    private val random = SecureRandom()

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "KinePulse remote catalog", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (engine == null) {
            startForeground(NOTIFICATION_ID, notification())
            RemoteAccessState.error = ""
            RemoteAccessState.pin = (100000 + random.nextInt(900000)).toString()
            scope.launch {
                try {
                    engine = embeddedServer(CIO, host = "0.0.0.0", port = 4242) {
                        routing {
                            get("/") {
                                call.respondText(PORTAL, ContentType.Text.Html)
                            }
                            get("/health") {
                                call.respondText("{\"service\":\"KinePulse\",\"pairingRequired\":true}", ContentType.Application.Json)
                            }
                            post("/code") {
                                call.respondText("{\"message\":\"Enter the pairing PIN displayed in KinePulse\"}", ContentType.Application.Json)
                            }
                            post("/verify-code") {
                                if (failures.get() >= 10) {
                                    call.respond(HttpStatusCode.TooManyRequests)
                                    return@post
                                }
                                val submitted = try {
                                    JSONObject(call.receiveText()).optString("code")
                                } catch (_: Exception) { "" }
                                if (submitted != RemoteAccessState.pin || submitted.length != 6) {
                                    failures.incrementAndGet()
                                    call.respond(HttpStatusCode.Unauthorized)
                                    return@post
                                }
                                failures.set(0)
                                val bytes = ByteArray(32).also(random::nextBytes)
                                val token = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
                                sessions[token] = System.currentTimeMillis() + 15 * 60_000L
                                call.response.cookies.append(Cookie(
                                    name = "kp_session", value = token, path = "/",
                                    httpOnly = true, maxAge = 900
                                ))
                                call.respondText("{\"authenticated\":true}", ContentType.Application.Json)
                            }
                            get("/video-list") {
                                if (!authorized(call)) {
                                    call.respond(HttpStatusCode.Unauthorized)
                                    return@get
                                }
                                val catalog = (this@RemoteAccessService.application as KinePulseApp).repository.videos.first()
                                val json = JSONArray()
                                for (video in catalog) {
                                    json.put(JSONObject()
                                        .put("id", video.id)
                                        .put("title", video.title)
                                        .put("duration", video.durationMs)
                                        .put("width", video.width)
                                        .put("height", video.height))
                                }
                                call.respondText(json.toString(), ContentType.Application.Json)
                            }
                            get("/track-list") {
                                if (!authorized(call)) {
                                    call.respond(HttpStatusCode.Unauthorized)
                                    return@get
                                }
                                val catalog = (this@RemoteAccessService.application as KinePulseApp).repository.audios.first()
                                val json = JSONArray()
                                for (track in catalog) {
                                    json.put(JSONObject()
                                        .put("id", track.id)
                                        .put("title", track.title)
                                        .put("artist", track.artist)
                                        .put("album", track.album)
                                        .put("duration", track.durationMs))
                                }
                                call.respondText(json.toString(), ContentType.Application.Json)
                            }
                        }
                    }.also { it.start(wait = false) }
                    RemoteAccessState.running = true
                } catch (e: Exception) {
                    RemoteAccessState.error = e.message ?: "Unable to start server"
                    RemoteAccessState.running = false
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun authorized(call: ApplicationCall): Boolean {
        val key = call.request.cookies["kp_session"] ?: return false
        val expiry = sessions[key] ?: return false
        if (expiry < System.currentTimeMillis()) {
            sessions.remove(key)
            return false
        }
        return true
    }

    private fun notification(): Notification {
        val activity = PendingIntent.getActivity(
            this, 0, Intent(this, RemoteAccessActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("KinePulse Wi-Fi catalog")
            .setContentText("Local browser access active; tap to manage")
            .setContentIntent(activity)
            .setOngoing(true)
            .build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        engine?.stop(500, 1000)
        engine = null
        sessions.clear()
        RemoteAccessState.pin = ""
        RemoteAccessState.running = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.kinepulse.player.remote.STOP"
        private const val CHANNEL = "kinepulse_remote"
        private const val NOTIFICATION_ID = 1002
        private val PORTAL = """
            <!doctype html><html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>KinePulse — Local catalog</title><style>
            body{font:16px system-ui;background:#212121;color:#fff;max-width:720px;
            margin:2rem auto;padding:1rem}h1{color:#ff8800}
            input,button{padding:.8rem;border-radius:8px;font:inherit}
            button{background:#ff8800;color:#111;border:0;cursor:pointer}
            input{background:#323232;color:white;border:1px solid #616161}
            li{padding:.7rem;border-bottom:1px solid #424242}
            </style></head><body><h1>KinePulse</h1>
            <p>Local Wi-Fi media catalog (read-only prototype).</p>
            <label>PIN shown on the phone <input id="pin" maxlength="6" inputmode="numeric"></label>
            <button onclick="pair()">Pair</button>
            <p id="msg"></p>
            <button onclick="list('video-list')">Videos</button>
            <button onclick="list('track-list')">Audio</button><ul id="items"></ul>
            <script>
            async function pair(){
              const r=await fetch('/verify-code',{method:'POST',
                headers:{'Content-Type':'application/json'},
                body:JSON.stringify({code:document.getElementById('pin').value})});
              document.getElementById('msg').textContent=r.ok?'Paired for 15 minutes':'Pairing failed';
            }
            async function list(route){
              const r=await fetch('/'+route);
              if(!r.ok){document.getElementById('msg').textContent='Pair first';return;}
              const data=await r.json(),list=document.getElementById('items');
              list.replaceChildren();
              data.forEach(item=>{
                const li=document.createElement('li');
                li.textContent=item.title+(item.artist?' — '+item.artist:'');
                list.appendChild(li);
              });
            }
            </script></body></html>
        """.trimIndent()
    }
}
