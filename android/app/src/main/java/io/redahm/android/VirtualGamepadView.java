package io.redahm.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * Touch overlay based on the X360Pad layout. It deliberately has a transparent
 * background so the SDL surface stays visible below the controls.
 */
public final class VirtualGamepadView extends View {
    public interface Listener {
        void onButton(String id, boolean pressed);
        void onAxis(String id, float x, float y);
    }

    private static final int ACCENT = Color.rgb(155, 211, 43);
    private static final int OUTLINE = Color.rgb(210, 215, 212);
    private static final int DIM = Color.rgb(138, 143, 140);
    private static final int PANEL = Color.argb(98, 13, 16, 15);

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<Integer, Control> captured = new HashMap<>();

    private Listener listener;
    private float unit = 1f;
    private final Stick leftStick = new Stick("LS");
    private final Stick rightStick = new Stick("RS");
    private final DPad dpad = new DPad();
    private final Button[] buttons = {
            new Button("A", "A", Color.rgb(63, 163, 74)),
            new Button("B", "B", Color.rgb(176, 27, 46)),
            new Button("X", "X", Color.rgb(59, 76, 184)),
            new Button("Y", "Y", Color.rgb(209, 163, 18)),
            new Button("L3", "L3", Color.rgb(74, 79, 76)),
            new Button("R3", "R3", Color.rgb(74, 79, 76)),
            new Button("VIEW", "□", Color.TRANSPARENT),
            new Button("SHARE", "↑", Color.TRANSPARENT),
            new Button("MENU", "☰", Color.TRANSPARENT),
            new Button("GUIDE", "", Color.TRANSPARENT),
            new Button("LB", "LB", PANEL), new Button("RB", "RB", PANEL),
            new Button("LT", "LT", PANEL), new Button("RT", "RT", PANEL)
    };

    public VirtualGamepadView(Context context) {
        super(context);
        setFocusable(true);
        setClickable(true);
        setBackgroundColor(Color.TRANSPARENT);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    public void setListener(Listener listener) { this.listener = listener; }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        unit = Math.min(w / 1280f, h / 720f);
        float fw = w, fh = h;
        // Shoulder controls
        setRect(button("LT"), fw * .07f, fh * .06f, 180, 92);
        setRect(button("RT"), fw * .93f - 180 * unit, fh * .06f, 180, 92);
        setRect(button("LB"), fw * .09f, fh * .20f, 120, 54);
        setRect(button("RB"), fw * .91f - 120 * unit, fh * .20f, 120, 54);

        leftStick.set(fw * .16f, fh * .66f, 74 * unit);
        rightStick.set(fw * .73f, fh * .80f, 78 * unit);
        dpad.set(fw * .35f, fh * .81f, 64 * unit, 31 * unit);

        circle(button("GUIDE"), fw * .50f, fh * .13f, 48 * unit);
        circle(button("L3"), leftStick.cx, leftStick.cy + 108 * unit, 28 * unit);
        circle(button("R3"), rightStick.cx - 102 * unit, rightStick.cy - 102 * unit, 28 * unit);
        circle(button("VIEW"), fw * .45f, fh * .91f, 23 * unit);
        circle(button("SHARE"), fw * .50f, fh * .87f, 23 * unit);
        circle(button("MENU"), fw * .55f, fh * .91f, 23 * unit);

        float cx = fw * .86f, cy = fh * .59f, spread = 57 * unit, r = 25 * unit;
        circle(button("Y"), cx, cy - spread, r); circle(button("A"), cx, cy + spread, r);
        circle(button("X"), cx - spread, cy, r); circle(button("B"), cx + spread, cy, r);
    }

    private void setRect(Button b, float x, float y, float width, float height) {
        b.rect.set(x, y, x + width * unit, y + height * unit);
        b.radius = 18 * unit;
    }
    private void circle(Button b, float x, float y, float radius) {
        b.cx = x; b.cy = y; b.r = radius;
    }
    private Button button(String id) {
        for (Button b : buttons) if (b.id.equals(id)) return b;
        throw new IllegalArgumentException(id);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (String id : new String[]{"LT", "RT", "LB", "RB"}) drawShoulder(canvas, button(id));
        drawGuide(canvas, button("GUIDE"));
        drawStick(canvas, leftStick); drawStick(canvas, rightStick); drawDpad(canvas);
        for (String id : new String[]{"L3", "R3", "VIEW", "SHARE", "MENU"}) drawSmall(canvas, button(id));
        for (String id : new String[]{"A", "B", "X", "Y"}) drawFace(canvas, button(id));
    }

    private void drawShoulder(Canvas c, Button b) {
        stroke.setStrokeWidth((b.id.endsWith("T") ? 5 : 4) * unit);
        stroke.setColor(b.pressed() ? ACCENT : OUTLINE);
        fill.setColor(b.pressed() ? Color.argb(150, 31, 42, 16) : PANEL);
        c.drawRoundRect(b.rect, b.radius, b.radius, fill); c.drawRoundRect(b.rect, b.radius, b.radius, stroke);
        text.setTextSize((b.id.endsWith("T") ? 35 : 25) * unit); text.setColor(b.pressed() ? ACCENT : OUTLINE);
        c.drawText(b.label, b.rect.centerX(), b.rect.centerY() + text.getTextSize() * .35f, text);
    }

    private void drawGuide(Canvas c, Button b) {
        stroke.setStrokeWidth(8 * unit); stroke.setColor(b.pressed() ? ACCENT : DIM);
        c.drawCircle(b.cx, b.cy, b.r, stroke);
        stroke.setStrokeWidth(15 * unit); stroke.setColor(ACCENT);
        c.drawLine(b.cx - b.r * .38f, b.cy - b.r * .34f, b.cx + b.r * .35f, b.cy + b.r * .34f, stroke);
        c.drawLine(b.cx + b.r * .38f, b.cy - b.r * .34f, b.cx - b.r * .35f, b.cy + b.r * .34f, stroke);
    }

    private void drawStick(Canvas c, Stick s) {
        stroke.setStrokeWidth(4 * unit); stroke.setColor(s.active() ? ACCENT : OUTLINE);
        fill.setColor(PANEL); c.drawCircle(s.cx, s.cy, s.outer, fill); c.drawCircle(s.cx, s.cy, s.outer, stroke);
        float knob = s.outer * .52f;
        float x = s.cx + s.x * (s.outer - knob), y = s.cy + s.y * (s.outer - knob);
        fill.setColor(s.active() ? Color.argb(160, 23, 33, 12) : PANEL);
        c.drawCircle(x, y, knob, fill); c.drawCircle(x, y, knob, stroke);
    }

    private void drawDpad(Canvas c) {
        float x = dpad.cx, y = dpad.cy, s = dpad.size, a = dpad.arm;
        Path p = new Path();
        p.moveTo(x-a,y-s); p.lineTo(x+a,y-s); p.lineTo(x+a,y-a); p.lineTo(x+s,y-a);
        p.lineTo(x+s,y+a); p.lineTo(x+a,y+a); p.lineTo(x+a,y+s); p.lineTo(x-a,y+s);
        p.lineTo(x-a,y+a); p.lineTo(x-s,y+a); p.lineTo(x-s,y-a); p.lineTo(x-a,y-a); p.close();
        fill.setColor(PANEL); c.drawPath(p, fill);
        stroke.setStrokeWidth(5 * unit); stroke.setColor(dpad.any() ? ACCENT : OUTLINE); c.drawPath(p, stroke);
        fill.setColor(Color.argb(150, 35, 48, 16));
        if (dpad.up) c.drawRect(x-a,y-s,x+a,y-a,fill); if (dpad.down) c.drawRect(x-a,y+a,x+a,y+s,fill);
        if (dpad.left) c.drawRect(x-s,y-a,x-a,y+a,fill); if (dpad.right) c.drawRect(x+a,y-a,x+s,y+a,fill);
    }

    private void drawFace(Canvas c, Button b) {
        fill.setColor(b.color); c.drawCircle(b.cx, b.cy, b.r * (b.pressed() ? 1.08f : 1f), fill);
        stroke.setStrokeWidth(3 * unit); stroke.setColor(b.pressed() ? Color.WHITE : OUTLINE);
        c.drawCircle(b.cx, b.cy, b.r * (b.pressed() ? 1.08f : 1f), stroke);
        text.setTextSize(b.r * 1.16f); text.setColor(Color.WHITE);
        c.drawText(b.label, b.cx, b.cy + text.getTextSize() * .35f, text);
    }

    private void drawSmall(Canvas c, Button b) {
        fill.setColor(b.pressed() ? Color.argb(155, 36, 59, 16) : PANEL); c.drawCircle(b.cx, b.cy, b.r, fill);
        stroke.setStrokeWidth(2.5f * unit); stroke.setColor(b.pressed() ? ACCENT : DIM); c.drawCircle(b.cx, b.cy, b.r, stroke);
        text.setTextSize(b.id.equals("MENU") ? b.r * .85f : b.r * .72f); text.setColor(b.pressed() ? ACCENT : OUTLINE);
        c.drawText(b.label, b.cx, b.cy + text.getTextSize() * .35f, text);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: claim(event.getPointerId(event.getActionIndex()), event.getX(event.getActionIndex()), event.getY(event.getActionIndex())); break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) updateCaptured(event.getPointerId(i), event.getX(i), event.getY(i)); break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: release(event.getPointerId(event.getActionIndex())); break;
            case MotionEvent.ACTION_CANCEL: releaseAll(); break;
            default: return true;
        }
        invalidate(); return true;
    }

    private void claim(int pointerId, float x, float y) {
        if (!leftStick.active() && leftStick.hit(x,y)) { leftStick.pointerId = pointerId; captured.put(pointerId, leftStick); updateStick(leftStick,x,y); haptic(); return; }
        if (!rightStick.active() && rightStick.hit(x,y)) { rightStick.pointerId = pointerId; captured.put(pointerId, rightStick); updateStick(rightStick,x,y); haptic(); return; }
        if (dpad.pointerId == -1 && dpad.hit(x,y)) { dpad.pointerId = pointerId; captured.put(pointerId, dpad); updateDpad(x,y); haptic(); return; }
        for (Button b : buttons) if (!b.pressed() && b.hit(x,y)) { b.pointerId = pointerId; captured.put(pointerId,b); emitButton(b.id,true); haptic(); return; }
    }
    private void updateCaptured(int id, float x, float y) {
        Control c = captured.get(id); if (c instanceof Stick) updateStick((Stick)c,x,y); else if (c instanceof DPad) updateDpad(x,y);
    }
    private void release(int id) {
        Control c = captured.remove(id); if (c == null) return;
        if (c instanceof Stick) { Stick s=(Stick)c; s.pointerId=-1; s.x=s.y=0; if(listener!=null) listener.onAxis(s.id,0,0); }
        else if (c instanceof DPad) { dpad.pointerId=-1; setDpad(false,false,false,false); }
        else { Button b=(Button)c; b.pointerId=-1; emitButton(b.id,false); }
    }
    private void releaseAll() { for (Integer id : captured.keySet().toArray(new Integer[0])) release(id); }
    private void updateStick(Stick s, float px, float py) {
        float x=(px-s.cx)/s.outer, y=(py-s.cy)/s.outer, length=(float)Math.sqrt(x*x+y*y);
        if(length>1){x/=length;y/=length;} s.x=x; s.y=y; if(listener!=null) listener.onAxis(s.id,x,y);
    }
    private void updateDpad(float x, float y) {
        float dx=x-dpad.cx, dy=y-dpad.cy, dead=dpad.arm*.45f;
        boolean up=false,down=false,left=false,right=false;
        if(Math.abs(dx)>dead || Math.abs(dy)>dead) { if(Math.abs(dx)>Math.abs(dy)*.45f){right=dx>0;left=dx<0;} if(Math.abs(dy)>Math.abs(dx)*.45f){down=dy>0;up=dy<0;} }
        setDpad(up,down,left,right);
    }
    private void setDpad(boolean up, boolean down, boolean left, boolean right) {
        if(dpad.up!=up) emitButton("DPAD_U",up); if(dpad.down!=down) emitButton("DPAD_D",down);
        if(dpad.left!=left) emitButton("DPAD_L",left); if(dpad.right!=right) emitButton("DPAD_R",right);
        dpad.up=up;dpad.down=down;dpad.left=left;dpad.right=right;
    }
    private void emitButton(String id, boolean pressed) { if(listener!=null) listener.onButton(id,pressed); }
    private void haptic() { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); }

    private interface Control {}
    private static final class Button implements Control {
        final String id,label; final int color; final RectF rect=new RectF(); float cx,cy,r,radius; int pointerId=-1;
        Button(String id,String label,int color){this.id=id;this.label=label;this.color=color;}
        boolean pressed(){return pointerId!=-1;}
        boolean hit(float x,float y){ if(!rect.isEmpty()) return rect.contains(x,y); float dx=x-cx,dy=y-cy; return dx*dx+dy*dy<=r*r*1.5f; }
    }
    private static final class Stick implements Control {
        final String id; float cx,cy,outer,x,y; int pointerId=-1;
        Stick(String id){this.id=id;} void set(float x,float y,float radius){cx=x;cy=y;outer=radius;}
        boolean active(){return pointerId!=-1;} boolean hit(float x,float y){float dx=x-cx,dy=y-cy;return dx*dx+dy*dy<=outer*outer*1.4f;}
    }
    private static final class DPad implements Control {
        float cx,cy,size,arm; int pointerId=-1; boolean up,down,left,right;
        void set(float x,float y,float s,float a){cx=x;cy=y;size=s;arm=a;}
        boolean hit(float x,float y){float dx=Math.abs(x-cx),dy=Math.abs(y-cy);return (dx<=size&&dy<=arm*1.45f)||(dy<=size&&dx<=arm*1.45f);}
        boolean any(){return up||down||left||right;}
    }
}
