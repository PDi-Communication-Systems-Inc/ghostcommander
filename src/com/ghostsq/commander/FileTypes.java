package com.ghostsq.commander;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class FileTypes extends Activity implements OnClickListener,
                                                   RGBPickerDialog.ResultSink   
{
    private static final String TAG = "FileTypes";
    private LayoutInflater    infl;
    private LinearLayout      ctr;
    private ColorsKeeper      ck;
    private Integer           clicked;

    @Override
    public void onCreate( Bundle bundle ) {
        try {
            super.onCreate( bundle );

            ck = new ColorsKeeper( this );
            ck.restore();
            int n = ck.restoreTypeColors();
            setContentView( R.layout.types );
            View b0 = findViewById( R.id.b0 );
            b0.setOnClickListener( this );
            b0.setTag( new Integer( 0 ) );
            TextView s0 = (TextView)findViewById( R.id.s0 );
            s0.setTextColor( ck.fgrColor );
            s0.setBackgroundColor( ck.bgrColor );
            ctr = (LinearLayout)findViewById( R.id.types_container );
            infl = getLayoutInflater();
            for( int i = 1; i <= n; i++ )
                addView( i, ck.ftColors.get( i - 1 ) );
            View antb = findViewById( R.id.add_new_type );
            antb.setOnClickListener( this );
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }
    
    @Override
    protected void onPause() {
        try {
            super.onPause();
            int n = ctr.getChildCount();
            for( int i = 1; i <= n; i++ ) {
                RelativeLayout tl = (RelativeLayout)ctr.getChildAt( i-1 );
                EditText t = (EditText)tl.findViewById( R.id.types );
                if( t != null ) {
                    ColorsKeeper.FileTypeColor ftc = ck.ftColors.get( i-1 );
                    if( ftc != null )
                        ftc.setMasks( t.getText().toString() );
                }
            }
            ck.store();
            ck.storeTypeColors();
            
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }

    @Override
    public void onClick( View b ) {
        try {
            int i = 0;
            int bid = b.getId();
            if( bid == R.id.add_new_type ) {
                i = ck.addTypeColor();
                addView( i, ck.ftColors.get( i - 1 ) );
                return;
            }
            clicked = (Integer)b.getTag();
            if( clicked != null ) {
                int color = clicked == 0 ? ck.fgrColor : ck.ftColors.get( clicked - 1 ).color;
                new RGBPickerDialog( this, this, color, 0 ).show();
            }
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }        
    }

    @Override
    public void colorChanged( int color ) {
        try {
            if( clicked != null ) {
                TextView stv = null;
                if( clicked == 0 ) {
                    ck.fgrColor = color;
                    stv = (TextView)findViewById( R.id.s0 );
                }
                else {
                    ck.ftColors.get( clicked - 1 ).setColor( color );
                    stv = (TextView)ctr.findViewWithTag( clicked );
                }
                if( stv != null )
                    stv.setTextColor( color );
                clicked = null;
            }
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }

    private final boolean addView( int i, ColorsKeeper.FileTypeColor ftc ) {
        try {
            RelativeLayout tl = (RelativeLayout)infl.inflate( R.layout.type, ctr, false );
            View b = tl.findViewById( R.id.b );
            Integer idx = new Integer( i );
            b.setTag( idx );
            b.setOnClickListener( this );
            TextView s = (TextView)tl.findViewById( R.id.s );
            s.setTag( idx );
            s.setTextColor( ftc.color );
            s.setBackgroundColor( ck.bgrColor );
            EditText t = (EditText)tl.findViewById( R.id.types );
            t.setText( ftc.masks );
            ctr.addView( tl );
            return true;
        } catch( Exception e ) {
            Log.e( TAG, ftc.masks, e );
        }
        return false;
    }
}
