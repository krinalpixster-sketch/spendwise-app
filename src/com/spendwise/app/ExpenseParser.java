package com.spendwise.app;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpenseParser {

    public static class ParsedResult {
        public double amount;
        public String title;
        public String category;
        public String date;

        public ParsedResult(double amount, String title, String category, String date) {
            this.amount = amount;
            this.title = title;
            this.category = category;
            this.date = date;
        }
    }

    public static ParsedResult parse(String smsText) {
        if (smsText == null || smsText.trim().isEmpty()) {
            return null;
        }
        String textLower = smsText.toLowerCase();

        // Check if message is a debit/spent notification
        boolean hasDebitKeyword = textLower.contains("debit") || textLower.contains("spent")
                || textLower.contains("paid") || textLower.contains("transferred")
                || textLower.contains("deducted") || textLower.contains("dr")
                || textLower.contains("txn") || textLower.contains("upi");

        // Skip credit/refund/salary unless debit is explicitly present
        if (textLower.contains("credited") || textLower.contains("deposit")) {
            if (!textLower.contains("debited")) {
                return null;
            }
        }

        if (!hasDebitKeyword) {
            return null;
        }

        // 1. Extract Amount
        Pattern amtPattern = Pattern.compile(
                "(?:rs\\.?|inr|₹|debited\\s+by|spent\\s+rs\\.?|paid\\s+rs\\.?)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher amtMatcher = amtPattern.matcher(smsText);
        double amount = 0.0;
        if (amtMatcher.find()) {
            String rawNum = amtMatcher.group(1).replaceAll("[,\\s]", "");
            try {
                amount = Double.parseDouble(rawNum);
            } catch (NumberFormatException ignored) {}
        }

        if (amount <= 0.0) {
            return null;
        }

        // 2. Extract Merchant / Payee
        String title = "UPI Transaction";
        Pattern merchantPattern = Pattern.compile(
                "(?:at|to|vpa|info|towards)\\s+([A-Za-z0-9\\s&_\\-\\.]+?)(?:\\s+on|\\s+ref|\\s+avail|\\s+bal|\\.|\\n|,|$)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher mMatcher = merchantPattern.matcher(smsText);
        if (mMatcher.find()) {
            String candidate = mMatcher.group(1).trim();
            if (candidate.length() > 2) {
                title = cleanMerchant(candidate);
            }
        } else {
            // Check for UPI/xxx/merchant
            Pattern upiPattern = Pattern.compile("UPI(?:/|-)[^/]+/(.+?)(?:/|$)", Pattern.CASE_INSENSITIVE);
            Matcher uMatcher = upiPattern.matcher(smsText);
            if (uMatcher.find()) {
                title = cleanMerchant(uMatcher.group(1));
            }
        }

        // 3. Auto-Categorize
        String category = categorize(smsText + " " + title);

        // 4. Date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateStr = sdf.format(new Date());

        return new ParsedResult(amount, title, category, dateStr);
    }

    private static String cleanMerchant(String raw) {
        String cleaned = raw.replaceAll("[/\\\\:*?\"<>|]", " ")
                .replaceAll("\\b\\d{5,}\\b", "")
                .trim();
        String[] words = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String w : words) {
            if (w.isEmpty() || w.equalsIgnoreCase("upi") || w.equalsIgnoreCase("cr") || w.equalsIgnoreCase("dr")) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1).toLowerCase());
            count++;
            if (count >= 3) break;
        }
        return sb.length() > 0 ? sb.toString() : "UPI Payment";
    }

    public static String categorize(String text) {
        String lower = text.toLowerCase();
        if (matches(lower, "swiggy", "zomato", "mcdonald", "kfc", "dominos", "starbucks", "burger", "pizza", "cafe", "restaurant", "dhaba", "eatclub", "chai", "coffee", "bakery")) {
            return "🍔 Food & Dining";
        }
        if (matches(lower, "blinkit", "zepto", "instamart", "dmart", "bigbasket", "milk", "dairy", "supermarket", "kirana", "grocery")) {
            return "🛒 Groceries";
        }
        if (matches(lower, "uber", "ola", "rapido", "metro", "irctc", "petrol", "fuel", "hpcl", "bpcl", "iocl", "fastag", "toll", "shell")) {
            return "🚗 Travel & Commute";
        }
        if (matches(lower, "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "zara", "h&m", "decathlon", "croma", "tata cliq")) {
            return "🛍️ Shopping";
        }
        if (matches(lower, "electricity", "bescom", "adani", "airtel", "jio", "vi", "gas", "broadband", "dishtv", "recharge", "billdesk")) {
            return "💡 Bills & Utilities";
        }
        if (matches(lower, "netflix", "prime", "spotify", "bookmyshow", "pvr", "inox", "hotstar", "youtube", "steam")) {
            return "🎬 Entertainment";
        }
        if (matches(lower, "apollo", "netmeds", "1mg", "pharmeasy", "medplus", "hospital", "clinic", "pharmacy", "doctor")) {
            return "💊 Health & Medical";
        }
        if (matches(lower, "udemy", "coursera", "college", "school", "tuition", "books", "course")) {
            return "💼 Work & Education";
        }
        return "📦 Others";
    }

    public static String getEmoji(String category) {
        if (category == null) return "💎";
        if (category.contains("Food")) return "🍔";
        if (category.contains("Groceries")) return "🛒";
        if (category.contains("Travel")) return "🚗";
        if (category.contains("Shopping")) return "🛍️";
        if (category.contains("Bills")) return "💡";
        if (category.contains("Entertainment")) return "🎬";
        if (category.contains("Health")) return "💊";
        if (category.contains("Work")) return "💼";
        return "📦";
    }

    private static boolean matches(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    public static String extractBankSource(String rawSms) {
        if (rawSms == null || rawSms.trim().isEmpty() || rawSms.equalsIgnoreCase("Manual entry")) {
            return "💵 Cash / Manual";
        }
        String lower = rawSms.toLowerCase();
        if (lower.contains("hdfc")) return "🏦 HDFC Bank";
        if (lower.contains("sbi") || lower.contains("state bank")) return "🏦 State Bank of India";
        if (lower.contains("icici")) return "🏦 ICICI Bank";
        if (lower.contains("axis")) return "🏦 Axis Bank";
        if (lower.contains("kotak")) return "🏦 Kotak Bank";
        if (lower.contains("pnb") || lower.contains("punjab national")) return "🏦 Punjab National Bank";
        if (lower.contains("bob") || lower.contains("bank of baroda")) return "🏦 Bank of Baroda";
        if (lower.contains("paytm")) return "📱 Paytm";
        if (lower.contains("canara")) return "🏦 Canara Bank";
        if (lower.contains("indusind")) return "🏦 IndusInd Bank";
        if (lower.contains("idfc")) return "🏦 IDFC FIRST Bank";
        if (lower.contains("union")) return "🏦 Union Bank";
        if (lower.contains("yes bank")) return "🏦 Yes Bank";
        if (lower.contains("cred")) return "⚡ CRED";
        if (lower.contains("gpay") || lower.contains("google pay")) return "⚡ Google Pay";
        if (lower.contains("phonepe")) return "⚡ PhonePe";
        if (lower.contains("upi") || lower.contains("vpa")) return "⚡ UPI Transfer";
        return "💳 Bank / Card";
    }

    public static int getBankColor(String bank) {
        if (bank == null) return 0xFF64748B;
        if (bank.contains("HDFC")) return 0xFF1D4ED8;
        if (bank.contains("SBI") || bank.contains("State Bank")) return 0xFF0284C7;
        if (bank.contains("ICICI")) return 0xFFEA580C;
        if (bank.contains("Axis")) return 0xFFBE123C;
        if (bank.contains("Kotak")) return 0xFFE11D48;
        if (bank.contains("Paytm")) return 0xFF06B6D4;
        if (bank.contains("Cash") || bank.contains("Manual")) return 0xFF10B981;
        if (bank.contains("CRED") || bank.contains("UPI")) return 0xFF8B5CF6;
        return 0xFF64748B;
    }
}
