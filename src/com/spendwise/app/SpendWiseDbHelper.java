package com.spendwise.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpendWiseDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "spendwise.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_EXPENSES = "expenses";
    private static final String TABLE_SETTINGS = "settings";

    public SpendWiseDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_EXPENSES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "date TEXT, " +
                "title TEXT, " +
                "category TEXT, " +
                "amount REAL, " +
                "raw_sms TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_SETTINGS + " (" +
                "key TEXT PRIMARY KEY, " +
                "value TEXT)");

        // Default monthly budget & update server
        db.execSQL("INSERT OR IGNORE INTO " + TABLE_SETTINGS + " (key, value) VALUES ('budget', '25000')");
        db.execSQL("INSERT OR IGNORE INTO " + TABLE_SETTINGS + " (key, value) VALUES ('server_url', 'https://atm-opera-crew-heel.trycloudflare.com')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EXPENSES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SETTINGS);
        onCreate(db);
    }

    public long insertExpense(String date, String title, String category, double amount, String rawSms) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("date", date);
        cv.put("title", title);
        cv.put("category", category);
        cv.put("amount", amount);
        cv.put("raw_sms", rawSms);
        return db.insert(TABLE_EXPENSES, null, cv);
    }

    public List<Expense> getAllExpenses() {
        List<Expense> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, date, title, category, amount, raw_sms FROM " + TABLE_EXPENSES + " ORDER BY id DESC", null);
        if (c.moveToFirst()) {
            do {
                list.add(new Expense(
                        c.getInt(0),
                        c.getString(1),
                        c.getString(2),
                        c.getString(3),
                        c.getDouble(4),
                        c.getString(5)
                ));
            } while (c.moveToNext());
        }
        c.close();
        return list;
    }

    public double getTotalSpent() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT SUM(amount) FROM " + TABLE_EXPENSES, null);
        double total = 0.0;
        if (c.moveToFirst() && !c.isNull(0)) {
            total = c.getDouble(0);
        }
        c.close();
        return total;
    }

    public Map<String, Double> getCategoryTotals() {
        Map<String, Double> map = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT category, SUM(amount) FROM " + TABLE_EXPENSES + " GROUP BY category", null);
        if (c.moveToFirst()) {
            do {
                map.put(c.getString(0), c.getDouble(1));
            } while (c.moveToNext());
        }
        c.close();
        return map;
    }

    public List<Expense> getExpensesByMonth(String yearMonth) {
        if (yearMonth == null || yearMonth.equalsIgnoreCase("ALL")) {
            return getAllExpenses();
        }
        List<Expense> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, date, title, category, amount, raw_sms FROM " + TABLE_EXPENSES + " WHERE date LIKE ? ORDER BY id DESC", new String[]{yearMonth + "%"});
        if (c.moveToFirst()) {
            do {
                list.add(new Expense(
                        c.getInt(0),
                        c.getString(1),
                        c.getString(2),
                        c.getString(3),
                        c.getDouble(4),
                        c.getString(5)
                ));
            } while (c.moveToNext());
        }
        c.close();
        return list;
    }

    public double getTotalSpentByMonth(String yearMonth) {
        if (yearMonth == null || yearMonth.equalsIgnoreCase("ALL")) {
            return getTotalSpent();
        }
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT SUM(amount) FROM " + TABLE_EXPENSES + " WHERE date LIKE ?", new String[]{yearMonth + "%"});
        double total = 0.0;
        if (c.moveToFirst() && !c.isNull(0)) {
            total = c.getDouble(0);
        }
        c.close();
        return total;
    }

    public Map<String, Double> getCategoryTotalsByMonth(String yearMonth) {
        if (yearMonth == null || yearMonth.equalsIgnoreCase("ALL")) {
            return getCategoryTotals();
        }
        Map<String, Double> map = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT category, SUM(amount) FROM " + TABLE_EXPENSES + " WHERE date LIKE ? GROUP BY category", new String[]{yearMonth + "%"});
        if (c.moveToFirst()) {
            do {
                map.put(c.getString(0), c.getDouble(1));
            } while (c.moveToNext());
        }
        c.close();
        return map;
    }

    public List<String> getAvailableMonths() {
        List<String> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT DISTINCT SUBSTR(date, 1, 7) FROM " + TABLE_EXPENSES + " WHERE date IS NOT NULL AND LENGTH(date) >= 7 ORDER BY date DESC", null);
        if (c.moveToFirst()) {
            do {
                String ym = c.getString(0);
                if (ym != null && !ym.isEmpty() && !list.contains(ym)) {
                    list.add(ym);
                }
            } while (c.moveToNext());
        }
        c.close();
        return list;
    }

    public double getBudget() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT value FROM " + TABLE_SETTINGS + " WHERE key = 'budget'", null);
        double b = 25000.0;
        if (c.moveToFirst()) {
            try {
                b = Double.parseDouble(c.getString(0));
            } catch (Exception ignored) {}
        }
        c.close();
        return b;
    }

    public void setBudget(double budget) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("value", String.valueOf(budget));
        db.update(TABLE_SETTINGS, cv, "key = 'budget'", null);
    }

    public String getServerUrl() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT value FROM " + TABLE_SETTINGS + " WHERE key = 'server_url'", null);
        String url = "https://atm-opera-crew-heel.trycloudflare.com";
        if (c.moveToFirst()) {
            url = c.getString(0);
        }
        c.close();
        return url;
    }

    public void setServerUrl(String url) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("key", "server_url");
        cv.put("value", url);
        db.insertWithOnConflict(TABLE_SETTINGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public boolean deleteExpense(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_EXPENSES, "id = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public void clearAll() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_EXPENSES);
    }
}
