package com.anland.consumer;

import android.app.Activity;

import java.lang.ref.WeakReference;

/**
 * Tracks the desktop window the user is working in.
 *
 * <p>Every desktop window is either {@link MainActivity} or {@link SecondaryActivity} (which
 * extends it), each owning its own native pipeline and HUD. Settings therefore cannot just
 * launch {@code MainActivity}: that would focus or create the primary window, i.e. a different
 * session from the one the user is looking at. It asks whichever window was last on screen and
 * is still alive instead.
 *
 * <p>The reference deliberately survives {@code onPause}: opening Settings pauses the desktop
 * window, and the editor still has to come back to it. It is only dropped when the window is
 * actually finishing or destroyed.
 */
final class DesktopWindows {

    /** A live desktop window that can open the HUD editor in place. */
    interface Editor {
        void enterHudEditor();
    }

    private static WeakReference<Activity> sLastDesktop = new WeakReference<>(null);

    private DesktopWindows() {}

    static void onResumed(Activity activity) {
        if (activity instanceof Editor) sLastDesktop = new WeakReference<>(activity);
    }

    /** The last desktop window that is still alive, or null when no session is running. */
    static Activity window() {
        Activity activity = sLastDesktop.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return null;
        return activity;
    }

    /** That window as an editor, or null when there is nothing to edit in place. */
    static Editor editor() {
        Activity activity = window();
        return activity instanceof Editor ? (Editor) activity : null;
    }
}
