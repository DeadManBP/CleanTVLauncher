package ca.cleantvlauncher

import android.app.Activity
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream

/** A simple DPAD-friendly image browser using MediaStore, not the Files app. */
class WallpaperBrowserActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("clean_tv_prefs", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(40), dp(24), dp(40), dp(24))
            setBackgroundColor(Color.rgb(15, 16, 20))
        }
        root.addView(TextView(this).apply {
            text = "Choose a wallpaper"
            textSize = 30f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "Select an image using the remote. Newest images appear first."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(18))
        })
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = false
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            isFocusableInTouchMode = false
            addView(list)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        val images = queryImages()
        if (images.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No images found. Make sure the wallpaper image is saved on the TV and CleanTV has storage access."
                textSize = 20f
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(20), dp(12), dp(20))
            })
            return
        }
        images.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isFocusableInTouchMode = true
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setBackgroundColor(Color.rgb(35, 36, 42))
                layoutParams = LinearLayout.LayoutParams(-1, dp(112)).apply {
                    bottomMargin = dp(8)
                }
            }
            val preview = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(dp(150), dp(92))
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        setImageBitmap(contentResolver.loadThumbnail(item.uri, android.util.Size(300, 184), null))
                    } else {
                        contentResolver.openInputStream(item.uri).use { setImageBitmap(android.graphics.BitmapFactory.decodeStream(it)) }
                    }
                } catch (_: Exception) {
                    setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
            row.addView(preview)
            row.addView(TextView(this).apply {
                text = item.name
                textSize = 18f
                setTextColor(Color.WHITE)
                maxLines = 2
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            })
            row.setOnFocusChangeListener { view, focused ->
                view.setBackgroundColor(if (focused) Color.rgb(190, 42, 55) else Color.rgb(35, 36, 42))
                view.scaleX = if (focused) 1.015f else 1f
                view.scaleY = if (focused) 1.015f else 1f
            }
            row.setOnClickListener { importImage(item.uri) }
            list.addView(row)
            if (index == 0) row.tag = "first-image"
        }
        list.post { list.findViewWithTag<View>("first-image")?.requestFocus() }
    }

    private data class ImageItem(val uri: Uri, val name: String)

    private fun queryImages(): List<ImageItem> {
        val result = mutableListOf<ImageItem>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        try {
            contentResolver.query(
                collection, projection, null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Image"
                    result.add(ImageItem(ContentUris.withAppendedId(collection, id), name))
                }
            }
        } catch (_: SecurityException) { }
        return result
    }

    private fun importImage(uri: Uri) {
        try {
            val destination = File(filesDir, "selected-wallpaper")
            contentResolver.openInputStream(uri).use { input ->
                if (input == null) return
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
            prefs.edit()
                .putString("wallpaper_file_path", destination.absolutePath)
                .remove("wallpaper_uri")
                .apply()
            setResult(Activity.RESULT_OK)
            finish()
        } catch (_: Exception) {
            // Keep the previous wallpaper if the selected image cannot be read.
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
