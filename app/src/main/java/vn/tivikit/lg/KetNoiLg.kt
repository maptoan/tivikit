package vn.tivikit.lg

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Tivi từ chối ghép đôi (bấm Từ chối, hoặc hết giờ hộp hỏi). */
class LgTuChoiException(chu: String) : IOException(chu)

/** Chứng chỉ TLS của tivi LG khác với lần ghép đôi trước (nghi ngờ giả mạo MITM trên mạng LAN). */
class LgChungChiDoiException(chu: String) : IOException(chu)

/** Tivi trả lỗi cho một yêu cầu; [khongCo] = tivi không có lệnh đó ("404 no such service or method"). */
class LgLoiException(chu: String) : IOException(chu) {
    val khongCo: Boolean get() = message?.contains("404") == true
}

/**
 * Một kết nối tới tivi LG [ip] (xem WebOs): kênh chính SSAP cho yêu cầu / trả lời, kênh phím cho nút remote. Thử
 * ws://ip:3000 trước, tivi từ chối thì wss://ip:3001 — chứng chỉ tự ký của tivi được ghim dấu vân tay SHA-256 ở lần đầu
 * (TOFU) để chống giả mạo về sau. [socketFactory]: socket của mạng Wi-Fi (Wi-Fi "không có Internet" thì Android lấy 4G làm mặc định).
 */
class KetNoiLg(val ip: String, socketFactory: SocketFactory?, private val dauChungChiDaLuu: String? = null) {
    /** Dấu vân tay SHA-256 của chứng chỉ TLS tivi LG đã dùng trong phiên này (để lưu ghim TOFU). */
    @Volatile var dauChungChi: String? = dauChungChiDaLuu?.takeIf { it.isNotBlank() }
        private set

    private val tinTofu = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain.isNullOrEmpty()) throw CertificateException("Thiếu chứng chỉ TLS từ tivi LG")
            val dau = bamSha256(chain[0].encoded)
            if (!khopTofu(dauChungChiDaLuu, dau)) throw CertificateException(CHUNG_CHI_DOI)
            dauChungChi = dau
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    private val http: OkHttpClient = OkHttpClient.Builder()
        .apply { if (socketFactory != null) socketFactory(socketFactory) }
        .sslSocketFactory(SSLContext.getInstance("TLS").apply { init(null, arrayOf(tinTofu), null) }.socketFactory, tinTofu)
        .hostnameVerifier { host, _ -> host.equals(ip, ignoreCase = true) }
        .connectTimeout(4, TimeUnit.SECONDS)
        // Chỉ áp cho lúc bắt tay WebSocket (tivi nhận kết nối mà không trả lời); sau đó OkHttp bỏ hạn đọc, ping lo việc canh.
        .readTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var chinh: WebSocket? = null
    @Volatile private var phim: WebSocket? = null
    private val moPhim = Mutex()
    private val cho = ConcurrentHashMap<String, Channel<WebOs.Tin>>()
    private val dem = AtomicInteger(0)

    /** Kênh chính còn mở. */
    @Volatile var song = false
        private set

    /**
     * Mở kênh chính, chào, ghép đôi. [khoa] = client-key cũ (null: lần đầu); [dangHoi] gọi khi tivi đang hiện hộp hỏi
     * Cho phép. Trả client-key tivi cấp (lưu lại cho lần sau).
     */
    suspend fun noi(khoa: String?, dangHoi: () -> Unit): String {
        val ws = mo("ws://$ip:${WebOs.CONG_WS}") ?: mo("wss://$ip:${WebOs.CONG_WSS}")
            ?: throw IOException("Không mở được kết nối tới $ip (cổng ${WebOs.CONG_WS} / ${WebOs.CONG_WSS})")
        chinh = ws; song = true
        // Chào và hỏi thông tin trước khi đăng ký: webOS đời mới cần, đời cũ không trả lời thì thôi.
        runCatching { hoi(WebOs.ID_CHAO, WebOs.chao(), 4000) }
        runCatching { yeuCau("system/getSystemInfo", ms = 4000) }
        val kenh = Channel<WebOs.Tin>(Channel.UNLIMITED)
        cho[WebOs.ID_DANG_KY] = kenh
        try {
            if (!song) { cho.remove(WebOs.ID_DANG_KY)?.close(); throw IOException(MAT_KET_NOI) }
            if (!ws.send(WebOs.dangKy(khoa))) throw IOException("Mất kết nối khi ghép đôi")
            return withTimeout(90_000) {
                var k: String? = null
                while (k == null) {
                    val t = kenh.receive()
                    when {
                        t.loai == "registered" -> k = t.payload?.optString("client-key").orEmpty()
                        t.loai == "error" && t.loi == MAT_KET_NOI -> throw IOException(MAT_KET_NOI)
                        t.loai == "error" -> throw LgTuChoiException(WebOs.chuLoi(t))
                        t.payload?.optString("pairingType") == "PROMPT" -> dangHoi()
                    }
                }
                k
            }
        } finally { cho.remove(WebOs.ID_DANG_KY) }
    }

    /** Gửi yêu cầu [uri] (không có "ssap://"), chờ trả lời; lỗi thì LgLoiException. */
    suspend fun yeuCau(uri: String, payload: JSONObject = JSONObject(), ms: Long = 10_000): JSONObject {
        val id = "tk${dem.incrementAndGet()}"
        val t = hoi(id, WebOs.yeuCau(id, uri, payload), ms)
        if (!WebOs.thanhCong(t)) throw LgLoiException(WebOs.chuLoi(t).ifBlank { "Tivi không làm được: $uri" })
        return t.payload ?: JSONObject()
    }

    /** Gửi mà không chờ trả lời (tắt tivi: tivi tắt trước khi kịp trả lời). */
    fun gui(uri: String, payload: JSONObject = JSONObject()): Boolean {
        val id = "tk${dem.incrementAndGet()}"
        return chinh?.send(WebOs.yeuCau(id, uri, payload)) == true
    }

    /** Bấm một phím remote (WebOs.nut). Kênh phím mở lúc cần, rớt thì mở lại một lần. */
    suspend fun nut(ten: String) {
        val p = phim ?: kenhPhim()
        if (!p.send(WebOs.nut(ten))) { phim = null; kenhPhim().send(WebOs.nut(ten)) }
    }

    private suspend fun kenhPhim(): WebSocket = moPhim.withLock {
        phim ?: run {
            val duong = yeuCau("com.webos.service.networkinput/getPointerInputSocket").optString("socketPath")
            (if (duong.isBlank()) null else mo(duong))?.also { phim = it } ?: throw IOException("Tivi không mở kênh phím")
        }
    }

    /** Tải một ảnh trên tivi (biểu tượng app, http://ip:3000/resources/…); lỗi thì null. */
    fun taiAnh(url: String): ByteArray? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
    }.getOrNull()

    fun dong() {
        song = false
        runCatching { phim?.close(1000, null) }; phim = null
        runCatching { chinh?.close(1000, null) }; chinh = null
        boCho()
        http.dispatcher.executorService.shutdown()
        runCatching { http.connectionPool.evictAll() }
    }

    private suspend fun hoi(id: String, tin: String, ms: Long): WebOs.Tin {
        val kenh = Channel<WebOs.Tin>(1)
        cho[id] = kenh
        try {
            if (!song) { cho.remove(id)?.close(); throw IOException(MAT_KET_NOI) }
            if (chinh?.send(tin) != true) throw IOException("Mất kết nối với tivi")
            return withTimeout(ms) { kenh.receive() }
        } finally { cho.remove(id) }
    }

    /** Mở một WebSocket; không mở được thì null, riêng lỗi lệch chứng chỉ TOFU thì ném thẳng để báo người dùng. */
    private suspend fun mo(url: String): WebSocket? = suspendCancellableCoroutine { c ->
        val ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { if (c.isActive) c.resume(webSocket) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val t = WebOs.doc(text) ?: return
                cho[t.id]?.trySend(t)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val lechChungChi = generateSequence(t) { it.cause }.any { it.message?.contains(CHUNG_CHI_DOI) == true }
                if (c.isActive) {
                    if (lechChungChi) c.resumeWithException(LgChungChiDoiException(CHUNG_CHI_DOI))
                    else c.resume(null)
                } else rot(webSocket)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = rot(webSocket)
        })
        c.invokeOnCancellation { ws.cancel() }
    }

    private fun rot(ws: WebSocket) {
        if (ws === phim) phim = null
        if (ws === chinh) { song = false; chinh = null; boCho() }
    }

    /** Kênh chính rớt: mọi yêu cầu đang chờ nhận lỗi ngay, không chờ hết giờ. */
    private fun boCho() {
        cho.values.forEach { it.trySend(WebOs.Tin("", "error", null, MAT_KET_NOI)) }
    }

    companion object {
        private const val MAT_KET_NOI = "Mất kết nối với tivi"
        const val CHUNG_CHI_DOI = "Chứng chỉ TLS của tivi LG đã thay đổi"

        fun bamSha256(duLieu: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(duLieu).joinToString("") { "%02x".format(it) }

        /** Khớp TOFU: lần đầu ([daLuu] trống) thì cho qua để ghim; các lần sau phải khớp dấu vân tay SHA-256. */
        fun khopTofu(daLuu: String?, moi: String): Boolean =
            moi.isNotBlank() && (daLuu.isNullOrBlank() || daLuu.equals(moi, ignoreCase = true))
    }
}
