package vn.tivikit.teleremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

/** Kiểm thử mã hoá AES-256-GCM bảo vệ Telegram Bot Token của Tele Remote trên tivi. */
class MaHoaKhoTest {
    @Test fun bocVaMo_baoVeTokenVaChongSuaDoi() {
        val gen = KeyGenerator.getInstance("AES").apply { init(256) }
        val k1 = gen.generateKey()
        val k2 = gen.generateKey()
        val mau = "123456789:AAExampleTelegramBotIdentifier99"

        val c1 = MaHoaKho.bocVoiKhoa(mau, k1)
        val c2 = MaHoaKho.bocVoiKhoa(mau, k1)
        assertTrue(c1.startsWith("enc1:"))
        assertNotEquals(c1, c2)
        assertEquals(mau, MaHoaKho.moVoiKhoa(c1, k1))
        assertEquals(mau, MaHoaKho.moVoiKhoa(c2, k1))

        assertNull(MaHoaKho.moVoiKhoa(c1, k2))
        val viTriSua = c1.lastIndexOf(':') + 1
        val sua = c1.substring(0, viTriSua) + (if (c1[viTriSua] == 'A') 'B' else 'A') + c1.substring(viTriSua + 1)
        assertNull(MaHoaKho.moVoiKhoa(sua, k1))
    }
}
