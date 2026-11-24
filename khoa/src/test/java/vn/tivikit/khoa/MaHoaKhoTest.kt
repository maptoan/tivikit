package vn.tivikit.khoa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

/** Kiểm thử mã hoá AES-256-GCM bảo vệ hạt giống TOTP và phiên Khoá trẻ em trên tivi. */
class MaHoaKhoTest {
    @Test fun bocVaMo_giuNguyenJsonTotpVaChongSuaDoi() {
        val gen = KeyGenerator.getInstance("AES").apply { init(256) }
        val k1 = gen.generateKey()
        val k2 = gen.generateKey()
        val jsonMaMo = """{"otp":"JBSWY3DPEHPK3PXP","muoi":"a1b2c3d4","bam":["0123456789abcdef"]}"""

        val c1 = MaHoaKho.bocVoiKhoa(jsonMaMo, k1)
        val c2 = MaHoaKho.bocVoiKhoa(jsonMaMo, k1)
        assertTrue(c1.startsWith("enc1:"))
        assertNotEquals(c1, c2)
        assertEquals(jsonMaMo, MaHoaKho.moVoiKhoa(c1, k1))
        assertEquals(jsonMaMo, MaHoaKho.moVoiKhoa(c2, k1))

        // Sai khoá hoặc dữ liệu bị chỉnh sửa đều bị từ chối.
        assertNull(MaHoaKho.moVoiKhoa(c1, k2))
        val sua = c1.dropLast(1) + (if (c1.last() == 'x') 'y' else 'x')
        assertNull(MaHoaKho.moVoiKhoa(sua, k1))
        assertNull(MaHoaKho.moVoiKhoa("enc1:ngan:du", k1))
    }
}
