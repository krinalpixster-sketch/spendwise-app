package com.spendwise.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int PERMISSION_REQ_CODE = 101;

    private SpendWiseDbHelper db;
    private TextView tvTotalSpent;
    private TextView tvBudgetCaption;
    private TextView tvRemainingBalance;
    private TextView tvBudgetPct;
    private TextView tvBadgeStatus;
    private ProgressBar pbBudget;
    private LinearLayout layoutCategoryBars;
    private LinearLayout layoutTransactions;
    private View layoutEmptyState;
    private TextView tvTransactionCount;
    private View bannerPermission;
    private TextView btnMonthSelector;
    private String selectedMonth;

    private TextView btnPrivacyToggle;
    private boolean isPrivacyMode = false;
    private LinearLayout btnDailySafeAllowance;
    private TextView tvDailySafeTitle;
    private TextView tvDailySafeSub;
    private TextView tvDailySafeAmount;
    private LinearLayout btnChhotaKharcha;
    private TextView tvChhotaKharchaAmount;
    private TextView tvChhotaKharchaSub;
    private LinearLayout btnNoSpendStreak;
    private TextView tvNoSpendDays;
    private TextView tvNoSpendSub;
    private LinearLayout layoutBankBars;
    private boolean isMicroFilterActive = false;

    private TextView tvMetricDailyAvg;
    private TextView tvMetricHighest;
    private TextView tvMetricTxCount;
    private View layoutSearchContainer;
    private EditText etSearch;
    private TextView btnClearSearch;
    private LinearLayout layoutFilterChips;
    private TextView tvEmptyEmoji;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;
    private String selectedCategory = "All";
    private String searchQuery = "";
    private String appliedThemeMode;

    private LinearLayout layoutUndoBanner;
    private TextView tvUndoMessage;
    private TextView btnUndoDelete;
    private Expense lastDeletedExpense;
    private final android.os.Handler undoHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable hideUndoRunnable = () -> {
        if (layoutUndoBanner != null) {
            layoutUndoBanner.setVisibility(View.GONE);
        }
        lastDeletedExpense = null;
    };

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUI();
            if (intent != null && intent.getBooleanExtra("is_over_budget", false)) {
                double overBy = intent.getDoubleExtra("over_by", 0.0);
                String merchant = intent.getStringExtra("merchant");
                double amt = intent.getDoubleExtra("amount", 0.0);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("🚨 Budget Exceeded Warning!")
                        .setMessage("Your recent transaction of ₹" + String.format(Locale.US, "%,.2f", amt) + " at " + merchant + " has pushed your spending OVER your monthly budget limit by ₹" + String.format(Locale.US, "%,.2f", overBy) + "!\n\nPlease review your spending target.")
                        .setPositiveButton("Adjust Budget", (d, w) -> promptChangeBudget())
                        .setNegativeButton("OK", null)
                        .show();
            }
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeHelper.applyThemeContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        appliedThemeMode = ThemeHelper.getThemeMode(this);
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("spendwise_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("onboarding_completed", false)) {
            Intent intent = new Intent(this, OnboardingActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        db = new SpendWiseDbHelper(this);

        tvTotalSpent = findViewById(R.id.tv_total_spent);
        tvBudgetCaption = findViewById(R.id.tv_budget_caption);
        tvRemainingBalance = findViewById(R.id.tv_remaining_balance);
        tvBudgetPct = findViewById(R.id.tv_budget_pct);
        tvBadgeStatus = findViewById(R.id.tv_badge_status);
        pbBudget = findViewById(R.id.pb_budget);
        layoutCategoryBars = findViewById(R.id.layout_category_bars);
        layoutTransactions = findViewById(R.id.layout_transactions);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        tvTransactionCount = findViewById(R.id.tv_transaction_count);
        bannerPermission = findViewById(R.id.banner_permission);

        tvMetricDailyAvg = findViewById(R.id.tv_metric_daily_avg);
        tvMetricHighest = findViewById(R.id.tv_metric_highest);
        tvMetricTxCount = findViewById(R.id.tv_metric_tx_count);
        layoutSearchContainer = findViewById(R.id.layout_search_container);
        etSearch = findViewById(R.id.et_search_transactions);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        layoutFilterChips = findViewById(R.id.layout_filter_chips);

        tvEmptyEmoji = findViewById(R.id.tv_empty_emoji);
        tvEmptyTitle = findViewById(R.id.tv_empty_title);
        tvEmptySubtitle = findViewById(R.id.tv_empty_subtitle);

        layoutUndoBanner = findViewById(R.id.layout_undo_banner);
        tvUndoMessage = findViewById(R.id.tv_undo_message);
        btnUndoDelete = findViewById(R.id.btn_undo_delete);
        if (btnUndoDelete != null) {
            btnUndoDelete.setOnClickListener(v -> undoLastDelete());
        }

        btnMonthSelector = findViewById(R.id.btn_month_selector);
        selectedMonth = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
        btnMonthSelector.setText(formatMonthDisplay(selectedMonth) + " ▾");
        btnMonthSelector.setOnClickListener(v -> showMonthPicker());

        setupSearchAndFilters();

        Button btnGrant = findViewById(R.id.btn_grant_permission);
        btnGrant.setOnClickListener(v -> requestPermissions());

        tvBadgeStatus.setOnClickListener(v -> showBudgetStatusExplanation());

        // Privacy Mode Toggle
        isPrivacyMode = getSharedPreferences("spendwise_prefs", MODE_PRIVATE).getBoolean("privacy_mode", false);
        btnPrivacyToggle = findViewById(R.id.btn_privacy_toggle);
        if (btnPrivacyToggle != null) {
            btnPrivacyToggle.setText(isPrivacyMode ? "🔒" : "👁️");
            btnPrivacyToggle.setOnClickListener(v -> togglePrivacyMode());
        }

        // Aaj Ka Safe Kharcha
        btnDailySafeAllowance = findViewById(R.id.btn_daily_safe_allowance);
        tvDailySafeTitle = findViewById(R.id.tv_daily_safe_title);
        tvDailySafeSub = findViewById(R.id.tv_daily_safe_sub);
        tvDailySafeAmount = findViewById(R.id.tv_daily_safe_amount);
        if (btnDailySafeAllowance != null) {
            btnDailySafeAllowance.setOnClickListener(v -> showDailySafeAllowanceDialog());
        }

        // Chhota Kharcha Micro Spend Detector
        btnChhotaKharcha = findViewById(R.id.btn_chhota_kharcha);
        tvChhotaKharchaAmount = findViewById(R.id.tv_chhota_kharcha_amount);
        tvChhotaKharchaSub = findViewById(R.id.tv_chhota_kharcha_sub);
        if (btnChhotaKharcha != null) {
            btnChhotaKharcha.setOnClickListener(v -> showChhotaKharchaDialog());
        }

        // Zero-Spend Days Streak
        btnNoSpendStreak = findViewById(R.id.btn_no_spend_streak);
        tvNoSpendDays = findViewById(R.id.tv_no_spend_days);
        tvNoSpendSub = findViewById(R.id.tv_no_spend_sub);
        if (btnNoSpendStreak != null) {
            btnNoSpendStreak.setOnClickListener(v -> showNoSpendStreakDialog());
        }

        // Bank Breakdown
        layoutBankBars = findViewById(R.id.layout_bank_bars);

        // Quick Action Buttons
        findViewById(R.id.btn_action_add).setOnClickListener(v -> showAddExpenseDialog());
        findViewById(R.id.btn_action_budget).setOnClickListener(v -> promptChangeBudget());
        findViewById(R.id.btn_action_test).setOnClickListener(v -> simulateRandomTransaction());
        findViewById(R.id.btn_action_clear).setOnClickListener(v -> confirmClearData());

        // Bottom Navigation Tabs
        final android.widget.ScrollView mainScrollView = findViewById(R.id.main_scroll_view);
        findViewById(R.id.nav_ledger).setOnClickListener(v -> {
            if (mainScrollView != null) mainScrollView.smoothScrollTo(0, 0);
            Toast.makeText(this, "Ledger (Top)", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.nav_insights).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, InsightsActivity.class);
            intent.putExtra("selected_month", selectedMonth);
            startActivity(intent);
        });
        findViewById(R.id.nav_budget).setOnClickListener(v -> promptChangeBudget());
        findViewById(R.id.nav_settings).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        checkPermissions();
        refreshUI();
        checkAppUpdates(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String currentMode = ThemeHelper.getThemeMode(this);
        if (appliedThemeMode != null && !appliedThemeMode.equals(currentMode)) {
            appliedThemeMode = currentMode;
            recreate();
            return;
        }
        isPrivacyMode = getSharedPreferences("spendwise_prefs", MODE_PRIVATE).getBoolean("privacy_mode", false);
        if (btnPrivacyToggle != null) {
            btnPrivacyToggle.setText(isPrivacyMode ? "🔒" : "👁️");
        }
        checkPermissions();
        refreshUI();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, new IntentFilter(SmsReceiver.ACTION_UPDATE_UI), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateReceiver, new IntentFilter(SmsReceiver.ACTION_UPDATE_UI));
        }
        checkAppUpdates(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(updateReceiver);
        } catch (Exception ignored) {}
    }

    private void checkPermissions() {
        boolean hasSms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        bannerPermission.setVisibility(hasSms ? View.GONE : View.VISIBLE);
    }

    private void requestPermissions() {
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
        checkPermissions();
        if (requestCode == PERMISSION_REQ_CODE) {
            boolean allGranted = true;
            if (grantResults.length > 0) {
                for (int res : grantResults) {
                    if (res != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
            } else {
                allGranted = false;
            }

            if (allGranted) {
                Toast.makeText(this, "Live auto-tracking enabled! 🚀", Toast.LENGTH_LONG).show();
            } else {
                showPermissionSettingsDialog();
            }
        }
    }

    private void showPermissionSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("SMS Permission Required 🔔")
                .setMessage("SpendWise needs SMS access to automatically read your bank & UPI transaction alerts.\n\nPlease allow the SMS permission in App Settings so SpendWise can track your spending seamlessly.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, "Unable to open Settings automatically.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Not Now", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private int getCategoryColor(String category) {
        if (category == null) return 0xFF94A3B8;
        if (category.contains("Food")) return 0xFFF59E0B;
        if (category.contains("Groceries")) return 0xFF10B981;
        if (category.contains("Travel") || category.contains("Transport")) return 0xFF06B6D4;
        if (category.contains("Shopping")) return 0xFF8B5CF6;
        if (category.contains("Bills") || category.contains("Utilities")) return 0xFFF43F5E;
        if (category.contains("Entertainment")) return 0xFF6366F1;
        if (category.contains("Health")) return 0xFFEF4444;
        if (category.contains("Work") || category.contains("Education")) return 0xFF3B82F6;
        return 0xFF94A3B8;
    }

    private void setupSearchAndFilters() {
        setupCategoryChips();

        if (layoutSearchContainer != null) {
            layoutSearchContainer.setOnClickListener(v -> {
                if (etSearch != null) {
                    etSearch.requestFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }

        if (etSearch != null) {
            etSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                        || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                    return true;
                }
                return false;
            });

            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    searchQuery = s != null ? s.toString().trim() : "";
                    if (btnClearSearch != null) {
                        btnClearSearch.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                    filterAndRenderTransactions();
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                if (etSearch != null) etSearch.setText("");
                searchQuery = "";
                filterAndRenderTransactions();
            });
        }
    }

    private void setupCategoryChips() {
        if (layoutFilterChips == null) return;
        layoutFilterChips.removeAllViews();
        String[] chips = new String[]{
                "All", "🍔 Food", "🛒 Groceries", "🚗 Travel",
                "🛍️ Shopping", "💡 Bills", "🎬 Entertainment",
                "💊 Health", "📦 Others"
        };
        for (final String chip : chips) {
            TextView tv = new TextView(this);
            tv.setText(chip);
            tv.setTextSize(12);
            tv.setPadding(32, 16, 32, 16);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 16, 0);
            tv.setLayoutParams(lp);

            boolean isSelected = chip.equals(selectedCategory) || (chip.startsWith("All") && selectedCategory.equals("All"));
            if (isSelected) {
                tv.setBackgroundResource(R.drawable.pill_filter_active);
                tv.setTextColor(0xFFFFFFFF);
            } else {
                tv.setBackgroundResource(R.drawable.pill_filter);
                tv.setTextColor(getColor(R.color.text_secondary));
            }

            tv.setOnClickListener(v -> {
                if (chip.startsWith("All")) {
                    selectedCategory = "All";
                } else {
                    selectedCategory = chip;
                }
                setupCategoryChips();
                filterAndRenderTransactions();
            });

            layoutFilterChips.addView(tv);
        }
    }

    private void togglePrivacyMode() {
        isPrivacyMode = !isPrivacyMode;
        getSharedPreferences("spendwise_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("privacy_mode", isPrivacyMode)
                .apply();
        if (btnPrivacyToggle != null) {
            btnPrivacyToggle.setText(isPrivacyMode ? "🔒" : "👁️");
        }
        Toast.makeText(this, isPrivacyMode ? "Privacy Mode ON: Balances masked 🔒" : "Privacy Mode OFF: Balances visible 👁️", Toast.LENGTH_SHORT).show();
        refreshUI();
    }

    private String formatMoney(double amount) {
        if (isPrivacyMode) {
            return "₹******";
        }
        return String.format(Locale.US, "₹%,.2f", amount);
    }

    private String formatMoneyInt(double amount) {
        if (isPrivacyMode) {
            return "₹****";
        }
        return String.format(Locale.US, "₹%,.0f", amount);
    }

    private void showDailySafeAllowanceDialog() {
        double budget = db.getBudget();
        double spent = (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth))
                ? db.getTotalSpent()
                : db.getTotalSpentByMonth(selectedMonth);
        double remaining = budget - spent;

        Calendar cal = Calendar.getInstance();
        int currentDay = cal.get(Calendar.DAY_OF_MONTH);
        int maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int daysLeft = Math.max(1, maxDays - currentDay + 1);

        double dailySafe = remaining > 0 ? (remaining / daysLeft) : 0.0;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayStr = sdf.format(new Date());
        double spentToday = 0.0;
        List<Expense> monthExpenses = (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth))
                ? db.getAllExpenses()
                : db.getExpensesByMonth(selectedMonth);
        for (Expense exp : monthExpenses) {
            if (todayStr.equals(extractDateOnly(exp.date))) {
                spentToday += exp.amount;
            }
        }
        double safeRemainingToday = dailySafe - spentToday;

        StringBuilder sb = new StringBuilder();
        sb.append("Monthly Budget: ").append(formatMoneyInt(budget)).append("\n");
        sb.append("Total Spent: ").append(formatMoney(spent)).append("\n");
        sb.append("Remaining Budget: ").append(formatMoney(remaining)).append("\n");
        sb.append("Days left in month: ").append(daysLeft).append(" days\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("👉 Daily Safe Allowance: ").append(formatMoneyInt(dailySafe)).append(" / day\n");
        sb.append("👉 Spent Today: ").append(formatMoney(spentToday)).append("\n");
        sb.append("👉 Remaining Safe Today: ").append(formatMoney(safeRemainingToday)).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n");

        if (remaining <= 0) {
            sb.append("🚨 Monthly budget is already exceeded! Avoid non-essential expenses for the rest of this month.");
        } else if (safeRemainingToday >= 0) {
            sb.append("✅ Looking good! You have ₹").append(String.format(Locale.US, "%,.0f", safeRemainingToday)).append(" safe allowance remaining today to stay comfortably within your monthly budget.");
        } else {
            sb.append("⚠️ Today's spending has exceeded the safe daily target by ₹").append(String.format(Locale.US, "%,.0f", Math.abs(safeRemainingToday))).append(". Try to adjust spending over the coming days to stay on track.");
        }

        new AlertDialog.Builder(this)
                .setTitle("🛡️ Daily Safe Limit")
                .setMessage(sb.toString())
                .setPositiveButton("Got It", null)
                .show();
    }

    private void showChhotaKharchaDialog() {
        List<Expense> monthExpenses = (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth))
                ? db.getAllExpenses()
                : db.getExpensesByMonth(selectedMonth);

        double chhotaTotal = 0.0;
        int chhotaCount = 0;
        for (Expense exp : monthExpenses) {
            if (exp.amount <= 150.0) {
                chhotaTotal += exp.amount;
                chhotaCount++;
            }
        }

        double totalSpent = (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth))
                ? db.getTotalSpent()
                : db.getTotalSpentByMonth(selectedMonth);
        int pctOfTotal = totalSpent > 0 ? (int) Math.round((chhotaTotal / totalSpent) * 100) : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("☕ Micro-Spends Overview (≤ ₹150):\n\n");
        sb.append("• Total Micro-Spends: ").append(formatMoney(chhotaTotal)).append("\n");
        sb.append("• Small Transactions: ").append(chhotaCount).append(" txns\n");
        sb.append("• Share of Monthly Spend: ").append(pctOfTotal).append("%\n\n");
        sb.append("Frequent small expenses (coffee, cab/auto, quick grocery deliveries, snacks) seem minor, but they often represent a significant percentage of total monthly spending!\n\n");
        sb.append("💡 Pro-Tip: Reducing just ₹50/day on impulse micro-spends saves ~₹1,500 every month.");

        new AlertDialog.Builder(this)
                .setTitle("☕ Micro-Spends Tracker")
                .setMessage(sb.toString())
                .setPositiveButton("Filter in Ledger", (d, w) -> filterMicroSpendsOnly())
                .setNegativeButton("Close", null)
                .show();
    }

    private void filterMicroSpendsOnly() {
        isMicroFilterActive = true;
        selectedCategory = "All";
        setupCategoryChips();
        filterAndRenderTransactions();
        Toast.makeText(this, "Filtered: Micro-Spends (≤ ₹150) ☕", Toast.LENGTH_SHORT).show();
    }

    private void showNoSpendStreakDialog() {
        List<Expense> monthExpenses = (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth))
                ? db.getAllExpenses()
                : db.getExpensesByMonth(selectedMonth);

        Set<String> spendDates = new HashSet<>();
        for (Expense exp : monthExpenses) {
            if (exp.date != null && !exp.date.isEmpty()) {
                spendDates.add(extractDateOnly(exp.date));
            }
        }

        Calendar cal = Calendar.getInstance();
        int todayDay = cal.get(Calendar.DAY_OF_MONTH);
        SimpleDateFormat ymFmt = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
        String currentYm = ymFmt.format(new Date());

        String targetYm = (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth)) ? currentYm : selectedMonth;
        int checkUpToDay = targetYm.equals(currentYm) ? todayDay : cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        int zeroSpendDays = 0;
        for (int d = 1; d <= checkUpToDay; d++) {
            String dateStr = String.format(Locale.US, "%s-%02d", targetYm, d);
            if (!spendDates.contains(dateStr)) {
                zeroSpendDays++;
            }
        }

        int currentStreak = 0;
        if (targetYm.equals(currentYm)) {
            for (int d = todayDay; d >= 1; d--) {
                String dateStr = String.format(Locale.US, "%s-%02d", targetYm, d);
                if (!spendDates.contains(dateStr)) {
                    currentStreak++;
                } else {
                    break;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🎯 Zero-Spend Days This Month: ").append(zeroSpendDays).append(" days\n");
        sb.append("🔥 Current Active Streak: ").append(currentStreak).append(" days\n\n");
        if (currentStreak >= 3) {
            sb.append("🏆 Unstoppable discipline! You are on a ").append(currentStreak).append("-day no-spend winning streak!");
        } else if (currentStreak >= 1) {
            sb.append("🔥 Awesome start! Keep going to build your savings streak.");
        } else {
            sb.append("💡 Challenge: Try making tomorrow a ₹0 Zero-Spend Day to start a brand new streak!");
        }

        new AlertDialog.Builder(this)
                .setTitle("🔥 Zero-Spend Days Streak")
                .setMessage(sb.toString())
                .setPositiveButton("Challenge Accepted! 🎯", null)
                .show();
    }

    private void renderCategoryBars(Map<String, Double> catMap, double totalSpent) {
        if (layoutCategoryBars == null) return;
        layoutCategoryBars.removeAllViews();
        if (catMap == null || catMap.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No spending recorded" + (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth) ? " yet." : " for " + formatMonthDisplay(selectedMonth) + "."));
            emptyTv.setTextColor(getColor(R.color.text_secondary));
            emptyTv.setTextSize(13);
            layoutCategoryBars.addView(emptyTv);
            return;
        }

        for (Map.Entry<String, Double> entry : catMap.entrySet()) {
            String cat = entry.getKey();
            double amt = entry.getValue();
            int pct = totalSpent > 0 ? (int) Math.round((amt / totalSpent) * 100) : 0;
            int catColor = getCategoryColor(cat);

            LinearLayout catItem = new LinearLayout(this);
            catItem.setOrientation(LinearLayout.VERTICAL);
            catItem.setPadding(0, 0, 0, 24);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            TextView tvName = new TextView(this);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvName.setText(cat);
            tvName.setTextColor(getColor(R.color.text_primary));
            tvName.setTextSize(13);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvAmt = new TextView(this);
            tvAmt.setText(formatMoney(amt) + " (" + pct + "%)");
            tvAmt.setTextColor(getColor(R.color.text_secondary));
            tvAmt.setTextSize(12);
            tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);

            row.addView(tvName);
            row.addView(tvAmt);
            catItem.addView(row);

            LinearLayout track = new LinearLayout(this);
            LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    14
            );
            trackLp.setMargins(0, 10, 0, 0);
            track.setLayoutParams(trackLp);
            track.setBackgroundResource(R.drawable.progress_track_bg);
            track.setOrientation(LinearLayout.HORIZONTAL);
            track.setWeightSum(100);

            View fill = new View(this);
            int safePct = Math.max(3, Math.min(100, pct));
            fill.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float) safePct));

            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(catColor);
            gd.setCornerRadius(8f);
            fill.setBackground(gd);

            View emptySpace = new View(this);
            emptySpace.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float) (100 - safePct)));

            track.addView(fill);
            track.addView(emptySpace);
            catItem.addView(track);

            layoutCategoryBars.addView(catItem);
        }
    }

    private void renderBankBars(Map<String, Double> bankMap, double totalSpent) {
        if (layoutBankBars == null) return;
        layoutBankBars.removeAllViews();
        if (bankMap == null || bankMap.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No bank accounts recorded" + (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth) ? " yet." : " for " + formatMonthDisplay(selectedMonth) + "."));
            emptyTv.setTextColor(getColor(R.color.text_secondary));
            emptyTv.setTextSize(13);
            layoutBankBars.addView(emptyTv);
            return;
        }

        for (Map.Entry<String, Double> entry : bankMap.entrySet()) {
            final String bankName = entry.getKey();
            double amt = entry.getValue();
            int pct = totalSpent > 0 ? (int) Math.round((amt / totalSpent) * 100) : 0;
            int bankColor = ExpenseParser.getBankColor(bankName);

            LinearLayout bankItem = new LinearLayout(this);
            bankItem.setOrientation(LinearLayout.VERTICAL);
            bankItem.setPadding(0, 0, 0, 24);
            bankItem.setClickable(true);
            bankItem.setFocusable(true);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            TextView tvName = new TextView(this);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvName.setText(bankName);
            tvName.setTextColor(getColor(R.color.text_primary));
            tvName.setTextSize(13);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvAmt = new TextView(this);
            tvAmt.setText(formatMoney(amt) + " (" + pct + "%)");
            tvAmt.setTextColor(getColor(R.color.text_secondary));
            tvAmt.setTextSize(12);
            tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);

            row.addView(tvName);
            row.addView(tvAmt);
            bankItem.addView(row);

            LinearLayout track = new LinearLayout(this);
            LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    14
            );
            trackLp.setMargins(0, 10, 0, 0);
            track.setLayoutParams(trackLp);
            track.setBackgroundResource(R.drawable.progress_track_bg);
            track.setOrientation(LinearLayout.HORIZONTAL);
            track.setWeightSum(100);

            View fill = new View(this);
            int safePct = Math.max(3, Math.min(100, pct));
            fill.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float) safePct));

            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(bankColor);
            gd.setCornerRadius(8f);
            fill.setBackground(gd);

            View emptySpace = new View(this);
            emptySpace.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float) (100 - safePct)));

            track.addView(fill);
            track.addView(emptySpace);
            bankItem.addView(track);

            bankItem.setOnClickListener(v -> {
                String cleanBank = bankName.replaceAll("[^a-zA-Z0-9 ]", "").trim();
                String[] words = cleanBank.split("\\s+");
                String searchKw = words.length > 0 ? words[0] : bankName;
                if (etSearch != null) {
                    etSearch.setText(searchKw);
                }
                Toast.makeText(this, "Showing " + bankName + " transactions", Toast.LENGTH_SHORT).show();
            });

            layoutBankBars.addView(bankItem);
        }
    }

    private void refreshUI() {
        double budget = db.getBudget();
        double spent = (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth))
                ? db.getTotalSpent()
                : db.getTotalSpentByMonth(selectedMonth);
        double remaining = budget - spent;
        int progress = budget > 0 ? (int) Math.min(100, (spent / budget) * 100) : 0;
        double pctLeft = budget > 0 ? Math.max(0, ((budget - spent) / budget) * 100) : 0;

        tvTotalSpent.setText(formatMoney(spent));
        tvBudgetCaption.setText("Monthly target: " + formatMoneyInt(budget));
        tvRemainingBalance.setText(formatMoney(remaining));
        tvBudgetPct.setText(String.format(Locale.US, "%.0f%% left", pctLeft));
        pbBudget.setProgress(progress);

        if (remaining >= 0) {
            tvBadgeStatus.setText("● On Track ⓘ");
            tvBadgeStatus.setTextColor(getColor(R.color.accent_emerald));
        } else {
            tvBadgeStatus.setText("● Over Budget ⓘ");
            tvBadgeStatus.setTextColor(getColor(R.color.accent_rose));
        }

        List<Expense> monthExpenses = (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth))
                ? db.getAllExpenses()
                : db.getExpensesByMonth(selectedMonth);

        // 1. Pro Metrics Row
        double maxSingle = 0.0;
        double chhotaTotal = 0.0;
        int chhotaCount = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayStr = sdf.format(new Date());
        double spentToday = 0.0;
        Set<String> spendDates = new HashSet<>();

        for (Expense exp : monthExpenses) {
            if (exp.amount > maxSingle) {
                maxSingle = exp.amount;
            }
            if (exp.amount <= 150.0) {
                chhotaTotal += exp.amount;
                chhotaCount++;
            }
            if (todayStr.equals(extractDateOnly(exp.date))) {
                spentToday += exp.amount;
            }
            if (exp.date != null && !exp.date.isEmpty()) {
                spendDates.add(extractDateOnly(exp.date));
            }
        }
        double dailyAvg = spent > 0 ? (spent / 30.0) : 0.0;

        if (tvMetricDailyAvg != null) tvMetricDailyAvg.setText(formatMoneyInt(dailyAvg));
        if (tvMetricHighest != null) tvMetricHighest.setText(formatMoneyInt(maxSingle));
        if (tvMetricTxCount != null) tvMetricTxCount.setText(monthExpenses.size() + " txns");

        // 2. Safe Daily Allowance Calculation
        Calendar cal = Calendar.getInstance();
        int currentDay = cal.get(Calendar.DAY_OF_MONTH);
        int maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int daysLeft = Math.max(1, maxDays - currentDay + 1);
        double dailySafe = remaining > 0 ? (remaining / daysLeft) : 0.0;

        if (tvDailySafeAmount != null) {
            if (remaining <= 0) {
                tvDailySafeAmount.setText("Over Budget ⚠️");
                tvDailySafeAmount.setTextColor(getColor(R.color.accent_rose));
            } else if (spentToday > dailySafe) {
                tvDailySafeAmount.setText(formatMoneyInt(dailySafe) + " / day ⚠️");
                tvDailySafeAmount.setTextColor(getColor(R.color.accent_coral));
            } else {
                tvDailySafeAmount.setText(formatMoneyInt(dailySafe) + " / day");
                tvDailySafeAmount.setTextColor(getColor(R.color.accent_emerald));
            }
        }

        // 3. Behavioral Insights Row 2 (Chhota Kharcha & Zero-Spend Streak)
        if (tvChhotaKharchaAmount != null) tvChhotaKharchaAmount.setText(formatMoney(chhotaTotal));
        if (tvChhotaKharchaSub != null) tvChhotaKharchaSub.setText(chhotaCount + " txns (< ₹150)");

        SimpleDateFormat ymFmt = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
        String currentYm = ymFmt.format(new Date());
        String targetYm = (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth)) ? currentYm : selectedMonth;
        int checkUpToDay = targetYm.equals(currentYm) ? currentDay : cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        int zeroSpendDays = 0;
        for (int d = 1; d <= checkUpToDay; d++) {
            String dateStr = String.format(Locale.US, "%s-%02d", targetYm, d);
            if (!spendDates.contains(dateStr)) {
                zeroSpendDays++;
            }
        }

        int currentStreak = 0;
        if (targetYm.equals(currentYm)) {
            for (int d = currentDay; d >= 1; d--) {
                String dateStr = String.format(Locale.US, "%s-%02d", targetYm, d);
                if (!spendDates.contains(dateStr)) {
                    currentStreak++;
                } else {
                    break;
                }
            }
        }

        if (tvNoSpendDays != null) tvNoSpendDays.setText(zeroSpendDays + " Days");
        if (tvNoSpendSub != null) {
            if (currentStreak > 0) {
                tvNoSpendSub.setText(currentStreak + " day streak 🔥");
            } else {
                tvNoSpendSub.setText("Active today");
            }
        }

        // 4. Category Breakdown with Visual Bars
        Map<String, Double> catMap = (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth))
                ? db.getCategoryTotals()
                : db.getCategoryTotalsByMonth(selectedMonth);
        renderCategoryBars(catMap, spent);

        // 5. Bank Breakdown with Visual Bars
        Map<String, Double> bankMap = new LinkedHashMap<>();
        for (Expense exp : monthExpenses) {
            String bank = ExpenseParser.extractBankSource(exp.rawSms);
            Double cur = bankMap.get(bank);
            bankMap.put(bank, (cur == null ? 0.0 : cur) + exp.amount);
        }
        renderBankBars(bankMap, spent);

        // 6. Filter & Render Transactions
        filterAndRenderTransactions();
    }

    private void filterAndRenderTransactions() {
        if (layoutTransactions == null) return;

        // When user types in search, search across ALL transactions in history
        List<Expense> pool;
        if (!searchQuery.isEmpty()) {
            pool = db.getAllExpenses();
        } else {
            pool = (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth))
                    ? db.getAllExpenses()
                    : db.getExpensesByMonth(selectedMonth);
        }

        List<Expense> filtered = new ArrayList<>();
        for (Expense exp : pool) {
            if (isMicroFilterActive && exp.amount > 150.0) {
                continue;
            }

            boolean matchesCategory = selectedCategory.equalsIgnoreCase("All")
                    || (exp.category != null && exp.category.toLowerCase().contains(selectedCategory.replaceAll("[^a-zA-Z]", "").toLowerCase()));

            boolean matchesSearch = searchQuery.isEmpty()
                    || (exp.title != null && exp.title.toLowerCase().contains(searchQuery.toLowerCase()))
                    || (exp.category != null && exp.category.toLowerCase().contains(searchQuery.toLowerCase()))
                    || String.valueOf(exp.amount).contains(searchQuery)
                    || (exp.rawSms != null && exp.rawSms.toLowerCase().contains(searchQuery.toLowerCase()));

            if (matchesCategory && matchesSearch) {
                filtered.add(exp);
            }
        }

        layoutTransactions.removeAllViews();
        if (tvTransactionCount != null) {
            String countText;
            if (isMicroFilterActive) {
                countText = filtered.size() + (filtered.size() == 1 ? " micro txn" : " micro txns") + " (≤ ₹150) • Tap 'All' to reset";
            } else if (!searchQuery.isEmpty()) {
                countText = filtered.size() + (filtered.size() == 1 ? " result" : " results") + " found for \"" + searchQuery + "\"";
            } else if (!selectedCategory.equalsIgnoreCase("All")) {
                countText = filtered.size() + (filtered.size() == 1 ? " entry" : " entries") + " in " + selectedCategory;
            } else {
                countText = filtered.size() + (filtered.size() == 1 ? " entry" : " entries") + (filtered.isEmpty() ? "" : " • Tap for receipt • Swipe to delete");
            }
            tvTransactionCount.setText(countText);
        }

        if (filtered.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            if (tvEmptyEmoji != null && tvEmptyTitle != null && tvEmptySubtitle != null) {
                if (isMicroFilterActive) {
                    tvEmptyEmoji.setText("☕");
                    tvEmptyTitle.setText("No micro-spends (≤ ₹150)");
                    tvEmptySubtitle.setText("No transactions under ₹150 found in this view");
                } else if (!searchQuery.isEmpty()) {
                    tvEmptyEmoji.setText("🔍");
                    tvEmptyTitle.setText("No results for \"" + searchQuery + "\"");
                    tvEmptySubtitle.setText("Try searching a different merchant or tap ✕ to clear");
                } else if (!selectedCategory.equalsIgnoreCase("All")) {
                    tvEmptyEmoji.setText("📂");
                    tvEmptyTitle.setText("No " + selectedCategory + " expenses");
                    tvEmptySubtitle.setText("Tap 'All' above or log a new expense");
                } else {
                    tvEmptyEmoji.setText("💳");
                    tvEmptyTitle.setText("No transactions recorded yet");
                    tvEmptySubtitle.setText("Pay via UPI or tap 'Test Alert' above");
                }
            }
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);
            for (Expense exp : filtered) {
                View item = inflater.inflate(R.layout.item_expense, layoutTransactions, false);
                TextView tvEmoji = item.findViewById(R.id.item_emoji);
                TextView tvTitle = item.findViewById(R.id.item_title);
                TextView tvSubtitle = item.findViewById(R.id.item_subtitle);
                TextView tvAmount = item.findViewById(R.id.item_amount);
                final View cardForeground = item.findViewById(R.id.card_foreground);
                View btnSwipeDelete = item.findViewById(R.id.btn_swipe_delete);

                tvEmoji.setText(ExpenseParser.getEmoji(exp.category));
                tvTitle.setText(exp.title);
                tvSubtitle.setText(exp.date + " • " + exp.category);
                tvAmount.setText("- " + formatMoney(exp.amount));

                btnSwipeDelete.setOnClickListener(v -> deleteExpenseWithAnimation(cardForeground, exp));

                // Swipe-left gesture handler
                cardForeground.setOnTouchListener(new View.OnTouchListener() {
                    private float startX;
                    private float startY;
                    private boolean isSwiping = false;
                    private static final int SWIPE_THRESHOLD = 160;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                startX = event.getRawX();
                                startY = event.getRawY();
                                isSwiping = false;
                                return true;

                            case MotionEvent.ACTION_MOVE:
                                float dx = event.getRawX() - startX;
                                float dy = event.getRawY() - startY;
                                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 15) {
                                    v.getParent().requestDisallowInterceptTouchEvent(true);
                                    if (dx < 0) {
                                        v.setTranslationX(dx);
                                        isSwiping = true;
                                    }
                                }
                                return true;

                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                float totalDx = event.getRawX() - startX;
                                float totalDy = event.getRawY() - startY;

                                if (isSwiping && totalDx < -SWIPE_THRESHOLD) {
                                    deleteExpenseWithAnimation(v, exp);
                                } else if (!isSwiping && Math.abs(totalDx) < 15 && Math.abs(totalDy) < 15) {
                                    showTransactionReceiptDialog(exp);
                                } else {
                                    v.animate()
                                            .translationX(0)
                                            .setDuration(200)
                                            .start();
                                }
                                return isSwiping;
                        }
                        return false;
                    }
                });

                layoutTransactions.addView(item);
            }
        }
    }

    private void deleteExpenseWithAnimation(View cardForeground, Expense exp) {
        cardForeground.animate()
                .translationX(-cardForeground.getWidth())
                .alpha(0f)
                .setDuration(220)
                .withEndAction(() -> performDeleteWithUndo(exp))
                .start();
    }

    private void performDeleteWithUndo(Expense exp) {
        lastDeletedExpense = exp;
        db.deleteExpense(exp.id);
        refreshUI();

        if (layoutUndoBanner != null && tvUndoMessage != null) {
            undoHandler.removeCallbacks(hideUndoRunnable);
            tvUndoMessage.setText(getString(R.string.deleted_undo_msg, exp.title));
            layoutUndoBanner.setVisibility(View.VISIBLE);
            undoHandler.postDelayed(hideUndoRunnable, 5000);
        } else {
            Toast.makeText(this, getString(R.string.deleted_undo_msg, exp.title), Toast.LENGTH_SHORT).show();
        }
    }

    private void undoLastDelete() {
        undoHandler.removeCallbacks(hideUndoRunnable);
        if (layoutUndoBanner != null) {
            layoutUndoBanner.setVisibility(View.GONE);
        }
        if (lastDeletedExpense != null) {
            Expense restored = lastDeletedExpense;
            lastDeletedExpense = null;
            db.insertExpense(restored.date, restored.title, restored.category, restored.amount, restored.rawSms);
            refreshUI();
            Toast.makeText(this, getString(R.string.restored_msg, restored.title), Toast.LENGTH_SHORT).show();
        }
    }

    private void showTransactionReceiptDialog(Expense exp) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 24, 40, 16);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(exp.title);
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(getColor(R.color.text_primary));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvTitle);

        TextView tvAmt = new TextView(this);
        tvAmt.setText("- " + formatMoney(exp.amount));
        tvAmt.setTextSize(26);
        tvAmt.setTextColor(getColor(R.color.accent_rose));
        tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);
        tvAmt.setPadding(0, 8, 0, 16);
        layout.addView(tvAmt);

        TextView tvDetails = new TextView(this);
        tvDetails.setText("• Category: " + exp.category + "\n• Bank / Source: " + ExpenseParser.extractBankSource(exp.rawSms) + "\n• Date & Time: " + exp.date + "\n• Status: Completed & Verified ✅");
        tvDetails.setTextColor(getColor(R.color.text_secondary));
        tvDetails.setTextSize(13);
        tvDetails.setLineSpacing(4, 1.2f);
        layout.addView(tvDetails);

        new AlertDialog.Builder(this)
                .setTitle("Transaction Receipt 🧾")
                .setView(layout)
                .setPositiveButton("Close", null)
                .setNeutralButton("Delete", (d, w) -> performDeleteWithUndo(exp))
                .show();
    }

    private String extractDateOnly(String dateTime) {
        if (dateTime == null) return "";
        String trimmed = dateTime.trim();
        return trimmed.length() >= 10 ? trimmed.substring(0, 10) : trimmed;
    }

    private String formatMonthDisplay(String ym) {
        if (ym == null || ym.equalsIgnoreCase("ALL")) {
            return "All Time";
        }
        try {
            SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM", Locale.US);
            Date d = inFmt.parse(ym);
            if (d != null) {
                SimpleDateFormat outFmt = new SimpleDateFormat("MMM yyyy", Locale.US);
                return outFmt.format(d);
            }
        } catch (Exception ignored) {}
        return ym;
    }

    private void showMonthPicker() {
        List<String> available = db.getAvailableMonths();
        String currentYm = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
        if (!available.contains(currentYm)) {
            available.add(0, currentYm);
        }

        final List<String> displayList = new ArrayList<>();
        final List<String> rawKeys = new ArrayList<>();

        // 1. Current Month
        displayList.add("📅 " + formatMonthDisplay(currentYm) + " (Current)");
        rawKeys.add(currentYm);

        // 2. Other Available Months
        for (String m : available) {
            if (!m.equals(currentYm)) {
                displayList.add("🗓️ " + formatMonthDisplay(m));
                rawKeys.add(m);
            }
        }

        // 3. All Time option
        displayList.add("🌐 All Time");
        rawKeys.add("ALL");

        int checkedIndex = -1;
        for (int i = 0; i < rawKeys.size(); i++) {
            if (rawKeys.get(i).equalsIgnoreCase(selectedMonth)) {
                checkedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Spending Period")
                .setSingleChoiceItems(displayList.toArray(new String[0]), checkedIndex, (dialog, which) -> {
                    selectedMonth = rawKeys.get(which);
                    btnMonthSelector.setText(formatMonthDisplay(selectedMonth) + " ▾");
                    refreshUI();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showBudgetStatusExplanation() {
        double budget = db.getBudget();
        double spent = (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth))
                ? db.getTotalSpent()
                : db.getTotalSpentByMonth(selectedMonth);
        double remaining = budget - spent;
        String monthName = formatMonthDisplay(selectedMonth);

        String title;
        String message;

        if (remaining >= 0) {
            double pctLeft = budget > 0 ? ((budget - spent) / budget) * 100 : 0;
            title = "📊 Budget Status: On Track";
            message = "What does 'On Track' mean?\n\n"
                    + "It means your spending is safe and within your monthly target for " + monthName + "!\n\n"
                    + "• Target Budget: ₹" + String.format(Locale.US, "%,.2f", budget) + "\n"
                    + "• Total Spent: ₹" + String.format(Locale.US, "%,.2f", spent) + "\n"
                    + "• Safe Balance Left: ₹" + String.format(Locale.US, "%,.2f", remaining) + "\n"
                    + "• Budget Remaining: " + String.format(Locale.US, "%.0f%%", pctLeft) + "\n\n"
                    + "💡 You are managing your finances well. Tap below if you want to increase or lower your monthly target.";
        } else {
            double overAmount = Math.abs(remaining);
            title = "⚠️ Budget Status: Over Budget";
            message = "What does 'Over Budget' mean?\n\n"
                    + "It means you have spent more than your set monthly target for " + monthName + "!\n\n"
                    + "• Target Budget: ₹" + String.format(Locale.US, "%,.2f", budget) + "\n"
                    + "• Total Spent: ₹" + String.format(Locale.US, "%,.2f", spent) + "\n"
                    + "• Exceeded By: ₹" + String.format(Locale.US, "%,.2f", overAmount) + "\n\n"
                    + "💡 You can tap 'Adjust Target Limit' below to increase your budget or adjust your limits.";
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Adjust Target Limit", (dialog, which) -> promptChangeBudget())
                .setNegativeButton("Got it", null)
                .show();
    }

    private void showAddExpenseDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);

        final EditText etTitle = new EditText(this);
        etTitle.setHint("Merchant / Description (e.g. Swiggy, Petrol)");
        layout.addView(etTitle);

        final EditText etAmount = new EditText(this);
        etAmount.setHint("Amount in ₹");
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(etAmount);

        final String currentYm = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
        final double currentSpent = db.getTotalSpentByMonth(currentYm);
        final double budget = db.getBudget();

        final TextView tvBudgetFeedback = new TextView(this);
        tvBudgetFeedback.setTextSize(12);
        tvBudgetFeedback.setPadding(8, 8, 8, 12);
        tvBudgetFeedback.setText("Current month spend: ₹" + String.format(Locale.US, "%,.2f", currentSpent) + " / ₹" + String.format(Locale.US, "%,.0f", budget));
        tvBudgetFeedback.setTextColor(getColor(R.color.text_secondary));
        layout.addView(tvBudgetFeedback);

        etAmount.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String val = s.toString().trim();
                if (val.isEmpty()) {
                    tvBudgetFeedback.setText("Current month spend: ₹" + String.format(Locale.US, "%,.2f", currentSpent) + " / ₹" + String.format(Locale.US, "%,.0f", budget));
                    tvBudgetFeedback.setTextColor(getColor(R.color.text_secondary));
                    return;
                }
                try {
                    double entered = Double.parseDouble(val);
                    double newTotal = currentSpent + entered;
                    if (newTotal > budget) {
                        double overBy = newTotal - budget;
                        tvBudgetFeedback.setText("⚠️ OVER BUDGET by ₹" + String.format(Locale.US, "%,.2f", overBy) + "!");
                        tvBudgetFeedback.setTextColor(getColor(R.color.accent_rose));
                    } else {
                        double left = budget - newTotal;
                        tvBudgetFeedback.setText("✅ Safe: ₹" + String.format(Locale.US, "%,.2f", left) + " will remain.");
                        tvBudgetFeedback.setTextColor(getColor(R.color.accent_emerald));
                    }
                } catch (Exception ignored) {}
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        final Spinner spCategory = new Spinner(this);
        String[] cats = new String[]{
                "🍔 Food & Dining", "🛒 Groceries", "🚗 Travel & Commute",
                "🛍️ Shopping", "💡 Bills & Utilities", "🎬 Entertainment",
                "💊 Health & Medical", "💼 Work & Education", "📦 Others"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats);
        spCategory.setAdapter(adapter);
        layout.addView(spCategory);

        new AlertDialog.Builder(this)
                .setTitle("Log Expense")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String title = etTitle.getText().toString().trim();
                    String amtStr = etAmount.getText().toString().trim();
                    if (!title.isEmpty() && !amtStr.isEmpty()) {
                        try {
                            double amt = Double.parseDouble(amtStr);
                            String cat = spCategory.getSelectedItem().toString();
                            String dt = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US).format(new Date());

                            double newTotal = currentSpent + amt;
                            if (newTotal > budget) {
                                double overBy = newTotal - budget;
                                confirmOverBudgetExpense(dt, title, cat, amt, overBy, newTotal, budget);
                            } else {
                                db.insertExpense(dt, title, cat, amt, "Manual Cash Entry");
                                refreshUI();
                                Toast.makeText(this, "Expense recorded.", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmOverBudgetExpense(String dt, String title, String cat, double amt, double overBy, double newTotal, double budget) {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ Over Budget Validation Alert")
                .setMessage("Logging this expense will exceed your monthly budget limit!\n\n"
                        + "• Transaction: " + title + " (₹" + String.format(Locale.US, "%,.2f", amt) + ")\n"
                        + "• Monthly Limit: ₹" + String.format(Locale.US, "%,.2f", budget) + "\n"
                        + "• New Total Spending: ₹" + String.format(Locale.US, "%,.2f", newTotal) + "\n"
                        + "• Exceeded By: ₹" + String.format(Locale.US, "%,.2f", overBy) + "\n\n"
                        + "Are you sure you want to log this expense anyway?")
                .setPositiveButton("Yes, Log Anyway", (dialog, which) -> {
                    db.insertExpense(dt, title, cat, amt, "Manual Cash Entry (Over Budget)");
                    refreshUI();
                    Toast.makeText(this, "⚠️ Expense logged: Over Budget by ₹" + String.format(Locale.US, "%,.0f", overBy), Toast.LENGTH_LONG).show();
                })
                .setNeutralButton("Adjust Budget", (dialog, which) -> promptChangeBudget())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void simulateRandomTransaction() {
        String[] mockSmsList = new String[]{
                "Dear SBI User, your A/c XX1234 debited by Rs 340.00 on 26-Nov-2026 by UPI to SWIGGY. Ref No 432521.",
                "HDFC Bank: Rs 1499.00 debited from a/c **5678 to AMAZON PAY on 26-11-2026. Avail Bal: Rs 18,200.",
                "ICICI Bank: Acct XX999 debited with INR 220.00 on 26-Nov-26. Info: UPI/ZEPTO/9876543.",
                "Axis Bank: Rs 450.00 spent on your Card ending 4321 at SHELL PETROL PUMP on 26-Nov-2026.",
                "Paytm: Paid Rs 50.00 to CHAI POINT via UPI. Ref 908123.",
                "Kotak Bank: Rs 40.00 debited from A/c 8891 for UPI/RAPIDO/AUTO on 26-Nov-2026.",
                "HDFC Bank: Rs 85.00 debited towards SHARMA CHAI AND SNACKS on 26-11-2026."
        };
        int idx = new Random().nextInt(mockSmsList.length);
        String chosenSms = mockSmsList[idx];

        boolean ok = SmsReceiver.processSms(this, chosenSms);
        if (ok) {
            refreshUI();
            Toast.makeText(this, "⚡ Simulated Real-Time Transaction Logged!", Toast.LENGTH_SHORT).show();
        }
    }

    private void promptChangeBudget() {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(String.valueOf((int) db.getBudget()));

        new AlertDialog.Builder(this)
                .setTitle("Monthly Target Limit")
                .setMessage("Enter new monthly budget limit (₹):")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String val = input.getText().toString().trim();
                    if (!val.isEmpty()) {
                        try {
                            double b = Double.parseDouble(val);
                            String currentYm = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
                            double currentSpent = db.getTotalSpentByMonth(currentYm);
                            if (b < currentSpent) {
                                double overBy = currentSpent - b;
                                new AlertDialog.Builder(this)
                                        .setTitle("⚠️ Target Lower Than Current Spend")
                                        .setMessage("You have already spent ₹" + String.format(Locale.US, "%,.2f", currentSpent) + " this month.\n\n"
                                                + "Setting your target to ₹" + String.format(Locale.US, "%,.2f", b) + " will immediately put you Over Budget by ₹" + String.format(Locale.US, "%,.2f", overBy) + ".\n\n"
                                                + "Do you want to proceed?")
                                        .setPositiveButton("Set Anyway", (dialog, which) -> {
                                            db.setBudget(b);
                                            refreshUI();
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            } else {
                                db.setBudget(b);
                                refreshUI();
                                Toast.makeText(this, "Monthly budget set to ₹" + String.format(Locale.US, "%,.0f", b), Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception ignored) {}
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this)
                .setTitle("Reset History")
                .setMessage("Are you sure you want to clear all recorded transactions?")
                .setPositiveButton("Reset", (d, w) -> {
                    db.clearAll();
                    refreshUI();
                    Toast.makeText(this, "Ledger reset.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private long lastUpdateCheckTime = 0;

    private void checkAppUpdates(final boolean manualTrigger) {
        long now = System.currentTimeMillis();
        if (!manualTrigger && (now - lastUpdateCheckTime < 10000)) {
            return;
        }
        lastUpdateCheckTime = now;

        String serverUrl = db.getServerUrl();
        if (manualTrigger) {
            Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
        }
        UpdateManager.checkForUpdate(this, serverUrl, new UpdateManager.UpdateCallback() {
            @Override
            public void onUpdateAvailable(UpdateManager.UpdateInfo info) {
                if (!isFinishing() && !isDestroyed()) {
                    UpdateManager.showUpdateDialog(MainActivity.this, info);
                }
            }

            @Override
            public void onNoUpdate() {
                if (manualTrigger) {
                    Toast.makeText(MainActivity.this, "SpendWise is up to date! (v" + getAppVersionName() + ")", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                if (manualTrigger) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Server Unreachable 📡")
                            .setMessage("Could not connect to update server at:\n" + serverUrl + "\n\nPlease ensure your phone and Mac are on the same Wi-Fi network, or tap below to update the Server IP address.")
                            .setPositiveButton("Change Server IP", (d, w) -> promptChangeServerIp())
                            .setNegativeButton("OK", null)
                            .show();
                }
            }
        });
    }

    private String getAppVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    private void showSettingsMenu() {
        String[] options = new String[]{
                isPrivacyMode ? "👁️ Disable Privacy Eye (Show Numbers)" : "🔒 Enable Privacy Eye (Hide Numbers)",
                "🔄 Check for Updates Now",
                "📄 Export Statement (CSV)",
                "🌐 Server Address (" + db.getServerUrl() + ")",
                "🎯 Monthly Target Limit",
                "🗑️ Reset All Transactions"
        };

        new AlertDialog.Builder(this)
                .setTitle("SpendWise Settings (v" + getAppVersionName() + ")")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            togglePrivacyMode();
                            break;
                        case 1:
                            checkAppUpdates(true);
                            break;
                        case 2:
                            exportExpensesToCsv();
                            break;
                        case 3:
                            promptChangeServerIp();
                            break;
                        case 4:
                            promptChangeBudget();
                            break;
                        case 5:
                            confirmClearData();
                            break;
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void exportExpensesToCsv() {
        List<Expense> expenses = (selectedMonth == null || "ALL".equalsIgnoreCase(selectedMonth))
                ? db.getAllExpenses()
                : db.getExpensesByMonth(selectedMonth);

        if (expenses.isEmpty()) {
            Toast.makeText(this, "No transactions to export.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("ID,Date,Merchant,Category,Bank_Source,Amount_INR,Raw_SMS\n");
        for (Expense exp : expenses) {
            sb.append(exp.id).append(",")
                    .append("\"").append(exp.date).append("\",")
                    .append("\"").append(exp.title.replace("\"", "\"\"")).append("\",")
                    .append("\"").append(exp.category.replace("\"", "\"\"")).append("\",")
                    .append("\"").append(ExpenseParser.extractBankSource(exp.rawSms).replace("\"", "\"\"")).append("\",")
                    .append(exp.amount).append(",")
                    .append("\"").append(exp.rawSms != null ? exp.rawSms.replace("\"", "\"\"") : "").append("\"\n");
        }

        try {
            java.io.File file = new java.io.File(getExternalFilesDir(null), "SpendWise_Statement_" + (selectedMonth != null ? selectedMonth : "All") + ".csv");
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(sb.toString());
            writer.flush();
            writer.close();

            Uri contentUri = Uri.parse("content://com.spendwise.app.fileprovider/" + file.getName());
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "SpendWise Expense Statement (" + formatMonthDisplay(selectedMonth) + ")");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Export Statement via:"));
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void promptChangeServerIp() {
        final EditText input = new EditText(this);
        input.setText(db.getServerUrl());

        new AlertDialog.Builder(this)
                .setTitle("Update Server Address")
                .setMessage("Enter your Mac's server address for in-app updates:")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String val = input.getText().toString().trim();
                    if (!val.isEmpty()) {
                        db.setServerUrl(val);
                        Toast.makeText(this, "Server updated: " + val, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
