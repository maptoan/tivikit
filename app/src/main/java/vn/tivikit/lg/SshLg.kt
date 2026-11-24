package vn.tivikit.lg

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/** Sai mật khẩu root khi đăng nhập SSH. */
class SshSaiMatKhauException : java.io.IOException("Sai mật khẩu SSH")

/** Khoá máy chủ SSH của tivi LG khác với lần kết nối trước (nghi ngờ giả mạo MITM trên mạng LAN). */
class SshKhoaDoiException : java.io.IOException("Khoá máy chủ SSH của tivi LG đã thay đổi")

/**
 * SSH root vào tivi LG đã root (Homebrew Channel bật SSH Server: dropbear cổng 22, mặc định root / alpine), chỉ cho việc
 * cần root (Vibehack). Mỗi việc một phiên: nối, chạy, đóng. Ghim khoá máy chủ ở lần nối đầu (TOFU) trước khi gửi mật khẩu.
 */
class SshLg(
    private val ip: String,
    private val matKhau: String,
    private val mang: SocketFactory?,
    private val dauKhoaDaLuu: String? = null,
    private val ghiDauKhoa: (String) -> Unit = {},
) {
    companion object {
        const val CONG = 22

        /** Kiểm tra khoá máy chủ theo cơ chế TOFU: chưa có thì ghim SHA-256 và cho qua; đã có thì bắt buộc khớp. */
        fun kiemTraTofu(daLuu: String?, khoaMoi: ByteArray, ghiMoi: (String) -> Unit = {}): Boolean {
            if (khoaMoi.isEmpty()) return false
            val moi = KetNoiLg.bamSha256(khoaMoi)
            if (daLuu.isNullOrBlank()) {
                ghiMoi(moi)
                return true
            }
            return daLuu.equals(moi, ignoreCase = true)
        }

        /** Dòng đầu cổng [cong] của [ip] (lời chào SSH), chờ tối đa [ms]; không mở / không nói gì thì null. */
        fun dongDau(ip: String, cong: Int, ms: Int, mang: SocketFactory?): String? = runCatching {
            ((mang ?: SocketFactory.getDefault()).createSocket() as Socket).use { s ->
                s.connect(InetSocketAddress(ip, cong), ms); s.soTimeout = ms
                val v = s.getInputStream(); val b = ByteArrayOutputStream()
                while (b.size() < 255) { val c = v.read(); if (c < 0 || c == '\n'.code) break; b.write(c) }
                b.toString("UTF-8").trim().takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private fun phien(): Session {
        val jsch = JSch()
        var daGhim = dauKhoaDaLuu
        jsch.hostKeyRepository = object : com.jcraft.jsch.HostKeyRepository {
            override fun check(host: String?, key: ByteArray?): Int {
                if (key == null || !kiemTraTofu(daGhim, key) { moi -> daGhim = moi; ghiDauKhoa(moi) }) {
                    return com.jcraft.jsch.HostKeyRepository.CHANGED
                }
                return com.jcraft.jsch.HostKeyRepository.OK
            }
            override fun add(hostkey: com.jcraft.jsch.HostKey?, ui: UserInfo?) {}
            override fun remove(host: String?, type: String?) {}
            override fun remove(host: String?, type: String?, key: ByteArray?) {}
            override fun getKnownHostsRepositoryID(): String = "mtvkit-tofu"
            override fun getHostKey(): Array<com.jcraft.jsch.HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<com.jcraft.jsch.HostKey> = emptyArray()
        }
        val s = jsch.getSession("root", ip, CONG)
        s.setPassword(matKhau)
        // Kiểm tra TOFU ngay ở bước trao đổi khoá (trước khi gửi mật khẩu root).
        s.setConfig("StrictHostKeyChecking", "yes")
        s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        // dropbear đời cũ chỉ có khoá ssh-rsa (SHA-1): JSch bản mới tắt mặc định, bật lại cho tivi cũ.
        s.setConfig("server_host_key", s.getConfig("server_host_key") + ",ssh-rsa")
        s.userInfo = object : UserInfo, UIKeyboardInteractive {
            override fun getPassphrase(): String? = null
            override fun getPassword(): String = matKhau
            override fun promptPassword(message: String?) = true
            override fun promptPassphrase(message: String?) = false
            override fun promptYesNo(message: String?) = false
            override fun showMessage(message: String?) {}
            override fun promptKeyboardInteractive(destination: String?, name: String?, instruction: String?, prompt: Array<out String>?, echo: BooleanArray?): Array<String> =
                Array(prompt?.size ?: 0) { matKhau }
        }
        if (mang != null) s.setSocketFactory(object : com.jcraft.jsch.SocketFactory {
            override fun createSocket(host: String, port: Int): Socket = mang.createSocket().apply { connect(InetSocketAddress(host, port), 6000) }
            override fun getInputStream(socket: Socket) = socket.getInputStream()
            override fun getOutputStream(socket: Socket) = socket.getOutputStream()
        })
        try { s.connect(8000) } catch (e: JSchException) {
            if (e.message?.contains("HostKey", ignoreCase = true) == true) throw SshKhoaDoiException()
            if (e.message?.contains("Auth", ignoreCase = true) == true) throw SshSaiMatKhauException()
            throw e
        }
        return s
    }

    /** Chạy [lenh], trả (mã thoát, chữ in ra); [dong] nhận từng dòng khi đang chạy. Quá [giay] giây thì ngắt (mã -1). */
    fun chay(lenh: String, giay: Int = 600, dong: (String) -> Unit = {}): Pair<Int, String> {
        val s = phien()
        try {
            val ch = s.openChannel("exec") as ChannelExec
            ch.setCommand(lenh)
            val vao = ch.inputStream
            ch.connect(8000)
            val doc = java.io.InputStreamReader(vao, Charsets.UTF_8)
            val tat = StringBuilder(); val hang = StringBuilder()
            val het = System.currentTimeMillis() + giay * 1000L
            val buf = CharArray(4096)
            while (System.currentTimeMillis() < het) {
                while (vao.available() > 0 || doc.ready()) {
                    val n = doc.read(buf); if (n < 0) break
                    tat.append(buf, 0, n)
                    for (i in 0 until n) {
                        val c = buf[i]
                        if (c == '\n') { dong(hang.toString()); hang.clear() } else hang.append(c)
                    }
                }
                if (ch.isClosed && vao.available() == 0 && !doc.ready()) break
                Thread.sleep(100)
            }
            if (hang.isNotEmpty()) dong(hang.toString())
            val ma = if (ch.isClosed) ch.exitStatus else -1
            ch.disconnect()
            return ma to tat.toString()
        } finally { s.disconnect() }
    }

    /** Ghi [du] vào [duong] trên tivi (qua `cat >`). */
    fun ghi(du: ByteArray, duong: String) {
        val s = phien()
        try {
            val ch = s.openChannel("exec") as ChannelExec
            ch.setCommand("cat > " + Vibehack.trich(duong))
            val ra = ch.outputStream
            ch.connect(8000)
            ra.write(du); ra.flush(); ra.close()
            val het = System.currentTimeMillis() + 30_000
            while (!ch.isClosed && System.currentTimeMillis() < het) Thread.sleep(100)
            if (ch.exitStatus != 0) throw java.io.IOException("Không ghi được $duong lên tivi")
            ch.disconnect()
        } finally { s.disconnect() }
    }

    /** Đăng nhập rồi xem tivi có root thật, Homebrew Channel, Vibehack, LG Input Hook không. */
    fun kiemTra(): Vibehack.KiemTra = Vibehack.docKiemTra(chay(Vibehack.LENH_KIEM_TRA, 20).second)
}
