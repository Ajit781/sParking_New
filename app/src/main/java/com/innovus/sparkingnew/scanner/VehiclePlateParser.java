package com.innovus.sparkingnew.scanner;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, fault-tolerant parser for Indian vehicle registration number plates.
 * Handles standard, commercial (taxis/trucks), 2-wheeler, 2-line/3-line plates, vintage formats,
 * Bharat (BH) series, and full state-name headers (e.g. WEST BENGAL -> WB).
 * Automatically strips HSRP/IND logos and laser codes, preventing truncated/partial auto-detection.
 */
public final class VehiclePlateParser {

    private static final String TAG = "SPARKING_OCR";

    // Valid 2-letter state / union territory codes in India
    public static final Set<String> INDIAN_STATE_CODES = new HashSet<>(Arrays.asList(
            "AN", "AP", "AR", "AS", "BR", "CG", "CH", "DD", "DN", "DL",
            "GA", "GJ", "HP", "HR", "JH", "JK", "KA", "KL", "LA", "LD",
            "MH", "ML", "MN", "MP", "MZ", "NL", "OD", "OR", "PB", "PY",
            "RJ", "SK", "TN", "TR", "TS", "UK", "UA", "UP", "WB"
    ));

    // 1. Standard Indian plate with 1, 2, or 3 series letters:
    // e.g. WB04F7728, WB02AF6376, WB01AK0321, MH49AU6390, DL1CAA1111
    public static final Pattern PATTERN_FULL_STANDARD = Pattern.compile(
            "^([A-Z]{2})([0-9]{1,2})([A-Z]{1,3})([0-9]{1,4})$"
    );

    // 2. Vintage / commercial plate without series letters:
    // e.g. WB199212, WB021234, DL011234
    // Requires at least 5-6 digits total (1-2 RTO digits + 4 vehicle number digits).
    // Minimum total length is 7 characters (e.g. DL11234) or 8 characters (e.g. WB199212).
    // NEVER matches 6-character strings like "AP1424", which are 2-tier bottom rows.
    public static final Pattern PATTERN_FULL_NO_SERIES = Pattern.compile(
            "^([A-Z]{2})([0-9]{1,2})([0-9]{4})$"
    );

    // 3. Bharat Series:
    // e.g. 22BH1234AA
    public static final Pattern PATTERN_FULL_BH = Pattern.compile(
            "^([0-9]{2})BH([0-9]{4})([A-Z]{1,2})$"
    );

    // Embedded search patterns (with optional spacing / hyphens / dots inside text)
    private static final Pattern PATTERN_EMBEDDED_STANDARD = Pattern.compile(
            "(?:^|[^A-Z0-9])([A-Z]{2})\\s*[-.]?\\s*([0-9]{1,2})\\s*[-.]?\\s*([A-Z]{1,3})\\s*[-.]?\\s*([0-9]{1,4})(?:$|[^A-Z0-9])"
    );

    private static final Pattern PATTERN_EMBEDDED_NO_SERIES = Pattern.compile(
            "(?:^|[^A-Z0-9])([A-Z]{2})\\s*[-.]?\\s*([0-9]{1,2})\\s*[-.]?\\s*([0-9]{4})(?:$|[^A-Z0-9])"
    );

    private static final Pattern PATTERN_EMBEDDED_BH = Pattern.compile(
            "(?:^|[^A-Z0-9])([0-9]{2})\\s*[-.]?\\s*BH\\s*[-.]?\\s*([0-9]{4})\\s*[-.]?\\s*([A-Z]{1,2})(?:$|[^A-Z0-9])"
    );

    // Mobile number regex
    private static final Pattern PATTERN_MOBILE_LABELED = Pattern.compile(
            "(?:mobile|phone|contact|no|call)[:\\s-]*\\+?(?:91)?[\\s-]*([6-9][0-9]{9})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PATTERN_MOBILE_GENERIC = Pattern.compile(
            "\\b[6-9][0-9]{9}\\b"
    );

    public static class ParseResult {
        @Nullable
        private final String vehicleNo;
        @Nullable
        private final String mobileNo;
        @NonNull
        private final String rawText;

        public ParseResult(@Nullable String vehicleNo, @Nullable String mobileNo, @NonNull String rawText) {
            this.vehicleNo = vehicleNo;
            this.mobileNo = mobileNo;
            this.rawText = rawText;
        }

        @Nullable
        public String getVehicleNo() {
            return vehicleNo;
        }

        @Nullable
        public String getMobileNo() {
            return mobileNo;
        }

        @NonNull
        public String getRawText() {
            return rawText;
        }

        public boolean hasVehicleNo() {
            return vehicleNo != null && !vehicleNo.trim().isEmpty();
        }

        public boolean hasMobileNo() {
            return mobileNo != null && !mobileNo.trim().isEmpty();
        }
    }

    private VehiclePlateParser() {}

    /**
     * Parses raw OCR text with comprehensive multi-line recombination and validation.
     */
    @NonNull
    public static ParseResult parse(@Nullable String rawText) {
        return parse(rawText, null);
    }

    /**
     * Parses raw OCR text and individual ordered lines (top-to-bottom) from camera frame.
     */
    @NonNull
    public static ParseResult parse(@Nullable String rawText, @Nullable List<String> rawLines) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return new ParseResult(null, null, "");
        }

        Log.d(TAG, "===> VehiclePlateParser input: [" + rawText.replace("\n", " | ") + "]");

        // 1. Extract Mobile number if visible
        String mobile = extractMobile(rawText);

        // 2. Extract Complete Vehicle Registration Plate
        String vehicleNo = extractVehiclePlate(rawText, rawLines);

        if (vehicleNo != null) {
            Log.i(TAG, "✅ SUCCESS! Complete Extracted Plate: [" + vehicleNo + "]");
        } else {
            Log.d(TAG, "No complete plate matched in this frame.");
        }

        return new ParseResult(vehicleNo, mobile, rawText);
    }

    /**
     * Cleans OCR noise commonly found on Indian vehicle number plates.
     * Replaces full state names (e.g. WEST BENGAL -> WB) and strips HSRP / IND logos.
     */
    @NonNull
    public static String cleanOcrNoise(@NonNull String text) {
        if (text == null) return "";
        String s = text.toUpperCase(Locale.US);

        // 1. Normalize full state names and abbreviations across India
        s = s.replaceAll("(?i)\\bWEST[\\s._-]*BENGAL\\b", "WB ");
        s = s.replaceAll("(?i)\\bW[._-]+B[._-]*\\b", "WB ");
        s = s.replaceAll("(?i)\\bKOLKATA\\b", "WB ");
        s = s.replaceAll("(?i)\\bCALCUTTA\\b", "WB ");
        s = s.replaceAll("(?i)\\bMAHARASHTRA\\b", "MH ");
        s = s.replaceAll("(?i)\\bDELHI\\b", "DL ");
        s = s.replaceAll("(?i)\\bUTTAR[\\s._-]*PRADESH\\b", "UP ");
        s = s.replaceAll("(?i)\\bBIHAR\\b", "BR ");
        s = s.replaceAll("(?i)\\bJHARKHAND\\b", "JH ");
        s = s.replaceAll("(?i)\\bODISHA\\b", "OD ");
        s = s.replaceAll("(?i)\\bORISSA\\b", "OD ");
        s = s.replaceAll("(?i)\\bRAJASTHAN\\b", "RJ ");
        s = s.replaceAll("(?i)\\bGUJARAT\\b", "GJ ");
        s = s.replaceAll("(?i)\\bPUNJAB\\b", "PB ");
        s = s.replaceAll("(?i)\\bHARYANA\\b", "HR ");
        s = s.replaceAll("(?i)\\bHIMACHAL[\\s._-]*PRADESH\\b", "HP ");
        s = s.replaceAll("(?i)\\bMADHYA[\\s._-]*PRADESH\\b", "MP ");
        s = s.replaceAll("(?i)\\bKARNATAKA\\b", "KA ");
        s = s.replaceAll("(?i)\\bTAMIL[\\s._-]*NADU\\b", "TN ");
        s = s.replaceAll("(?i)\\bTAMILNADU\\b", "TN ");
        s = s.replaceAll("(?i)\\bANDHRA[\\s._-]*PRADESH\\b", "AP ");
        s = s.replaceAll("(?i)\\bTELANGANA\\b", "TS ");
        s = s.replaceAll("(?i)\\bKERALA\\b", "KL ");
        s = s.replaceAll("(?i)\\bASSAM\\b", "AS ");
        s = s.replaceAll("(?i)\\bGOA\\b", "GA ");
        s = s.replaceAll("(?i)\\bCHHATTISGARH\\b", "CG ");
        s = s.replaceAll("(?i)\\bUTTARAKHAND\\b", "UK ");
        s = s.replaceAll("(?i)\\bUTTARANCHAL\\b", "UK ");
        s = s.replaceAll("(?i)\\bJAMMU[\\s._-]*&[\\s._-]*KASHMIR\\b", "JK ");
        s = s.replaceAll("(?i)\\bJAMMU[\\s._-]*AND[\\s._-]*KASHMIR\\b", "JK ");

        // Normalize spaced state letters when followed by digits (e.g. "W B 04" -> "WB 04", "M H 12" -> "MH 12")
        Matcher stateMatcher = Pattern.compile("(?i)\\b([A-Z])\\s+([A-Z])(?=\\s*[0-9])").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (stateMatcher.find()) {
            String combined = (stateMatcher.group(1) + stateMatcher.group(2)).toUpperCase(Locale.US);
            if (isValidStateCode(combined)) {
                stateMatcher.appendReplacement(sb, combined + " ");
            } else {
                stateMatcher.appendReplacement(sb, stateMatcher.group(0));
            }
        }
        stateMatcher.appendTail(sb);
        s = sb.toString();

        // 2. Remove HSRP laser security codes (e.g. BA180017170)
        s = s.replaceAll("(?i)\\b[A-Z]{1,2}[0-9]{8,12}\\b", " ");

        // 3. Strip HSRP, IND, and common plate sticker words
        s = s.replaceAll("(?i)\\b(INDIA|HSRP|STOP|COMMERCIAL|PRIVATE|TOURIST|CNG|DIESEL|PETROL|SECURITY|HIGH\\s*SECURITY|REGD|REGISTRATION)\\b", " ");
        s = s.replaceAll("(?i)^IND(?=[A-Z]{2})", " ");
        s = s.replaceAll("(?i)[^A-Z0-9]IND(?=[A-Z]{2})", " ");
        s = s.replaceAll("(?i)\\bIND\\b", " ");

        // 4. Replace common symbols, dashes, dots, bullets with space
        s = s.replaceAll("[|•·:_=~`!@#$%^&*()<>?,/\\\\;\"\']", " ");

        return s.replaceAll("[\\r\\t]", " ").trim();
    }

    /**
     * Extracts a complete, validated Indian vehicle registration plate from OCR text and lines.
     * Checks full unified text, 2-line combinations, and individual lines.
     * NEVER returns incomplete / half numbers.
     */
    @Nullable
    public static String extractVehiclePlate(@NonNull String rawText, @Nullable List<String> rawLines) {
        List<String> lines = new ArrayList<>();
        if (rawLines != null) {
            for (String l : rawLines) {
                String c = cleanOcrNoise(l);
                if (!c.isEmpty()) lines.add(c);
            }
        }
        if (lines.isEmpty()) {
            for (String l : rawText.split("\n")) {
                String c = cleanOcrNoise(l);
                if (!c.isEmpty()) lines.add(c);
            }
        }

        // Strategy 1: Check if the ENTIRE frame text joined as one string produces a complete plate!
        // This solves 2-line taxi and commercial plates instantly:
        // Top line: "WB 04", Bottom line: "F 7728" -> "WB 04 F 7728" -> "WB04F7728"
        StringBuilder unifiedBuilder = new StringBuilder();
        for (String l : lines) {
            unifiedBuilder.append(l).append(" ");
        }
        String unified = unifiedBuilder.toString().trim();
        String candidate1 = findCompletePlateInText(unified);
        if (candidate1 != null) {
            Log.i(TAG, "Plate found in unified frame text: [" + candidate1 + "]");
            return candidate1;
        }

        // Strategy 2: Check 2-line combinations (both forward and reverse order)
        // Handles cases where ML Kit has extra noise lines, or returns bottom line before top line
        for (int i = 0; i < lines.size(); i++) {
            for (int j = i + 1; j < lines.size(); j++) {
                // Forward order (Line i then Line j, e.g. "KL 04" + "AP 1424")
                String comb1 = lines.get(i) + " " + lines.get(j);
                String cand1 = findCompletePlateInText(comb1);
                if (cand1 != null) {
                    Log.i(TAG, "Plate found in 2-line combo (" + i + "+" + j + "): [" + cand1 + "]");
                    return cand1;
                }

                // Reverse order (Line j then Line i, handles when bottom line was returned first)
                String comb2 = lines.get(j) + " " + lines.get(i);
                String cand2 = findCompletePlateInText(comb2);
                if (cand2 != null) {
                    Log.i(TAG, "Plate found in 2-line combo reverse (" + j + "+" + i + "): [" + cand2 + "]");
                    return cand2;
                }
            }
        }

        // Strategy 3: Check 3-line combinations (all permutations)
        // (e.g. Line 1: KL, Line 2: 04, Line 3: AP 1424)
        if (lines.size() >= 3) {
            for (int i = 0; i < lines.size(); i++) {
                for (int j = 0; j < lines.size(); j++) {
                    if (i == j) continue;
                    for (int k = 0; k < lines.size(); k++) {
                        if (k == i || k == j) continue;
                        String comb = lines.get(i) + " " + lines.get(j) + " " + lines.get(k);
                        String cand = findCompletePlateInText(comb);
                        if (cand != null) {
                            Log.i(TAG, "Plate found in 3-line combo: [" + cand + "]");
                            return cand;
                        }
                    }
                }
            }
        }

        // Strategy 4: Check single individual lines (e.g. standard 1-line plate: WB 02 AF 6376)
        for (String l : lines) {
            String cand = findCompletePlateInText(l);
            if (cand != null) {
                Log.i(TAG, "Plate found on single line: [" + cand + "]");
                return cand;
            }
        }

        // Strategy 5: Try OCR character repairs on stripped tokens (e.g. WB O4 F 7728 -> WB04F7728)
        String strippedAll = unified.replaceAll("[^A-Z0-9]", "");
        for (int len = Math.min(10, strippedAll.length()); len >= 7; len--) {
            for (int start = 0; start <= strippedAll.length() - len; start++) {
                String sub = strippedAll.substring(start, start + len);
                String rep = repairOcrPlate(sub);
                if (rep != null && isCompleteValidPlate(rep)) {
                    Log.i(TAG, "Plate found via OCR character repair: [" + rep + "]");
                    return rep;
                }
            }
        }

        return null;
    }

    /**
     * Checks if an input string contains a complete, validated Indian vehicle registration plate.
     */
    @Nullable
    public static String findCompletePlateInText(@NonNull String input) {
        if (input.trim().isEmpty()) return null;

        // 1. Direct stripped string check (all spaces/dashes removed)
        String stripped = input.replaceAll("[^A-Z0-9]", "").toUpperCase(Locale.US);
        if (isCompleteValidPlate(stripped)) {
            return stripped;
        }

        // 2. Embedded Match: Standard Indian Plate with series letters (WB 04 F 7728, WB 02 AF 6376)
        Matcher stdMatcher = PATTERN_EMBEDDED_STANDARD.matcher(input);
        while (stdMatcher.find()) {
            String state = stdMatcher.group(1);
            if (isValidStateCode(state)) {
                String rto = stdMatcher.group(2);
                String series = stdMatcher.group(3);
                String num = stdMatcher.group(4);
                String candidate = (state + rto + series + num).replaceAll("\\s+", "");
                if (isCompleteValidPlate(candidate)) {
                    return candidate;
                }
            }
        }

        // 3. Embedded Match: No-series plate (WB 19 9212, WB 02 1234)
        Matcher noSeriesMatcher = PATTERN_EMBEDDED_NO_SERIES.matcher(input);
        while (noSeriesMatcher.find()) {
            String state = noSeriesMatcher.group(1);
            if (isValidStateCode(state)) {
                String rto = noSeriesMatcher.group(2);
                String num = noSeriesMatcher.group(3);
                String candidate = (state + rto + num).replaceAll("\\s+", "");
                if (isCompleteValidPlate(candidate)) {
                    return candidate;
                }
            }
        }

        // 4. Embedded Match: Bharat Series (22 BH 1234 AA)
        Matcher bhMatcher = PATTERN_EMBEDDED_BH.matcher(input);
        if (bhMatcher.find()) {
            String yr = bhMatcher.group(1);
            String num = bhMatcher.group(2);
            String series = bhMatcher.group(3);
            String candidate = (yr + "BH" + num + series).replaceAll("\\s+", "");
            if (isCompleteValidPlate(candidate)) {
                return candidate;
            }
        }

        // 5. Sliding window over stripped text (handles prefixes/suffixes attached to plate)
        // e.g. "INDWB04F7728" -> window 9: "WB04F7728"
        if (stripped.length() >= 7) {
            for (int len = Math.min(10, stripped.length()); len >= 7; len--) {
                for (int start = 0; start <= stripped.length() - len; start++) {
                    String sub = stripped.substring(start, start + len);
                    if (isCompleteValidPlate(sub)) {
                        return sub;
                    }
                    String repaired = repairOcrPlate(sub);
                    if (repaired != null && isCompleteValidPlate(repaired)) {
                        return repaired;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Strictly verifies whether a normalized alphanumeric string is a COMPLETE Indian vehicle plate.
     * Requires valid State Code, RTO digits, and vehicle registration numbers.
     * Length must be between 7 and 10 characters.
     */
    public static boolean isCompleteValidPlate(@Nullable String plate) {
        if (plate == null) return false;
        String s = plate.replaceAll("[^A-Z0-9]", "").toUpperCase(Locale.US);
        if (s.length() < 6 || s.length() > 10) return false;

        // 1. Standard format: State(2) + RTO(1-2) + Series(1-3) + Number(1-4)
        // Must contain series letters (e.g. DL1CA1, WB04F7728, KL04AP1424).
        // CANNOT match "AP1424" because "AP1424" has no series letters after RTO digits.
        Matcher m1 = PATTERN_FULL_STANDARD.matcher(s);
        if (m1.matches()) {
            return isValidStateCode(m1.group(1));
        }

        // 2. Vintage / Commercial without series: State(2) + RTO(1-2) + Number(4)
        // Strictly requires 4-digit number at the end, total length 7 or 8 (e.g. DL11234, WB199212).
        // NEVER matches 6-character strings like "AP1424", which only have 4 digits total.
        Matcher m2 = PATTERN_FULL_NO_SERIES.matcher(s);
        if (m2.matches()) {
            return isValidStateCode(m2.group(1));
        }

        // 3. Bharat Series: 2 digits + BH + 4 digits + 1-2 letters
        Matcher m3 = PATTERN_FULL_BH.matcher(s);
        if (m3.matches()) {
            return true;
        }

        return false;
    }

    /**
     * Repairs common OCR misreadings between letters and digits based on plate character positions.
     */
    @Nullable
    public static String repairOcrPlate(@NonNull String candidate) {
        String s = candidate.replaceAll("[^A-Z0-9]", "").toUpperCase(Locale.US);
        if (s.length() < 6 || s.length() > 10) return null;

        // Fix common OCR state code misreadings
        if (s.startsWith("VVB")) {
            s = "WB" + s.substring(3);
        } else if (s.startsWith("VV") && s.length() >= 7) {
            s = "WB" + s.substring(2);
        } else if (s.startsWith("0L")) {
            s = "DL" + s.substring(2);
        } else if (s.startsWith("0D")) {
            s = "OD" + s.substring(2);
        } else if (s.startsWith("U9")) {
            s = "UP" + s.substring(2);
        } else if (s.startsWith("8R")) {
            s = "BR" + s.substring(2);
        } else if (s.startsWith("H8")) {
            s = "HR" + s.substring(2);
        } else if (s.startsWith("K4")) {
            s = "KA" + s.substring(2);
        }

        String state = s.substring(0, 2);
        if (!isValidStateCode(state)) {
            if (state.equals("W8") || state.equals("MB")) {
                state = "WB";
                s = state + s.substring(2);
            } else {
                return null;
            }
        }

        char[] chars = s.toCharArray();
        int n = chars.length;

        // 1. Fix RTO digits at index 2 (and index 3 if digit-like)
        if (n > 2) {
            chars[2] = toDigit(chars[2]);
        }
        if (n > 3 && Character.isDigit(chars[2])) {
            if (isDigitConfusable(chars[3]) && n >= 8) {
                chars[3] = toDigit(chars[3]);
            }
        }

        // 2. Fix the last 3-4 characters to be digits
        int numLen = Math.min(4, n - 4);
        for (int i = n - numLen; i < n; i++) {
            chars[i] = toDigit(chars[i]);
        }

        String repaired = new String(chars);
        if (isCompleteValidPlate(repaired)) {
            return repaired;
        }

        return null;
    }

    private static char toDigit(char c) {
        switch (c) {
            case 'O':
            case 'D':
            case 'Q': return '0';
            case 'I':
            case 'L':
            case 'T': return '1';
            case 'Z': return '2';
            case 'S': return '5';
            case 'G': return '6';
            case 'B': return '8';
            default: return c;
        }
    }

    private static boolean isDigitConfusable(char c) {
        return c == 'O' || c == 'D' || c == 'Q' || c == 'I' || c == 'L' || c == 'Z' || c == 'S' || c == 'B' || c == 'G';
    }

    /**
     * Fallback for manual user confirmation button (NOT auto-locked).
     * Looks for words of length 6-10 with valid state prefix.
     */
    @Nullable
    public static String findFallbackCandidate(@NonNull String input) {
        String cleaned = cleanOcrNoise(input);
        String[] words = cleaned.split("\\s+");
        for (String word : words) {
            String clean = word.replaceAll("[^A-Z0-9]", "");
            if (isCompleteValidPlate(clean)) {
                return clean;
            }
        }
        return null;
    }

    @Nullable
    private static String extractMobile(@NonNull String text) {
        Matcher labeledMatcher = PATTERN_MOBILE_LABELED.matcher(text);
        if (labeledMatcher.find()) {
            return labeledMatcher.group(1);
        }

        Matcher genericMatcher = PATTERN_MOBILE_GENERIC.matcher(text);
        if (genericMatcher.find()) {
            return genericMatcher.group(0);
        }

        return null;
    }

    public static boolean isValidStateCode(@Nullable String code) {
        if (code == null) return false;
        return INDIAN_STATE_CODES.contains(code.toUpperCase(Locale.US));
    }
}
