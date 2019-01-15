package com.util.skin.library.res

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.util.skin.library.SkinManager
import com.util.skin.library.res.ColorState.Companion.checkColorValid
import com.util.skin.library.res.ColorState.Companion.toJSONObject
import com.util.skin.library.utils.SkinImageUtils
import com.util.skin.library.utils.SkinPreference
import com.util.skin.library.utils.Slog
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference
import java.util.*

object SkinUserThemeManager {

    private val mColorNameStateMap = HashMap<String, ColorState>()
    private val mColorCacheLock = java.lang.Object()
    private val mColorCaches = WeakHashMap<Int, WeakReference<ColorStateList>>()
    var isColorEmpty: Boolean = false
        private set

    private val mDrawablePathAndAngleMap = HashMap<String, String>()
    private val mDrawableCacheLock = java.lang.Object()
    private val mDrawableCaches = WeakHashMap<Int, WeakReference<Drawable>>()
    var isDrawableEmpty: Boolean = false
        private set

    private const val TAG = "SkinCompatUserThemeManager"
    private const val KEY_TYPE = "type"
    private const val KEY_TYPE_COLOR = "color"
    private const val KEY_TYPE_DRAWABLE = "drawable"
    private const val KEY_DRAWABLE_NAME = "drawableName"
    private const val KEY_DRAWABLE_PATH_AND_ANGLE = "drawablePathAndAngle"

    init {
        try {
            startLoadFromSharedPreferences()
        } catch (e: JSONException) {
            mColorNameStateMap.clear()
            mDrawablePathAndAngleMap.clear()
            if (Slog.DEBUG) {
                Slog.i(TAG, "startLoadFromSharedPreferences error: $e")
            }
        }

    }

    private fun checkPathValid(path: String): Boolean {
        val valid = !TextUtils.isEmpty(path) && File(path).exists()
        if (Slog.DEBUG && !valid) {
            Slog.i(TAG, "Invalid drawable path : $path")
        }
        return valid
    }

    @Throws(JSONException::class)
    private fun startLoadFromSharedPreferences() {
        val colors = SkinPreference.userTheme
        if (!TextUtils.isEmpty(colors)) {
            val jsonArray = JSONArray(colors)
            if (Slog.DEBUG) {
                Slog.i(TAG, "startLoadFromSharedPreferences: " + jsonArray.toString())
            }
            val count = jsonArray.length()
            for (i in 0 until count) {
                val jsonObject = jsonArray.getJSONObject(i)
                if (jsonObject.has(KEY_TYPE)) {
                    val type = jsonObject.getString(KEY_TYPE)
                    if (KEY_TYPE_COLOR == type) {
                        val state = ColorState.fromJSONObject(jsonObject)
                        if (state != null) {
                            mColorNameStateMap[state.colorName] = state
                        }
                    } else if (KEY_TYPE_DRAWABLE == type) {
                        val drawableName = jsonObject.getString(KEY_DRAWABLE_NAME)
                        val drawablePathAndAngle = jsonObject.getString(KEY_DRAWABLE_PATH_AND_ANGLE)
                        if (!TextUtils.isEmpty(drawableName) && !TextUtils.isEmpty(drawablePathAndAngle)) {
                            mDrawablePathAndAngleMap[drawableName] = drawablePathAndAngle
                        }
                    }
                }
            }
            isColorEmpty = mColorNameStateMap.isEmpty()
            isDrawableEmpty = mDrawablePathAndAngleMap.isEmpty()
        }
    }

    fun apply() {
        val jsonArray = JSONArray()
        for (colorName in mColorNameStateMap.keys) {
            val state = mColorNameStateMap[colorName]
            if (state != null) {
                try {
                    jsonArray.put(toJSONObject(state).putOpt(KEY_TYPE, KEY_TYPE_COLOR))
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

            }
        }
        for (drawableName in mDrawablePathAndAngleMap.keys) {
            val `object` = JSONObject()
            try {
                jsonArray.put(
                    `object`.putOpt(KEY_TYPE, KEY_TYPE_DRAWABLE)
                        .putOpt(KEY_DRAWABLE_NAME, drawableName)
                        .putOpt(KEY_DRAWABLE_PATH_AND_ANGLE, mDrawablePathAndAngleMap[drawableName])
                )
            } catch (e: JSONException) {
                e.printStackTrace()
            }

        }
        if (Slog.DEBUG) {
            Slog.i(TAG, "Apply user theme: " + jsonArray.toString())
        }
        SkinPreference.setUserTheme(jsonArray.toString()).commitEditor()
        SkinManager.notifyUpdateSkin()
    }

    fun addColorState(@ColorRes colorRes: Int, state: ColorState?) {
        val entry = getEntryName(colorRes, KEY_TYPE_COLOR)
        if (!TextUtils.isEmpty(entry) && state != null) {
            state.colorName = entry!!
            mColorNameStateMap[entry] = state
            removeColorInCache(colorRes)
            isColorEmpty = false
        }
    }

    fun addColorState(@ColorRes colorRes: Int, colorDefault: String) {
        if (!checkColorValid("colorDefault", colorDefault)) {
            return
        }
        val entry = getEntryName(colorRes, KEY_TYPE_COLOR)
        if (!TextUtils.isEmpty(entry)) {
            mColorNameStateMap[entry!!] = ColorState(entry, colorDefault)
            removeColorInCache(colorRes)
            isColorEmpty = false
        }
    }

    fun removeColorState(@ColorRes colorRes: Int) {
        val entry = getEntryName(colorRes, KEY_TYPE_COLOR)
        if (!TextUtils.isEmpty(entry)) {
            mColorNameStateMap.remove(entry)
            removeColorInCache(colorRes)
            isColorEmpty = mColorNameStateMap.isEmpty()
        }
    }

    internal fun removeColorState(colorName: String) {
        if (!TextUtils.isEmpty(colorName)) {
            mColorNameStateMap.remove(colorName)
            isColorEmpty = mColorNameStateMap.isEmpty()
        }
    }

    fun getColorState(colorName: String): ColorState? {
        return mColorNameStateMap[colorName]
    }

    fun getColorState(@ColorRes colorRes: Int): ColorState? {
        val entry = getEntryName(colorRes, KEY_TYPE_COLOR)
        return if (!TextUtils.isEmpty(entry)) {
            mColorNameStateMap[entry]
        } else null
    }

    fun getColorStateList(@ColorRes colorRes: Int): ColorStateList? {
        var colorStateList = getCachedColor(colorRes)
        if (colorStateList == null) {
            val entry = getEntryName(colorRes, KEY_TYPE_COLOR)
            if (!TextUtils.isEmpty(entry)) {
                val state = mColorNameStateMap[entry]
                if (state != null) {
                    colorStateList = state.parse()
                    if (colorStateList != null) {
                        addColorToCache(colorRes, colorStateList)
                    }
                }
            }
        }
        return colorStateList
    }

    fun addDrawablePath(@DrawableRes drawableRes: Int, drawablePath: String) {
        if (!checkPathValid(drawablePath)) {
            return
        }
        val entry = getEntryName(drawableRes, KEY_TYPE_DRAWABLE)
        if (!TextUtils.isEmpty(entry)) {
            val angle = SkinImageUtils.getImageRotateAngle(drawablePath)
            val drawablePathAndAngle = drawablePath + ":" + angle.toString()
            mDrawablePathAndAngleMap[entry!!] = drawablePathAndAngle
            removeDrawableInCache(drawableRes)
            isDrawableEmpty = false
        }
    }

    fun addDrawablePath(@DrawableRes drawableRes: Int, drawablePath: String, angle: Int) {
        if (!checkPathValid(drawablePath)) {
            return
        }
        val entry = getEntryName(drawableRes, KEY_TYPE_DRAWABLE)
        if (!TextUtils.isEmpty(entry)) {
            val drawablePathAndAngle = drawablePath + ":" + angle.toString()
            mDrawablePathAndAngleMap[entry!!] = drawablePathAndAngle
            removeDrawableInCache(drawableRes)
            isDrawableEmpty = false
        }
    }

    fun removeDrawablePath(@DrawableRes drawableRes: Int) {
        val entry = getEntryName(drawableRes, KEY_TYPE_DRAWABLE)
        if (!TextUtils.isEmpty(entry)) {
            mDrawablePathAndAngleMap.remove(entry)
            removeDrawableInCache(drawableRes)
            isDrawableEmpty = mDrawablePathAndAngleMap.isEmpty()
        }
    }

    fun getDrawablePath(drawableName: String): String {
        val drawablePathAndAngle = mDrawablePathAndAngleMap[drawableName]
        if (!TextUtils.isEmpty(drawablePathAndAngle)) {
            val splits = drawablePathAndAngle!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            return splits[0]
        }
        return ""
    }

    fun getDrawableAngle(drawableName: String): Int {
        val drawablePathAndAngle = mDrawablePathAndAngleMap[drawableName]
        if (!TextUtils.isEmpty(drawablePathAndAngle)) {
            val splits = drawablePathAndAngle!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (splits.size == 2) {
                return Integer.valueOf(splits[1])
            }
        }
        return 0
    }

    fun getDrawable(@DrawableRes drawableRes: Int): Drawable? {
        var drawable = getCachedDrawable(drawableRes)
        if (drawable == null) {
            val entry = getEntryName(drawableRes, KEY_TYPE_DRAWABLE)
            if (!TextUtils.isEmpty(entry)) {
                val drawablePathAndAngle = mDrawablePathAndAngleMap[entry]
                if (!TextUtils.isEmpty(drawablePathAndAngle)) {
                    val splits = drawablePathAndAngle!!
                        .split(":".toRegex())
                        .dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    val path = splits[0]
                    var angle = 0
                    if (splits.size == 2) {
                        angle = Integer.valueOf(splits[1])
                    }
                    if (checkPathValid(path)) {
                        if (angle == 0) {
                            drawable = Drawable.createFromPath(path)
                        } else {
                            val m = Matrix()
                            m.postRotate(angle.toFloat())
                            var bitmap = BitmapFactory.decodeFile(path)
                            bitmap = Bitmap.createBitmap(
                                bitmap, 0, 0,
                                bitmap.width, bitmap.height, m, true
                            )
                            drawable = BitmapDrawable(null, bitmap)
                        }
                        if (drawable != null) {
                            addDrawableToCache(drawableRes, drawable)
                        }
                    }
                }
            }
        }
        return drawable
    }

    fun clearColors() {
        mColorNameStateMap.clear()
        clearColorCaches()
        isColorEmpty = true
        apply()
    }

    fun clearDrawables() {
        mDrawablePathAndAngleMap.clear()
        clearDrawableCaches()
        isDrawableEmpty = true
        apply()
    }

    internal fun clearCaches() {
        clearColorCaches()
        clearDrawableCaches()
    }

    private fun clearColorCaches() {
        synchronized(mColorCacheLock) {
            mColorCaches.clear()
        }
    }

    private fun clearDrawableCaches() {
        synchronized(mDrawableCacheLock) {
            mDrawableCaches.clear()
        }
    }

    private fun getCachedColor(@ColorRes colorRes: Int): ColorStateList? {
        synchronized(mColorCacheLock) {
            val colorRef = mColorCaches[colorRes]
            if (colorRef != null) {
                val colorStateList = colorRef.get()
                if (colorStateList != null) {
                    return colorStateList
                } else {
                    mColorCaches.remove(colorRes)
                }
            }
        }
        return null
    }

    private fun addColorToCache(@ColorRes colorRes: Int, colorStateList: ColorStateList?) {
        if (colorStateList != null) {
            synchronized(mColorCacheLock) {
                mColorCaches.put(colorRes, WeakReference(colorStateList))
            }
        }
    }

    private fun removeColorInCache(@ColorRes colorRes: Int) {
        synchronized(mColorCacheLock) {
            mColorCaches.remove(colorRes)
        }
    }

    private fun getCachedDrawable(@DrawableRes drawableRes: Int): Drawable? {
        synchronized(mDrawableCacheLock) {
            val drawableRef = mDrawableCaches[drawableRes]
            if (drawableRef != null) {
                val drawable = drawableRef.get()
                if (drawable != null) {
                    return drawable
                } else {
                    mDrawableCaches.remove(drawableRes)
                }
            }
        }
        return null
    }

    private fun addDrawableToCache(@DrawableRes drawableRes: Int, drawable: Drawable?) {
        if (drawable != null) {
            synchronized(mDrawableCacheLock) {
                mDrawableCaches.put(drawableRes, WeakReference(drawable))
            }
        }
    }

    private fun removeDrawableInCache(@DrawableRes drawableRes: Int) {
        synchronized(mDrawableCacheLock) {
            mDrawableCaches.remove(drawableRes)
        }
    }

    private fun getEntryName(resId: Int, entryType: String): String? {
        val context = SkinManager.context
        return try {
            val type = context.resources.getResourceTypeName(resId)
            if (entryType.equals(type, ignoreCase = true)) {
                context.resources.getResourceEntryName(resId)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
