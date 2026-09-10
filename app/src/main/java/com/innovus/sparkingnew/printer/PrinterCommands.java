package com.innovus.sparkingnew.printer;

public class PrinterCommands {
    public static final int PAGE_WIDTH_TWO_INCH = 32;
    public static final int PAGE_WIDTH_TWO_INCH_SMALL = 24;
    public static final int PAGE_WIDTH_TWO_INCH_SMALL_BOTTOM = 38;

    public static final byte[] HT = {0x09};
    public static final byte[] LF = {0x0A};
    public static final byte[] CR = {0x0D};
    public static final byte[] ESC = {0x1B};
    public static final byte[] FS = {0x1C};
    public static final byte[] GS = {0x1D};

    public static final byte[] INIT = {0x1B, 0x40};
    public static final byte[] FEED_LINE = {0x0A};
    public static final byte[] SELECT_FONT_A = {0x1B, 0x4D, 0x00};
    public static final byte[] SET_LINE_SPACING_24 = {0x1B, 0x33, 24};
    public static final byte[] SET_LINE_SPACING_30 = {0x1B, 0x33, 30};

    public static final byte[] ALIGN_LEFT = {0x1B, 0x61, 0x00};
    public static final byte[] ALIGN_CENTER = {0x1B, 0x61, 0x01};
    public static final byte[] ALIGN_RIGHT = {0x1B, 0x61, 0x02};

    public static final byte[] BOLD_ON = {0x1B, 0x45, 0x01};
    public static final byte[] BOLD_OFF = {0x1B, 0x45, 0x00};

    public static final byte[] DOUBLE_HEIGHT_WIDTH = {0x1D, 0x21, 0x11};
    public static final byte[] DOUBLE_WIDTH = {0x1D, 0x21, 0x10};
    public static final byte[] DOUBLE_HEIGHT = {0x1D, 0x21, 0x01};
    public static final byte[] NORMAL = {0x1D, 0x21, 0x00};
}
