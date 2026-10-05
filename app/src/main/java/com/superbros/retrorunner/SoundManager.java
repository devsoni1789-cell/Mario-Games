package com.superbros.retrorunner;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small dependency-free procedural SFX generator. */
final class SoundManager {
    static final int COIN=1, JUMP=2, GROW=3, HURT=4, DIE=5;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private volatile boolean released;
    private final AtomicBoolean playing = new AtomicBoolean(false);

    void play(final int type, boolean muted) {
        if (muted || released || !playing.compareAndSet(false, true)) return;
        exec.execute(() -> {
            if (released) return;
            int rate=22050, ms=type==DIE?220:100, count=rate*ms/1000;
            short[] data=new short[count];
            double base;
            switch(type){
                case COIN: base=880; break;
                case JUMP: base=520; break;
                case GROW: base=660; break;
                case HURT: base=170; break;
                default: base=110;
            }
            for(int i=0;i<count;i++){
                double t=i/(double)rate;
                double f=base*(type==COIN||type==GROW ? 1.0+0.55*t : 1.0-0.45*t);
                double env=Math.min(1.0,i/500.0)*Math.min(1.0,(count-i)/900.0);
                data[i]=(short)(Math.sin(2*Math.PI*f*t)*2600*env);
            }
            AudioTrack track=null;
            try {
                AudioAttributes aa=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
                AudioFormat af=new AudioFormat.Builder().setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
                track=new AudioTrack(aa,af,data.length*2,AudioTrack.MODE_STATIC,
                        AudioTrack.WRITE_BLOCKING);
                track.write(data,0,data.length);
                track.play();
                try { Thread.sleep(ms+30L); } catch(InterruptedException ignored) {}
            } finally { if(track!=null){ try{track.stop();}catch(Exception ignored){} track.release(); } }
        });
    }

    void release(){ released=true; exec.shutdownNow(); }
}
