package com.superbros.retrorunner;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class GameView extends View {
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG), t=new Paint(Paint.ANTI_ALIAS_FLAG);
    List<RectF> platforms=new ArrayList<>(), coins=new ArrayList<>();
    List<Enemy> enemies=new ArrayList<>();
    float px=120, py=280, vx, vy, cam, time;
    boolean left,right,jumping,paused,over,won;
    int lives=3, score=0;
    final float GROUND=430, PW=42, PH=58;

    GameView(Context c){super(c); t.setTypeface(Typeface.MONOSPACE); build();}

    void build(){
        float[][] g={{0,GROUND,1000,40},{1120,GROUND,900,40},{2150,GROUND,1150,40},
                     {3450,GROUND,850,40},{4450,GROUND,1100,40},{5750,GROUND,1600,40}};
        for(float[] a:g) platforms.add(new RectF(a[0],a[1],a[0]+a[2],a[1]+a[3]));
        float[][] q={{480,340,180,32},{760,280,150,32},{1300,340,170,32},{1550,280,160,32},
                     {2350,330,180,32},{2700,270,190,32},{3650,285,180,32},{4700,325,180,32},
                     {5100,255,200,32},{6000,335,180,32},{6400,285,170,32}};
        for(float[] a:q) platforms.add(new RectF(a[0],a[1],a[0]+a[2],a[1]+a[3]));
        int[][] cs={{530,290},{580,290},{815,230},{865,230},{1335,290},{1390,290},
        {1580,230},{1635,230},{2380,280},{2430,280},{2740,220},{2800,220},
        {3690,235},{3750,235},{4740,275},{4800,275},{5150,205},{5210,205},
        {6050,285},{6110,285},{6460,235},{6520,235},{6900,370}};
        for(int[] a:cs) coins.add(new RectF(a[0]-9,a[1]-14,a[0]+9,a[1]+14));
        int[][] es={{680,396,640,930},{1200,396,1140,1500},{1880,396,1700,1980},
        {2260,396,2200,3050},{2920,396,2800,3250},{3550,396,3480,4200},
        {4550,396,4500,5450},{5850,396,5800,6250},{6600,396,6500,7000}};
        for(int[] a:es) enemies.add(new Enemy(a[0],a[1],a[2],a[3]));
    }

    boolean isPlaying(){return !paused&&!over&&!won;}
    void togglePause(){paused=!paused;}

    @Override protected void onDraw(Canvas c){
        float s=getWidth()/960f; c.save(); c.scale(s,s);
        draw(c,960,(int)(getHeight()/s)); c.restore();
        if(isPlaying()){update(Math.min(.033f,(System.nanoTime()%1000000000)/1e9f)); postInvalidateOnAnimation();}
    }

    void draw(Canvas c,int w,int h){
        p.setColor(Color.rgb(105,185,250)); c.drawRect(0,0,w,h,p);
        p.setColor(Color.rgb(75,190,105));
        for(int i=-1;i<9;i++){float x=i*170-(cam*.15f)%170; Path z=new Path();
            z.moveTo(x,430);z.lineTo(x+85,335);z.lineTo(x+170,430);z.close();c.drawPath(z,p);}
        c.save();c.translate(-cam,0);
        for(RectF r:platforms){
            p.setColor(r.top>=GROUND?Color.rgb(135,85,45):Color.rgb(220,150,60));c.drawRect(r,p);
            if(r.top>=GROUND){p.setColor(Color.rgb(55,155,65));c.drawRect(r.left,r.top,r.right,r.top+8,p);}
        }
        for(RectF q:coins)if(q.left>=0){p.setColor(Color.rgb(255,215,25));c.drawOval(q,p);}
        for(Enemy e:enemies)if(e.alive){p.setColor(Color.rgb(125,75,48));c.drawRoundRect(e.x,e.y,e.x+40,e.y+32,10,10,p);}
        drawPlayer(c);
        p.setColor(Color.DKGRAY);c.drawRect(7040,200,7048,GROUND,p);
        p.setColor(Color.rgb(255,80,80));Path f=new Path();f.moveTo(7048,205);f.lineTo(7125,225);f.lineTo(7048,245);f.close();c.drawPath(f,p);
        c.restore();
        p.setColor(Color.argb(150,0,0,0));c.drawRoundRect(10,10,350,58,12,12,p);
        t.setColor(Color.WHITE);t.setTextSize(20);c.drawText("COINS "+score+"   LIVES "+lives+"   WORLD 1-1",22,41,t);
        if(!paused&&!over&&!won) controls(c,w,h); else overlay(c,w,h);
    }

    void drawPlayer(Canvas c){
        p.setColor(Color.rgb(245,140,40));c.drawRect(px+7,py,px+35,py+17,p);
        p.setColor(Color.rgb(25,95,125));c.drawRect(px+5,py+15,px+37,py+43,p);
        p.setColor(Color.rgb(245,205,155));c.drawCircle(px+21,py+15,12,p);
        p.setColor(Color.DKGRAY);c.drawRect(px+4,py+42,px+18,py+58,p);c.drawRect(px+25,py+42,px+39,py+58,p);
    }

    void controls(Canvas c,int w,int h){
        p.setColor(Color.argb(100,0,0,0));c.drawCircle(70,h-70,45,p);c.drawCircle(170,h-70,45,p);c.drawCircle(w-80,h-75,55,p);
        t.setColor(Color.WHITE);t.setTextSize(34);c.drawText("◀",53,h-58,t);c.drawText("▶",153,h-58,t);c.drawText("▲",w-95,h-62,t);
    }

    void overlay(Canvas c,int w,int h){
        p.setColor(Color.argb(195,0,0,0));c.drawRect(0,0,w,h,p);
        t.setTextAlign(Paint.Align.CENTER);t.setColor(Color.WHITE);t.setTextSize(42);
        c.drawText(won?"LEVEL CLEAR!":over?"GAME OVER":"PAUSED",w/2f,h/2f-20,t);
        t.setTextSize(20);c.drawText("Tap to continue",w/2f,h/2f+25,t);t.setTextAlign(Paint.Align.LEFT);
    }

    void update(float dt){
        if(paused||over||won)return;
        float old=py;
        if(left)vx=-230;else if(right)vx=230;else vx*=.80f;
        if(jumping&&grounded()){vy=-560;jumping=false;}
        vy+=1350*dt;px+=vx*dt;py+=vy*dt;px=Math.max(0,Math.min(7150,px));
        if(vy>=0)for(RectF r:platforms)if(px+PW>r.left&&px<r.right&&old+PH<=r.top&&py+PH>=r.top){py=r.top-PH;vy=0;break;}
        if(py>650){lose();return;}
        for(int i=0;i<coins.size();i++)if(coins.get(i).left>=0&&hit(px,py,PW,PH,coins.get(i))){coins.get(i).set(-100,-100,-90,-90);score++;}
        for(Enemy e:enemies)if(e.alive){e.x+=e.dir*70*dt;if(e.x<e.min||e.x>e.max)e.dir*=-1;
            if(hit(px,py,PW,PH,new RectF(e.x,e.y,e.x+40,e.y+32))){if(vy>80&&py+PH<e.y+18){e.alive=false;vy=-340;}else{lose();return;}}}
        if(px>7010)won=true;cam=Math.max(0,Math.min(6190,px-300));
    }

    boolean grounded(){float b=py+PH;for(RectF r:platforms)if(px+PW>r.left&&px<r.right&&Math.abs(b-r.top)<4&&vy>=0)return true;return false;}
    boolean hit(float x,float y,float w,float h,RectF r){return x<r.right&&x+w>r.left&&y<r.bottom&&y+h>r.top;}
    void lose(){if(--lives<=0){over=true;return;}px=120;py=280;vx=vy=0;cam=0;}

    @Override public boolean onTouchEvent(MotionEvent e){
        float s=getWidth()/960f,x=e.getX()/s,y=e.getY()/s,h=getHeight()/s;
        if(e.getAction()==MotionEvent.ACTION_DOWN&&(paused||over||won)){
            if(over||won){lives=3;score=0;px=120;py=280;vx=vy=0;cam=0;for(Enemy n:enemies)n.alive=true;}
            paused=false;over=false;won=false;return true;
        }
        if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){
            left=x<125&&y>h-145;right=x>=125&&x<235&&y>h-145;if(x>850&&y>h-150)jumping=true;return true;
        }
        if(e.getAction()==MotionEvent.ACTION_UP){left=right=false;return true;}return true;
    }

    static class Enemy{float x,y,min,max;int dir=1;boolean alive=true;Enemy(float a,float b,float c,float d){x=a;y=b;min=c;max=d;}}
}
