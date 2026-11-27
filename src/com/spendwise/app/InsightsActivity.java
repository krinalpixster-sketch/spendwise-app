package com.spendwise.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InsightsActivity extends Activity {

    private SpendWiseDbHelper db;
    private boolean isPrivacyMode;
    private String selectedMonth;
    private NumberFormat currencyFormat;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeHelper.applyThemeContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_insights);

        db = new SpendWiseDbHelper(this);
        SharedPreferences prefs = getSharedPreferences("spendwise_prefs", MODE_PRIVATE);
        isPrivacyMode = prefs.getBoolean("privacy_mode", false);
        currencyFormat = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        currencyFormat.setMaximumFractionDigits(0);

        String passedMonth = getIntent().getStringExtra("selected_month");
        String currentYm = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
        if (passedMonth == null || passedMonth.trim().isEmpty() || "ALL".equalsIgnoreCase(passedMonth)) {
            selectedMonth = currentYm;
        } else {
            selectedMonth = passedMonth;
        }

        View btnBack = findViewById(R.id.btn_insights_back);
        btnBack.setOnClickListener(v -> finish());
        UIAnimator.attachPressBounce(btnBack);

        TextView tvMonthPill = findViewById(R.id.tv_insights_month_pill);
        tvMonthPill.setText(formatMonthLabel(selectedMonth));

        refreshInsights();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshInsights();
    }

    private void refreshInsights() {
        SharedPreferences prefs = getSharedPreferences("spendwise_prefs", MODE_PRIVATE);
        isPrivacyMode = prefs.getBoolean("privacy_mode", false);
        String currentYm = new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
        renderMonthComparison();
        renderMonthEndForecast(currentYm);
        renderWeeklyPattern();
    }

    private void renderMonthComparison() {
        String prevMonth = getPreviousMonth(selectedMonth);
        double currSpent = db.getTotalSpentByMonth(selectedMonth);
        double prevSpent = db.getTotalSpentByMonth(prevMonth);

        TextView tvTitle = findViewById(R.id.tv_mom_title);
        TextView tvBadge = findViewById(R.id.tv_mom_badge);
        TextView tvCurrLabel = findViewById(R.id.tv_mom_curr_label);
        TextView tvPrevLabel = findViewById(R.id.tv_mom_prev_label);
        TextView tvCurrAmt = findViewById(R.id.tv_mom_curr_amt);
        TextView tvPrevAmt = findViewById(R.id.tv_mom_prev_amt);
        ProgressBar pbCurr = findViewById(R.id.pb_mom_curr);
        ProgressBar pbPrev = findViewById(R.id.pb_mom_prev);
        TextView tvSummary = findViewById(R.id.tv_mom_summary);

        String currName = formatMonthLabel(selectedMonth);
        String prevName = formatMonthLabel(prevMonth);
        tvTitle.setText(currName + " vs " + prevName);
        tvCurrLabel.setText(currName);
        tvPrevLabel.setText(prevName);

        tvCurrAmt.setText(formatMoney(currSpent));
        tvPrevAmt.setText(formatMoney(prevSpent));

        double maxVal = Math.max(currSpent, prevSpent);
        if (maxVal <= 0) {
            pbCurr.setProgress(0);
            pbPrev.setProgress(0);
        } else {
            pbCurr.setProgress((int) Math.round((currSpent / maxVal) * 100));
            pbPrev.setProgress((int) Math.round((prevSpent / maxVal) * 100));
        }

        if (prevSpent <= 0 && currSpent <= 0) {
            tvBadge.setText("No Data");
            tvBadge.setTextColor(getColor(R.color.text_secondary));
            tvSummary.setText("No transactions recorded yet for these months.");
        } else if (prevSpent <= 0) {
            tvBadge.setText("First Active Month");
            tvBadge.setTextColor(getColor(R.color.accent_indigo));
            tvSummary.setText("No expenses recorded in " + prevName + " to compare against.");
        } else {
            double diff = currSpent - prevSpent;
            double pct = Math.abs((diff / prevSpent) * 100.0);
            if (diff <= 0) {
                tvBadge.setText("↓ " + String.format(Locale.US, "%.0f%%", pct) + " Less");
                tvBadge.setTextColor(getColor(R.color.accent_emerald));
                tvSummary.setText("Great discipline! You spent " + formatMoney(Math.abs(diff)) + " less than " + prevName + ".");
            } else {
                tvBadge.setText("↑ " + String.format(Locale.US, "%.0f%%", pct) + " More");
                tvBadge.setTextColor(getColor(R.color.accent_rose));
                tvSummary.setText("Spending is up by " + formatMoney(diff) + " compared to " + prevName + ".");
            }
        }
    }

    private void renderMonthEndForecast(String currentYm) {
        double spent = db.getTotalSpentByMonth(selectedMonth);
        double budget = db.getBudget();

        Calendar cal = parseMonthCalendar(selectedMonth);
        int totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int daysElapsed;
        boolean isCurrentMonth = selectedMonth.equals(currentYm);

        if (isCurrentMonth) {
            daysElapsed = Math.max(1, Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
        } else {
            daysElapsed = totalDays;
        }

        double dailyPace = spent / daysElapsed;
        double projected = isCurrentMonth ? (dailyPace * totalDays) : spent;

        TextView tvProjected = findViewById(R.id.tv_forecast_projected);
        TextView tvStatus = findViewById(R.id.tv_forecast_status);
        ProgressBar pbForecast = findViewById(R.id.pb_forecast);
        TextView tvPace = findViewById(R.id.tv_forecast_pace);
        TextView tvBudget = findViewById(R.id.tv_forecast_budget);
        TextView tvNote = findViewById(R.id.tv_forecast_note);

        tvProjected.setText(formatMoney(projected));
        tvPace.setText("Daily Pace: " + formatMoney(dailyPace) + "/day");
        tvBudget.setText("Budget: " + formatMoney(budget));

        int pct = budget > 0 ? (int) Math.min(100, Math.round((projected / budget) * 100)) : 0;
        pbForecast.setProgress(pct);

        if (projected <= budget) {
            double underBy = budget - projected;
            tvStatus.setText("Within Budget");
            tvStatus.setTextColor(getColor(R.color.accent_emerald));
            pbForecast.getProgressDrawable().setTint(getColor(R.color.accent_emerald));
            if (isCurrentMonth) {
                tvNote.setText("At your current pace (" + daysElapsed + "/" + totalDays + " days), you are projected to save " + formatMoney(underBy) + " under your monthly target.");
            } else {
                tvNote.setText("You finished " + formatMonthLabel(selectedMonth) + " safely " + formatMoney(underBy) + " under your monthly budget.");
            }
        } else {
            double overBy = projected - budget;
            tvStatus.setText("Over Forecast");
            tvStatus.setTextColor(getColor(R.color.accent_rose));
            pbForecast.getProgressDrawable().setTint(getColor(R.color.accent_rose));
            if (isCurrentMonth) {
                tvNote.setText("Heads up! At this daily pace, you may exceed your monthly budget by " + formatMoney(overBy) + ".");
            } else {
                tvNote.setText("Spending in " + formatMonthLabel(selectedMonth) + " exceeded your target by " + formatMoney(overBy) + ".");
            }
        }
    }

    private void renderWeeklyPattern() {
        List<Expense> expenses = db.getExpensesByMonth(selectedMonth);
        // Index 0 = Mon, 1 = Tue, ..., 6 = Sun
        double[] dayTotals = new double[7];
        String[] dayLabels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();

        for (Expense e : expenses) {
            if (e.date == null) continue;
            try {
                Date d = sdf.parse(e.date.trim());
                if (d != null) {
                    cal.setTime(d);
                    int dow = cal.get(Calendar.DAY_OF_WEEK); // Sun=1, Mon=2 ... Sat=7
                    int idx = (dow == Calendar.SUNDAY) ? 6 : (dow - Calendar.MONDAY);
                    if (idx >= 0 && idx < 7) {
                        dayTotals[idx] += e.amount;
                    }
                }
            } catch (Exception ignored) {}
        }

        double maxDay = 0;
        int peakIdx = 0;
        double weekdaySum = 0;
        double weekendSum = 0;

        for (int i = 0; i < 7; i++) {
            if (dayTotals[i] > maxDay) {
                maxDay = dayTotals[i];
                peakIdx = i;
            }
            if (i < 5) {
                weekdaySum += dayTotals[i];
            } else {
                weekendSum += dayTotals[i];
            }
        }

        TextView tvPeakBadge = findViewById(R.id.tv_weekly_peak_badge);
        TextView tvWeekdayShare = findViewById(R.id.tv_weekday_share);
        TextView tvWeekendShare = findViewById(R.id.tv_weekend_share);
        LinearLayout container = findViewById(R.id.layout_weekly_bars);
        container.removeAllViews();

        if (maxDay <= 0) {
            tvPeakBadge.setText("Peak: —");
        } else {
            tvPeakBadge.setText("Peak: " + dayLabels[peakIdx]);
        }

        double total = weekdaySum + weekendSum;
        if (total > 0) {
            int wdPct = (int) Math.round((weekdaySum / total) * 100);
            int wePct = 100 - wdPct;
            tvWeekdayShare.setText("Weekdays: " + wdPct + "% (" + formatMoney(weekdaySum) + ")");
            tvWeekendShare.setText("Weekends: " + wePct + "% (" + formatMoney(weekendSum) + ")");
        } else {
            tvWeekdayShare.setText("Weekdays: 0%");
            tvWeekendShare.setText("Weekends: 0%");
        }

        int maxBarHeightPx = dpToPx(105);
        int minBarHeightPx = dpToPx(6);

        for (int i = 0; i < 7; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            col.setLayoutParams(colParams);

            // Amount label on top of bar
            TextView tvVal = new TextView(this);
            if (dayTotals[i] > 0) {
                tvVal.setText(isPrivacyMode ? "₹**" : formatCompactAmount(dayTotals[i]));
            } else {
                tvVal.setText("");
            }
            tvVal.setTextSize(10);
            tvVal.setTextColor(i == peakIdx && maxDay > 0 ? getColor(R.color.accent_indigo) : getColor(R.color.text_secondary));
            tvVal.setGravity(Gravity.CENTER);
            tvVal.setPadding(0, 0, 0, dpToPx(4));
            col.addView(tvVal);

            // Bar view
            View bar = new View(this);
            int barHeight = (maxDay > 0 && dayTotals[i] > 0)
                    ? Math.max(minBarHeightPx, (int) Math.round((dayTotals[i] / maxDay) * maxBarHeightPx))
                    : minBarHeightPx;
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dpToPx(22), barHeight);
            bar.setLayoutParams(barParams);

            GradientDrawable gd = new GradientDrawable();
            float r = dpToPx(6);
            gd.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            if (dayTotals[i] <= 0) {
                gd.setColor(getColor(R.color.progress_track));
            } else if (i == peakIdx) {
                gd.setColor(getColor(R.color.accent_indigo));
            } else if (i >= 5) {
                gd.setColor(getColor(R.color.accent_cyan));
            } else {
                gd.setColor(getColor(R.color.accent_emerald));
            }
            bar.setBackground(gd);
            bar.setPivotY(barHeight);
            bar.setScaleY(0f);
            bar.animate()
                    .scaleY(1f)
                    .setDuration(420)
                    .setStartDelay(i * 45L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                    .start();
            col.addView(bar);

            // Day label at bottom
            TextView tvDay = new TextView(this);
            tvDay.setText(dayLabels[i]);
            tvDay.setTextSize(11);
            tvDay.setTypeface(null, (i == peakIdx && maxDay > 0) ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tvDay.setTextColor((i == peakIdx && maxDay > 0) ? getColor(R.color.text_primary) : getColor(R.color.text_secondary));
            tvDay.setGravity(Gravity.CENTER);
            tvDay.setPadding(0, dpToPx(6), 0, 0);
            col.addView(tvDay);

            container.addView(col);
        }
    }

    private String formatMoney(double amount) {
        if (isPrivacyMode) {
            return "₹****";
        }
        return "₹" + currencyFormat.format(Math.round(amount));
    }

    private String formatCompactAmount(double amount) {
        if (amount >= 1000) {
            return String.format(Locale.US, "%.1fk", amount / 1000.0);
        }
        return String.valueOf(Math.round(amount));
    }

    private Calendar parseMonthCalendar(String ym) {
        Calendar cal = Calendar.getInstance();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM", Locale.US);
            Date d = sdf.parse(ym);
            if (d != null) cal.setTime(d);
        } catch (Exception ignored) {}
        return cal;
    }

    private String getPreviousMonth(String ym) {
        Calendar cal = parseMonthCalendar(ym);
        cal.add(Calendar.MONTH, -1);
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(cal.getTime());
    }

    private String formatMonthLabel(String ym) {
        try {
            SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM", Locale.US);
            SimpleDateFormat outFmt = new SimpleDateFormat("MMM yyyy", Locale.US);
            Date d = inFmt.parse(ym);
            if (d != null) return outFmt.format(d);
        } catch (Exception ignored) {}
        return ym;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
