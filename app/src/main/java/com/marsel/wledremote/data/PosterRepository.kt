package com.marsel.wledremote.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Необязательные ключи. Без них постеры берутся из Википедии (ключ не нужен).
 *
 * Если хочешь постеры с Кинопоиска (точнее для русских названий):
 * 1) получи бесплатный токен на https://kinopoiskapiunofficial.tech
 * 2) вставь его между кавычками ниже.
 */
object PosterConfig {
    const val KINOPOISK_TOKEN = "9ac1bfb7-8c4d-48fb-89c7-d4092b4a3796"
}

/**
 * Ищет постер по названию, перекодирует в небольшой JPEG и кэширует на диске.
 * Возвращает null, если ничего не нашлось или нет сети.
 */
class PosterRepository(context: Context) {

    private val cacheDir = File(context.cacheDir, "posters").apply { mkdirs() }
    private val memory = LruCache<String, ByteArray>(6)
    private val misses = HashSet<String>() // что искали и точно не нашли (до перезапуска)

    suspend fun load(rawTitle: String): ByteArray? = withContext(Dispatchers.IO) {
        val query = PosterTitle.parse(rawTitle) ?: return@withContext null
        val key = cacheKey(query)

        synchronized(memory) { memory.get(key) }?.let { return@withContext it }

        val file = File(cacheDir, "$key.jpg")
        if (file.exists()) {
            val cached = try { file.readBytes() } catch (e: IOException) { null }
            if (cached != null && cached.isNotEmpty()) {
                synchronized(memory) { memory.put(key, cached) }
                return@withContext cached
            }
        }

        synchronized(misses) { if (key in misses) return@withContext null }

        val bytes = try {
            fetch(query)
        } catch (e: IOException) {
            Log.w(TAG, "Нет сети или сервис недоступен: ${e.message}")
            return@withContext null // ошибку сети не запоминаем - попробуем в следующий раз
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка поиска постера: $e")
            null
        }

        if (bytes != null) {
            synchronized(memory) { memory.put(key, bytes) }
            try { file.writeBytes(bytes) } catch (e: IOException) { /* кэш на диске не обязателен */ }
        } else {
            synchronized(misses) { misses.add(key) }
        }
        Log.d(TAG, "poster for '${query.title}' (${query.year}): ${bytes?.size ?: 0} bytes")
        bytes
    }

    /**
     * Пробует источники по очереди: Кинопоиск (новый адрес, потом старый), потом Википедия (ru, en). Если один источник ответил ошибкой
     * (кончилась квота, ключ не подошёл, таймаут, не скачалась картинка), идём к следующему, а не сдаёмся.
     * Если ничего не нашли и при этом были ошибки сети, бросаем ошибку: тогда "не нашли" не запоминается
     * и поиск повторится в следующий раз.
     */
    private fun fetch(query: PosterQuery): ByteArray? {
        val sources: List<Pair<String, () -> String?>> = listOf(
            "Кинопоиск v2.2" to { kinopoiskNew(query) },
            "Кинопоиск v2.1" to { kinopoisk(query) },
            "Википедия ru" to { wikipedia(query, "ru", listOf(" фильм", " сериал", "")) },
            "Википедия en" to { wikipedia(query, "en", listOf(" film", " TV series")) }
        )
        var lastError: IOException? = null
        for ((name, findUrl) in sources) {
            try {
                val imageUrl = findUrl() ?: continue
                val bytes = recode(httpGet(imageUrl))
                if (bytes != null) return bytes
            } catch (e: IOException) {
                Log.w(TAG, "$name не сработал: ${e.message}")
                lastError = e
            } catch (e: JSONException) {
                Log.w(TAG, "$name вернул непонятный ответ: ${e.message}")
            }
        }
        lastError?.let { throw it }
        return null
    }

    // ---------- Кинопоиск (только если указан токен) ----------

    private fun kinopoisk(query: PosterQuery): String? {
        if (PosterConfig.KINOPOISK_TOKEN.isBlank()) return null
        val url = "https://kinopoiskapiunofficial.tech/api/v2.1/films/search-by-keyword" +
            "?keyword=${Uri.encode(query.title)}&page=1"
        val json = JSONObject(String(httpGet(url, mapOf("X-API-KEY" to PosterConfig.KINOPOISK_TOKEN))))
        val arr = json.optJSONArray("films") ?: return null
        val films = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            KpFilm(
                nameRu = o.optString("nameRu").takeIf { it.isNotBlank() && it != "null" },
                nameEn = o.optString("nameEn").takeIf { it.isNotBlank() && it != "null" },
                year = o.optString("year").toIntOrNull(),
                posterUrl = o.optString("posterUrl").takeIf { it.isNotBlank() && it != "null" }
            )
        }
        return PosterTitle.pickKinopoisk(films, query)?.posterUrl
    }

    /** Новый адрес поиска Кинопоиска (v2.2): надёжнее старого, который иногда отвечает ошибкой 500 на отдельные названия. */
    private fun kinopoiskNew(query: PosterQuery): String? {
        if (PosterConfig.KINOPOISK_TOKEN.isBlank()) return null
        val url = "https://kinopoiskapiunofficial.tech/api/v2.2/films" +
            "?keyword=${Uri.encode(query.title)}&page=1"
        val json = JSONObject(String(httpGet(url, mapOf("X-API-KEY" to PosterConfig.KINOPOISK_TOKEN))))
        val arr = json.optJSONArray("items") ?: return null
        val films = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            KpFilm(
                nameRu = o.optString("nameRu").takeIf { it.isNotBlank() && it != "null" },
                nameEn = o.optString("nameEn").takeIf { it.isNotBlank() && it != "null" }
                    ?: o.optString("nameOriginal").takeIf { it.isNotBlank() && it != "null" },
                year = o.optString("year").toIntOrNull(),
                posterUrl = o.optString("posterUrl").takeIf { it.isNotBlank() && it != "null" }
            )
        }
        return PosterTitle.pickKinopoisk(films, query)?.posterUrl
    }

    // ---------- Википедия (ключ не нужен) ----------

    private fun wikipedia(query: PosterQuery, lang: String, suffixes: List<String>): String? {
        for (suffix in suffixes) {
            val url = "https://$lang.wikipedia.org/w/api.php?action=query&format=json" +
                "&generator=search&gsrsearch=${Uri.encode(query.title + suffix)}&gsrlimit=6&gsrnamespace=0" +
                "&prop=pageimages&piprop=thumbnail&pithumbsize=600&pilimit=6"
            val json = JSONObject(String(httpGet(url)))
            val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: continue
            val hits = pages.keys().asSequence().mapNotNull { id ->
                val p = pages.optJSONObject(id) ?: return@mapNotNull null
                val thumb = p.optJSONObject("thumbnail")
                WikiHit(
                    title = p.optString("title"),
                    imageUrl = thumb?.optString("source")?.takeIf { it.isNotBlank() },
                    width = thumb?.optInt("width") ?: 0,
                    height = thumb?.optInt("height") ?: 0,
                    index = p.optInt("index", Int.MAX_VALUE)
                )
            }.toList()
            PosterTitle.pickWikiHit(hits, query)?.imageUrl?.let { return it }
        }
        return null
    }

    // ---------- Сеть и картинки ----------

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_DOWNLOAD_BYTES) throw IOException("Слишком большой ответ")
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Уменьшаем до разумного размера и сохраняем как JPEG: для уведомления хватает ~720 px по высоте. */
    private fun recode(raw: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outHeight / (sample * 2) >= MAX_HEIGHT) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val scaled = if (decoded.height > MAX_HEIGHT) {
            val w = (decoded.width.toFloat() * MAX_HEIGHT / decoded.height).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, w, MAX_HEIGHT, true)
        } else decoded

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return out.toByteArray()
    }

    private fun cacheKey(query: PosterQuery): String {
        val src = PosterTitle.normalize(query.title) + "|" + (query.year ?: 0)
        val digest = MessageDigest.getInstance("SHA-1").digest(src.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "WledPoster"
        const val USER_AGENT = "WLEDRemote/1.0 (Android hobby app; poster lookup)"
        const val MAX_HEIGHT = 720
        const val MAX_DOWNLOAD_BYTES = 6_000_000
    }
}

/** Красивая картинка-заглушка (градиент и название), если постер не нашёлся. */
object PosterArt {

    /** По умолчанию картинка квадратная (для уведомления); для карточки на экране можно задать вертикальную. */
    fun generate(title: String, width: Int = 600, height: Int = 600): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Цвет зависит от названия, чтобы у разных фильмов фон был разный
        val hue = (abs(title.hashCode()) % 360).toFloat()
        val top = Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.46f))
        val bottom = Color.HSVToColor(floatArrayOf((hue + 40f) % 360f, 0.70f, 0.13f))
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width * 0.6f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

        val text = title.trim().ifEmpty { "WLED Remote" }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 58f
            isFakeBoldText = true
            setShadowLayer(8f, 0f, 3f, Color.argb(120, 0, 0, 0))
        }
        val padding = 56
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width - padding * 2)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(5)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(padding.toFloat(), (height - layout.height) / 2f)
        layout.draw(canvas)
        canvas.restore()

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}