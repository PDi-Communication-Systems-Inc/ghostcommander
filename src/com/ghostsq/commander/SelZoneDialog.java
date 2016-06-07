package com.ghostsq.commander;

import com.ghostsq.commander.utils.Utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.SeekBar;

public class SelZoneDialog extends AlertDialog implements DialogInterface.OnClickListener,
        OnCheckedChangeListener, SeekBar.OnSeekBarChangeListener {
    
    public interface ResultSink {
        void selZoneChanged( boolean atRight_, int width_ );
    }
    
    private final static String TAG = "SelZone";
    private ResultSink sink; 
    private Context context;
    private LinearLayout layout;
    private CheckBox r_cb;
    private SeekBar  width_seek;
    private int width, sel_color;
    private boolean atRight;

    SelZoneDialog( Context c, ResultSink sink_, boolean atRight_, int width_, int sel_color_ ) {
        super( c );
        context = c;
        sink = sink_;
        width = width_;
        atRight = atRight_;
        sel_color = sel_color_;
        setTitle( c.getString( R.string.selection_zone_setup ) );
        LayoutInflater factory = LayoutInflater.from( c );
        setView( factory.inflate( R.layout.selzone, null ) );
        setButton( BUTTON_POSITIVE, c.getString( R.string.dialog_ok ),     this );
        setButton( BUTTON_NEGATIVE, c.getString( R.string.dialog_cancel ), this );
    }
    
    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        
        layout = (LinearLayout)findViewById( R.id.sel_zone_setup );
        
        r_cb = (CheckBox)findViewById( R.id.sel_onright );
        if( r_cb != null ) {
            r_cb.setOnCheckedChangeListener( this );
            r_cb.setChecked( atRight );
        }
        width_seek = (SeekBar)findViewById( R.id.sel_width );
        if( width_seek != null ) {
            width_seek.setProgressDrawable( createSeekBarDrawable( sel_color ) );
            width_seek.setOnSeekBarChangeListener( this );
            width_seek.setProgress( width );
        }
    }

    private final LayerDrawable createSeekBarDrawable( int fg_color ) {
        LayerDrawable ld = null;
        try {
            Drawable[] list = new Drawable[2];
            final int bg_color = 0xff9d9e9d;
            GradientDrawable bg = Utils.getShadingEx( atRight ? fg_color : bg_color, 0.6f );
            bg.setCornerRadius( 5 );
            list[0] = bg;
            GradientDrawable fg = Utils.getShadingEx( atRight ? bg_color : fg_color, 0.6f );
            fg.setCornerRadius( 5 );
            list[1] = new ClipDrawable( fg, Gravity.LEFT, ClipDrawable.HORIZONTAL );
            ld = new LayerDrawable( list );
            ld.setId( 0, android.R.id.background );
            ld.setId( 1, android.R.id.progress );
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
        return ld;
    }
    
    @Override
    public void onCheckedChanged( CompoundButton buttonView, boolean isChecked ) {
        atRight = isChecked;
        if( width_seek != null ) {
            layout.removeView( width_seek );
            width_seek = new SeekBar( context );
            Drawable sbd = createSeekBarDrawable( sel_color );
            width_seek.setProgressDrawable( sbd );
            width_seek.setOnSeekBarChangeListener( this );
            width_seek.setProgress( width );
            layout.addView( width_seek );
        }
    }

    // SeekBar.OnSeekBarChangeListener methods
    @Override 
    public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
        if( !fromUser ) return;
        width = progress;
    }

    @Override
    public void onStartTrackingTouch( SeekBar seekBar ) {
    }

    @Override
    public void onStopTrackingTouch( SeekBar seekBar ) {
    }
    
    @Override // DialogInterface.OnClickListener
    public void onClick( DialogInterface dialog, int which ) {
        if( which == BUTTON_POSITIVE && sink != null )
            sink.selZoneChanged( atRight, width );
        dismiss();
    }
}
