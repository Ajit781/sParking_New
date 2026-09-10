package com.innovus.sparkingnew;

import com.innovus.sparkingnew.scanner.VehiclePlateParser;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class VehiclePlateParserTest {

    @Test
    public void testStandardPlates() {
        VehiclePlateParser.ParseResult r1 = VehiclePlateParser.parse("IND WB 02 AK 1234");
        assertTrue(r1.hasVehicleNo());
        assertEquals("WB02AK1234", r1.getVehicleNo());

        VehiclePlateParser.ParseResult r2 = VehiclePlateParser.parse("MH-12-DE-1433");
        assertTrue(r2.hasVehicleNo());
        assertEquals("MH12DE1433", r2.getVehicleNo());

        VehiclePlateParser.ParseResult r3 = VehiclePlateParser.parse("DL 1C AB 9988\nSome other text");
        assertTrue(r3.hasVehicleNo());
        assertEquals("DL1CAB9988", r3.getVehicleNo());
    }

    @Test
    public void testShortPlates() {
        VehiclePlateParser.ParseResult r1 = VehiclePlateParser.parse("WB051234");
        assertTrue(r1.hasVehicleNo());
        assertEquals("WB051234", r1.getVehicleNo());

        VehiclePlateParser.ParseResult r2 = VehiclePlateParser.parse("HSRP WB 05 1234");
        assertTrue(r2.hasVehicleNo());
        assertEquals("WB051234", r2.getVehicleNo());

        VehiclePlateParser.ParseResult r3 = VehiclePlateParser.parse("DL 1C A 1");
        assertTrue(r3.hasVehicleNo());
        assertEquals("DL1CA1", r3.getVehicleNo());
    }

    @Test
    public void testBharatSeriesPlate() {
        VehiclePlateParser.ParseResult r = VehiclePlateParser.parse("22 BH 1234 AA");
        assertTrue(r.hasVehicleNo());
        assertEquals("22BH1234AA", r.getVehicleNo());
    }

    @Test
    public void testMobileNumberExtraction() {
        VehiclePlateParser.ParseResult r1 = VehiclePlateParser.parse("WB 02 AK 1234\nMobile: 9876543210");
        assertTrue(r1.hasVehicleNo());
        assertEquals("WB02AK1234", r1.getVehicleNo());
        assertTrue(r1.hasMobileNo());
        assertEquals("9876543210", r1.getMobileNo());

        VehiclePlateParser.ParseResult r2 = VehiclePlateParser.parse("Contact: 7003412345 DL01A1234");
        assertEquals("DL01A1234", r2.getVehicleNo());
        assertEquals("7003412345", r2.getMobileNo());
    }

    @Test
    public void testTwoTierPlates() {
        VehiclePlateParser.ParseResult r = VehiclePlateParser.parse("WB 02\nAK 1234");
        assertTrue(r.hasVehicleNo());
        assertEquals("WB02AK1234", r.getVehicleNo());
    }

    @Test
    public void testIndDirectlyTouchingState() {
        VehiclePlateParser.ParseResult r = VehiclePlateParser.parse("INDWB02AK1234");
        assertTrue(r.hasVehicleNo());
        assertEquals("WB02AK1234", r.getVehicleNo());
    }

    @Test
    public void testSymbolsAndNoise() {
        VehiclePlateParser.ParseResult r = VehiclePlateParser.parse("| WB-02.AK.1234 |");
        assertTrue(r.hasVehicleNo());
        assertEquals("WB02AK1234", r.getVehicleNo());
    }

    /**
     * User Image 1: Kolkata Yellow Taxi 2-line plate
     * Line 1: "WB 04"
     * Line 2: "F 7728"
     * Left noise: "IND", "BA180017170"
     */
    @Test
    public void testKolkataYellowTaxiTwoTier() {
        VehiclePlateParser.ParseResult r = VehiclePlateParser.parse(
                "IND\nWB 04\nBA180017170\nF 7728",
                Arrays.asList("IND", "WB 04", "BA180017170", "F 7728")
        );
        assertTrue(r.hasVehicleNo());
        assertEquals("WB04F7728", r.getVehicleNo());
    }

    /**
     * User Image 2: Vintage Kolkata Taxi with WEST.BENGAL
     * Line 1: "WEST.BENGAL 19"
     * Line 2: "9212"
     * Header noise: "STOP"
     */
    @Test
    public void testKolkataVintageTaxiWestBengal() {
        VehiclePlateParser.ParseResult r1 = VehiclePlateParser.parse(
                "STOP\nWEST.BENGAL 19\n9212",
                Arrays.asList("STOP", "WEST.BENGAL 19", "9212")
        );
        assertTrue(r1.hasVehicleNo());
        assertEquals("WB199212", r1.getVehicleNo());

        VehiclePlateParser.ParseResult r2 = VehiclePlateParser.parse(
                "WEST BENGAL 19\n9212"
        );
        assertTrue(r2.hasVehicleNo());
        assertEquals("WB199212", r2.getVehicleNo());
    }

    /**
     * User Image 3: Standard single row "WB 02 AF 6376"
     */
    @Test
    public void testUserImage3SingleRow() {
        VehiclePlateParser.ParseResult r = VehiclePlateParser.parse("WB 02 AF 6376");
        assertTrue(r.hasVehicleNo());
        assertEquals("WB02AF6376", r.getVehicleNo());
    }

    /**
     * User Image 4: Embossed "WB 01 AK 0321" with IND
     */
    @Test
    public void testUserImage4Embossed() {
        VehiclePlateParser.ParseResult r = VehiclePlateParser.parse("IND WB 01 AK 0321");
        assertTrue(r.hasVehicleNo());
        assertEquals("WB01AK0321", r.getVehicleNo());
    }

    /**
     * User Image 5: Two-tier with series letter split across lines
     * Line 1: "MH 49 A"
     * Line 2: "U 6390"
     */
    @Test
    public void testUserImage5SplitSeriesTwoTier() {
        VehiclePlateParser.ParseResult r = VehiclePlateParser.parse(
                "IND\nMH 49 A\nU 6390",
                Arrays.asList("IND", "MH 49 A", "U 6390")
        );
        assertTrue(r.hasVehicleNo());
        assertEquals("MH49AU6390", r.getVehicleNo());
    }

    /**
     * Ensures scanner NEVER accepts partial / truncated numbers like "F 7728" alone
     */
    @Test
    public void testRejectPartialNumbers() {
        VehiclePlateParser.ParseResult r1 = VehiclePlateParser.parse("F 7728");
        assertFalse("Single line 'F 7728' must not be accepted as complete plate", r1.hasVehicleNo());

        VehiclePlateParser.ParseResult r2 = VehiclePlateParser.parse("MH 49 A");
        assertFalse("Single line 'MH 49 A' must not be accepted as complete plate", r2.hasVehicleNo());

        VehiclePlateParser.ParseResult r3 = VehiclePlateParser.parse("U 6390");
        assertFalse("Single line 'U 6390' must not be accepted as complete plate", r3.hasVehicleNo());
    }

    @Test
    public void testOtherStatesWithFullNames() {
        VehiclePlateParser.ParseResult r1 = VehiclePlateParser.parse("MAHARASHTRA 12 DE 1433");
        assertTrue(r1.hasVehicleNo());
        assertEquals("MH12DE1433", r1.getVehicleNo());

        VehiclePlateParser.ParseResult r2 = VehiclePlateParser.parse("DELHI 01 C 1234");
        assertTrue(r2.hasVehicleNo());
        assertEquals("DL01C1234", r2.getVehicleNo());

        VehiclePlateParser.ParseResult r3 = VehiclePlateParser.parse("GUJARAT 01 AB 1234");
        assertTrue(r3.hasVehicleNo());
        assertEquals("GJ01AB1234", r3.getVehicleNo());

        VehiclePlateParser.ParseResult r4 = VehiclePlateParser.parse("UTTAR PRADESH 32 B 9999");
        assertTrue(r4.hasVehicleNo());
        assertEquals("UP32B9999", r4.getVehicleNo());
    }

    @Test
    public void testSpacedStateLetters() {
        VehiclePlateParser.ParseResult r1 = VehiclePlateParser.parse("W B 04 F 7728");
        assertTrue(r1.hasVehicleNo());
        assertEquals("WB04F7728", r1.getVehicleNo());

        VehiclePlateParser.ParseResult r2 = VehiclePlateParser.parse("M H 12 DE 1433");
        assertTrue(r2.hasVehicleNo());
        assertEquals("MH12DE1433", r2.getVehicleNo());
    }

    @Test
    public void testCommercialStickersNoise() {
        VehiclePlateParser.ParseResult r = VehiclePlateParser.parse("COMMERCIAL TOURIST TAXI WB 04 F 7728 CNG");
        assertTrue(r.hasVehicleNo());
        assertEquals("WB04F7728", r.getVehicleNo());
    }

    @Test
    public void testOcrStateRepairs() {
        VehiclePlateParser.ParseResult r1 = VehiclePlateParser.parse("0L 01 AB 1234");
        assertTrue(r1.hasVehicleNo());
        assertEquals("DL01AB1234", r1.getVehicleNo());

        VehiclePlateParser.ParseResult r2 = VehiclePlateParser.parse("U9 32 B 1234");
        assertTrue(r2.hasVehicleNo());
        assertEquals("UP32B1234", r2.getVehicleNo());
    }

    /**
     * User's Kerala 2-Tier Plate:
     * Line 1: "KL 04"
     * Line 2: "AP 1424"
     * And Bottom row alone "AP 1424" MUST NEVER be accepted as a complete plate!
     */
    @Test
    public void testKeralaTwoTierPlateAndRejectBottomRow() {
        // 1. Both lines ordered top-to-bottom
        VehiclePlateParser.ParseResult r1 = VehiclePlateParser.parse(
                "IND\nKL 04\nAP 1424",
                Arrays.asList("IND", "KL 04", "AP 1424")
        );
        assertTrue("KL 04 + AP 1424 must parse to KL04AP1424", r1.hasVehicleNo());
        assertEquals("KL04AP1424", r1.getVehicleNo());

        // 2. Reverse order: bottom line detected first
        VehiclePlateParser.ParseResult r2 = VehiclePlateParser.parse(
                "AP 1424\nKL 04",
                Arrays.asList("AP 1424", "KL 04")
        );
        assertTrue("Reverse order must also parse to KL04AP1424", r2.hasVehicleNo());
        assertEquals("KL04AP1424", r2.getVehicleNo());

        // 3. Single-line plate: "KL 04 AP 1424"
        VehiclePlateParser.ParseResult r3 = VehiclePlateParser.parse("IND KL 04 AP 1424");
        assertTrue("Single line must parse to KL04AP1424", r3.hasVehicleNo());
        assertEquals("KL04AP1424", r3.getVehicleNo());

        // 4. CRITICAL ASSERTION: "AP 1424" ALONE MUST BE REJECTED!
        // It must NOT be mistaken for an Andhra Pradesh plate!
        VehiclePlateParser.ParseResult rAlone = VehiclePlateParser.parse("AP 1424");
        assertFalse("Bottom line 'AP 1424' alone MUST NOT be accepted as a complete plate!", rAlone.hasVehicleNo());

        // 5. Other 6-char 2-tier bottom rows that match state codes must also be rejected
        assertFalse("DL 1234 alone must be rejected", VehiclePlateParser.parse("DL 1234").hasVehicleNo());
        assertFalse("MH 4567 alone must be rejected", VehiclePlateParser.parse("MH 4567").hasVehicleNo());
        assertFalse("UP 8888 alone must be rejected", VehiclePlateParser.parse("UP 8888").hasVehicleNo());
        assertFalse("HR 0001 alone must be rejected", VehiclePlateParser.parse("HR 0001").hasVehicleNo());
    }
}
