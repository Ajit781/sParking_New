package com.innovus.vyoma.s_parking_agentApollo;

import static com.innovus.vyoma.s_parking_agentApollo.DashBoardActivity.isBound;
import static com.innovus.vyoma.s_parking_agentApollo.DashBoardActivity.mService;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.FragmentTransaction;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TimePicker;

import com.bumptech.glide.Glide;
import com.crashlytics.android.Crashlytics;
import com.eze.api.EzeAPI;
import com.pax.dal.entity.EFontTypeAscii;
import com.pax.dal.entity.EFontTypeExtCode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import adapter.BookingSummaryListAdapter;
import data_objects.SParkingAgentModel;
import data_objects.bean.ParkingSummaryByAgentBean;
import dmax.dialog.SpotsDialog;
import shared_pref.SharedStorage;
import utilities.ShowAlertDialog;
import utilities.async_tasks.AsyncResponse;
import utilities.async_tasks.RemoteAsync;
import utilities.constants.Constants;
import utilities.constants.Urls;
import utilities.eazytap.PrinterTester;
import utilities.listnerofRecyclerView.CustomItemClickListener;

public class BookingSummaryActivity extends AppCompatActivity implements View.OnClickListener, AsyncResponse, DatePickerDialog.OnDateSetListener{
    private TextView tv_fromDate,tv_toDate,tv_message;
    private Button btn_calenderfrom,btn_calenderto,btn_Submit,btn_print;
    private RecyclerView rv_parking_list;
    private LinearLayout ll_main;
    private RelativeLayout no_result,rl_recycler_main;
    private ImageView noitem_in_cart;
    private String selectDateFrom="";
    private String formatDate="";
    private String dateTime="";
    private int Grand_total=0;
    private int total_count=0;
    public static final int NOT_FOUND = -1;
    String newMonth="";
    String newDay ="";
    int day, month, year, hour, minute, second;
    int myday, myMonth, myYear, myHour, myMinute,mySecond;
    private SpotsDialog progressDialog;
    RemoteAsync remoteAsync;
    SParkingAgentModel dataModel=SParkingAgentModel.getInstance();
    BookingSummaryListAdapter bookingSummaryListAdapter;
    private final int REQUEST_CODE_PRINT_BITMAP = 10029;
    // print ParkingSummary
    Bitmap bitmap= null;
    Canvas canvas=null;
    Rect bounds=null;
    Paint paint=null;
    int x=0;
    int y=0;
    String strText="";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_summary);
        getSupportActionBar().setTitle(Html.fromHtml("<font color='#FFFFFF'>"+getResources().getString(R.string.parking_summary)+"</font>"));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        initViews();
    }

    private void initViews() {
        tv_fromDate =(TextView) findViewById(R.id.tv_fromDate);
        tv_toDate =(TextView) findViewById(R.id.tv_toDate);
        tv_message =(TextView) findViewById(R.id.tv_message);
        btn_calenderfrom =(Button) findViewById(R.id.btn_calenderfrom);
        btn_calenderto =(Button) findViewById(R.id.btn_calenderto);
        btn_Submit =(Button) findViewById(R.id.btn_Submit);
        btn_print =(Button) findViewById(R.id.btn_print);
        rv_parking_list =(RecyclerView) findViewById(R.id.rv_parking_list);
        rl_recycler_main =(RelativeLayout) findViewById(R.id.rl_recycler_main);
        ll_main = (LinearLayout) findViewById(R.id.ll_main);
        no_result =(RelativeLayout) findViewById(R.id.no_result);
        noitem_in_cart =(ImageView) findViewById(R.id.noitem_in_cart);

        btn_calenderfrom.setOnClickListener(this);
        btn_calenderto.setOnClickListener(this);
        btn_Submit.setOnClickListener(this);
        btn_print.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId()==R.id.btn_calenderfrom){
            selectDateFrom = "fromDate";
            openDateTimePickerDialog();
        }
        if (view.getId()==R.id.btn_calenderto){
            selectDateFrom = "toDate";
            openDateTimePickerDialog();
        }
        if (view.getId()==R.id.btn_Submit){
            if (validate()){
                //Network check
                if(SharedStorage.getValue(getApplicationContext(),"agent_mode").equals("1")){
                    try{
                        getAgentWiseParkingSummary(tv_fromDate.getText().toString().trim(),tv_toDate.getText().toString());

                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }else{
                    ShowAlertDialog.showAlertDialogFailure(this,getResources().getString(R.string.kindlyswitch_online));
                }
            }
        }
        if (view.getId()==R.id.btn_print){
            if (SharedStorage.getValue(BookingSummaryActivity.this,"printer_name").equals("eazy_Tap")) {
                btn_print.setClickable(false);
                btn_print.setEnabled(false);
                try {
                    //printEazytapBillWrapText();
                    //printPaxBillWrapText();
                    printEazytapBillUsingSDK();
//                    printBillUsingPineLab();
                } catch (Exception e) {
                    e.printStackTrace();

                }

            }
           else if (SharedStorage.getValue(BookingSummaryActivity.this,"printer_name").equals("verifone")) {
                btn_print.setClickable(false);
                btn_print.setEnabled(false);
                try {
                   // printEazytapBillWrapText();
                    printPaxBillWrapText();
                    //printEazytapBillUsingSDK();
                } catch (Exception e) {
                    e.printStackTrace();

                }

            }

        }

    }
    // print report
    private void printEazytapBillWrapText() {
        new Thread(new Runnable() {
            public void run() {
                try{
                    String dateTime[] = getDateTime();
                    PrinterTester.getInstance().init();
                    PrinterTester.getInstance().fontSet(EFontTypeAscii.FONT_16_32, EFontTypeExtCode.FONT_16_16);
                    PrinterTester.getInstance().setGray(30);


                    StringBuilder print_bill = new StringBuilder();

                    String str = SharedStorage.getValue(BookingSummaryActivity.this,"parkinglocation");
                    String[] arrOfStr = str.split("-");

                    for (String str_location : arrOfStr){
                        print_bill.append(paddingCenter(str_location, PAGE_WIDTH_TWO_INCH)).append("\n");


                    }
                    PrinterTester.getInstance().printStr(String.valueOf(print_bill),null);
                    print_bill = new StringBuilder();
                    print_bill.append(paddingCenter("EXIT SUMMARY - REPORT", PAGE_WIDTH_TWO_INCH)).append("\n");
                    PrinterTester.getInstance().printStr(String.valueOf(print_bill),null);
                    print_bill = new StringBuilder();

//                    String strUser = SharedStorage.getValue(getApplicationContext(),"Userame");
//                    String[] arrOfUser = strUser.split("-");
//
//                    for (String str_userName : arrOfUser){
//                        print_bill.append(paddingCenter(str_userName, PAGE_WIDTH_TWO_INCH)).append("\n");
//
//
//                    }
//                    print_bill.append(paddingCenter("AGENT  : "+SharedStorage.getValue(getApplicationContext(),"Userame"), PAGE_WIDTH_TWO_INCH)).append("\n");
//                    PrinterTester.getInstance().printStr(String.valueOf(print_bill),null);
//                    PrinterTester.getInstance().step(2);
                    PrinterTester.getInstance().printStr("AGENT  : "+SharedStorage.getValue(getApplicationContext(),"Userame")+"\n",null);

                    PrinterTester.getInstance().fontSet(EFontTypeAscii.FONT_12_24,EFontTypeExtCode.FONT_16_16);
                    PrinterTester.getInstance().printStr("--------------------------------"+"\n",null);

                    PrinterTester.getInstance().printStr("From Date: "+tv_fromDate.getText().toString()+"\n",null);
                    PrinterTester.getInstance().printStr("To Date  : "+tv_toDate.getText().toString()+"\n",null);

                    PrinterTester.getInstance().printStr("--------------------------------"+"\n",null);

//                    PrinterTester.getInstance().printStr("--------------------------------"+"\n",null);

                    if (dataModel.parkingSummaryByAgentBeanArrayList.size()>0){
                        int sl_no = 0;
                        String vehicle_name="";
                        int count = 0;
                        int amount = 0;
                        int vehicleTypeId = 0;
                        Grand_total=0;
                        total_count=0;
                        for(int i=0;i<dataModel.parkingSummaryByAgentBeanArrayList.size();i++){
                            sl_no =i;
                            vehicle_name=dataModel.parkingSummaryByAgentBeanArrayList.get(i).getVehicleTypeName();
                            count =dataModel.parkingSummaryByAgentBeanArrayList.get(i).getParkingCount();
                            amount =dataModel.parkingSummaryByAgentBeanArrayList.get(i).getCollectionAmount();
                            vehicleTypeId =dataModel.parkingSummaryByAgentBeanArrayList.get(i).getVehicleTypeID();
                            Grand_total= Grand_total+amount;
                            total_count= total_count+count;

                                PrinterTester.getInstance().printStr(vehicle_name+"\n",null);
                                PrinterTester.getInstance().printStr("COUNT        : "+count+"\n",null);
                                PrinterTester.getInstance().printStr("AMOUNT       : "+amount+"\n",null);
                                PrinterTester.getInstance().printStr("--------------------------------"+"\n",null);


                        }

                    }
                    PrinterTester.getInstance().fontSet(EFontTypeAscii.FONT_16_32, EFontTypeExtCode.FONT_16_16);
                    PrinterTester.getInstance().printStr("GRAND TOTAL "+total_count+" "+Grand_total+"\n",null);
                   // PrinterTester.getInstance().printStr("GRAND TOTAL "+"1234"+" "+"234567"+"\n",null);
                    PrinterTester.getInstance().fontSet(EFontTypeAscii.FONT_12_24,EFontTypeExtCode.FONT_16_16);
                    PrinterTester.getInstance().printStr("--------------------------------"+"\n",null);

                    PrinterTester.getInstance().printStr("GSTIN - "+SharedStorage.getValue(BookingSummaryActivity.this,"AgencyGSTNo")+"\n",null);


                    PrinterTester.getInstance().fontSet(EFontTypeAscii.FONT_8_32,EFontTypeExtCode.FONT_16_16);

                    PrinterTester.getInstance().printStr("Fee is inclusive of GST"+"\n",null);
                    PrinterTester.getInstance().fontSet(EFontTypeAscii.FONT_12_24,EFontTypeExtCode.FONT_16_16);

                    PrinterTester.getInstance().printStr("--------------------------------"+"\n",null);
                    PrinterTester.getInstance().fontSet(EFontTypeAscii.FONT_8_32,EFontTypeExtCode.FONT_16_16);
                    PrinterTester.getInstance().printStr("Printed on -"+" "+dateTime[0]+" "+dateTime[1],null);
//                            PrinterTester.getInstance().printStr("Download s-Parking App from Play Store\n",null);
                    PrinterTester.getInstance().printStr("\n",null);
                    PrinterTester.getInstance().printStr("\n",null);
                    PrinterTester.getInstance().step(60);


                } catch (Exception e) {
                    e.printStackTrace();
                }
                PrinterTester.getInstance().step(2);


                final String status = PrinterTester.getInstance().start();
                // int status_code = Integer.parseInt(PrinterTester.getInstance().getStatus());
                tv_fromDate.post(new Runnable() {
                    public void run() {
                        // CToast.show(getApplicationContext(),status);
                        if (status.equals("Out of paper ")) {
                            final AlertDialog alertDialog = new AlertDialog.Builder(
                                    BookingSummaryActivity.this).create();

                            LayoutInflater inflater = getLayoutInflater();
                            View dialogView = inflater.inflate(R.layout.customdialoglayout, null);
                            alertDialog.setView(dialogView);
                            TextView heading = (TextView) dialogView.findViewById(R.id.heading);
                            TextView msg_txt = (TextView) dialogView.findViewById(R.id.msg_txt);
                            Button btnOk = (Button) dialogView.findViewById(R.id.btnOk);

                            heading.setText(R.string.validation_name);
                            msg_txt.setText(status);
                            btnOk.setText(getResources().getString(R.string.reprint));
                            btnOk.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    alertDialog.dismiss();
                                    try {
                                        printEazytapBillWrapText();
                                    } catch (Exception e) {

                                        e.printStackTrace();

                                    }
                                }
                            });
                            //Animate alert dialog box
                            FragmentTransaction ft = getFragmentManager().beginTransaction();
                            ft.setCustomAnimations(android.R.animator.fade_in,
                                    android.R.animator.fade_out);
                            // Showing Alert Message
                            alertDialog.show();
                            alertDialog.setCancelable(false);

                        }else{
                            Grand_total=0;
                            total_count=0;
                            tv_toDate.setText("");
                            tv_fromDate.setText("");
                            btn_print.setClickable(true);
                            btn_print.setEnabled(true);
                            dataModel.parkingSummaryByAgentBeanArrayList.removeAll(dataModel.parkingSummaryByAgentBeanArrayList);
                            startActivity(new Intent(BookingSummaryActivity.this, DashBoardActivity.class));
                            overridePendingTransition(R.anim.trans_right_in, R.anim.trans_right_out);
                            finish();

                        }


                    }


                });

            }
        }).start();

    }

    // print report
    private void printEazytapBillUsingSDK() {
        new Thread(new Runnable() {
            public void run() {
                try{
                    PrinterTester.getInstance().init();
                    Bitmap bitmap = null;
                    JSONObject jsonRequest = new JSONObject();
                    JSONObject jsonImageObj = new JSONObject();
                    Integer iBitmapBaseHeight = 500;
                    String gstNo = SharedStorage.getValue(BookingSummaryActivity.this,"AgencyGSTNo");
                    if(null == gstNo || gstNo.trim().equals(""))
                    {
                        iBitmapBaseHeight -= 24;
                    }


                    String[] arrLocName = breakStringToLines(SharedStorage.getValue(BookingSummaryActivity.this,"parkinglocation"),35);
                    if(arrLocName.length <= 1)
                    {
                        Integer bitmapHeight = 0;

                        if(dataModel.parkingSummaryByAgentBeanArrayList.size() <= 1)
                        {
                            bitmapHeight = iBitmapBaseHeight;
                        }
                        else
                        {
                            bitmapHeight = iBitmapBaseHeight + ((dataModel.parkingSummaryByAgentBeanArrayList.size() - 1) * (24 * 4));
                        }

                        bitmap = Bitmap.createBitmap(400, bitmapHeight, Bitmap.Config.ARGB_8888);
                    }
                    else
                    {
                        Integer bitmapHeight = 0;

                        if(dataModel.parkingSummaryByAgentBeanArrayList.size() <= 1)
                        {
                            bitmapHeight = iBitmapBaseHeight + ((arrLocName.length - 1) * 24);
                        }
                        else
                        {
                            bitmapHeight = iBitmapBaseHeight + ((arrLocName.length - 1) * 24) + ((dataModel.parkingSummaryByAgentBeanArrayList.size() - 1) * (24 * 4));
                        }

                        bitmap = Bitmap.createBitmap(400, bitmapHeight, Bitmap.Config.ARGB_8888);
                    }

                    bitmap.eraseColor(Color.WHITE);
                    Canvas canvas = new Canvas(bitmap);
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/SignikaNegative-Medium.ttf");
                    paint.setTypeface(typeface);
                    paint.setColor(Color.rgb(0,0, 0));
                    Rect bounds = new Rect();

//                    // Set first line in Bitmap
//                    paint.setTextSize((int) (28));
//                    String strText = "Smartpower";
//                    paint.getTextBounds(strText, 0, strText.length(), bounds);
//                    int x = (bitmap.getWidth() - bounds.width())/2;
//                    int y = 30;
//                    canvas.drawText(strText, x, y, paint);
//
//                    // Set second line in Bitmap
//                    paint.setTextSize((int) (22));
//                    strText = "www.smartpower.co.in";
//                    paint.getTextBounds(strText, 0, strText.length(), bounds);
//                    x = (bitmap.getWidth() - bounds.width())/2;
//                    y += 24;
//                    canvas.drawText(strText, x, y, paint);

                    // Set third line in Bitmap
                    Typeface typeface1 = Typeface.createFromAsset(getAssets(), "fonts/SignikaNegative-Medium.ttf");
                    paint.setTypeface(typeface1);
                    paint.setTextSize((int) (22));
                    // strText = SharedStorage.getValue(ParkingSummaryActivity.this,"parkinglocation");
                    // String[] arrLoc = breakStringToLines(strText,35);
                    for(String strLocName : arrLocName)
                    {
                        if(null != strLocName && !strLocName.trim().equals(""))
                        {
                            paint.getTextBounds(strLocName, 0, strLocName.length(), bounds);
                            x = (bitmap.getWidth() - bounds.width())/2;
                            y += 24;
                            canvas.drawText(strLocName, x, y, paint);
                        }
                    }

                    // Set second line in Bitmap
                    paint.setTextSize((int) (22));
                    strText = "EXIT SUMMARY - REPORT";
                    paint.getTextBounds(strText, 0, strText.length(), bounds);
                    x = (bitmap.getWidth() - bounds.width()) / 2;
                    y += 24;
                    canvas.drawText(strText, x, y, paint);


                    // Set third line in Bitmap
                    paint.setTextSize((int) (22));
                    strText = "AGENT  : "+SharedStorage.getValue(getApplicationContext(),"Userame");
                    paint.getTextBounds(strText, 0, strText.length(), bounds);
                    x = (bitmap.getWidth() - bounds.width())/2;
                    y += 24;
                    canvas.drawText(strText, x, y, paint);

                    // Set third line in Bitmap
                    paint.setTextSize((int) (22));
                    strText = "-------------------------------------------------------";
                    paint.getTextBounds(strText, 0, strText.length(), bounds);
                    x = (bitmap.getWidth() - bounds.width())/2;
                    y += 24;
                    canvas.drawText(strText, x, y, paint);

                    // Set fourth line in Bitmap
                    paint.setTextSize((int) (22));
                    strText = "From Date  : "+tv_fromDate.getText().toString();
                    paint.getTextBounds(strText, 0, strText.length(), bounds);
                    x = 25;
                    y += 24;
                    canvas.drawText(strText, x, y, paint);

                    // Set fifth line in Bitmap
                    paint.setTextSize((int) (22));
                    strText = "To Date       : "+tv_toDate.getText().toString();
                    paint.getTextBounds(strText, 0, strText.length(), bounds);
                    x = 25;
                    y += 24;
                    canvas.drawText(strText, x, y, paint);

                    // Set sixth line in Bitmap
                    paint.setTextSize((int) (24));
                    strText = "-------------------------------------------------------";
                    paint.getTextBounds(strText, 0, strText.length(), bounds);
                    x = 25;
                    y += 24;
                    canvas.drawText(strText, x, y, paint);

                    if (dataModel.parkingSummaryByAgentBeanArrayList.size()>0){
                        int sl_no = 0;
                        String vehicle_name="";
                        int count = 0;
                        int amount = 0;
                        int vehicleTypeId = 0;
                        Grand_total=0;
                        total_count=0;
                        for(int i=0;i<dataModel.parkingSummaryByAgentBeanArrayList.size();i++){
                            sl_no =i;
                            vehicle_name=dataModel.parkingSummaryByAgentBeanArrayList.get(i).getVehicleTypeName();
                            count =dataModel.parkingSummaryByAgentBeanArrayList.get(i).getParkingCount();
                            amount =dataModel.parkingSummaryByAgentBeanArrayList.get(i).getCollectionAmount();
                            vehicleTypeId =dataModel.parkingSummaryByAgentBeanArrayList.get(i).getVehicleTypeID();
                            Grand_total= Grand_total+amount;
                            total_count= total_count+count;


                            paint.setTextSize((int) (22));
                            strText = vehicle_name;
                            paint.getTextBounds(strText, 0, strText.length(), bounds);
                            x = 25;
                            y += 24;
                            canvas.drawText(strText, x, y, paint);

                            // Set fifth line in Bitmap
                            paint.setTextSize((int) (22));
                            strText = "COUNT        : "+count;
                            paint.getTextBounds(strText, 0, strText.length(), bounds);
                            x = 25;
                            y += 24;
                            canvas.drawText(strText, x, y, paint);

                            // Set fifth line in Bitmap
                            paint.setTextSize((int) (22));
                            strText = "AMOUNT    : "+"Rs. "+amount;
                            paint.getTextBounds(strText, 0, strText.length(), bounds);
                            x = 25;
                            y += 24;
                            canvas.drawText(strText, x, y, paint);

                            // Set sixth line in Bitmap
                            paint.setTextSize((int) (22));
                            strText = "-------------------------------------------------------";
                            paint.getTextBounds(strText, 0, strText.length(), bounds);
                            x = 25;
                            y += 24;
                            canvas.drawText(strText, x, y, paint);

                        }

                    }
                    // Set fifth line in Bitmap
                    paint.setTextSize((int) (24));
                    strText = "TOTAL COUNT    : "+total_count;
                    paint.getTextBounds(strText, 0, strText.length(), bounds);
                    x = 25;
                    y += 24;
                    canvas.drawText(strText, x, y, paint);

                    // Set fifth line in Bitmap
                    paint.setTextSize((int) (24));
                    strText = "TOTAL AMOUNT : "+"Rs. "+Grand_total;
                    paint.getTextBounds(strText, 0, strText.length(), bounds);
                    x = 25;
                    y += 24;
                    canvas.drawText(strText, x, y, paint);
//
//            // Set seventh line in Bitmap
//            paint.setTextSize((int) (24));
//            strText = "GRAND TOTAL "+total_count+"  "+Grand_total;
//            paint.getTextBounds(strText, 0, strText.length(), bounds);
//            x = 35;
//            y += 24;
//            canvas.drawText(strText, x, y, paint);

                    // Set twelfth line in Bitmap
                    paint.setTextSize((int) (22));
                    strText = "-------------------------------------------------------";
                    paint.getTextBounds(strText, 0, strText.length(), bounds);
                    x = 25;
                    y += 24;
                    canvas.drawText(strText, x, y, paint);

//                    if(!(null == gstNo || gstNo.trim().equals("")))
//                    {
//                        // Set fifteenth line in Bitmap
//                        paint.setTextSize((int) (22));
//                        strText = "GSTIN : "+SharedStorage.getValue(BookingSummaryActivity.this,"AgencyGSTNo");
//                        paint.getTextBounds(strText, 0, strText.length(), bounds);
//                        x = (bitmap.getWidth() - bounds.width())/2;
//                        y += 24;
//                        canvas.drawText(strText, x, y, paint);
//
//                        // Set sixteenth line in Bitmap
//                        paint.setTextSize((int) (22));
//                        strText = "Fee is inclusive of GST";
//                        paint.getTextBounds(strText, 0, strText.length(), bounds);
//                        x = (bitmap.getWidth() - bounds.width())/2;
//                        y += 24;
//                        canvas.drawText(strText, x, y, paint);
//                        // for current Date and time
//
//                    }

                    String dateTime[] = getDateTime();

                    // Set sixteenth line in Bitmap
                    paint.setTextSize((int) (24));
                    strText = "Printed on -"+" "+dateTime[0]+" "+dateTime[1];
                    paint.getTextBounds(strText, 0, strText.length(), bounds);
                    x = (bitmap.getWidth() - bounds.width())/2;
                    y += 24;
                    canvas.drawText(strText, x, y, paint);

                    PrinterTester.getInstance().printBitmap(bitmap);
                    PrinterTester.getInstance().step(2);


                final String status = PrinterTester.getInstance().start();
                // int status_code = Integer.parseInt(PrinterTester.getInstance().getStatus());
                tv_fromDate.post(new Runnable() {
                    public void run() {
                        // CToast.show(getApplicationContext(),status);
                        if (status.equals("Out of paper ")) {
                            final AlertDialog alertDialog = new AlertDialog.Builder(
                                    BookingSummaryActivity.this).create();

                            LayoutInflater inflater = getLayoutInflater();
                            View dialogView = inflater.inflate(R.layout.customdialoglayout, null);
                            alertDialog.setView(dialogView);
                            TextView heading = (TextView) dialogView.findViewById(R.id.heading);
                            TextView msg_txt = (TextView) dialogView.findViewById(R.id.msg_txt);
                            Button btnOk = (Button) dialogView.findViewById(R.id.btnOk);

                            heading.setText(R.string.validation_name);
                            msg_txt.setText(status);
                            btnOk.setText(getResources().getString(R.string.reprint));
                            btnOk.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    alertDialog.dismiss();
                                    try {
                                        printEazytapBillWrapText();
                                    } catch (Exception e) {

                                        e.printStackTrace();

                                    }
                                }
                            });
                            //Animate alert dialog box
                            FragmentTransaction ft = getFragmentManager().beginTransaction();
                            ft.setCustomAnimations(android.R.animator.fade_in,
                                    android.R.animator.fade_out);
                            // Showing Alert Message
                            alertDialog.show();
                            alertDialog.setCancelable(false);

                        }else{
                            Grand_total=0;
                            total_count=0;
                            tv_toDate.setText("");
                            tv_fromDate.setText("");
                            btn_print.setClickable(true);
                            btn_print.setEnabled(true);
                            dataModel.parkingSummaryByAgentBeanArrayList.removeAll(dataModel.parkingSummaryByAgentBeanArrayList);
                            startActivity(new Intent(BookingSummaryActivity.this, DashBoardActivity.class));
                            overridePendingTransition(R.anim.trans_right_in, R.anim.trans_right_out);
                            finish();

                        }


                    }


                });

            }catch (Exception e) {
                    e.printStackTrace();
                }


            }
        }).start();

    }


    // print report
    private void printPaxBillWrapText() {
        try
        {
            JSONObject jsonRequest = new JSONObject();
            JSONObject jsonImageObj = new JSONObject();
//            String location_str= SharedStorage.getValue(BookingSummaryActivity.this,"parkinglocation");
//
//            Bitmap bitmap = Bitmap.createBitmap(400, 1000, Bitmap.Config.ARGB_8888);
            Integer iBitmapBaseHeight = 520;
            String gstNo = SharedStorage.getValue(BookingSummaryActivity.this,"AgencyGSTNo");
            if(null == gstNo || gstNo.trim().equals(""))
            {
                iBitmapBaseHeight -= 24;
            }


            String[] arrLocName = breakStringToLines(SharedStorage.getValue(BookingSummaryActivity.this,"parkinglocation"),35);
            if(arrLocName.length <= 1)
            {
                Integer bitmapHeight = 0;

                if(dataModel.parkingSummaryByAgentBeanArrayList.size() <= 1)
                {
                    bitmapHeight = iBitmapBaseHeight;
                }
                else
                {
                    bitmapHeight = iBitmapBaseHeight + ((dataModel.parkingSummaryByAgentBeanArrayList.size() - 1) * (24 * 4));
                }

                bitmap = Bitmap.createBitmap(400, bitmapHeight, Bitmap.Config.ARGB_8888);
            }
            else
            {
                Integer bitmapHeight = 0;

                if(dataModel.parkingSummaryByAgentBeanArrayList.size() <= 1)
                {
                    bitmapHeight = iBitmapBaseHeight + ((arrLocName.length - 1) * 24);
                }
                else
                {
                    bitmapHeight = iBitmapBaseHeight + ((arrLocName.length - 1) * 24) + ((dataModel.parkingSummaryByAgentBeanArrayList.size() - 1) * (24 * 4));
                }

                bitmap = Bitmap.createBitmap(400, bitmapHeight, Bitmap.Config.ARGB_8888);
            }

            bitmap.eraseColor(Color.WHITE);

            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.rgb(0,0, 0));
            Rect bounds = new Rect();

            //            if (location_str.length()>60){
//                bitmap = Bitmap.createBitmap(400, 1200, Bitmap.Config.ARGB_8888);
//                bitmap.eraseColor(Color.WHITE);
//            }else if((location_str.length()<60)&&(location_str.length()>40)){
//                bitmap = Bitmap.createBitmap(400, 1200, Bitmap.Config.ARGB_8888);
//                bitmap.eraseColor(Color.WHITE);
//            }else{
//                bitmap = Bitmap.createBitmap(400, 1200, Bitmap.Config.ARGB_8888);
//                bitmap.eraseColor(Color.WHITE);
//            }
//
//
//            canvas = new Canvas(bitmap);
//            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
//            paint.setColor(Color.rgb(0,0, 0));
//            bounds = new Rect();

//            // Set first line in Bitmap
//            paint.setTextSize((int) (26));
//            strText = "SmartPower";
//            paint.getTextBounds(strText, 0, strText.length(), bounds);
//            x = (bitmap.getWidth() - bounds.width())/2;
//            y = 30;
//            canvas.drawText(strText, x, y, paint);
//
//            // Set second line in Bitmap
//            paint.setTextSize((int) (22));
//            strText = "www.smartpower.co.in";
//            paint.getTextBounds(strText, 0, strText.length(), bounds);
//            x = (bitmap.getWidth() - bounds.width())/2;
//            y += 24;
//            canvas.drawText(strText, x, y, paint);

            // Set third line in Bitmap
            paint.setTextSize((int) (22));
            // strText = SharedStorage.getValue(ParkingSummaryActivity.this,"parkinglocation");
            // String[] arrLoc = breakStringToLines(strText,35);
            for(String strLocName : arrLocName)
            {
                if(null != strLocName && !strLocName.trim().equals(""))
                {
                    paint.getTextBounds(strLocName, 0, strLocName.length(), bounds);
                    x = (bitmap.getWidth() - bounds.width())/2;
                    y += 24;
                    canvas.drawText(strLocName, x, y, paint);
                }
            }


            // Set second line in Bitmap
            paint.setTextSize((int) (22));
            strText = "EXIT SUMMARY - REPORT";
            paint.getTextBounds(strText, 0, strText.length(), bounds);
            x = (bitmap.getWidth() - bounds.width()) / 2;
            y += 24;
            canvas.drawText(strText, x, y, paint);


            // Set third line in Bitmap
            paint.setTextSize((int) (22));
            strText = "AGENT  : "+SharedStorage.getValue(getApplicationContext(),"Userame");
            paint.getTextBounds(strText, 0, strText.length(), bounds);
            x = (bitmap.getWidth() - bounds.width())/2;
            y += 24;
            canvas.drawText(strText, x, y, paint);

            // Set third line in Bitmap
            paint.setTextSize((int) (22));
            strText = "-------------------------------------------------------";
            paint.getTextBounds(strText, 0, strText.length(), bounds);
            x = (bitmap.getWidth() - bounds.width())/2;
            y += 24;
            canvas.drawText(strText, x, y, paint);

            // Set fourth line in Bitmap
            paint.setTextSize((int) (22));
            strText = "From Date  : "+tv_fromDate.getText().toString();
            paint.getTextBounds(strText, 0, strText.length(), bounds);
            x = 35;
            y += 24;
            canvas.drawText(strText, x, y, paint);

            // Set fifth line in Bitmap
            paint.setTextSize((int) (22));
            strText = "To Date       : "+tv_toDate.getText().toString();
            paint.getTextBounds(strText, 0, strText.length(), bounds);
            x = 35;
            y += 24;
            canvas.drawText(strText, x, y, paint);

            // Set sixth line in Bitmap
            paint.setTextSize((int) (22));
            strText = "-------------------------------------------------------";
            paint.getTextBounds(strText, 0, strText.length(), bounds);
            x = 35;
            y += 24;
            canvas.drawText(strText, x, y, paint);

            if (dataModel.parkingSummaryByAgentBeanArrayList.size()>0){
                int sl_no = 0;
                String vehicle_name="";
                int count = 0;
                int amount = 0;
                int vehicleTypeId = 0;
                Grand_total=0;
                total_count=0;
                for(int i=0;i<dataModel.parkingSummaryByAgentBeanArrayList.size();i++){
                    sl_no =i;
                    vehicle_name=dataModel.parkingSummaryByAgentBeanArrayList.get(i).getVehicleTypeName();
                    count =dataModel.parkingSummaryByAgentBeanArrayList.get(i).getParkingCount();
                    amount =dataModel.parkingSummaryByAgentBeanArrayList.get(i).getCollectionAmount();
                    vehicleTypeId =dataModel.parkingSummaryByAgentBeanArrayList.get(i).getVehicleTypeID();
                    Grand_total= Grand_total+amount;
                    total_count= total_count+count;


                        paint.setTextSize((int) (22));
                        strText = vehicle_name;
                        paint.getTextBounds(strText, 0, strText.length(), bounds);
                        x = 35;
                        y += 24;
                        canvas.drawText(strText, x, y, paint);

                        // Set fifth line in Bitmap
                        paint.setTextSize((int) (22));
                        strText = "COUNT        : "+count;
                        paint.getTextBounds(strText, 0, strText.length(), bounds);
                        x = 35;
                        y += 24;
                        canvas.drawText(strText, x, y, paint);

                        // Set fifth line in Bitmap
                        paint.setTextSize((int) (22));
                        strText = "AMOUNT    : "+"Rs. "+amount;
                        paint.getTextBounds(strText, 0, strText.length(), bounds);
                        x = 35;
                        y += 24;
                        canvas.drawText(strText, x, y, paint);

                        // Set sixth line in Bitmap
                        paint.setTextSize((int) (22));
                        strText = "-------------------------------------------------------";
                        paint.getTextBounds(strText, 0, strText.length(), bounds);
                        x = 35;
                        y += 24;
                        canvas.drawText(strText, x, y, paint);

                }

            }
            // Set fifth line in Bitmap
            paint.setTextSize((int) (24));
            strText = "TOTAL COUNT    : "+total_count;
            paint.getTextBounds(strText, 0, strText.length(), bounds);
            x = 35;
            y += 24;
            canvas.drawText(strText, x, y, paint);

            // Set fifth line in Bitmap
            paint.setTextSize((int) (24));
            strText = "TOTAL AMOUNT : "+"Rs. "+Grand_total;
            paint.getTextBounds(strText, 0, strText.length(), bounds);
            x = 35;
            y += 24;
            canvas.drawText(strText, x, y, paint);
//
//            // Set seventh line in Bitmap
//            paint.setTextSize((int) (24));
//            strText = "GRAND TOTAL "+total_count+"  "+Grand_total;
//            paint.getTextBounds(strText, 0, strText.length(), bounds);
//            x = 35;
//            y += 24;
//            canvas.drawText(strText, x, y, paint);

            // Set twelfth line in Bitmap
            paint.setTextSize((int) (22));
            strText = "-------------------------------------------------------";
            paint.getTextBounds(strText, 0, strText.length(), bounds);
            x = 35;
            y += 24;
            canvas.drawText(strText, x, y, paint);

            if(!(null == gstNo || gstNo.trim().equals("")))
            {
                // Set fifteenth line in Bitmap
                paint.setTextSize((int) (22));
                strText = "GSTN : "+gstNo;
                paint.getTextBounds(strText, 0, strText.length(), bounds);
                x = (bitmap.getWidth() - bounds.width())/2;
                y += 24;
                canvas.drawText(strText, x, y, paint);

                // Set sixteenth line in Bitmap
                paint.setTextSize((int) (22));
                strText = "Fee is inclusive of GST";
                paint.getTextBounds(strText, 0, strText.length(), bounds);
                x = (bitmap.getWidth() - bounds.width())/2;
                y += 24;
                canvas.drawText(strText, x, y, paint);
                // for current Date and time


            }
            String dateTime[] = getDateTime();
            // Set sixteenth line in Bitmap
            paint.setTextSize((int) (22));
            strText = "Printed on -"+" "+dateTime[0]+" "+dateTime[1];
            paint.getTextBounds(strText, 0, strText.length(), bounds);
            x = (bitmap.getWidth() - bounds.width())/2;
            y += 24;
            canvas.drawText(strText, x, y, paint);

            String encodedImageData = getEncoded64ImageStringFromBitmap(bitmap);
            // Building Image Object
            jsonImageObj.put("imageData", encodedImageData);
            jsonImageObj.put("imageType", "JPEG");
            jsonRequest.put("image", jsonImageObj); // Pass this attribute when you have a valid captured signature image
            EzeAPI.printBitmap(BookingSummaryActivity.this, REQUEST_CODE_PRINT_BITMAP, jsonRequest);
//            txt_owner_fee.post(new Runnable() {
//                public void run() {
////                        CToast.show(getApplicationContext(),status);
//                    try {
//                        Thread.sleep(300);
//                        pagechange();
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            });

        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }

    }

    private String getEncoded64ImageStringFromBitmap(Bitmap bmp) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Bitmap bitmap = bmp;
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] imageBytes = baos.toByteArray();
        String encodedDate = Base64.encodeToString(imageBytes, Base64.DEFAULT);
        return encodedDate;
    }



    /******************* eazyTap printer printing code ****************/
    public static final int PAGE_WIDTH_TWO_INCH = 22;
    public static final int PAGE_WIDTH_TWO_INCH_SMALL = 30;
    public static final int PAGE_WIDTH_TWO_INCH_SMALL_BOTTOM = 40;
    static String paddingLeft(String data, int n) {
        StringBuilder temp = new StringBuilder();
        for (int i = 0; i < n; i++)
            temp.append(" ");
        return (temp + data);
    }

    static String paddingRight(String data, int n) {
        StringBuilder temp = new StringBuilder();
        for (int i = 0; i < n; i++)
            temp.append(" ");
        return (data + temp);

    }

    public static String paddingCenter(String data, int n) {
        String temp = "";
        String t1;
        String t2;
        String t3;

        if (data.length() > n) {
            t1 = String.format("%s\n", data.substring(0, n));
            t2 = data.substring(n, data.length());
            if (t2.length() > n) {
                t3 = paddingCenter(t2, n);
            } else {
                t3 = paddingLeft(t2, ((n / 2) - (t2.length() / 2)));
            }
            temp = t1 + t3;
        } else if (data.length() < n) {
            temp = paddingLeft(data, ((n / 2) - (data.length() / 2)));
        } else if (data.length() == n) {
            temp = data;
        }
        return temp;

    }
    private boolean validate() {
        boolean result = true;

        if (tv_fromDate.getText().toString().equals(tv_toDate.getText().toString())){
            ShowAlertDialog.showAlertDialog(BookingSummaryActivity.this,getResources().getString(R.string.fromDateandToDateValid));
            result = false;
            return result;
        }

        if (tv_fromDate.getText().toString().equals("")){
            ShowAlertDialog.showAlertDialog(BookingSummaryActivity.this,getResources().getString(R.string.validTodate));
            result = false;
            return result;

        }
        if (tv_toDate.getText().toString().equals("")){
                ShowAlertDialog.showAlertDialog(BookingSummaryActivity.this,getResources().getString(R.string.validfromdate));
                result = false;
                return result;

        }

        return result;
    }
    // Request service for GenerateAuthToken checking
    private void GenerateAuthToken() {
        Urls Urls = new Urls();
        String login_url = Urls.GenerateAuthToken;

        remoteAsync = new RemoteAsync(login_url);
        remoteAsync.type = RemoteAsync.GENERATEAUTHTOKEN;
        remoteAsync.delegate = this;

        String urlParams = "";
        try {
            urlParams = "userid=" + URLEncoder.encode("AndroidApp", "UTF-8") +
                    "&password=" + URLEncoder.encode("vyoma@123", "UTF-8");
        }catch(Exception e){
        }

        remoteAsync.execute(urlParams);
        Log.e("params>>",urlParams);
    }

// get Parking Summary list
    private void getAgentWiseParkingSummary( String fromDate,String toDate) {
        start_progress_dialog();
        Urls Urls = new Urls();
        String userid = SharedStorage.getValue(getApplicationContext(),"UserId");
        String start_parking_url = Urls.AgentWiseParkingSummary;

        remoteAsync = new RemoteAsync(start_parking_url);
        remoteAsync.type = RemoteAsync.AGENTWISEPARKINGSUMMARY;
        remoteAsync.delegate = this;

        String urlParams = "";
        try {
            urlParams = "AgentID=" + URLEncoder.encode(userid, "UTF-8") +
                    "&FromDate=" + URLEncoder.encode(fromDate, "UTF-8") +
                    "&ToDate=" + URLEncoder.encode(toDate, "UTF-8");
            //urlParams = "https://maps.googleapis.com/maps/api/place/details/json?placeid=" + selectedGoogleAddressBean.getPlace_id() + "&key=AIzaSyDzZucI3DFyg6-JxaIFqYCNREX8FT72JAM";
        }catch(Exception e){
            Log.e("ParamsException-->", e.getMessage());
            Crashlytics.log(Log.ERROR,"SParkingAgent_startparking",e.getMessage());
        }

        remoteAsync.execute(urlParams);
        Log.e("params>>",urlParams);
    }

    private void openDateTimePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        year = calendar.get(Calendar.YEAR);
        month = calendar.get(Calendar.MONTH);
        day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(BookingSummaryActivity.this, BookingSummaryActivity.this,year, month+1,day);
        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();

    }

    void start_progress_dialog() {
        try{
            progressDialog = new SpotsDialog(BookingSummaryActivity.this, R.style.CustomWaitDialog);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }catch (Exception e){
            e.printStackTrace();
            Crashlytics.log(Log.ERROR,"SParkingAgent_startprogress",e.getMessage());
        }

    }

    void stop_progress_dialog() {

        if(progressDialog!=null){

            try{
                progressDialog.dismiss();
                progressDialog=null;
            }catch (Exception e){
                e.printStackTrace();
                Crashlytics.log(Log.ERROR,"SParkingAgent_stopprogress",e.getMessage());
            }

        }

    }

    @Override
    public void processFinish(String type, String output) {
        if (type.equals(RemoteAsync.AGENTWISEPARKINGSUMMARY)) {
            stop_progress_dialog();

            try {
                JSONObject obj = new JSONObject(output);
                Log.e("Response-->", obj.toString());

                if (obj.getString("status").equals(Constants.SUCCESS)) {
                    ArrayList<ParkingSummaryByAgentBean> parkingSummaryByAgentBeansList = new ArrayList<ParkingSummaryByAgentBean>();
                    try{
                        JSONArray ParkingSummaryByAgentList = obj.getJSONArray("ParkingSummaryByAgent");
                        if (ParkingSummaryByAgentList.length() > 0) {
                            for (int i = 0; i < ParkingSummaryByAgentList.length(); i++) {
                                JSONObject object = ParkingSummaryByAgentList.getJSONObject(i);
                                ParkingSummaryByAgentBean parkingSummaryByAgentBean = new ParkingSummaryByAgentBean();
                                parkingSummaryByAgentBean.setVehicleTypeID(object.getInt("VehicleTypeID"));
                                parkingSummaryByAgentBean.setVehicleTypeName(object.getString("VehicleTypeName"));
                                parkingSummaryByAgentBean.setAgentID(object.getInt("AgentID"));
                                parkingSummaryByAgentBean.setAgentName(object.getString("AgentName"));
                                parkingSummaryByAgentBean.setParkingCount(object.getInt("ParkingCount"));
                                parkingSummaryByAgentBean.setCollectionAmount(object.getInt("CollectionAmount"));
                                parkingSummaryByAgentBean.setViewtype(1);
                                parkingSummaryByAgentBeansList.add(parkingSummaryByAgentBean);
                            }
                        }

                    }catch(Exception e){
                        e.printStackTrace();

                    }
                    dataModel.parkingSummaryByAgentBeanArrayList.removeAll(dataModel.parkingSummaryByAgentBeanArrayList);
                    dataModel.parkingSummaryByAgentBeanArrayList.addAll(parkingSummaryByAgentBeansList);
                    getparkingSummaryrecyclerview("");//show  vehicle to into recycler view

                }
                else if (obj.getString("status").equals(Constants.NOT_SUCCESS)){

                   // ShowAlertDialog.showAlertDialogFailure(this,getResources().getString(R.string.kindlyswitch_online));
                    final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
                    LayoutInflater inflater = this.getLayoutInflater();
                    View dialogView = inflater.inflate(R.layout.customdialog_end_parking, null);
                    alertDialog.setView(dialogView);
                    TextView heading = (TextView) dialogView.findViewById(R.id.heading);
                    TextView msg_txt = (TextView) dialogView.findViewById(R.id.msg_txt);
                    Button btnyes = (Button) dialogView.findViewById(R.id.btnyes);
                    Button btnno = (Button) dialogView.findViewById(R.id.btnno);
                    btnyes.setText(getResources().getString(R.string.retry));
                    btnno.setText(getResources().getString(R.string.cancel));

                    heading.setText(R.string.validation_name);


                    heading.setText(R.string.validation_name);
                    msg_txt.setText(getResources().getString(R.string.nonetavailable));
                    //msg_txt.setText(getResources().getString(R.string.no_internet));

                    btnno.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            alertDialog.dismiss();

                        }
                    });
                    btnyes.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            alertDialog.dismiss();
                            //Network check
                            if(SharedStorage.getValue(getApplicationContext(),"agent_mode").equals("1")){
                                try{
                                    getAgentWiseParkingSummary(tv_fromDate.getText().toString().trim(),tv_toDate.getText().toString());

                                }catch(Exception e){
                                    e.printStackTrace();
                                }
                            }else{
                                ShowAlertDialog.showAlertDialogFailure(BookingSummaryActivity.this,getResources().getString(R.string.kindlyswitch_online));
                            }

                        }
                    });
                    //Animate alert dialog box
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.setCustomAnimations(android.R.animator.fade_in,
                            android.R.animator.fade_out);
                    alertDialog.show();
                    alertDialog.setCancelable(false);

                }
                else if (obj.getString("status").equals(Constants.TOKEN_EXP)){

                    GenerateAuthToken();
                    try {
                        Thread.sleep(300);
                        //get all parking details by parking id
                        getAgentWiseParkingSummary(tv_fromDate.getText().toString().trim(),tv_toDate.getText().toString());

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
                else {
                    JSONObject msg = new JSONObject(output);
                    //  getparkingSummaryrecyclerview(msg.getString("message"));// this method is for vehicle list showin recyclerview
                    ShowAlertDialog.showAlertDialogFailure(this,obj.getString("message"));

                    //ShowAlertDialog.showAlertDialog(DashBoardActivity.this, msg.getString("message"));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    // show parking Summery in list
    private void getparkingSummaryrecyclerview(String message) {
        if (dataModel.parkingSummaryByAgentBeanArrayList.size()>0){
            no_result.setVisibility(View.GONE);
            rl_recycler_main.setVisibility(View.VISIBLE);
            btn_print.setVisibility(View.VISIBLE);
            rv_parking_list.setVisibility(View.VISIBLE);
            LinearLayoutManager layoutManager = new LinearLayoutManager(BookingSummaryActivity.this, LinearLayoutManager.VERTICAL, false);
            rv_parking_list.setLayoutManager(layoutManager);
            rv_parking_list.setItemAnimator(new DefaultItemAnimator());
            bookingSummaryListAdapter= new BookingSummaryListAdapter(dataModel.parkingSummaryByAgentBeanArrayList, BookingSummaryActivity.this, new CustomItemClickListener() {
                @Override
                public void onItemClick(View v, int position) {
                   // String vechile_no=(dataModel.dataObjectArrayList.get(position).getM_strVehicleNo());

                }
            });
           // ItemTouchHelper.SimpleCallback itemTouchHelperCallback = new RecyclerItemTouchHelper(0, ItemTouchHelper.LEFT, DashBoardActivity.this);
           // new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(rv_vehicle_list);
            rv_parking_list.setAdapter(bookingSummaryListAdapter);
            bookingSummaryListAdapter.notifyDataSetChanged();

        }else {
           // rl_recycler_main.setVisibility(View.GONE);
            ll_main.setVisibility(View.GONE);
            no_result.setVisibility(View.VISIBLE);
            rv_parking_list.setVisibility(View.GONE);
            tv_message.setText(message);
            btn_print.setVisibility(View.GONE);
            // for  showing gif file if there is no any vehicle in the list
            Glide.with(getApplicationContext()).load(R.drawable.nobooking).asGif().into(noitem_in_cart);// for  showing gif file if there is no any vehicle in the list
        }

    }

    @Override
    public void onDateSet(DatePicker datePicker, int year, int month, int day) {
        myYear = year;
        myMonth = month+1;
        myday = day;
        Log.e("myMonth", String.valueOf(myMonth));
        if (myMonth<10){
            newMonth = "0"+String.valueOf(myMonth);
        }else{
            newMonth= String.valueOf(myMonth);
        }
        if(myday<10){
            newDay = "0" +String.valueOf(myday);
        }else{

            newDay = String.valueOf(day);
        }

        Calendar c = Calendar.getInstance();
       // c.set(Calendar.HOUR_OF_DAY, 24);
        hour = c.get(Calendar.HOUR_OF_DAY);
        minute = c.get(Calendar.MINUTE);
        second = c.get(Calendar.SECOND);
        TimePickerDialog timePickerDialog = new TimePickerDialog(BookingSummaryActivity.this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker timePicker, int hourOfDay, int minute) {
                myHour = hourOfDay;
                myMinute = minute;
                // mySecond = second;
                String current_date = myYear + "-" + newMonth + "-" + newDay + " " + myHour + ":" + myMinute + ":" + second;

                if (selectDateFrom.equals("fromDate")) {
                    tv_fromDate.setText(current_date);
                } else {
                    tv_toDate.setText(current_date);

                }
            }
        },hour, minute, true);

        timePickerDialog.show();
    }

//    @Override
//    public void onTimeSet(TimePicker timePicker, int hourOfDay, int minute) {
//        myHour = hourOfDay;
//        myMinute = minute;
//        // mySecond = second;
//            String current_date = myYear + "-" + newMonth + "-" + newDay + " " + myHour + ":" + myMinute + ":" + second;
//
//        if (selectDateFrom.equals("fromDate")) {
//            tv_fromDate.setText(current_date);
//        } else {
//            tv_toDate.setText(current_date);
//
//        }
//
//
//    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        startActivity(new Intent(BookingSummaryActivity.this, DashBoardActivity.class));
        overridePendingTransition(R.anim.trans_right_in, R.anim.trans_right_out);
        finish();
        super.onBackPressed();
    }

    private String[] getDateTime() {

        //calculation of hours using checkin and checkout time
        final Calendar c = Calendar.getInstance();
        String dateTime [] = new String[3];
        int day =c.get(Calendar.DAY_OF_MONTH);
        int month =c.get(Calendar.MONTH)+1;
        String sday ="";
        String sMonth ="";

        if (month<10){
            sMonth="0"+String.valueOf(month);
        }else{
            sMonth=String.valueOf(month);
        }
        if (day < 10) {
            sday = "0"+String.valueOf(day);
        } else {
            sday = String.valueOf(day);
        }
        //  dateTime[0] = c.get(Calendar.YEAR) +"-"+ String.valueOf(c.get(Calendar.MONTH)+1) +"-"+ c.get(Calendar.DAY_OF_MONTH);
        dateTime[0] = c.get(Calendar.YEAR) +"-"+ sMonth +"-"+ sday;
        //dateTime[1] = c.get(Calendar.HOUR_OF_DAY) +":"+ c.get(Calendar.MINUTE);
        String curTimeSec = String.format("%02d:%02d:%02d",c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
        String curTime = String.format("%02d:%02d",c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        dateTime[1] = curTime;
        dateTime[2] = curTimeSec;

        return dateTime;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQUEST_CODE_PRINT_BITMAP) {
            try {
                if (intent != null && intent.hasExtra("response")) {
                    if (resultCode == RESULT_OK) {
                        JSONObject response = new JSONObject(intent.getStringExtra("response"));
                        response = response.getJSONObject("result");
                        Log.e("response", String.valueOf(response));

                        Grand_total=0;
                        total_count=0;
                        tv_toDate.setText("");
                        tv_fromDate.setText("");

                        dataModel.parkingSummaryByAgentBeanArrayList.removeAll(dataModel.parkingSummaryByAgentBeanArrayList);
                        startActivity(new Intent(BookingSummaryActivity.this, DashBoardActivity.class));
                        overridePendingTransition(R.anim.trans_right_in, R.anim.trans_right_out);
                        finish();

                        // Initialization of SDK is successful, proceed with your action
                    } else if (resultCode == RESULT_CANCELED) {
                        JSONObject response = new JSONObject(intent.getStringExtra("response"));
                        response = response.getJSONObject("error");
                        String errorCode = response.getString("code");
                        String errorMessage = response.getString("message");
                        Log.e("response", String.valueOf(response));

                        final AlertDialog alertDialog = new AlertDialog.Builder(
                                BookingSummaryActivity.this).create();

                        LayoutInflater inflater = this.getLayoutInflater();
                        View dialogView = inflater.inflate(R.layout.customdialoglayout, null);
                        alertDialog.setView(dialogView);
                        TextView heading = (TextView) dialogView.findViewById(R.id.heading);
                        TextView msg_txt = (TextView) dialogView.findViewById(R.id.msg_txt);
                        Button btnOk = (Button) dialogView.findViewById(R.id.btnOk);

                        heading.setText(R.string.validation_name);
                        msg_txt.setText(errorMessage);
                        btnOk.setText(getResources().getString(R.string.reprint));
                        btnOk.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                alertDialog.dismiss();
                                try {
                                    printPaxBillWrapText();
                                } catch (Exception e) {

                                    e.printStackTrace();

                                }
                            }
                        });
                        //Animate alert dialog box
                        FragmentTransaction ft = this.getFragmentManager().beginTransaction();
                        ft.setCustomAnimations(android.R.animator.fade_in,
                                android.R.animator.fade_out);
                        // Showing Alert Message
                        alertDialog.show();
                        alertDialog.setCancelable(false);

                    }
                    else{
                        JSONObject response = new JSONObject(intent.getStringExtra("response"));
                        response = response.getJSONObject("error");
                        String errorCode = response.getString("code");
                        String errorMessage = response.getString("message");
                        Log.e("response", String.valueOf(response));

                        final AlertDialog alertDialog = new AlertDialog.Builder(
                                BookingSummaryActivity.this).create();

                        LayoutInflater inflater = this.getLayoutInflater();
                        View dialogView = inflater.inflate(R.layout.customdialoglayout, null);
                        alertDialog.setView(dialogView);
                        TextView heading = (TextView) dialogView.findViewById(R.id.heading);
                        TextView msg_txt = (TextView) dialogView.findViewById(R.id.msg_txt);
                        Button btnOk = (Button) dialogView.findViewById(R.id.btnOk);

                        heading.setText(R.string.validation_name);
                        msg_txt.setText(errorMessage);
                        btnOk.setText(getResources().getString(R.string.reprint));
                        btnOk.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                alertDialog.dismiss();
                                try {
                                    printPaxBillWrapText();

                                } catch (Exception e) {

                                    e.printStackTrace();

                                }
                            }
                        });
                        //Animate alert dialog box
                        FragmentTransaction ft = this.getFragmentManager().beginTransaction();
                        ft.setCustomAnimations(android.R.animator.fade_in,
                                android.R.animator.fade_out);
                        // Showing Alert Message
                        alertDialog.show();
                        alertDialog.setCancelable(false);
                    }
                }
                else{
                    // Show dialog : Text : "Printer Error, Data null from Ezetap : " + resultCode
                    ShowAlertDialog.showAlertDialog(this, "Printer Error, Data null from Ezetap : " + resultCode);
                }
            } catch (Exception e) {
                e.printStackTrace();
                //  ShowAlertDialog.showAlertDialog(this, e.getMessage());
                // Do your exception handling
            }
        }
    }

    // for location line break
    public static String[] breakStringToLines(String str, int maxLength)
    {
        StringBuilder result = new StringBuilder();
        while (str.length() > maxLength)
        {
            // Attempt to break on whitespace first,
            int breakingIndex = lastIndexOfRegex(str, "\\s", maxLength);

            // Then on other non-alphanumeric characters,
            if (breakingIndex == NOT_FOUND) breakingIndex = lastIndexOfRegex(str, "[^a-zA-Z0-9]", maxLength);

            // And if all else fails, break in the middle of the word
            if (breakingIndex == NOT_FOUND) breakingIndex = maxLength;

            // Append each prepared line to the builder
            result.append(str.substring(0, breakingIndex + 1));
            result.append("\n");

            // And start the next line
            str = str.substring(breakingIndex + 1);
        }

        // Check if there are any residual characters left
        if (str.length() > 0)
        {
            result.append(str);
        }

        // Return the resulting string
        return result.toString().split("\n");
    }
    // lastIndexOfRegex
    public static int lastIndexOfRegex(String str, String toFind, int fromIndex)
    {
        // Limit the search by searching on a suitable substring
        return lastIndexOfRegex(str.substring(0, fromIndex), toFind);
    }

    //lastIndexOfRegex
    public static int lastIndexOfRegex(String str, String toFind)
    {
        Pattern pattern = Pattern.compile(toFind);
        Matcher matcher = pattern.matcher(str);

        // Default to the NOT_FOUND constant
        int lastIndex = NOT_FOUND;

        // Search for the given pattern
        while (matcher.find())
        {
            lastIndex = matcher.start();
        }

        return lastIndex;
    }


    private void printBillUsingPineLab() {

        if (!isBound || mService == null) {
            Log.e("PrintService", "Service is not bound or is null");
            return;
        }

        Message message = Message.obtain(null, 1001);  // PrintData method code
        Bundle data = new Bundle();

        JSONObject printRequest = new JSONObject();
        try {
            // Header
            JSONObject header = new JSONObject();
            header.put("ApplicationId", "8754f022bd7f475a9f29284a656d3401"); // Your Application ID
//            header.put("ApplicationId", "8754f022bd7f475a9f29284a656d3401");
            header.put("MethodId", "1002");          // PrintData method ID
            header.put("VersionNo", "1.0");
            printRequest.put("Header", header);

            // Detail
            JSONObject detail = new JSONObject();
            detail.put("PrintRefNo", "123456");     // Unique reference number
            detail.put("SavePrintData", true);      // Save print data on device

            // Print Data Array
            JSONArray printData = new JSONArray();

            // Row gap function (adds empty lines)

            // Title Line: INSLIP

            String[] arrLocName = breakStringToLines(SharedStorage.getValue(BookingSummaryActivity.this,"parkinglocation"),35);

            for(String strLocName : arrLocName)
            {
                if(null != strLocName && !strLocName.trim().equals(""))
                {
                    JSONObject printTitle = new JSONObject();
                    printTitle.put("PrintDataType", 0);
                    printTitle.put("PrinterWidth", 32);
                    printTitle.put("IsCenterAligned", true);
                    printTitle.put("DataToPrint", strLocName);
                    printData.put(printTitle);
                }
            }

            JSONObject summary = new JSONObject();
            summary.put("PrintDataType", 0);
            summary.put("PrinterWidth", 32);
            summary.put("IsCenterAligned", true);
            summary.put("DataToPrint", "EXIT SUMMARY - REPORT");
            printData.put(summary);

            JSONObject agent = new JSONObject();
            agent.put("PrintDataType", 0);
            agent.put("PrinterWidth", 32);
            agent.put("IsCenterAligned", true);
            agent.put("DataToPrint", "AGENT  : " + SharedStorage.getValue(getApplicationContext(),"Userame"));
            printData.put(agent);


            JSONObject line = new JSONObject();
            line.put("PrintDataType", 0);
            line.put("PrinterWidth", 32);
            line.put("IsCenterAligned", true);
            line.put("DataToPrint", "-----------------------------");
            printData.put(line);

            JSONObject newLine3 = new JSONObject();
            newLine3.put("PrintDataType", 0);
            newLine3.put("PrinterWidth", 32);
            newLine3.put("IsCenterAligned", true);
            newLine3.put("DataToPrint", " ");
            printData.put(newLine3);


            JSONObject fromDate = new JSONObject();
            fromDate.put("PrintDataType", 0);
            fromDate.put("PrinterWidth", 32);
            fromDate.put("IsCenterAligned", true);
            fromDate.put("DataToPrint", "From Date  : "+tv_fromDate.getText().toString());
            printData.put(fromDate);


            JSONObject toDate = new JSONObject();
            toDate.put("PrintDataType", 0);
            toDate.put("PrinterWidth", 32);
            toDate.put("IsCenterAligned", true);
            toDate.put("DataToPrint", "To Date   : "+tv_toDate.getText().toString());
            printData.put(toDate);

            JSONObject newLine1 = new JSONObject();
            newLine1.put("PrintDataType", 0);
            newLine1.put("PrinterWidth", 32);
            newLine1.put("IsCenterAligned", true);
            newLine1.put("DataToPrint", " ");
            printData.put(newLine1);

            if (dataModel.parkingSummaryByAgentBeanArrayList.size()>0){
                int sl_no = 0;
                String vehicle_name="";
                int count = 0;
                int amount = 0;
                int vehicleTypeId = 0;
                Grand_total=0;
                total_count=0;
                for(int i=0;i<dataModel.parkingSummaryByAgentBeanArrayList.size();i++){
                    sl_no =i;
                    vehicle_name=dataModel.parkingSummaryByAgentBeanArrayList.get(i).getVehicleTypeName();
                    count =dataModel.parkingSummaryByAgentBeanArrayList.get(i).getParkingCount();
                    amount =dataModel.parkingSummaryByAgentBeanArrayList.get(i).getCollectionAmount();
                    vehicleTypeId =dataModel.parkingSummaryByAgentBeanArrayList.get(i).getVehicleTypeID();
                    Grand_total= Grand_total+amount;
                    total_count= total_count+count;


                    JSONObject vehicle = new JSONObject();
                    vehicle.put("PrintDataType", 0);
                    vehicle.put("PrinterWidth", 32);
                    vehicle.put("IsCenterAligned", true);
                    vehicle.put("DataToPrint", vehicle_name);
                    printData.put(vehicle);


                    JSONObject count1 = new JSONObject();
                    count1.put("PrintDataType", 0);
                    count1.put("PrinterWidth", 32);
                    count1.put("IsCenterAligned", true);
                    count1.put("DataToPrint", "COUNT    : "+count);
                    printData.put(count1);




                    // Set fifth line in Bitmap
                    JSONObject Amount = new JSONObject();
                    Amount.put("PrintDataType", 0);
                    Amount.put("PrinterWidth", 32);
                    Amount.put("IsCenterAligned", true);
                    Amount.put("DataToPrint", "AMOUNT  : "+"Rs. "+amount);
                    printData.put(Amount);


                    JSONObject line1 = new JSONObject();
                    line1.put("PrintDataType", 0);
                    line1.put("PrinterWidth", 32);
                    line1.put("IsCenterAligned", true);
                    line1.put("DataToPrint", "-----------------------------");
                    printData.put(line1);

                }

            }

            JSONObject totalCount = new JSONObject();
            totalCount.put("PrintDataType", 0);
            totalCount.put("PrinterWidth", 32);
            totalCount.put("IsCenterAligned", true);
            totalCount.put("DataToPrint", "TOTAL COUNT    : "+total_count);
            printData.put(totalCount);


            JSONObject totalAmount = new JSONObject();
            totalAmount.put("PrintDataType", 0);
            totalAmount.put("PrinterWidth", 32);
            totalAmount.put("IsCenterAligned", true);
            totalAmount.put("DataToPrint", "TOTAL AMOUNT : "+"Rs. "+Grand_total);
            printData.put(totalAmount);


            JSONObject newLine4 = new JSONObject();
            newLine4.put("PrintDataType", 0);
            newLine4.put("PrinterWidth", 32);
            newLine4.put("IsCenterAligned", true);
            newLine4.put("DataToPrint", "-----------------------------");
            printData.put(newLine4);



            String dateTime[] = getDateTime();

            JSONObject datetime = new JSONObject();
            datetime.put("PrintDataType", 0);
            datetime.put("PrinterWidth", 32);
            datetime.put("IsCenterAligned", true);
            datetime.put("DataToPrint", "Printed on -"+" "+dateTime[0]+" "+dateTime[1]);
            printData.put(datetime);




            JSONObject newLine = new JSONObject();
            newLine.put("PrintDataType", 0);
            newLine.put("PrinterWidth", 32);
            newLine.put("IsCenterAligned", true);
            newLine.put("DataToPrint", " ");
            printData.put(newLine);



            JSONObject newLine5 = new JSONObject();
            newLine5.put("PrintDataType", 0);
            newLine5.put("PrinterWidth", 10);
            newLine5.put("IsCenterAligned", true);
            newLine5.put("DataToPrint", "\n\n");
            printData.put(newLine5);

            // Add array to detail
            detail.put("Data", printData);
            printRequest.put("Detail", detail);

            // Send request
            data.putString("MASTERAPPREQUEST", printRequest.toString());
            message.setData(data);
            message.replyTo = new Messenger(new BookingSummaryActivity.IncomingHandler()); // Handle response

            mService.send(message);

        } catch (JSONException | RemoteException e) {
            e.printStackTrace();
        }
    }




    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            String response = bundle.getString("MASTERAPPRESPONSE");
            Log.i("PrintService", "Received response: " + response);

            try {
                // Handle the JSON response
                JSONObject responseObject = new JSONObject(response);
                JSONObject responseHeader = responseObject.getJSONObject("Header");
                JSONObject responseObj = responseObject.getJSONObject("Response");
                int responseCode = responseObj.getInt("ResponseCode");
                String responseMsg = responseObj.getString("ResponseMsg");

                if (responseCode==0) {

                    Grand_total=0;
                    total_count=0;
                    tv_toDate.setText("");
                    tv_fromDate.setText("");
                    btn_print.setClickable(true);
                    btn_print.setEnabled(true);
                    dataModel.parkingSummaryByAgentBeanArrayList.removeAll(dataModel.parkingSummaryByAgentBeanArrayList);
                    startActivity(new Intent(BookingSummaryActivity.this, DashBoardActivity.class));
                    overridePendingTransition(R.anim.trans_right_in, R.anim.trans_right_out);
                    finish();

                    Log.i("PrintService", "Print successful: " + responseMsg);
                }
                else if (responseCode==1002){
                    final AlertDialog alertDialog = new AlertDialog.Builder(
                            BookingSummaryActivity.this).create();

                    LayoutInflater inflater = getLayoutInflater();
                    View dialogView = inflater.inflate(R.layout.customdialoglayout, null);
                    alertDialog.setView(dialogView);
                    TextView heading = (TextView) dialogView.findViewById(R.id.heading);
                    TextView msg_txt = (TextView) dialogView.findViewById(R.id.msg_txt);
                    Button btnOk = (Button) dialogView.findViewById(R.id.btnOk);

                    heading.setText(R.string.validation_name);
                    msg_txt.setText(responseMsg);
                    btnOk.setText(getResources().getString(R.string.reprint));
                    btnOk.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            alertDialog.dismiss();
                            try {
                                printBillUsingPineLab();

                            } catch (Exception e) {

                                e.printStackTrace();

                            }
                        }
                    });
                    //Animate alert dialog box
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.setCustomAnimations(android.R.animator.fade_in,
                            android.R.animator.fade_out);
                    // Showing Alert Message
                    alertDialog.show();
                    alertDialog.setCancelable(false);

                    Log.e("PrintService", "Print failed: " + responseMsg);
                }
                else{
                    final AlertDialog alertDialog = new AlertDialog.Builder(
                            BookingSummaryActivity.this).create();

                    LayoutInflater inflater = getLayoutInflater();
                    View dialogView = inflater.inflate(R.layout.customdialoglayout, null);
                    alertDialog.setView(dialogView);
                    TextView heading = (TextView) dialogView.findViewById(R.id.heading);
                    TextView msg_txt = (TextView) dialogView.findViewById(R.id.msg_txt);
                    Button btnOk = (Button) dialogView.findViewById(R.id.btnOk);

                    heading.setText(R.string.validation_name);
                    msg_txt.setText(responseMsg);
                    btnOk.setText(getResources().getString(R.string.reprint));
                    btnOk.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            alertDialog.dismiss();

                        }
                    });
                    //Animate alert dialog box
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.setCustomAnimations(android.R.animator.fade_in,
                            android.R.animator.fade_out);
                    // Showing Alert Message
                    alertDialog.show();
                    alertDialog.setCancelable(false);

                    Log.e("PrintService", "Print failed: " + responseMsg);
                }



            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }


}