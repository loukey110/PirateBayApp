package com.piratebay.app.network

import android.content.Context
import android.content.SharedPreferences
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
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("translation_prefs", Context.MODE_PRIVATE)
    
    var appId: String
        get() = prefs.getString("baidu_app_id", "") ?: ""
        set(value) = prefs.edit().putString("baidu_app_id", value).apply()
    
    var secretKey: String
        get() = prefs.getString("baidu_secret_key", "") ?: ""
        set(value) = prefs.edit().putString("baidu_secret_key", value).apply()
    
    fun isConfigured(): Boolean {
        return appId.isNotEmpty() && secretKey.isNotEmpty()
    }
    
    suspend fun translate(text: String, from: String = "en", to: String = "zh"): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConfigured()) {
                    return@withContext Result.failure(Exception("请先配置百度翻译API密钥"))
                }
                
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
