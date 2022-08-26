package com.util.skin.library.app

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import com.util.skin.library.SkinManager
import com.util.skin.library.widget.SkinSupportable
import java.lang.ref.WeakReference

/**
 * [LayoutInflater.onCreateView] 方法监听并转发
 */
internal class SkinDelegate private constructor(private val mContext: Context) :
    LayoutInflater.Factory2 {
    private val mSkinHelpers = ArrayList<WeakReference<SkinSupportable>>()

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        return null
    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        val view = createView(parent, name, context, attrs) ?: return null

        if (view is SkinSupportable && view.skinnable) {
            // 记录支持换肤功能的View
            mSkinHelpers.add(WeakReference(view as SkinSupportable))
        }

        return view
    }

    private fun createView(
        parent: View?, name: String, context: Context,
        attrs: AttributeSet
    ): View? {
        var tempContext = context
        val wrapperList = SkinManager.wrappers
        for (wrapper in wrapperList) {
            val wrappedContext = wrapper.wrapContext(mContext, parent, attrs)
            tempContext = wrappedContext
        }
        return SkinViewInflater.createView(name, tempContext, attrs)
    }

    fun applySkin() {
        if (!mSkinHelpers.isEmpty()) {
            for (ref in mSkinHelpers) {
                ref.get()?.apply {
                    if (skinnable) {
                        applySkin()
                    }
                }
            }
        }
    }

    companion object {

        fun create(context: Context): SkinDelegate {
            return SkinDelegate(context)
        }
    }
}
