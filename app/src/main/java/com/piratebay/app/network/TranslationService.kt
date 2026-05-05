package com.piratebay.app.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class TranslationService(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val appId: String = getAppId()
    private val secretKey: String = getSecretKey()
    
    fun isConfigured(): Boolean = true
    
    private fun getAppId(): String {
        val chars = charArrayOf(
            '2','0','2','6','0','5','0','5',
            '0','0','2','6','0','7',
            '3','5','4'
        )
        return String(chars)
    }
    
    private fun getSecretKey(): String {
        val chars = charArrayOf(
            'D','z','9','0','d','m','G','M',
            '6','f','o','a','T','V','O','0',
            'u','d','E','U'
        )
        return String(chars)
    }
    
    suspend fun translate(text: String, from: String = "en", to: String = "zh"): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val salt = System.currentTimeMillis().toString()
                val sign = generateSign(text, salt)
                
                val formBody = FormBody.Builder()
                    .add("q", text)
                    .add("from", from)
                    .add("to", to)
                    .add("appid", appId)
                    .add("salt", salt)
                    .add("sign", sign)
                    .build()
                
                val request = Request.Builder()
                    .url("https://fanyi-api.baidu.com/api/trans/vip/translate")
                    .post(formBody)
                    .build()
                
                val response = client.newCall(request).execute()
                val json = response.body?.string() ?: return@withContext Result.failure(Exception("空响应"))
                
                val jsonObject = JSONObject(json)
                
                if (jsonObject.has("error_code")) {
                    val errorCode = jsonObject.getString("error_code")
                    val errorMsg = jsonObject.optString("error_msg", "翻译失败")
                    return@withContext Result.failure(Exception("错误 $errorCode: $errorMsg"))
                }
                
                val transResult = jsonObject.getJSONArray("trans_result")
                val result = StringBuilder()
                
                for (i in 0 until transResult.length()) {
                    val item = transResult.getJSONObject(i)
                    result.append(item.getString("dst"))
                    if (i < transResult.length() - 1) {
                        result.append("\n")
                    }
                }
                
                Result.success(result.toString())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    private fun generateSign(query: String, salt: String): String {
        val str = appId + query + salt + secretKey
        return md5(str)
    }
    
    private fun md5(str: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(str.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
