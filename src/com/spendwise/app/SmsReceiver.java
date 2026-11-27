package com.spendwise.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.Locale;

public class SmsReceiver extends BroadcastReceiver {

    public static final String ACTION_UPDATE_UI = "com.spendwise.app.UPDATE_UI";
    private static final String CHANNEL_ID = "spendwise_realtime_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            return;
        }

        StringBuilder fullBody = new StringBuilder();
        String sender = messages[0].getOriginatingAddress();
        for (SmsMessage msg : messages) {
            if (msg.getMessageBody() != null) {
                fullBody.append(msg.getMessageBody());
            }
        }

        String smsText = fullBody.toString();
        Log.d("SpendWise", "Received SMS from " + sender + ": " + smsText);

        processSms(context, smsText);
    }

    public static boolean processSms(Context context, String smsText) {
        ExpenseParser.ParsedResult res = ExpenseParser.parse(smsText);
        if (res == null) {
            return false;
        }

        // Save to SQLite Database
        SpendWiseDbHelper db = new SpendWiseDbHelper(context);
        String yearMonth = res.date != null && res.date.length() >= 7 ? res.date.substring(0, 7) : "";
        double currentSpentBefore = db.getTotalSpentByMonth(yearMonth);
        double budget = db.getBudget();

        db.insertExpense(res.date, res.title, res.category, res.amount, smsText);

        double totalAfter = currentSpentBefore + res.amount;
        boolean isOverBudget = budget > 0 && totalAfter > budget;
        double overBy = isOverBudget ? (totalAfter - budget) : 0.0;

        // Show Instant Notification on Phone with Over-Budget Alert if applicable
        showNotification(context, res, isOverBudget, overBy, budget);

        // Broadcast to MainActivity if active
        Intent updateIntent = new Intent(ACTION_UPDATE_UI);
        updateIntent.putExtra("is_over_budget", isOverBudget);
        updateIntent.putExtra("over_by", overBy);
        updateIntent.putExtra("merchant", res.title);
        updateIntent.putExtra("amount", res.amount);
        context.sendBroadcast(updateIntent);

        return true;
    }

    private static void showNotification(Context context, ExpenseParser.ParsedResult res, boolean isOverBudget, double overBy, double budget) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SpendWise Expense Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Real-time notifications when a UPI/Bank expense is detected");
            channel.enableVibration(true);
            nm.createNotificationChannel(channel);
        }

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                context,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title;
        String text;
        if (isOverBudget) {
            title = String.format(Locale.US, "🚨 Over Budget Alert! ₹%.0f at %s", res.amount, res.title);
            text = String.format(Locale.US, "Exceeded monthly limit by ₹%,.0f! (Limit: ₹%,.0f)", overBy, budget);
        } else {
            title = String.format(Locale.US, "💎 SpendWise: Logged ₹%.0f at %s", res.amount, res.title);
            text = res.category + " • Automatically tracked in real-time!";
        }

        android.app.Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(context);
        }

        builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true);

        nm.notify((int) System.currentTimeMillis(), builder.build());
    }
}
