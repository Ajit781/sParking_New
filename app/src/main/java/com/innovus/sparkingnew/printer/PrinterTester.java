package com.innovus.sparkingnew.printer;

import android.content.Context;
import android.graphics.Bitmap;

import com.innovus.sparkingnew.SParkingApp;
import com.pax.dal.IDAL;
import com.pax.dal.IPrinter;
import com.pax.dal.entity.EFontTypeAscii;
import com.pax.dal.entity.EFontTypeExtCode;
import com.pax.dal.exceptions.PrinterDevException;

/**
 * Ported from SParkingAgent for PAX POS thermal printer hardware.
 */
public class PrinterTester extends BaseTester {

    private static PrinterTester printerTester;
    private IPrinter printer;

    private PrinterTester(Context context) {
        IDAL dal = SParkingApp.getDal(context);
        if (dal != null) {
            try {
                printer = dal.getPrinter();
            } catch (Throwable t) {
                logErr("Constructor", t.getMessage());
            }
        }
    }

    public static synchronized PrinterTester getInstance() {
        return getInstance(null);
    }

    public static synchronized PrinterTester getInstance(Context context) {
        if (printerTester == null) {
            printerTester = new PrinterTester(context);
        } else if (printerTester.printer == null) {
            IDAL dal = SParkingApp.getDal(context);
            if (dal != null) {
                try {
                    printerTester.printer = dal.getPrinter();
                } catch (Throwable ignored) {}
            }
        }
        return printerTester;
    }

    public boolean isPrinterAvailable() {
        return printer != null;
    }

    public void init() {
        if (printer == null) return;
        try {
            printer.init();
            logTrue("init");
        } catch (PrinterDevException e) {
            logErr("init", e.toString());
        }
    }

    public String getStatus() {
        if (printer == null) return "Printer not available";
        try {
            int status = printer.getStatus();
            logTrue("getStatus");
            return statusCode2Str(status);
        } catch (PrinterDevException e) {
            logErr("getStatus", e.toString());
            return "";
        }
    }

    public void fontSet(EFontTypeAscii asciiFontType, EFontTypeExtCode cFontType) {
        if (printer == null) return;
        try {
            printer.fontSet(asciiFontType, cFontType);
            logTrue("fontSet");
        } catch (PrinterDevException e) {
            logErr("fontSet", e.toString());
        }
    }

    public void spaceSet(byte wordSpace, byte lineSpace) {
        if (printer == null) return;
        try {
            printer.spaceSet(wordSpace, lineSpace);
            logTrue("spaceSet");
        } catch (PrinterDevException e) {
            logErr("spaceSet", e.toString());
        }
    }

    public void printStr(String str, String charset) {
        if (printer == null) return;
        try {
            printer.printStr(str, charset);
            logTrue("printStr");
        } catch (PrinterDevException e) {
            logErr("printStr", e.toString());
        }
    }

    public void step(int b) {
        if (printer == null) return;
        try {
            printer.step(b);
            logTrue("setStep");
        } catch (PrinterDevException e) {
            logErr("setStep", e.toString());
        }
    }

    public void printBitmap(Bitmap bitmap) {
        if (printer == null) return;
        try {
            printer.printBitmap(bitmap);
            logTrue("printBitmap");
        } catch (PrinterDevException e) {
            logErr("printBitmap", e.toString());
        }
    }

    public String start() {
        if (printer == null) return "Printer not available";
        try {
            int res = printer.start();
            logTrue("start");
            return statusCode2Str(res);
        } catch (PrinterDevException e) {
            logErr("start", e.toString());
            return e.getMessage();
        }
    }

    public void leftIndents(short indent) {
        if (printer == null) return;
        try {
            printer.leftIndent(indent);
            logTrue("leftIndent");
        } catch (PrinterDevException e) {
            logErr("leftIndent", e.toString());
        }
    }

    public int getDotLine() {
        if (printer == null) return -1;
        try {
            int dotLine = printer.getDotLine();
            logTrue("getDotLine");
            return dotLine;
        } catch (PrinterDevException e) {
            logErr("getDotLine", e.toString());
            return -2;
        }
    }

    public void setGray(int level) {
        if (printer == null) return;
        try {
            printer.setGray(level);
            logTrue("setGray");
        } catch (PrinterDevException e) {
            logErr("setGray", e.toString());
        }
    }

    public void setDoubleWidth(boolean isAscDouble, boolean isLocalDouble) {
        if (printer == null) return;
        try {
            printer.doubleWidth(isAscDouble, isLocalDouble);
            logTrue("doubleWidth");
        } catch (PrinterDevException e) {
            logErr("doubleWidth", e.toString());
        }
    }

    public void setDoubleHeight(boolean isAscDouble, boolean isLocalDouble) {
        if (printer == null) return;
        try {
            printer.doubleHeight(isAscDouble, isLocalDouble);
            logTrue("doubleHeight");
        } catch (PrinterDevException e) {
            logErr("doubleHeight", e.toString());
        }
    }

    public void setInvert(boolean isInvert) {
        if (printer == null) return;
        try {
            printer.invert(isInvert);
            logTrue("setInvert");
        } catch (PrinterDevException e) {
            logErr("setInvert", e.toString());
        }
    }

    public String cutPaper(int mode) {
        if (printer == null) return "Printer not available";
        try {
            printer.cutPaper(mode);
            logTrue("cutPaper");
            return "cut paper successful";
        } catch (PrinterDevException e) {
            logErr("cutPaper", e.toString());
            return e.toString();
        }
    }

    public String statusCode2Str(int status) {
        switch (status) {
            case 0:
                return "Success";
            case 1:
                return "Printer is busy";
            case 2:
                return "Out of paper";
            case 3:
                return "The format of print data packet error";
            case 4:
                return "Printer malfunctions";
            case 8:
                return "Printer over heats";
            case 9:
                return "Printer voltage is too low";
            case 240:
                return "Printing is unfinished";
            case 252:
                return "The printer has not installed font library";
            case 254:
                return "Data package is too long";
            default:
                return "Unknown status: " + status;
        }
    }
}
