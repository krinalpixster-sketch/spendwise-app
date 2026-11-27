package com.spendwise.app;

public class Expense {
    public int id;
    public String date;
    public String title;
    public String category;
    public double amount;
    public String rawSms;

    public Expense(int id, String date, String title, String category, double amount, String rawSms) {
        this.id = id;
        this.date = date;
        this.title = title;
        this.category = category;
        this.amount = amount;
        this.rawSms = rawSms;
    }
}
