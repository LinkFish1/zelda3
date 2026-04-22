package com.dishii.zelda3;

import org.libsdl.app.SDLActivity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends SDLActivity {

    // Controller state
    @SuppressWarnings("deprecation")
    private Vibrator vibrator;
    private View drawerPanel;
    private View drawerDim;
    private boolean drawerOpen = false;
    private int tabTopMargin = -1;
    private float controllerAlpha = 0.85f;
    private View[] alphaTargets;
    private boolean turboActive = false;
    private TextView turboBtn = null;
    private File screenshotDir = null;
    private File iniFile = null;

    // Tab state
    private LinearLayout[] tabContents = new LinearLayout[3];
    private TextView[] tabBtns = new TextView[3];
    private TextView restartBanner = null;

    // INI cache
    private final Map<String, String> iniValues = new LinkedHashMap<>();

    // Keycodes -- zelda3.ini: Controls = y,h,g,j,v,n,x,z,s,a,c,v
    private static final int KEY_UP     = KeyEvent.KEYCODE_Y;
    private static final int KEY_DOWN   = KeyEvent.KEYCODE_H;
    private static final int KEY_LEFT   = KeyEvent.KEYCODE_G;
    private static final int KEY_RIGHT  = KeyEvent.KEYCODE_J;
    private static final int KEY_SELECT = KeyEvent.KEYCODE_V;
    private static final int KEY_START  = KeyEvent.KEYCODE_N;
    private static final int KEY_A      = KeyEvent.KEYCODE_X;
    private static final int KEY_B      = KeyEvent.KEYCODE_Z;
    private static final int KEY_X      = KeyEvent.KEYCODE_S;
    private static final int KEY_Y_BTN  = KeyEvent.KEYCODE_A;
    private static final int KEY_L      = KeyEvent.KEYCODE_C;
    private static final int KEY_R      = KeyEvent.KEYCODE_BACK;
    private static final int KEY_TURBO  = KeyEvent.KEYCODE_M;

    private static final int[] F_KEYS = {
            KeyEvent.KEYCODE_F1,  KeyEvent.KEYCODE_F2,  KeyEvent.KEYCODE_F3,
            KeyEvent.KEYCODE_F4,  KeyEvent.KEYCODE_F5,  KeyEvent.KEYCODE_F6,
            KeyEvent.KEYCODE_F7,  KeyEvent.KEYCODE_F8,  KeyEvent.KEYCODE_F9,
            KeyEvent.KEYCODE_F10
    };

    // =========================================================================
    // onCreate
    // =========================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        screenshotDir = new File(getExternalFilesDir(null), "saves/thumbs");
        screenshotDir.mkdirs();
        iniFile = new File(getExternalFilesDir(null), "zelda3.ini");

        if (isExternalStorageWritable()) {
            File externalDir = getExternalFilesDir(null);
            if (externalDir != null) {
                File datNotice        = new File(externalDir, "PLACE zelda3_assets.dat HERE");
                File saves_folder     = new File(externalDir + File.separator + "saves");
                File saves_ref_folder = new File(saves_folder + File.separator + "ref");
                saves_folder.mkdirs();
                saves_ref_folder.mkdirs();
                try {
                    AssetCopyUtil.copyAssetsToExternal(this, "saves/ref",
                            (getExternalFilesDir(null) != null
                                    ? getExternalFilesDir(null).getAbsolutePath() : "") + "/saves/ref");
                    datNotice.createNewFile();
                    if (iniFile.createNewFile()) {
                        InputStream is;
                        try { is = getAssets().open("zelda3.ini"); }
                        catch (IOException e) { e.printStackTrace(); return; }
                        writeDataToFile(iniFile, is);
                    }
                } catch (IOException e) { e.printStackTrace(); }
            }
        }

        loadIni();
        buildOverlay();
    }

    // =========================================================================
    // INI read/write
    // =========================================================================
    private void loadIni() {
        iniValues.clear();
        if (iniFile == null || !iniFile.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(iniFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.startsWith(";") || line.startsWith("[")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                // Strip inline comments
                int hash = val.indexOf('#');
                if (hash >= 0) val = val.substring(0, hash).trim();
                iniValues.put(key, val);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private String iniGet(String key, String def) {
        String v = iniValues.get(key);
        return v != null ? v : def;
    }

    private boolean iniGetBool(String key, boolean def) {
        String v = iniGet(key, def ? "1" : "0");
        return v.equals("1") || v.equalsIgnoreCase("true");
    }

    /** Write a single key=value into the INI file, preserving all other lines. */
    private void iniSet(String key, String value) {
        iniValues.put(key, value);
        if (iniFile == null) return;
        List<String> lines = new ArrayList<>();
        boolean found = false;
        try (BufferedReader br = new BufferedReader(new FileReader(iniFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("#") && !trimmed.startsWith(";")) {
                    int eq = trimmed.indexOf('=');
                    if (eq >= 0) {
                        String k = trimmed.substring(0, eq).trim();
                        if (k.equals(key)) {
                            lines.add(key + " = " + value);
                            found = true;
                            continue;
                        }
                    }
                }
                lines.add(line);
            }
        } catch (IOException e) { e.printStackTrace(); return; }
        if (!found) lines.add(key + " = " + value);
        try (PrintWriter pw = new PrintWriter(iniFile)) {
            for (String l : lines) pw.println(l);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void iniSetBool(String key, boolean value) {
        iniSet(key, value ? "1" : "0");
    }

    // =========================================================================
    // Build controller overlay
    // =========================================================================
    private void buildOverlay() {
        ViewGroup root = findViewById(android.R.id.content);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- FIXED D-PAD (8-directional with diagonal support) --------------
        // Fixed position at bottom-left. Supports UP/DOWN/LEFT/RIGHT and diagonals.
        // X/Y axes are independent: can press UP+LEFT simultaneously, etc.

        // Shared highlight state for visual feedback: -1=none, 0=up, 45=up-right, etc.
        final float[] dpadHighlightAngle = {-1f};

        View dpadView = new View(this) {
            final android.graphics.Paint dpadPaint =
                    new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            final android.graphics.RectF dpadH = new android.graphics.RectF();
            final android.graphics.RectF dpadV = new android.graphics.RectF();

            @Override
            protected void onDraw(android.graphics.Canvas c) {
                float cx = getWidth() / 2f, cy = getHeight() / 2f;
                float arm = getWidth() * 0.48f, thick = getWidth() * 0.32f;
                android.graphics.Paint p = dpadPaint;

                // Cross fill
                p.setColor(0xCC16213E);
                dpadH.set(cx - arm, cy - thick / 2, cx + arm, cy + thick / 2);
                dpadV.set(cx - thick / 2, cy - arm, cx + thick / 2, cy + arm);
                c.drawRoundRect(dpadH, thick * 0.2f, thick * 0.2f, p);
                c.drawRoundRect(dpadV, thick * 0.2f, thick * 0.2f, p);

                // Cross stroke
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeWidth(2f);
                p.setColor(0xFF4E9AF1);
                p.setAlpha(140);
                c.drawRoundRect(dpadH, thick * 0.2f, thick * 0.2f, p);
                c.drawRoundRect(dpadV, thick * 0.2f, thick * 0.2f, p);
                p.setStyle(android.graphics.Paint.Style.FILL);
                p.setAlpha(255);

                // Direction highlight (8 directions)
                if (dpadHighlightAngle[0] >= 0) {
                    p.setColor(0x664E9AF1); // Semi-transparent blue
                    float rad = (float) Math.toRadians(dpadHighlightAngle[0]);
                    float dist = arm * 0.5f;
                    float hx = cx + (float) Math.sin(rad) * dist;
                    float hy = cy - (float) Math.cos(rad) * dist;
                    c.drawCircle(hx, hy, thick * 0.4f, p);
                }

                // Arrow triangles
                p.setColor(0xFFB0C4FF);
                float as = thick * 0.28f;
                drawA(c, p, cx, cy - arm + as * 1.2f, as, 0);      // Up
                drawA(c, p, cx, cy + arm - as * 1.2f, as, 180);    // Down
                drawA(c, p, cx - arm + as * 1.2f, cy, as, 270);    // Left
                drawA(c, p, cx + arm - as * 1.2f, cy, as, 90);     // Right
            }

            private void drawA(android.graphics.Canvas c, android.graphics.Paint p,
                               float x, float y, float s, float deg) {
                float r = (float) Math.toRadians(deg);
                float fx = (float) Math.sin(r), fy = -(float) Math.cos(r);
                float rx = fy, ry = -fx;
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(x + fx * s * 0.6f, y + fy * s * 0.6f);
                path.lineTo(x - rx * s * 0.5f - fx * s * 0.5f, y - ry * s * 0.5f - fy * s * 0.5f);
                path.lineTo(x + rx * s * 0.5f - fx * s * 0.5f, y + ry * s * 0.5f - fy * s * 0.5f);
                path.close();
                c.drawPath(path, p);
            }
        };
        dpadView.setWillNotDraw(false);

        // Fixed layout: bottom-left corner
        int dpadSize = dp(190);
        FrameLayout.LayoutParams dpadLp = new FrameLayout.LayoutParams(dpadSize, dpadSize);
        dpadLp.gravity = Gravity.BOTTOM | Gravity.START;
        dpadLp.setMargins(dp(5), 0, 0, dp(65));
        overlay.addView(dpadView, dpadLp);
        dpadView.setAlpha(controllerAlpha);

        // 8-directional touch state: 0=UP, 1=DOWN, 2=LEFT, 3=RIGHT
        final boolean[] dirPressed = {false, false, false, false};

        // Need final reference to dpadView for invalidate() inside listener
        final View dpadViewRef = dpadView;

        dpadView.setOnTouchListener((view, e) -> {
            float x = e.getX();
            float y = e.getY();
            float w = view.getWidth();
            float h = view.getHeight();
            int action = e.getAction();

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                // Independent axis detection: left/right and up/down are separate
                boolean newLeft = x < w * 0.33f;
                boolean newRight = x > w * 0.67f;
                boolean newUp = y < h * 0.33f;
                boolean newDown = y > h * 0.67f;

                // Release directions that are no longer active
                if (!newLeft && dirPressed[2]) { releaseDir(2); dirPressed[2] = false; }
                if (!newRight && dirPressed[3]) { releaseDir(3); dirPressed[3] = false; }
                if (!newUp && dirPressed[0]) { releaseDir(0); dirPressed[0] = false; }
                if (!newDown && dirPressed[1]) { releaseDir(1); dirPressed[1] = false; }

                // Press newly active directions
                if (newLeft && !dirPressed[2]) { pressDir(2); dirPressed[2] = true; vibrate(10); }
                if (newRight && !dirPressed[3]) { pressDir(3); dirPressed[3] = true; vibrate(10); }
                if (newUp && !dirPressed[0]) { pressDir(0); dirPressed[0] = true; vibrate(10); }
                if (newDown && !dirPressed[1]) { pressDir(1); dirPressed[1] = true; vibrate(10); }

                // Update visual highlight angle
                if (newUp && newRight) dpadHighlightAngle[0] = 45;
                else if (newDown && newRight) dpadHighlightAngle[0] = 135;
                else if (newDown && newLeft) dpadHighlightAngle[0] = 225;
                else if (newUp && newLeft) dpadHighlightAngle[0] = 315;
                else if (newUp) dpadHighlightAngle[0] = 0;
                else if (newRight) dpadHighlightAngle[0] = 90;
                else if (newDown) dpadHighlightAngle[0] = 180;
                else if (newLeft) dpadHighlightAngle[0] = 270;
                else dpadHighlightAngle[0] = -1;

                dpadViewRef.invalidate();
                return true;

            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                // Release all directions
                for (int i = 0; i < 4; i++) {
                    if (dirPressed[i]) {
                        releaseDir(i);
                        dirPressed[i] = false;
                    }
                }
                dpadHighlightAngle[0] = -1;
                dpadViewRef.invalidate();
                return true;
            }
            return false;
        });

        // ---- FACE BUTTONS ----------------------------------------------------
        TextView btnB=makeFaceBtn("B",0xFFE74C3C); addBtn(overlay,btnB,dp(56),dp(56),Gravity.BOTTOM|Gravity.END,0,0,dp(71),dp(75)); wire(btnB,KEY_B);
        TextView btnA=makeFaceBtn("A",0xFF2ECC71); addBtn(overlay,btnA,dp(56),dp(56),Gravity.BOTTOM|Gravity.END,0,0,dp(8),dp(130)); wire(btnA,KEY_A);
        TextView btnY=makeFaceBtn("Y",0xFF9B59B6); addBtn(overlay,btnY,dp(56),dp(56),Gravity.BOTTOM|Gravity.END,0,0,dp(134),dp(130)); wire(btnY,KEY_Y_BTN);
        TextView btnX=makeFaceBtn("X",0xFF3498DB); addBtn(overlay,btnX,dp(56),dp(56),Gravity.BOTTOM|Gravity.END,0,0,dp(71),dp(185)); wire(btnX,KEY_X);

        // ---- SHOULDER BUTTONS ------------------------------------------------
        TextView btnL=makeShoulderBtn("L"); addBtn(overlay,btnL,dp(100),dp(40),Gravity.TOP|Gravity.START,dp(8),dp(8),0,0); wire(btnL,KEY_L);
        TextView btnR=makeShoulderBtn("R"); addBtn(overlay,btnR,dp(100),dp(40),Gravity.TOP|Gravity.END,0,dp(8),dp(8),0); wire(btnR,KEY_R);

        // ---- START / SELECT --------------------------------------------------
        TextView btnSel=makePillBtn("SELECT"); addBtn(overlay,btnSel,dp(80),dp(30),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,0,dp(90),dp(16)); wire(btnSel,KEY_SELECT);
        TextView btnSt=makePillBtn("START");   addBtn(overlay,btnSt, dp(80),dp(30),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,dp(90),0,0,dp(16)); wire(btnSt,KEY_START);

        alphaTargets = new View[]{dpadView, btnA, btnB, btnX, btnY, btnL, btnR, btnSel, btnSt};
        for (View v : alphaTargets) v.setAlpha(controllerAlpha);

        // ---- DRAWER TAB (draggable) ------------------------------------------
        TextView drawerTab=new TextView(this);
        drawerTab.setText("☰"); drawerTab.setTextColor(Color.WHITE);
        drawerTab.setTextSize(16); drawerTab.setGravity(Gravity.CENTER);
        GradientDrawable tabBg=new GradientDrawable();
        tabBg.setColor(0xEE0F3460); tabBg.setStroke(dp(1),0xFF4E9AF1);
        tabBg.setCornerRadii(new float[]{0,0,dp(12),dp(12),dp(12),dp(12),0,0});
        drawerTab.setBackground(tabBg);
        FrameLayout.LayoutParams tabLp=new FrameLayout.LayoutParams(dp(36),dp(56));
        tabLp.gravity=Gravity.TOP|Gravity.START;
        drawerTab.setLayoutParams(tabLp);
        final float[] dY0={0}; final int[] dM0={0}; final boolean[] wasDrag={false};
        drawerTab.setOnTouchListener((v,e)->{
            FrameLayout.LayoutParams lp2=(FrameLayout.LayoutParams)drawerTab.getLayoutParams();
            switch(e.getAction()){
                case MotionEvent.ACTION_DOWN: dY0[0]=e.getRawY(); dM0[0]=lp2.topMargin; wasDrag[0]=false; return true;
                case MotionEvent.ACTION_MOVE:
                    float dy=e.getRawY()-dY0[0];
                    if(Math.abs(dy)>dp(6)) wasDrag[0]=true;
                    if(wasDrag[0]){
                        int nm=Math.max(0,Math.min(dM0[0]+(int)dy,overlay.getHeight()-drawerTab.getHeight()));
                        lp2.topMargin=nm; tabTopMargin=nm; drawerTab.setLayoutParams(lp2);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if(!wasDrag[0]){vibrate(30);toggleDrawer();} return true;
            }
            return false;
        });
        overlay.addOnLayoutChangeListener((v2,l,t,r,b,ol,ot,or,ob)->{
            if(tabTopMargin==-1&&overlay.getHeight()>0){
                FrameLayout.LayoutParams lp3=(FrameLayout.LayoutParams)drawerTab.getLayoutParams();
                lp3.topMargin=(overlay.getHeight()-dp(56))/2; tabTopMargin=lp3.topMargin;
                drawerTab.setLayoutParams(lp3);
            }
        });
        overlay.addView(drawerTab,tabLp);

        // ---- DIM overlay -----------------------------------------------------
        View dimView=new View(this); dimView.setBackgroundColor(0x88000000);
        dimView.setVisibility(View.GONE);
        FrameLayout.LayoutParams dimLp=new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        dimLp.leftMargin=dp(320); overlay.addView(dimView,dimLp);
        dimView.setOnClickListener(v->{vibrate(20);toggleDrawer();}); drawerDim=dimView;

        // ---- DRAWER PANEL (on top) -------------------------------------------
        drawerPanel=buildDrawerPanel();
        overlay.addView(drawerPanel);
        root.addView(overlay);

        // Fade in on first touch
        overlay.setAlpha(0f);
        final boolean[] revealed={false};
        overlay.setOnTouchListener((v,e)->{
            if(!revealed[0]&&e.getAction()==MotionEvent.ACTION_DOWN){
                revealed[0]=true; overlay.animate().alpha(1f).setDuration(300).start();
            }
            return false;
        });
    }

    // =========================================================================
    // Slot card helpers
    // =========================================================================
    private LinearLayout makeSlotCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC0D1117); bg.setStroke(dp(1), 0x554E9AF1); bg.setCornerRadius(dp(8));
        card.setBackground(bg);
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        return card;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        return lp;
    }

    private TextView makeSectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(0xFF4E9AF1); tv.setTextSize(12);
        tv.setTypeface(Typeface.DEFAULT_BOLD); tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC0F1F3D); bg.setStroke(dp(1), 0xFF4E9AF1); bg.setCornerRadius(dp(8));
        tv.setBackground(bg); tv.setPadding(0, dp(7), 0, dp(7));
        return tv;
    }

    private LinearLayout.LayoutParams sectionHeaderLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        return lp;
    }

    // =========================================================================
    // Build three-tab drawer panel
    // =========================================================================
    private View buildDrawerPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF5070B18); bg.setStroke(dp(1),0xFF4E9AF1);
        bg.setCornerRadii(new float[]{0,0,dp(20),dp(20),dp(20),dp(20),0,0});
        panel.setBackground(bg);
        panel.setPadding(0,dp(14),0,dp(8));

        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(
                dp(320),ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity=Gravity.START;
        panel.setLayoutParams(lp);
        panel.setTranslationX(-dp(320));
        panel.setVisibility(View.INVISIBLE);

        // Handle bar
        View handle=new View(this); handle.setBackgroundColor(0x884E9AF1);
        LinearLayout.LayoutParams hlp=new LinearLayout.LayoutParams(dp(40),dp(4));
        hlp.gravity=Gravity.CENTER_HORIZONTAL; hlp.bottomMargin=dp(10);
        panel.addView(handle,hlp);

        // ---- TAB ROW ---------------------------------------------------------
        LinearLayout tabRow=new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(dp(8),0,dp(8),0);
        LinearLayout.LayoutParams trLp=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        trLp.bottomMargin=dp(8);
        String[] tabLabels={"💾 Saves","⚙ Settings","🎮 Control"};
        for(int i=0;i<3;i++){
            final int ti=i;
            TextView tb=new TextView(this);
            tb.setText(tabLabels[i]); tb.setTextSize(10);
            tb.setTypeface(Typeface.DEFAULT_BOLD); tb.setGravity(Gravity.CENTER);
            tb.setPadding(0,dp(6),0,dp(6));
            LinearLayout.LayoutParams tblp=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f);
            tblp.setMargins(i==0?0:dp(3),0,i==2?0:dp(3),0);
            updateTabStyle(tb,i==0);
            tb.setOnClickListener(v->switchTab(ti));
            tabRow.addView(tb,tblp);
            tabBtns[i]=tb;
        }
        panel.addView(tabRow,trLp);

        // ---- TAB CONTENT AREA ------------------------------------------------
        FrameLayout contentFrame=new FrameLayout(this);
        LinearLayout.LayoutParams cfLp=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,0,1f);
        panel.addView(contentFrame,cfLp);

        // Tab 0: Saves
        tabContents[0]=buildSavesTab();
        contentFrame.addView(tabContents[0]);

        // Tab 1: Settings
        tabContents[1]=buildSettingsTab();
        tabContents[1].setVisibility(View.GONE);
        contentFrame.addView(tabContents[1]);

        // Tab 2: Controller
        tabContents[2]=buildControllerTab();
        tabContents[2].setVisibility(View.GONE);
        contentFrame.addView(tabContents[2]);

        return panel;
    }

    private void updateTabStyle(TextView tb, boolean active) {
        GradientDrawable bg=new GradientDrawable();
        bg.setCornerRadius(dp(8));
        if(active){ bg.setColor(0xFF4E9AF1); tb.setTextColor(0xFF070B18); }
        else       { bg.setColor(0xCC1A1A2E); bg.setStroke(dp(1),0xFF4E9AF1); tb.setTextColor(0xFFB0C4FF); }
        tb.setBackground(bg);
    }

    private void switchTab(int idx) {
        for(int i=0;i<3;i++){
            tabContents[i].setVisibility(i==idx?View.VISIBLE:View.GONE);
            updateTabStyle(tabBtns[i],i==idx);
        }
        vibrate(15);
    }

    // =========================================================================
    // Tab 0: Saves
    // =========================================================================
    private LinearLayout buildSavesTab() {
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setPadding(dp(12), 0, dp(12), 0);

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
        File savesDir = new File(getExternalFilesDir(null), "saves");

        final ImageView[] thumbViews = new ImageView[10];
        final TextView[]  dateLabels = new TextView[10];
        final TextView[]  loadBtns   = new TextView[10];

        ScrollView sv = new ScrollView(this);
        sv.setOnTouchListener((v, e) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true); return false;
        });
        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        scrollContent.setPadding(0, dp(4), 0, dp(8));

        for (int i = 0; i < 10; i++) {
            final int slot = i;

            File saveFile = new File(savesDir, "save_" + i + ".dat");
            if (!saveFile.exists()) saveFile = new File(savesDir, "save_" + i + ".bak");
            if (!saveFile.exists()) saveFile = new File(savesDir, i + ".sav");
            File thumbFile = new File(screenshotDir, "slot_" + i + ".jpg");
            final File tf = thumbFile;
            boolean slotUsed = saveFile.exists() || thumbFile.exists();
            String dateStr = slotUsed
                    ? sdf.format(new Date(thumbFile.exists()
                    ? thumbFile.lastModified() : saveFile.lastModified()))
                    : "empty";

            // Card
            LinearLayout card = makeSlotCard();

            // Thumbnail
            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (thumbFile.exists())
                thumb.setImageBitmap(BitmapFactory.decodeFile(thumbFile.getAbsolutePath()));
            else
                thumb.setBackgroundColor(0xFF050810);
            LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
            thumbLp.bottomMargin = dp(6);
            card.addView(thumb, thumbLp);
            thumbViews[i] = thumb;

            // Bottom row: [Save]  |  Slot N / date  |  [Load]
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            // Left: Save button
            TextView saveBtn = makeSlotBtn("Save");
            row.addView(saveBtn, new LinearLayout.LayoutParams(dp(60), dp(32)));

            // Centre: slot number + date stacked
            LinearLayout centre = new LinearLayout(this);
            centre.setOrientation(LinearLayout.VERTICAL);
            centre.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView slotLbl = new TextView(this);
            slotLbl.setText("Slot " + (i + 1));
            slotLbl.setTextColor(0xFF4E9AF1);
            slotLbl.setTextSize(11);
            slotLbl.setTypeface(Typeface.DEFAULT_BOLD);
            slotLbl.setGravity(Gravity.CENTER);
            centre.addView(slotLbl, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            final TextView dateLabel = new TextView(this);
            dateLabel.setText(dateStr);
            dateLabel.setTextColor(0x88FFFFFF);
            dateLabel.setTextSize(9);
            dateLabel.setGravity(Gravity.CENTER);
            centre.addView(dateLabel, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(centre, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            dateLabels[i] = dateLabel;

            // Right: Load button
            TextView loadBtn = makeSlotBtn("Load");
            row.addView(loadBtn, new LinearLayout.LayoutParams(dp(60), dp(32)));
            loadBtns[i] = loadBtn;

            card.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            // Listeners
            saveBtn.setOnClickListener(v -> {
                vibrate(40); saveSlot(slot); flashBtn(saveBtn);
                dateLabels[slot].setText(sdf.format(new Date()));
                loadBtns[slot].setAlpha(1f); loadBtns[slot].setClickable(true);
                captureScreenshot(tf, thumbViews[slot]);
                Toast.makeText(this, "Saved slot " + (slot + 1), Toast.LENGTH_SHORT).show();
            });
            loadBtn.setOnClickListener(v -> {
                vibrate(40); loadSlot(slot); flashBtn(loadBtn);
                Toast.makeText(this, "Loaded slot " + (slot + 1), Toast.LENGTH_SHORT).show();
            });
            if (!slotUsed) { loadBtn.setAlpha(0.4f); loadBtn.setClickable(false); }

            scrollContent.addView(card, cardLp());
        }

        sv.addView(scrollContent);
        tab.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return tab;
    }


    // =========================================================================
    // Tab 1: Settings
    // =========================================================================
    private LinearLayout buildSettingsTab() {
        LinearLayout tab=new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);

        // Restart banner (hidden by default)
        restartBanner=new TextView(this);
        restartBanner.setText("⏳ Restart game to apply changes");
        restartBanner.setTextColor(0xFFFFD700); restartBanner.setTextSize(10);
        restartBanner.setGravity(Gravity.CENTER);
        restartBanner.setPadding(dp(8),dp(6),dp(8),dp(6));
        restartBanner.setBackgroundColor(0xCC1A1200);
        restartBanner.setVisibility(View.GONE);
        tab.addView(restartBanner,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView sv=new ScrollView(this);
        sv.setOnTouchListener((v,e)->{v.getParent().requestDisallowInterceptTouchEvent(true);return false;});
        LinearLayout scroll=new LinearLayout(this);
        scroll.setOrientation(LinearLayout.VERTICAL);
        scroll.setPadding(dp(12),dp(4),dp(12),dp(12));

        // ---- GENERAL ---------------------------------------------------------
        addSectionHeader(scroll,"GENERAL");
        addToggle(scroll,"Autosave on quit","Autosave",false);
        addDropdown(scroll,"Aspect Ratio","ExtendedAspectRatio",
                new String[]{"4:3","16:9","16:10","18:9"},
                new String[]{"4:3","16:9","16:10","18:9"},
                iniGet("ExtendedAspectRatio","18:9"));
        addToggle(scroll,"Disable frame delay","DisableFrameDelay",false);
        addToggle(scroll,"Skip intro on keypress","SkipIntroOnKeypress",false);

        // ---- GRAPHICS --------------------------------------------------------
        addSectionHeader(scroll,"GRAPHICS");
        addToggle(scroll,"New renderer (faster)","NewRenderer",true);
        addToggle(scroll,"Enhanced Mode 7 (world map)","EnhancedMode7",true);
        addToggle(scroll,"Remove sprite limits","NoSpriteLimits",true);
        addToggle(scroll,"Linear filtering","LinearFiltering",false);
        addToggle(scroll,"Dim flashing effects","DimFlashes",false);
        addToggle(scroll,"Ignore aspect ratio","IgnoreAspectRatio",false);

        // ---- SOUND -----------------------------------------------------------
        addSectionHeader(scroll,"SOUND");
        addToggle(scroll,"Enable audio","EnableAudio",true);
        addDropdown(scroll,"Audio frequency","AudioFreq",
                new String[]{"11025 Hz","22050 Hz","32000 Hz","44100 Hz","48000 Hz"},
                new String[]{"11025","22050","32000","44100","48000"},
                iniGet("AudioFreq","44100"));
        addDropdown(scroll,"Audio channels","AudioChannels",
                new String[]{"Mono","Stereo"},
                new String[]{"1","2"},
                iniGet("AudioChannels","2"));
        addDropdown(scroll,"Buffer size","AudioSamples",
                new String[]{"512 (low latency)","1024","2048","4096 (stable)"},
                new String[]{"512","1024","2048","4096"},
                iniGet("AudioSamples","512"));

        // ---- FEATURES --------------------------------------------------------
        addSectionHeader(scroll,"FEATURES");
        addToggle(scroll,"L/R item switch","ItemSwitchLR",false);
        addToggle(scroll,"Limit LR switch to 4 items","ItemSwitchLRLimit",false);
        addToggle(scroll,"Turn while dashing","TurnWhileDashing",false);
        addToggle(scroll,"Mirror warps to Dark World","MirrorToDarkworld",false);
        addToggle(scroll,"Collect items with sword","CollectItemsWithSword",false);
        addToggle(scroll,"Break pots with sword","BreakPotsWithSword",false);
        addToggle(scroll,"Disable low health beep","DisableLowHealthBeep",false);
        addToggle(scroll,"Show max items in yellow","ShowMaxItemsInYellow",false);
        addToggle(scroll,"More active bombs (4)","MoreActiveBombs",false);
        addToggle(scroll,"Carry 9999 rupees","CarryMoreRupees",false);
        addToggle(scroll,"Misc bug fixes","MiscBugFixes",false);
        addToggle(scroll,"Advanced bug fixes","GameChangingBugFixes",false);
        addToggle(scroll,"Cancel bird travel (X)","CancelBirdTravel",false);

        sv.addView(scroll);
        tab.addView(sv,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,0,1f));
        return tab;
    }

    private void addSectionHeader(LinearLayout parent, String title) {
        TextView tv=new TextView(this);
        tv.setText(title); tv.setTextColor(0xFF4E9AF1); tv.setTextSize(11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin=dp(10); lp.bottomMargin=dp(4);
        // Underline
        tv.setPadding(0,0,0,dp(3));
        GradientDrawable u=new GradientDrawable();
        u.setColor(0x00000000); u.setStroke(dp(1),0x554E9AF1);
        parent.addView(tv,lp);
        View line=new View(this); line.setBackgroundColor(0x554E9AF1);
        parent.addView(line,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,dp(1)));
    }

    private void addToggle(LinearLayout parent, String label, String iniKey, boolean defaultVal) {
        boolean current = iniGetBool(iniKey, defaultVal);

        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(7),0,dp(7));

        TextView lbl=new TextView(this); lbl.setText(label);
        lbl.setTextColor(0xFFDDDDDD); lbl.setTextSize(11);
        row.addView(lbl,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));

        // Toggle pill
        final boolean[] state={current};
        TextView toggle=new TextView(this);
        toggle.setTextSize(10); toggle.setTypeface(Typeface.DEFAULT_BOLD);
        toggle.setGravity(Gravity.CENTER);
        toggle.setPadding(dp(10),dp(4),dp(10),dp(4));
        updateToggleStyle(toggle,state[0]);
        toggle.setOnClickListener(v->{
            state[0]=!state[0];
            updateToggleStyle(toggle,state[0]);
            iniSetBool(iniKey,state[0]);
            showRestartBanner();
            vibrate(15);
        });
        row.addView(toggle,new LinearLayout.LayoutParams(dp(64),LinearLayout.LayoutParams.WRAP_CONTENT));

        // Divider
        View div=new View(this); div.setBackgroundColor(0x22FFFFFF);
        LinearLayout wrap=new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        wrap.addView(div,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(1)));
        parent.addView(wrap);
    }

    private void updateToggleStyle(TextView toggle, boolean on) {
        GradientDrawable bg=new GradientDrawable(); bg.setCornerRadius(dp(20));
        if(on){ bg.setColor(0xFF2ECC71); toggle.setTextColor(0xFF070B18); toggle.setText("ON"); }
        else  { bg.setColor(0xCC1A1A2E); bg.setStroke(dp(1),0xFF4E9AF1); toggle.setTextColor(0xFFB0C4FF); toggle.setText("OFF"); }
        toggle.setBackground(bg);
    }

    private void addDropdown(LinearLayout parent, String label, String iniKey,
                             String[] displayVals, String[] rawVals, String currentRaw) {
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0,dp(7),0,dp(4));

        TextView lbl=new TextView(this); lbl.setText(label);
        lbl.setTextColor(0xFFDDDDDD); lbl.setTextSize(11);
        LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.bottomMargin=dp(4); row.addView(lbl,llp);

        // Option chips in a horizontal wrap
        LinearLayout chipRow=new LinearLayout(this); chipRow.setOrientation(LinearLayout.HORIZONTAL);
        final int[] selected={0};
        for(int i=0;i<rawVals.length;i++){
            if(rawVals[i].equalsIgnoreCase(currentRaw)) selected[0]=i;
        }
        final TextView[] chips=new TextView[rawVals.length];
        for(int i=0;i<rawVals.length;i++){
            final int idx=i;
            TextView chip=new TextView(this);
            chip.setText(displayVals[i]); chip.setTextSize(9);
            chip.setTypeface(Typeface.DEFAULT_BOLD); chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(8),dp(4),dp(8),dp(4));
            LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.setMargins(0,0,dp(4),0);
            updateChipStyle(chip,i==selected[0]);
            chip.setOnClickListener(v->{
                updateChipStyle(chips[selected[0]],false);
                selected[0]=idx; updateChipStyle(chips[idx],true);
                iniSet(iniKey,rawVals[idx]);
                showRestartBanner(); vibrate(15);
            });
            chips[i]=chip; chipRow.addView(chip,clp);
        }
        row.addView(chipRow,new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));

        View div=new View(this); div.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,dp(1));
        dlp.topMargin=dp(6);
        row.addView(div,dlp);
        parent.addView(row);
    }

    private void updateChipStyle(TextView chip, boolean active) {
        GradientDrawable bg=new GradientDrawable(); bg.setCornerRadius(dp(6));
        if(active){ bg.setColor(0xFF4E9AF1); chip.setTextColor(0xFF070B18); }
        else      { bg.setColor(0xCC1A1A2E); bg.setStroke(dp(1),0xFF4E9AF1); chip.setTextColor(0xFFB0C4FF); }
        chip.setBackground(bg);
    }

    private void showRestartBanner() {
        if(restartBanner!=null) restartBanner.setVisibility(View.VISIBLE);
    }

    // =========================================================================
    // Tab 2: Controller
    // =========================================================================
    private LinearLayout buildControllerTab() {
        LinearLayout tab=new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setPadding(dp(12),dp(4),dp(12),dp(12));

        // Opacity slider
        TextView alphaLabel=new TextView(this); alphaLabel.setText("Controller Opacity");
        alphaLabel.setTextColor(0xAAB0C4FF); alphaLabel.setTextSize(10);
        alphaLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams alLp=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        alLp.topMargin=dp(8); alLp.bottomMargin=dp(2); tab.addView(alphaLabel,alLp);

        SeekBar alphaBar=new SeekBar(this);
        alphaBar.setMax(100); alphaBar.setProgress((int)(controllerAlpha*100));
        LinearLayout.LayoutParams abLp=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        abLp.bottomMargin=dp(14);
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar sb,int p,boolean f){
                controllerAlpha=p/100f;
                if(alphaTargets!=null) for(View v:alphaTargets) v.setAlpha(controllerAlpha);
            }
            @Override public void onStartTrackingTouch(SeekBar sb){}
            @Override public void onStopTrackingTouch(SeekBar sb){}
        });
        tab.addView(alphaBar,abLp);

        // Turbo toggle
        turboBtn=makeSlotBtn("TURBO: OFF"); turboBtn.setTextColor(0xAAFFFFFF);
        LinearLayout.LayoutParams tbLp=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        tbLp.bottomMargin=dp(10);
        turboBtn.setOnClickListener(v->{
            turboActive=!turboActive;
            if(turboActive){
                onNativeKeyDown(KEY_TURBO); turboBtn.setText("TURBO: ON");
                turboBtn.setTextColor(0xFF2ECC71);
                GradientDrawable on=new GradientDrawable();
                on.setColor(0xCC0D3020); on.setStroke(dp(1),0xFF2ECC71); on.setCornerRadius(dp(8));
                turboBtn.setBackground(on);
            } else {
                onNativeKeyUp(KEY_TURBO); turboBtn.setText("TURBO: OFF");
                turboBtn.setTextColor(0xAAFFFFFF);
                GradientDrawable off=new GradientDrawable();
                off.setColor(0xCC1A1A2E); off.setStroke(dp(1),0xFF4E9AF1); off.setCornerRadius(dp(8));
                turboBtn.setBackground(off);
            }
            vibrate(20);
        });
        tab.addView(turboBtn,tbLp);

        return tab;
    }

    // =========================================================================
    // Drawer toggle
    // =========================================================================
    private void toggleDrawer() {
        int pw=dp(320);
        if(!drawerOpen){
            drawerPanel.setVisibility(View.VISIBLE);
            if(drawerDim!=null) drawerDim.setVisibility(View.VISIBLE);
            ValueAnimator a=ValueAnimator.ofFloat(-pw,0f);
            a.setDuration(220); a.setInterpolator(new DecelerateInterpolator(2f));
            a.addUpdateListener(x->drawerPanel.setTranslationX((float)x.getAnimatedValue()));
            a.start(); drawerOpen=true;
        } else {
            ValueAnimator a=ValueAnimator.ofFloat(0f,-pw);
            a.setDuration(180); a.setInterpolator(new DecelerateInterpolator(2f));
            a.addUpdateListener(x->drawerPanel.setTranslationX((float)x.getAnimatedValue()));
            a.addListener(new AnimatorListenerAdapter(){
                @Override public void onAnimationEnd(Animator anim){
                    drawerPanel.setVisibility(View.INVISIBLE);
                    if(drawerDim!=null) drawerDim.setVisibility(View.GONE);
                }
            });
            a.start(); drawerOpen=false;
        }
    }

    // =========================================================================
    // D-Pad helpers
    // =========================================================================
    private void pressDir(int d){
        switch(d){case 0:onNativeKeyDown(KEY_UP);break;case 1:onNativeKeyDown(KEY_DOWN);break;
            case 2:onNativeKeyDown(KEY_LEFT);break;case 3:onNativeKeyDown(KEY_RIGHT);break;}
    }
    private void releaseDir(int d){
        switch(d){case 0:onNativeKeyUp(KEY_UP);break;case 1:onNativeKeyUp(KEY_DOWN);break;
            case 2:onNativeKeyUp(KEY_LEFT);break;case 3:onNativeKeyUp(KEY_RIGHT);break;}
    }

    // =========================================================================
    // Wire button to keycode
    // =========================================================================
    private void wire(View v,int kc){
        v.setOnTouchListener((view,e)->{
            switch(e.getAction()){
                case MotionEvent.ACTION_DOWN: onNativeKeyDown(kc); vibrate(14); return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: onNativeKeyUp(kc); return true;
            }
            return false;
        });
    }

    // =========================================================================
    // Save / Load
    // =========================================================================
    private void saveSlot(int i){
        onNativeKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT); onNativeKeyDown(F_KEYS[i]);
        new Handler(android.os.Looper.getMainLooper()).postDelayed(()->{onNativeKeyUp(F_KEYS[i]);onNativeKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT);},80);
    }
    private void loadSlot(int i){
        onNativeKeyDown(F_KEYS[i]);
        new Handler(android.os.Looper.getMainLooper()).postDelayed(()->onNativeKeyUp(F_KEYS[i]),80);
    }

    // =========================================================================
    // Screenshot
    // =========================================================================
    @SuppressWarnings("deprecation")
    private void captureScreenshot(File outFile,ImageView thumbView){
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.O) return;
        android.view.SurfaceView surf=findSdlSurface((ViewGroup)getWindow().getDecorView());
        if(surf==null) return;
        ViewGroup root=(ViewGroup)getWindow().getDecorView().findViewById(android.R.id.content);
        View ov=null;
        for(int i=root.getChildCount()-1;i>=0;i--){View c=root.getChildAt(i);if(c instanceof FrameLayout){ov=c;break;}}
        final View fov=ov;
        if(fov!=null) fov.setVisibility(View.INVISIBLE);
        new Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
            int w=surf.getWidth(),h=surf.getHeight();
            if(w==0||h==0){runOnUiThread(()->{if(fov!=null)fov.setVisibility(View.VISIBLE);});return;}
            Bitmap bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            PixelCopy.request(surf,bmp,result->{
                runOnUiThread(()->{if(fov!=null)fov.setVisibility(View.VISIBLE);});
                if(result==PixelCopy.SUCCESS){
                    Bitmap scaled=Bitmap.createScaledBitmap(bmp,270,152,true); bmp.recycle();
                    try{FileOutputStream fos=new FileOutputStream(outFile);scaled.compress(Bitmap.CompressFormat.JPEG,85,fos);fos.close();}
                    catch(Exception e){e.printStackTrace();}
                    runOnUiThread(()->thumbView.setImageBitmap(scaled));
                } else bmp.recycle();
            },new Handler(android.os.Looper.getMainLooper()));
        },80);
    }

    private android.view.SurfaceView findSdlSurface(ViewGroup p){
        for(int i=0;i<p.getChildCount();i++){
            View c=p.getChildAt(i);
            if(c instanceof android.view.SurfaceView) return (android.view.SurfaceView)c;
            if(c instanceof ViewGroup){android.view.SurfaceView f=findSdlSurface((ViewGroup)c);if(f!=null)return f;}
        }
        return null;
    }

    // =========================================================================
    // Layout helper
    // =========================================================================
    private void addBtn(FrameLayout parent,View v,int w,int h,int gravity,int mL,int mT,int mR,int mB){
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(w,h);
        lp.gravity=gravity; lp.setMargins(mL,mT,mR,mB); parent.addView(v,lp);
    }

    // =========================================================================
    // Button factories
    // =========================================================================
    private TextView makeFaceBtn(String l,int c){
        TextView tv=new TextView(this); tv.setText(l); tv.setTextColor(c); tv.setTextSize(16);
        tv.setTypeface(Typeface.DEFAULT_BOLD); tv.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL); bg.setColor(0xCC1A1A2E); bg.setStroke(dp(2),c);
        tv.setBackground(bg); return tv;
    }
    private TextView makeShoulderBtn(String l){
        TextView tv=new TextView(this); tv.setText(l); tv.setTextColor(Color.WHITE); tv.setTextSize(14);
        tv.setTypeface(Typeface.DEFAULT_BOLD); tv.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xCC0F3460); bg.setStroke(dp(1),0xFF4E9AF1); bg.setCornerRadius(dp(10));
        tv.setBackground(bg); return tv;
    }
    private TextView makePillBtn(String l){
        TextView tv=new TextView(this); tv.setText(l); tv.setTextColor(0xFFB0C4FF); tv.setTextSize(10);
        tv.setTypeface(Typeface.DEFAULT_BOLD); tv.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xCC1A1A2E); bg.setStroke(dp(1),0xFF4E9AF1); bg.setCornerRadius(dp(15));
        tv.setBackground(bg); return tv;
    }
    private TextView makeSlotBtn(String t){
        TextView tv=new TextView(this); tv.setText(t); tv.setTextColor(Color.WHITE); tv.setTextSize(11);
        tv.setTypeface(Typeface.DEFAULT_BOLD); tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(4),dp(6),dp(4),dp(6)); tv.setClickable(true);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xCC1A1A2E); bg.setStroke(dp(1),0xFF4E9AF1); bg.setCornerRadius(dp(8));
        tv.setBackground(bg); return tv;
    }
    private void flashBtn(TextView btn){
        GradientDrawable on=new GradientDrawable(); on.setColor(0xFF4E9AF1); on.setCornerRadius(dp(8));
        btn.setBackground(on); btn.setTextColor(0xFF070B18);
        new Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
            GradientDrawable off=new GradientDrawable();
            off.setColor(0xCC1A1A2E); off.setStroke(dp(1),0xFF4E9AF1); off.setCornerRadius(dp(8));
            btn.setBackground(off); btn.setTextColor(Color.WHITE);
        },200);
    }

    // =========================================================================
    // Utilities
    // =========================================================================
    private int dp(int dp){return Math.round(dp*getResources().getDisplayMetrics().density);}

    @SuppressWarnings("deprecation")
    private void vibrate(long ms){if(vibrator!=null&&vibrator.hasVibrator())vibrator.vibrate(ms);}

    private void writeDataToFile(File file,InputStream in){
        try{FileOutputStream out=new FileOutputStream(file);byte[] buf=new byte[1024];int len;
            while((len=in.read(buf))>0)out.write(buf,0,len);out.close();in.close();}
        catch(IOException e){e.printStackTrace();}
    }

    private boolean isExternalStorageWritable(){
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }
}