package vn.tivikit.dulieu

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Mã hoá khoá ghép đôi và mật khẩu SSH lưu cục bộ bằng AES-256-GCM qua AndroidKeyStore (tự nâng cấp mượt từ bản chữ rõ cũ).
 */
object MaHoaKho {
    const val TIEN_TO = "enc1:"
    private const val CHE_DO = "AES/GCM/NoPadding"
    private const val DO_DAI_IV = 12
    private const val DO_DAI_TAG = 128

    /** Mã hoá chuỗi [ro] bằng [khoa] AES-GCM thành định dạng "enc1:<iv>:<ciphertext>". */
    fun bocVoiKhoa(ro: String, khoa: SecretKey): String {
        val c = Cipher.getInstance(CHE_DO)
        c.init(Cipher.ENCRYPT_MODE, khoa)
        val iv = c.iv
        val ma = c.doFinal(ro.toByteArray(Charsets.UTF_8))
        val enc = Base64.getUrlEncoder().withoutPadding()
        return TIEN_TO + enc.encodeToString(iv) + ":" + enc.encodeToString(ma)
    }

    /** Giải mã chuỗi định dạng "enc1:<iv>:<ciphertext>"; trả null nếu sai khoá hoặc dữ liệu bị sửa đổi. */
    fun moVoiKhoa(ma: String, khoa: SecretKey): String? {
        if (!ma.startsWith(TIEN_TO)) return null
        val phan = ma.removePrefix(TIEN_TO).split(':', limit = 2)
        if (phan.size != 2) return null
        return runCatching {
            val dec = Base64.getUrlDecoder()
            val iv = dec.decode(phan[0])
            if (iv.size != DO_DAI_IV) return null
            val du = dec.decode(phan[1])
            val c = Cipher.getInstance(CHE_DO)
            c.init(Cipher.DECRYPT_MODE, khoa, GCMParameterSpec(DO_DAI_TAG, iv))
            String(c.doFinal(du), Charsets.UTF_8)
        }.getOrNull()
    }

    @Synchronized
    private fun layKhoa(tenKhoa: String): SecretKey? = runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(tenKhoa, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(tenKhoa, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }.getOrNull()

    /** Mã hoá một giá trị lẻ (nếu chưa mã hoá); trả nguyên bản nếu máy không có AndroidKeyStore. */
    fun bocChuoi(ro: String, tenKhoa: String): String {
        if (ro.isEmpty() || ro.startsWith(TIEN_TO)) return ro
        return layKhoa(tenKhoa)?.let { k -> runCatching { bocVoiKhoa(ro, k) }.getOrNull() } ?: ro
    }

    /** Giải mã giá trị (nếu có tiền tố enc1:); chuỗi rõ từ bản cũ giữ nguyên để tự nâng cấp ở lần lưu kế. */
    fun moChuoi(luu: String, tenKhoa: String): String {
        if (luu.isEmpty() || !luu.startsWith(TIEN_TO)) return luu
        val khoa = layKhoa(tenKhoa) ?: return ""
        return moVoiKhoa(luu, khoa).orEmpty()
    }

    /** Đọc chuỗi nhạy cảm ở [muc]; nếu đang lưu dạng rõ từ bản cũ thì tự mã hoá lại ngay. */
    fun docChuoi(p: SharedPreferences, muc: String, tenKhoa: String): String? {
        val luu = p.getString(muc, null) ?: return null
        if (luu.isEmpty()) return ""
        if (luu.startsWith(TIEN_TO)) {
            val khoa = layKhoa(tenKhoa) ?: return null
            return moVoiKhoa(luu, khoa)
        }
        layKhoa(tenKhoa)?.let { k ->
            runCatching { bocVoiKhoa(luu, k) }.getOrNull()?.let { moi -> p.edit().putString(muc, moi).apply() }
        }
        return luu
    }

    /** Ghi chuỗi nhạy cảm [ro] vào [muc] (null = xoá mục). */
    fun ghiChuoi(p: SharedPreferences, muc: String, ro: String?, tenKhoa: String) {
        if (ro == null) {
            p.edit().remove(muc).apply()
            return
        }
        if (ro.isEmpty()) {
            p.edit().putString(muc, "").apply()
            return
        }
        val luu = layKhoa(tenKhoa)?.let { k -> runCatching { bocVoiKhoa(ro, k) }.getOrNull() } ?: ro
        p.edit().putString(muc, luu).apply()
    }
}
