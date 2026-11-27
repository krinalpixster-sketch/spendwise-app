package com.spendwise.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.List;

public class SettingsActivity extends Activity {

    private SpendWiseDbHelper db;
    private SharedPreferences prefs;

    private TextView btnThemeLight;
    private TextView btnThemeDark;
    private TextView btnThemeSystem;

    private Switch switchPrivacy;
    private TextView tvBudgetVal;

    private DecimalFormat currencyFormat = new DecimalFormat("#,##0");

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeHelper.applyThemeContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        db = new SpendWiseDbHelper(this);
        prefs = getSharedPreferences(ThemeHelper.PREF_NAME, Context.MODE_PRIVATE);

        initViews();
        setupThemeSelector();
        setupPrivacyMode();
        setupBudgetSetting();
        setupDataActions();
    }

    private void initViews() {
        TextView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        btnThemeLight = findViewById(R.id.btn_theme_light);
        btnThemeDark = findViewById(R.id.btn_theme_dark);
        btnThemeSystem = findViewById(R.id.btn_theme_system);

        switchPrivacy = findViewById(R.id.switch_privacy_mode);
        tvBudgetVal = findViewById(R.id.tv_settings_budget_val);

        tvBudgetVal.setText("₹" + currencyFormat.format(db.getBudget()) + " / month");

        TextView tvFooterVersion = findViewById(R.id.tv_settings_footer_version);
        if (tvFooterVersion != null) {
            tvFooterVersion.setText("v" + getAppVersionName());
        }
    }

    private void setupThemeSelector() {
        String currentMode = ThemeHelper.getThemeMode(this);
        highlightThemeButton(currentMode);

        btnThemeLight.setOnClickListener(v -> changeTheme(ThemeHelper.MODE_LIGHT));
        btnThemeDark.setOnClickListener(v -> changeTheme(ThemeHelper.MODE_DARK));
        btnThemeSystem.setOnClickListener(v -> changeTheme(ThemeHelper.MODE_SYSTEM));
    }

    private void highlightThemeButton(String mode) {
        int activeText = 0xFFFFFFFF;
        int inactiveText = getColor(R.color.text_secondary);

        btnThemeLight.setBackgroundResource(R.drawable.theme_pill_unselected);
        btnThemeLight.setTextColor(inactiveText);

        btnThemeDark.setBackgroundResource(R.drawable.theme_pill_unselected);
        btnThemeDark.setTextColor(inactiveText);

        btnThemeSystem.setBackgroundResource(R.drawable.theme_pill_unselected);
        btnThemeSystem.setTextColor(inactiveText);

        if (ThemeHelper.MODE_LIGHT.equals(mode)) {
            btnThemeLight.setBackgroundResource(R.drawable.theme_pill_selected);
            btnThemeLight.setTextColor(activeText);
        } else if (ThemeHelper.MODE_DARK.equals(mode)) {
            btnThemeDark.setBackgroundResource(R.drawable.theme_pill_selected);
            btnThemeDark.setTextColor(activeText);
        } else {
            btnThemeSystem.setBackgroundResource(R.drawable.theme_pill_selected);
            btnThemeSystem.setTextColor(activeText);
        }
    }

    private void changeTheme(String newMode) {
        String currentMode = ThemeHelper.getThemeMode(this);
        if (!currentMode.equals(newMode)) {
            ThemeHelper.setThemeMode(this, newMode);
            recreate();
        }
    }

    private void setupPrivacyMode() {
        boolean isPrivacy = prefs.getBoolean("privacy_mode", false);
        switchPrivacy.setChecked(isPrivacy);

        switchPrivacy.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("privacy_mode", isChecked).apply();
            Toast.makeText(this, isChecked ? "Privacy Mode Enabled (Amounts Hidden)" : "Privacy Mode Disabled (Amounts Visible)", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.row_privacy_mode).setOnClickListener(v -> switchPrivacy.toggle());
    }

    private void setupBudgetSetting() {
        findViewById(R.id.row_target_budget).setOnClickListener(v -> promptChangeBudget());
    }

    private void promptChangeBudget() {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.valueOf((int) db.getBudget()));
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Monthly Target Budget")
                .setMessage("Set your monthly target spending limit in ₹:")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String str = input.getText().toString().trim();
                    if (!str.isEmpty()) {
                        try {
                            double newBudget = Double.parseDouble(str);
                            if (newBudget > 0) {
                                db.setBudget(newBudget);
                                tvBudgetVal.setText("₹" + currencyFormat.format(newBudget) + " / month");
                                Toast.makeText(this, "Target budget updated: ₹" + currencyFormat.format(newBudget), Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupDataActions() {
        findViewById(R.id.row_export_csv).setOnClickListener(v -> exportExpensesToCsv());
        findViewById(R.id.row_reset_data).setOnClickListener(v -> confirmClearData());
    }

    private void exportExpensesToCsv() {
        List<Expense> expenses = db.getAllExpenses();
        if (expenses.isEmpty()) {
            Toast.makeText(this, "No transactions found to export.", Toast.LENGTH_SHORT).show();
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
            File file = new File(getExternalFilesDir(null), "SpendWise_Statement_All.csv");
            FileWriter writer = new FileWriter(file);
            writer.write(sb.toString());
            writer.flush();
            writer.close();

            Uri contentUri = Uri.parse("content://com.spendwise.app.fileprovider/" + file.getName());
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "SpendWise Expense Statement (All)");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Export Statement via:"));
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this)
                .setTitle("Reset All Transactions? 🗑️")
                .setMessage("Are you sure you want to permanently clear all transactions from SpendWise?\n\nThis will reset your ledger, spending trends, and category charts. This action cannot be undone.")
                .setPositiveButton("Clear Everything", (dialog, which) -> {
                    db.clearAll();
                    Toast.makeText(this, "All transaction records cleared.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getAppVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "2.4.0";
        }
    }
}
