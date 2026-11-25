package vn.tivikit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import vn.tivikit.dulieu.Iptv
import vn.tivikit.dulieu.MaHoaKho
import vn.tivikit.dulieu.TaiLink
import vn.tivikit.lg.KetNoiLg
import vn.tivikit.lg.SshLg
import java.io.File
import javax.crypto.KeyGenerator

/** Kiểm chứng 5 hạng mục gia cố bảo mật chiều sâu trên toàn bộ dự án MTVkit. */
class BaoMatTest {

    private fun thuMucGoc(): File {
        val hienTai = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return if (File(hienTai, "settings.gradle.kts").isFile) hienTai else hienTai.parentFile
    }

    @Test fun manifest_quyenNoiBoChuKyVaTatSaoLuu() {
        val goc = thuMucGoc()
        val tatCaModule = listOf("app", "khoa", "kenh", "hengio", "cast", "mapvoice", "teleremote", "go", "launcher", "trentv", "iptv")
        for (m in tatCaModule) {
            val xml = File(goc, "$m/src/main/AndroidManifest.xml").readText()
            assertTrue("$m phải tắt allowBackup", xml.contains("""android:allowBackup="false""""))
        }

        val moduleLienThong = listOf("khoa", "mapvoice", "iptv", "kenh", "launcher", "teleremote", "cast", "hengio")
        for (m in moduleLienThong) {
            val xml = File(goc, "$m/src/main/AndroidManifest.xml").readText()
            assertTrue("$m phải khai báo permission NOI_BO cấp signature",
                xml.contains("""<permission android:name="vn.tivikit.permission.NOI_BO" android:protectionLevel="signature" />"""))
            assertTrue("$m phải xin uses-permission NOI_BO",
                xml.contains("""<uses-permission android:name="vn.tivikit.permission.NOI_BO" />"""))
        }

        val khoaXml = File(goc, "khoa/src/main/AndroidManifest.xml").readText()
        assertTrue(Regex("""android:name="\.XacNhan"[\s\S]*?android:permission="vn\.tivikit\.permission\.NOI_BO"""").containsMatchIn(khoaXml))
        assertTrue(Regex("""android:name="\.KhoaNgay"[\s\S]*?android:permission="vn\.tivikit\.permission\.NOI_BO"""").containsMatchIn(khoaXml))

        val mvXml = File(goc, "mapvoice/src/main/AndroidManifest.xml").readText()
        assertTrue(Regex("""android:name="\.MoTuDong"[\s\S]*?android:permission="vn\.tivikit\.permission\.NOI_BO"""").containsMatchIn(mvXml))

        val iptvXml = File(goc, "iptv/src/main/AndroidManifest.xml").readText()
        assertTrue(Regex("""android:name="\.PhatKenh"[\s\S]*?android:permission="vn\.tivikit\.permission\.NOI_BO"""").containsMatchIn(iptvXml))
    }

    @Test fun taiLink_chanHaCapHttpsXuongHttpVaChanSsrf() {
        // Cho phép chuyển hướng HTTPS → HTTPS và HTTP → HTTPS.
        assertEquals(
            "https://cdn.example.org/app-v2.apk",
            TaiLink.buocChuyenHuong("https://get.videolan.org/vlc.apk", "https://cdn.example.org/app-v2.apk", batBuocHttps = true, chanMangNoiBo = true)
        )
        assertEquals(
            "https://cdn.example.org/app.apk",
            TaiLink.buocChuyenHuong("http://short.example.org/1", "https://cdn.example.org/app.apk", batBuocHttps = false)
        )

        // Cấm tuyệt đối hạ cấp HTTPS → HTTP khi chuyển hướng.
        try {
            TaiLink.buocChuyenHuong("https://mirrors.kodi.tv/kodi.apk", "http://mirror-la.example.org/kodi.apk")
            fail("Phải chặn chuyển hướng hạ cấp HTTPS → HTTP")
        } catch (_: IllegalStateException) {}

        // Cấm chuyển hướng về localhost / loopback / file:// / mạng LAN khi tải app gợi ý.
        for (nguyHiem in listOf(
            "https://127.0.0.1/evil.apk",
            "https://localhost:8080/evil.apk",
            "https://[::1]/evil.apk",
            "https://192.168.1.99/evil.apk",
            "file:///etc/hosts",
        )) {
            try {
                TaiLink.buocChuyenHuong("https://api.github.com/rel", nguyHiem, batBuocHttps = true, chanMangNoiBo = true)
                fail("Phải chặn đích chuyển hướng không an toàn: $nguyHiem")
            } catch (_: IllegalStateException) {}
        }

        // Kiểm tra định dạng IP lách luật (số nguyên thập phân, hex, IPv6 mapped, link-local).
        assertFalse(TaiLink.urlHopLe("http://127.0.0.1/a.apk"))
        assertFalse(TaiLink.urlHopLe("http://2130706433/a.apk"))
        assertFalse(TaiLink.urlHopLe("http://0x7f000001/a.apk"))
        assertFalse(TaiLink.urlHopLe("http://[::ffff:127.0.0.1]/a.apk"))
        assertFalse(TaiLink.urlHopLe("http://169.254.169.254/latest/meta-data"))
        assertFalse(TaiLink.urlHopLe("http://0.0.0.0:8008/a.apk"))
        assertFalse(TaiLink.urlHopLe("http://sub.localhost/a.apk"))
        assertFalse(TaiLink.urlHopLe("http://github.com/a.apk", batBuocHttps = true))
        assertTrue(TaiLink.urlHopLe("https://cafe.ba/app.apk", batBuocHttps = true, chanMangNoiBo = true))
        assertTrue(TaiLink.urlHopLe("https://github.com/yuliskov/SmartTube/releases/download/v1/a.apk", batBuocHttps = true, chanMangNoiBo = true))
    }

    @Test fun iptv_lenhDatChanSsrfChoPhepNasNha() {
        assertTrue(Iptv.lenhDat("https://iptv.example.org/playlist.m3u").contains("content://vn.tivikit.iptv"))
        assertTrue(Iptv.lenhDat("http://192.168.1.20:4022/playlist.m3u").contains("192.168.1.20"))
        for (xau in listOf("http://127.0.0.1:8008/a.m3u", "http://localhost/a.m3u", "http://[::1]/a.m3u", "http://169.254.1.1/a.m3u")) {
            try {
                Iptv.lenhDat(xau)
                fail("Phải chặn link M3U nội bộ: $xau")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun lgTofu_ghimChungChiTlsVaKhoaSsh() {
        val cert1 = "chung-chi-tv-lg-1".toByteArray()
        val cert2 = "chung-chi-gia-mao-mitm".toByteArray()
        val dau1 = KetNoiLg.bamSha256(cert1)
        val dau2 = KetNoiLg.bamSha256(cert2)

        assertEquals(64, dau1.length)
        assertNotEquals(dau1, dau2)
        // Lần đầu chưa có dấu vân tay: cho phép ghim (TOFU).
        assertTrue(KetNoiLg.khopTofu(null, dau1))
        assertTrue(KetNoiLg.khopTofu("", dau1))
        // Lần sau đúng chứng chỉ cũ: cho phép; chứng chỉ lạ: chặn!
        assertTrue(KetNoiLg.khopTofu(dau1.uppercase(), dau1))
        assertFalse(KetNoiLg.khopTofu(dau1, dau2))
        assertFalse(KetNoiLg.khopTofu(dau1, ""))

        // Kiểm tra TOFU cho SSH Host Key trước bước xác thực mật khẩu.
        var daGhim: String? = null
        assertTrue(SshLg.kiemTraTofu(daGhim, cert1) { daGhim = it })
        assertEquals(dau1, daGhim)
        assertTrue(SshLg.kiemTraTofu(daGhim, cert1))
        assertFalse(SshLg.kiemTraTofu(daGhim, cert2))
        assertFalse(SshLg.kiemTraTofu(null, ByteArray(0)))
    }

    @Test fun maHoaKho_aes256GcmChongGiaMao() {
        val gen = KeyGenerator.getInstance("AES").apply { init(256) }
        val khoa1 = gen.generateKey()
        val khoa2 = gen.generateKey()
        val biMat = "client-key-lg-webos-va-mat-khau-tieng-viet-🔒"

        val maA = MaHoaKho.bocVoiKhoa(biMat, khoa1)
        val maB = MaHoaKho.bocVoiKhoa(biMat, khoa1)
        assertTrue(maA.startsWith(MaHoaKho.TIEN_TO))
        // Mỗi lần mã hoá sinh IV ngẫu nhiên riêng nên bản mã không bao giờ trùng nhau.
        assertNotEquals(maA, maB)
        assertEquals(biMat, MaHoaKho.moVoiKhoa(maA, khoa1))
        assertEquals(biMat, MaHoaKho.moVoiKhoa(maB, khoa1))

        // Giải mã bằng khoá khác hoặc sửa 1 ký tự bản mã phải bị GCM tag từ chối (trả null).
        assertNull(MaHoaKho.moVoiKhoa(maA, khoa2))
        val viTriSua = maA.lastIndexOf(':') + 1
        val biSua = maA.substring(0, viTriSua) + (if (maA[viTriSua] == 'A') 'B' else 'A') + maA.substring(viTriSua + 1)
        assertNull(MaHoaKho.moVoiKhoa(biSua, khoa1))
        assertNull(MaHoaKho.moVoiKhoa("khong-co-tien-to", khoa1))
    }
}
