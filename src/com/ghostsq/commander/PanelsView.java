package com.ghostsq.commander;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

public class PanelsView extends ViewGroup {
    private final static String TAG = "PanelsView"; 
    private boolean sxs = false;
    private WindowManager wm;
    private int           panel_width;
    private View          lv, rv, dv, ls, rs;
    
    public PanelsView( Context context ) {
        super( context );
    }

    public  PanelsView( Context context, AttributeSet attrs ) {
        super( context, attrs );
    }

    public void init( WindowManager wm_ ) {
        wm = wm_;
        lv = findViewById( R.id.left_list );
        rv = findViewById( R.id.right_list );
        dv = findViewById( R.id.divider );
        ls = findViewById( R.id.left_stat );
        rs = findViewById( R.id.right_stat );
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics( dm );
        if( dm.density <= 1 ) {
            ls.setVisibility( GONE );
            rs.setVisibility( GONE );
            ls = null;
            rs = null;
        }
    }
    
    public void setMode( boolean sxs_ ) {
        sxs = sxs_;
        //Log.v( TAG, "setMode: " + sxs ); 
        requestLayout();
    }

    @Override
    protected void onMeasure( int widthMeasureSpec, int heightMeasureSpec ) {
        //Log.v( TAG, "w:" + MeasureSpec.toString( widthMeasureSpec ) + " h:" + MeasureSpec.toString( heightMeasureSpec ) );

        int av_h = MeasureSpec.getSize( heightMeasureSpec );
        
        panel_width = wm.getDefaultDisplay().getWidth();
        if( sxs ) {
            panel_width /= 2;
            panel_width -= 1;
        } else
            panel_width -= 4;
        int w_spec = MeasureSpec.makeMeasureSpec( panel_width, MeasureSpec.EXACTLY );
        int h_spec = MeasureSpec.makeMeasureSpec( av_h - 10, MeasureSpec.EXACTLY );
        if( ls != null && rs != null ) {
            ls.measure( w_spec, MeasureSpec.makeMeasureSpec( av_h/10, MeasureSpec.AT_MOST ) );
            rs.measure( w_spec, MeasureSpec.makeMeasureSpec( av_h/10, MeasureSpec.AT_MOST ) );
        }
        lv.measure( w_spec, h_spec );
        dv.measure( MeasureSpec.makeMeasureSpec( 1, MeasureSpec.EXACTLY ), heightMeasureSpec );
        rv.measure( w_spec, h_spec );
        setMeasuredDimension( resolveSize( panel_width * 2 + 1, widthMeasureSpec ),
                              resolveSize( getSuggestedMinimumHeight(), heightMeasureSpec));
    }
    
    @Override
    protected void onLayout( boolean changed, int l, int t, int r, int b ) {
        try {
            //Log.v( TAG, "l:" + l + " t:" + t + " r:" + r + " b:" + b + " ch:" + changed );
            //Log.v( TAG, "rv mw:" + rv.getMeasuredWidth() );
            int stat_h = ls != null && rs != null ? ls.getMeasuredHeight() : 0;
            lv.layout(  l, t, panel_width, b - stat_h );
            if( ls != null )
                ls.layout(  l, b - stat_h, panel_width, b );
            dv.layout(  l + panel_width, t, l + panel_width + 1, b );
            rv.layout(  l + panel_width + 1, t, r, b - stat_h );
            if( rs != null )
                rs.layout(  l + panel_width + 1, b - stat_h, r, b );
        } catch( Exception e ) {
            e.printStackTrace();
        } catch( Error e ) {
            e.printStackTrace();
        }
    }
}
