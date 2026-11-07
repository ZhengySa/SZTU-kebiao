package com.classflow.timetable.data.remote.sztu

import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 与学校统一身份认证前端等价的密码加密：DES / ECB / PKCS5Padding，结果转 Base64。
 *
 * 这一步不是"绕过安全"，而是**复刻浏览器本来就在做的事**：
 * 学校登录页的 JS 会先 `encryptByDES(密码)` 再提交，服务端解密后校验。
 * 不这么加密，服务端会认为密码格式非法。
 */
object DesEcb {

    fun encryptPassword(plain: String): String {
        val keyBytes = SztuConfig.DES_KEY
            .toByteArray(StandardCharsets.UTF_8)
            .copyOf(SztuConfig.DES_KEY_BYTES)
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "DES"))
        val out = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }
}
