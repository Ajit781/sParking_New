package com.innovus.sparkingnew.printer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility functions for bitmap formatting, centering text, and QR code creation.
 * Ported directly from SParkingAgent.
 */
public class PrinterUtils {

    public static final int PAGE_WIDTH_TWO_INCH = 32;
    public static final int PAGE_WIDTH_TWO_INCH_SMALL = 24;
    public static final int PAGE_WIDTH_TWO_INCH_SMALL_BOTTOM = 38;

    public static String paddingCenter(String data, int n) {
        if (data == null) return "";
        String temp = data;
        if (data.length() > n) {
            temp = data.substring(0, n);
        } else if (data.length() < n) {
            temp = paddingLeft(data, ((n / 2) - (data.length() / 2)));
        }
        return temp;
    }

    public static String paddingLeft(String data, int n) {
        if (data == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(" ");
        }
        sb.append(data);
        return sb.toString();
    }

    public static Bitmap pad(Bitmap src, int padding_x, int padding_y) {
        if (src == null) return null;
        Bitmap outputImage = Bitmap.createBitmap(src.getWidth() + padding_x, src.getHeight() + padding_y, Bitmap.Config.RGB_565);
        Canvas can = new Canvas(outputImage);
        can.drawColor(Color.WHITE);
        can.drawBitmap(src, padding_x, padding_y, null);
        return outputImage;
    }

    public static Bitmap generateQrBitmap(String text, int width, int height) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height);
            int matrixWidth = bitMatrix.getWidth();
            int matrixHeight = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.RGB_565);
            for (int x = 0; x < matrixWidth; x++) {
                for (int y = 0; y < matrixHeight; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bmp;
        } catch (Exception e) {
            Log.e("PrinterUtils", "QR Generation error: " + e.getMessage(), e);
            return null;
        }
    }

    public static byte[] decodeBitmap(Bitmap bmp) {
        if (bmp == null) return null;
        int bmpWidth = bmp.getWidth();
        int bmpHeight = bmp.getHeight();

        List<String> list = new ArrayList<>();
        int zeroCount = bmpWidth % 8;

        String zeroStr = "";
        if (zeroCount > 0) {
            for (int i = 0; i < (8 - zeroCount); i++) {
                zeroStr = zeroStr + "0";
            }
        }

        for (int i = 0; i < bmpHeight; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < bmpWidth; j++) {
                int color = bmp.getPixel(j, i);
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                if (r > 160 && g > 160 && b > 160) {
                    sb.append("0");
                } else {
                    sb.append("1");
                }
            }
            if (zeroCount > 0) {
                sb.append(zeroStr);
            }
            list.add(sb.toString());
        }

        List<String> bmpHexList = binaryListToHexStringList(list);
        String commandHexString = "1D763000";
        String widthHexString = Integer.toHexString(bmpWidth % 8 == 0 ? bmpWidth / 8 : (bmpWidth / 8 + 1));
        if (widthHexString.length() > 2) {
            return null;
        } else if (widthHexString.length() == 1) {
            widthHexString = "0" + widthHexString;
        }
        widthHexString = widthHexString + "00";

        String heightHexString = Integer.toHexString(bmpHeight);
        if (heightHexString.length() > 2) {
            return null;
        } else if (heightHexString.length() == 1) {
            heightHexString = "0" + heightHexString;
        }
        heightHexString = heightHexString + "00";

        List<String> commandList = new ArrayList<>();
        commandList.add(commandHexString + widthHexString + heightHexString);
        commandList.addAll(bmpHexList);

        return hexList2Byte(commandList);
    }

    private static List<String> binaryListToHexStringList(List<String> list) {
        List<String> hexList = new ArrayList<>();
        for (String binaryStr : list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < binaryStr.length(); i += 8) {
                String str = binaryStr.substring(i, Math.min(i + 8, binaryStr.length()));
                String hexString = myBinaryStrToHexString(str);
                sb.append(hexString);
            }
            hexList.add(sb.toString());
        }
        return hexList;
    }

    private static String myBinaryStrToHexString(String binaryStr) {
        String hex = "";
        String f4 = binaryStr.substring(0, Math.min(4, binaryStr.length()));
        String b4 = binaryStr.length() > 4 ? binaryStr.substring(4) : "0000";
        for (int i = 0; i < binaryArray.length; i++) {
            if (f4.equals(binaryArray[i])) {
                hex += hexStr.substring(i, i + 1);
            }
        }
        for (int i = 0; i < binaryArray.length; i++) {
            if (b4.equals(binaryArray[i])) {
                hex += hexStr.substring(i, i + 1);
            }
        }
        return hex;
    }

    private static final String hexStr = "0123456789ABCDEF";
    private static final String[] binaryArray = {
            "0000", "0001", "0010", "0011",
            "0100", "0101", "0110", "0111",
            "1000", "1001", "1010", "1011",
            "1100", "1101", "1110", "1111"
    };

    private static byte[] hexList2Byte(List<String> list) {
        List<byte[]> commandList = new ArrayList<>();
        for (String hexStr : list) {
            commandList.add(hexStringToBytes(hexStr));
        }
        return sysCopy(commandList);
    }

    private static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    private static byte charToByte(char c) {
        return (byte) hexStr.indexOf(c);
    }

    private static byte[] sysCopy(List<byte[]> srcArrays) {
        int len = 0;
        for (byte[] srcArray : srcArrays) {
            len += srcArray.length;
        }
        byte[] destArray = new byte[len];
        int destLen = 0;
        for (byte[] srcArray : srcArrays) {
            System.arraycopy(srcArray, 0, destArray, destLen, srcArray.length);
            destLen += srcArray.length;
        }
        return destArray;
    }

    public static String[] breakStringToLines(String text, int maxLineLength) {
        if (text == null) return new String[]{""};
        if (text.length() <= maxLineLength) return new String[]{text};
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLineLength, text.length());
            lines.add(text.substring(start, end));
            start = end;
        }
        return lines.toArray(new String[0]);
    }
}
