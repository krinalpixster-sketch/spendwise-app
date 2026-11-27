package com.spendwise.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewFlipper;

public class OnboardingActivity extends Activity {

    private static final int PERMISSION_REQ_CODE = 101;

    private ViewFlipper viewFlipper;
    private TextView tvStepIndicator;
    private TextView btnSkip;
    private Button btnPrev;
    private Button btnNext;

    private View dotStep1;
    private View dotStep2;
    private View dotStep3;

    private int currentStep = 0;
    private GestureDetector gestureDetector;
    private SharedPreferences prefs;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeHelper.applyThemeContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        prefs = getSharedPreferences("spendwise_prefs", MODE_PRIVATE);

        viewFlipper = findViewById(R.id.view_flipper_onboarding);
        tvStepIndicator = findViewById(R.id.tv_step_indicator);
        btnSkip = findViewById(R.id.btn_skip);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);

        dotStep1 = findViewById(R.id.dot_step_1);
        dotStep2 = findViewById(R.id.dot_step_2);
        dotStep3 = findViewById(R.id.dot_step_3);

        setupButtons();
        setupGestures();
        updateStepUI(0);
    }

    private void setupButtons() {
        btnNext.setOnClickListener(v -> {
            if (currentStep < 2) {
                goToStep(currentStep + 1);
            } else {
                finishOnboarding();
            }
        });

        btnPrev.setOnClickListener(v -> {
            if (currentStep > 0) {
                goToStep(currentStep - 1);
            }
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());
    }

    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 80;
            private static final int SWIPE_VELOCITY_THRESHOLD = 80;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX < 0) {
                            // Swipe Left -> Next
                            if (currentStep < 2) {
                                goToStep(currentStep + 1);
                                return true;
                            }
                        } else {
                            // Swipe Right -> Prev
                            if (currentStep > 0) {
                                goToStep(currentStep - 1);
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null && gestureDetector.onTouchEvent(ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void goToStep(int targetStep) {
        if (targetStep == currentStep) return;

        if (targetStep > currentStep) {
            viewFlipper.setInAnimation(this, R.anim.slide_in_right);
            viewFlipper.setOutAnimation(this, R.anim.slide_out_left);
            viewFlipper.setDisplayedChild(targetStep);
        } else {
            viewFlipper.setInAnimation(this, R.anim.slide_in_left);
            viewFlipper.setOutAnimation(this, R.anim.slide_out_right);
            viewFlipper.setDisplayedChild(targetStep);
        }

        currentStep = targetStep;
        updateStepUI(currentStep);
    }

    private void updateStepUI(int step) {
        tvStepIndicator.setText("STEP " + (step + 1) + " OF 3");

        if (step == 0) {
            btnPrev.setVisibility(View.GONE);
            btnNext.setText("Continue →");
        } else if (step == 1) {
            btnPrev.setVisibility(View.VISIBLE);
            btnNext.setText("Continue →");
        } else {
            btnPrev.setVisibility(View.VISIBLE);
            btnNext.setText("Get Started 🚀");
        }

        updateIndicator(dotStep1, step == 0);
        updateIndicator(dotStep2, step == 1);
        updateIndicator(dotStep3, step == 2);
    }

    private void updateIndicator(View dot, boolean isActive) {
        ViewGroup.LayoutParams lp = dot.getLayoutParams();
        int activeWidth = dpToPx(28);
        int inactiveWidth = dpToPx(8);

        lp.width = isActive ? activeWidth : inactiveWidth;
        dot.setLayoutParams(lp);
        dot.setBackgroundResource(isActive ? R.drawable.indicator_active : R.drawable.indicator_inactive);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void finishOnboarding() {
        prefs.edit().putBoolean("onboarding_completed", true).apply();

        // Check SMS permissions
        boolean hasSms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        if (!hasSms) {
            requestSmsPermissions();
        } else {
            proceedToMain();
        }
    }

    private void requestSmsPermissions() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms = new String[]{
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            perms = new String[]{
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
            };
        }
        requestPermissions(perms, PERMISSION_REQ_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        proceedToMain();
    }

    private void proceedToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
