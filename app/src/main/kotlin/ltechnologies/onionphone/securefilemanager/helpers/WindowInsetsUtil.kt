package ltechnologies.onionphone.securefilemanager.helpers



import android.app.Activity

import android.util.TypedValue

import android.view.View

import android.view.ViewGroup

import androidx.appcompat.app.AppCompatActivity

import androidx.core.graphics.Insets

import androidx.core.view.ViewCompat

import androidx.core.view.WindowCompat

import androidx.core.view.WindowInsetsCompat

import androidx.drawerlayout.widget.DrawerLayout

import com.google.android.material.navigation.NavigationView



// ponytail: per-target insets — bottom nav gets bottom inset only, no double top bar

object WindowInsetsUtil {



    private fun barTypes(): Int =

        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()



    fun apply(activity: Activity) {

        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

            activity.window.isNavigationBarContrastEnforced = false

        }



        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        if (content.childCount == 0) return

        val root = content.getChildAt(0)



        val toolbar = findNamed(activity, "toolbar")

        val appBar = findNamed(activity, "app_bar")

        val topBar = appBar ?: toolbar

        val bottomNav = findNamed(activity, "bottom_navigation")

        val navDrawer = findNamed(activity, "navigation_view")

        val decorActionBar = hasDecorActionBar(activity)



        when (root) {

            is DrawerLayout -> applyDrawerLayout(root, topBar, navDrawer)

            else -> applyRootInsets(root, topBar, bottomNav, decorActionBar)

        }



        topBar?.let { applyTopBarInsets(it) }

        bottomNav?.let { applyBottomBarInsets(it) }

        if (navDrawer != null && root !is DrawerLayout) {

            applyFullBarInsets(navDrawer)

        }



        ViewCompat.requestApplyInsets(root)

    }



    private fun applyDrawerLayout(drawer: DrawerLayout, topBar: View?, navDrawer: View?) {

        dispatchInsets(drawer)

        for (i in 0 until drawer.childCount) {

            val child = drawer.getChildAt(i)

            if (child is NavigationView) {

                applyFullBarInsets(child)

            } else {

                applyRootInsets(child, topBar, null, false)

            }

        }

        if (navDrawer != null && navDrawer.parent != drawer) {

            applyFullBarInsets(navDrawer)

        }

    }



    private fun applyRootInsets(root: View, topBar: View?, bottomNav: View?, decorActionBar: Boolean) {

        val pl = root.paddingLeft

        val pt = root.paddingTop

        val pr = root.paddingRight

        val pb = root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->

            val bars = insets.getInsets(barTypes())

            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val top = when {

                decorActionBar && topBar == null -> bars.top + actionBarSize(view)

                !defersTopInsetToTopBar(root, topBar) -> bars.top

                else -> 0

            }

            val bottom = if (bottomNav != null) pb else pb + maxOf(bars.bottom, ime.bottom)

            view.setPadding(pl + bars.left, pt + top, pr + bars.right, bottom)

            insets

        }

    }



    private fun defersTopInsetToTopBar(root: View, topBar: View?): Boolean {

        if (topBar == null) return false

        var p: View? = topBar

        while (p != null) {

            if (p === root) return true

            p = p.parent as? View

        }

        return false

    }



    private fun applyTopBarInsets(topBar: View) {

        val pl = topBar.paddingLeft

        val pt = topBar.paddingTop

        val pr = topBar.paddingRight

        val pb = topBar.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->

            val bars = insets.getInsets(barTypes())

            view.setPadding(pl + bars.left, pt + bars.top, pr + bars.right, pb)

            insets

        }

    }



    /** Bottom nav / system bar at screen bottom — never add status-bar top inset here. */

    private fun applyBottomBarInsets(view: View) {

        val pl = view.paddingLeft

        val pt = view.paddingTop

        val pr = view.paddingRight

        val pb = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->

            val bars = insets.getInsets(barTypes())

            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val bottom = maxOf(bars.bottom, ime.bottom)

            v.setPadding(pl + bars.left, pt, pr + bars.right, pb + bottom)

            insets

        }

    }



    private fun applyFullBarInsets(view: View) {

        val pl = view.paddingLeft

        val pt = view.paddingTop

        val pr = view.paddingRight

        val pb = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->

            val bars = insets.getInsets(barTypes())

            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val bottom = maxOf(bars.bottom, ime.bottom)

            v.setPadding(pl + bars.left, pt + bars.top, pr + bars.right, pb + bottom)

            insets

        }

    }



    private fun dispatchInsets(view: View) {

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets -> insets }

    }



    private fun hasDecorActionBar(activity: Activity): Boolean {

        val tv = TypedValue()

        val themeWantsBar = activity.theme.resolveAttribute(android.R.attr.windowActionBar, tv, true)

                && tv.data != 0

        if (!themeWantsBar) return false

        val bar = (activity as? AppCompatActivity)?.supportActionBar ?: return false

        return bar.isShowing

    }



    private fun actionBarSize(view: View): Int {

        val tv = TypedValue()

        return if (view.context.theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {

            TypedValue.complexToDimensionPixelSize(tv.data, view.resources.displayMetrics)

        } else {

            0

        }

    }



    private fun findNamed(activity: Activity, name: String): View? {

        val id = activity.resources.getIdentifier(name, "id", activity.packageName)

        return if (id != 0) activity.findViewById(id) else null

    }

}

