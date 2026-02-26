package com.aariz.freshtrack;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// ─────────────────────────────────────────────────────────────
//  Data class
// ─────────────────────────────────────────────────────────────
class GS1ParsedData {
    public String gtin = "";
    public String expiryDate = "";
    public String batchLot = "";
    public String serialNumber = "";
    public String productionDate = "";
    public String bestBeforeDate = "";
    public String sellByDate = "";
    public String weight = "";
    public String price = "";
    public String rawData = "";
    public Map<String, String> applicationIdentifiers = Collections.emptyMap();

    public GS1ParsedData() {}

    public GS1ParsedData(String rawData) {
        this.rawData = rawData;
    }
}

// ─────────────────────────────────────────────────────────────
//  Parser
// ─────────────────────────────────────────────────────────────
public class GS1Parser {

    private static final String TAG = "GS1Parser";

    private static final String AI_EXPIRY_DATE = "17";
    private static final String AI_PRODUCTION_DATE = "11";
    private static final String AI_BEST_BEFORE_DATE = "15";
    private static final String AI_SELL_BY_DATE = "16";
    private static final String AI_GTIN = "01";
    private static final String AI_BATCH_LOT = "10";
    private static final String AI_SERIAL_NUMBER = "21";
    private static final String AI_WEIGHT = "30";

    private static final String GROUP_SEPARATOR = "\u001D";
    private static final String FNC1 = "]C1";

    private static final Set<String> VALID_AIS = new HashSet<>();

    static {
        Collections.addAll(VALID_AIS,
                "00", "01", "02", "10", "11", "12", "13", "15", "16", "17", "20", "21",
                "22", "30", "31", "32", "33", "34", "35", "36", "37", "90", "91", "92",
                "93", "94", "95", "96", "97", "98", "99", "240", "241", "242", "243",
                "250", "251", "253", "254", "255", "390", "391", "392", "393", "394",
                "395", "396", "397", "400", "401", "402", "403", "410", "411", "412",
                "413", "414", "415", "416", "417", "420", "421", "422", "423", "424",
                "425", "426", "427");
    }

    public GS1ParsedData parseGS1Data(String rawData) {
        Log.d(TAG, "Parsing GS1 data: " + rawData);
        try {
            String clean = preprocessGS1Data(rawData);
            Map<String, String> ais = extractApplicationIdentifiers(clean);

            String expiryDate = parseAndFormatDate(ais.get(AI_EXPIRY_DATE));
            String productionDate = parseAndFormatDate(ais.get(AI_PRODUCTION_DATE));
            String bestBeforeDate = parseAndFormatDate(ais.get(AI_BEST_BEFORE_DATE));
            String sellByDate = parseAndFormatDate(ais.get(AI_SELL_BY_DATE));

            String finalExpiry;
            if (!expiryDate.isEmpty()) finalExpiry = expiryDate;
            else if (!bestBeforeDate.isEmpty()) finalExpiry = bestBeforeDate;
            else if (!sellByDate.isEmpty()) finalExpiry = sellByDate;
            else finalExpiry = "";

            GS1ParsedData data = new GS1ParsedData();
            data.gtin = orEmpty(ais.get(AI_GTIN));
            data.expiryDate = finalExpiry;
            data.batchLot = orEmpty(ais.get(AI_BATCH_LOT));
            data.serialNumber = orEmpty(ais.get(AI_SERIAL_NUMBER));
            data.productionDate = productionDate;
            data.bestBeforeDate = bestBeforeDate;
            data.sellByDate = sellByDate;
            data.weight = orEmpty(ais.get(AI_WEIGHT));
            data.rawData = rawData;
            data.applicationIdentifiers = ais;
            Log.d(TAG, "Parsed: gtin=" + data.gtin + " expiry=" + data.expiryDate);
            return data;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing GS1 data: " + e.getMessage(), e);
            return new GS1ParsedData(rawData);
        }
    }

    private String preprocessGS1Data(String rawData) {
        String clean = rawData
                .replace(FNC1, "")
                .replace("]C1", "")
                .replace("]d2", "")
                .replace("<GS>", GROUP_SEPARATOR)
                .replace("\\u001D", GROUP_SEPARATOR);
        Log.d(TAG, "Preprocessed: " + clean);
        return clean;
    }

    private Map<String, String> extractApplicationIdentifiers(String data) {
        Map<String, String> ais = new HashMap<>();

        List<String> segments;
        if (data.contains(GROUP_SEPARATOR)) {
            segments = new ArrayList<>();
            for (String s : data.split(GROUP_SEPARATOR, -1)) segments.add(s);
        } else {
            segments = parseWithoutSeparators(data);
        }

        for (String segment : segments) {
            if (segment.length() < 3) continue;
            for (int aiLen = 2; aiLen <= 4; aiLen++) {
                if (segment.length() > aiLen) {
                    String ai = segment.substring(0, aiLen);
                    String value = segment.substring(aiLen);
                    if (VALID_AIS.contains(ai)) {
                        ais.put(ai, value);
                        Log.d(TAG, "Found AI: " + ai + " = " + value);
                        break;
                    }
                }
            }
        }
        return ais;
    }

    private List<String> parseWithoutSeparators(String data) {
        List<String> segments = new ArrayList<>();
        int pos = 0;
        while (pos < data.length()) {
            String ai = findNextApplicationIdentifier(data, pos);
            if (ai != null) {
                int valueLen = getExpectedValueLength(ai, data, pos + ai.length());
                int end = pos + ai.length() + valueLen;
                if (end <= data.length()) {
                    segments.add(data.substring(pos, end));
                    pos = end;
                } else {
                    segments.add(data.substring(pos));
                    break;
                }
            } else {
                break;
            }
        }
        return segments;
    }

    private String findNextApplicationIdentifier(String data, int startPos) {
        String[] common = {"01", "10", "11", "15", "17", "21", "30", "392"};
        for (String ai : common) {
            if (startPos + ai.length() <= data.length()
                    && data.substring(startPos, startPos + ai.length()).equals(ai)) {
                return ai;
            }
        }
        return null;
    }

    private int getExpectedValueLength(String ai, String data, int valueStartPos) {
        switch (ai) {
            case "01": return 14;
            case "11": case "15": case "17": return 6;
            case "30": return 8;
            default: {
                String remaining = data.substring(valueStartPos);
                Integer next = findNextAIPosition(remaining);
                return next != null ? next : remaining.length();
            }
        }
    }

    private Integer findNextAIPosition(String data) {
        String[] common = {"01", "10", "11", "15", "17", "21", "30"};
        for (int i = 1; i < data.length() - 1; i++) {
            for (String ai : common) {
                if (i + ai.length() <= data.length()
                        && data.substring(i, i + ai.length()).equals(ai)) {
                    return i;
                }
            }
        }
        return null;
    }

    private String parseAndFormatDate(String dateString) {
        if (dateString == null || dateString.length() != 6) return "";
        try {
            int year = Integer.parseInt(dateString.substring(0, 2));
            int month = Integer.parseInt(dateString.substring(2, 4));
            int day = Integer.parseInt(dateString.substring(4, 6));
            int fullYear = year <= 49 ? 2000 + year : 1900 + year;

            if (month < 1 || month > 12 || day < 1 || day > 31) {
                Log.w(TAG, "Invalid date: y=" + fullYear + " m=" + month + " d=" + day);
                return "";
            }

            Calendar cal = Calendar.getInstance();
            cal.set(fullYear, month - 1, day);
            return new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(cal.getTime());
        } catch (Exception e) {
            Log.e(TAG, "Error parsing date: " + dateString, e);
            return "";
        }
    }

    public boolean isGS1Format(String data) {
        return data.contains(GROUP_SEPARATOR)
                || data.contains(FNC1)
                || data.contains("]C1")
                || data.contains("]d2")
                || startsWithGS1AI(data);
    }

    private boolean startsWithGS1AI(String data) {
        if (data.length() < 2) return false;
        String[] starts = {"01", "10", "11", "15", "17", "21", "30", "90", "91"};
        String prefix = data.substring(0, 2);
        for (String s : starts) {
            if (s.equals(prefix) && data.length() > 10) return true;
        }
        return false;
    }

    public String extractPrimaryBarcode(GS1ParsedData gs1Data) {
        if (!gs1Data.gtin.isEmpty()) return gs1Data.gtin;
        if (gs1Data.rawData.length() >= 12) {
            String cleaned = gs1Data.rawData.replaceAll("[^0-9]", "");
            if (cleaned.length() >= 12) return cleaned.substring(0, Math.min(14, cleaned.length()));
        }
        return gs1Data.rawData;
    }

    private static String orEmpty(String s) {
        return s != null ? s : "";
    }
}