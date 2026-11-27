package com.spendwise.app;

import android.animation.ObjectAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ProgressBar;

public class UIAnimator {

    /**
     * Attaches a tactile spring press-and-release bounce animation to any clickable view
     * without interfering with its OnClickListener.
     */
    public static void attachPressBounce(View... views) {
        if (views == null) return;
        for (View v : views) {
            if (v == null) continue;
            v.setOnTouchListener((view, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate()
                                .scaleX(0.94f)
                                .scaleY(0.94f)
                                .setDuration(90)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(220)
                                .setInterpolator(new OvershootInterpolator(1.8f))
                                .start();
                        break;
                }
                return false;
            });
        }
    }

    /**
     * Smoothly animates a view's entrance (fade + slide up).
     */
    public static void animateEntrance(View view, long delayMs) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(28f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(360)
                .setStartDelay(delayMs)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    /**
     * Staggers entrance animations across all direct children of a container.
     */
    public static void animateChildrenStaggered(ViewGroup container, long stepDelayMs) {
        if (container == null) return;
        int count = Math.min(container.getChildCount(), 8);
        for (int i = 0; i < count; i++) {
            View child = container.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                animateEntrance(child, i * stepDelayMs);
            }
        }
    }

    /**
     * Smoothly fills a ProgressBar from 0 to target.
     */
    public static void animateProgress(ProgressBar pb, int target) {
        if (pb == null) return;
        ObjectAnimator anim = ObjectAnimator.ofInt(pb, "progress", 0, target);
        anim.setDuration(550);
        anim.setInterpolator(new DecelerateInterpolator(1.5f));
        anim.start();
    }

    /**
     * Quick pop animation for interactive icons/tabs.
     */
    public static void popView(View view) {
        if (view == null) return;
        view.animate()
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setDuration(110)
                .withEndAction(() -> view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(180)
                        .setInterpolator(new OvershootInterpolator())
                        .start())
                .start();
    }
}
