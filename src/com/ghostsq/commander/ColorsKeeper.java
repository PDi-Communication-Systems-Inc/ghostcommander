package com.ghostsq.commander;

import java.util.ArrayList;

import com.ghostsq.commander.utils.Utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

public final class ColorsKeeper {
    public  static final String BGR_COLORS = "bgr_color_picker"; 
    public  static final String FGR_COLORS = "fgr_color_picker"; 
    public  static final String SEL_COLORS = "sel_color_picker"; 
    public  static final String SFG_COLORS = "sfg_color_picker"; 
    public  static final String CUR_COLORS = "cur_color_picker";
    public  static final String TTL_COLORS = "ttl_color_picker";
    public  static final String BTN_COLORS = "btn_color_picker";

    private  Context ctx;
    private  SharedPreferences   colorPref = null;
    public   int ttlColor, bgrColor, fgrColor, selColor, sfgColor, curColor, btnColor;
    
    public class FileTypeColor {
        private static final String   TYPES_pref = "types";
        private static final String FGR_COL_pref = "fgr_color";
        public  String  masks;
        public  int     color;
        public  boolean masksDirty = false, colorDirty = false;
        public  FileTypeColor() {
            color = 0;
        }
        public  FileTypeColor( String m, int c ) {
            masks = m;
            color = c;
        }
        public final void setMasks( String m ) {
            masks = m;
            masksDirty = true;
        }
        public final void setColor( int c ) {
            color = c;
            colorDirty = true;
        }
        public final boolean restore( Context ctx, SharedPreferences pref, int i ) {
            try {
                color = colorPref.getInt(  FGR_COL_pref + i, getDefColor( ctx, i ) );
                masks = colorPref.getString( TYPES_pref + i, getDefMasks( i ) );
                return masks != null; 
            }
            catch( Exception e ) {
            }
            return false;
        }
        public final void store( SharedPreferences.Editor editor, int i ) {
            if( colorDirty ) {
                String pref_key = FGR_COL_pref + i;
                editor.putInt( pref_key, color );
            }
            if( masksDirty ) {
                String pref_key = TYPES_pref + i;
                editor.putString( pref_key, masks );
            }
        }
        
        public final String getDefMasks( int i ) {
            String cat = null;
            switch( i ) {
            case 1: return "/*;*/";     // directories
            case 2:    // "*.gif;*.jpg;*.png;*.bmp";
                cat = Utils.C_IMAGE;
                break;
            case 3: // "*.avi;*.mov;*.mp4;*.mpeg";
                cat = Utils.C_VIDEO;
                break;
            case 4: // "*.mp3;*.wav;*.mid*";
                cat = Utils.C_AUDIO;
                break;
            case 5: return "*.htm*;*.xml;*.pdf;*.csv;*.doc*;*.xls*";
            case 6: return "*.apk;*.zip;*.jar;*.rar";
            }
            if( cat != null ) {
                String[] exts = Utils.getExtsByCategory( cat );
                if( exts != null ) {
                    StringBuffer ret_buf = new StringBuffer();
                    boolean fst = true;
                    for( int k = 0; k < exts.length; k++ ) {
                        if( !fst )
                            ret_buf.append( ";" );
                        ret_buf.append( "*" );
                        ret_buf.append( exts[k] );
                        fst = false;
                    }
                    return ret_buf.toString();
                }
            }            
            return null;
        }
        public int getDefColor( Context ctx, int i ) {
            Resources r = ctx.getResources();
            switch( i ) {
            case 1: return r.getColor( R.color.fg1_def );
            case 2: return r.getColor( R.color.fg2_def );
            case 3: return r.getColor( R.color.fg3_def );
            case 4: return r.getColor( R.color.fg4_def );
            case 5: return r.getColor( R.color.fg5_def );
            case 6: return r.getColor( R.color.fg6_def );
            default:return r.getColor( R.color.fgr_def );
            }
        }
    }
    public  ArrayList<FileTypeColor>  ftColors;
    
    public boolean isButtonsDefault() {
        return btnColor == 0x00000000;
    }
    
    public ColorsKeeper( Context ctx_ ) {
        ctx = ctx_;
        Resources  r = ctx.getResources();
        ttlColor = r.getColor( R.color.ttl_def ); 
        bgrColor = r.getColor( R.color.bgr_def );
        fgrColor = r.getColor( R.color.fgr_def );
        selColor = r.getColor( R.color.sel_def );
        sfgColor = r.getColor( R.color.fgr_def );
        btnColor = Prefs.getDefaultColor( ctx, BTN_COLORS, false );
        curColor = 0;
    }
    
    public int getColor( String key ) {
        if( BGR_COLORS.equals( key ) ) return bgrColor;
        if( FGR_COLORS.equals( key ) ) return fgrColor;
        if( SEL_COLORS.equals( key ) ) return selColor;
        if( SFG_COLORS.equals( key ) ) return sfgColor;
        if( CUR_COLORS.equals( key ) ) return curColor;
        if( TTL_COLORS.equals( key ) ) return ttlColor;
        if( BTN_COLORS.equals( key ) ) return btnColor;
        return 0;
    }

    public void setColor( String key, int c ) {
        if( BGR_COLORS.equals( key ) ) bgrColor = c;
        if( FGR_COLORS.equals( key ) ) fgrColor = c;
        if( SEL_COLORS.equals( key ) ) selColor = c;
        if( SFG_COLORS.equals( key ) ) sfgColor = c;
        if( CUR_COLORS.equals( key ) ) curColor = c;
        if( TTL_COLORS.equals( key ) ) ttlColor = c;
        if( BTN_COLORS.equals( key ) ) btnColor = c;
    }
    
    public final void store() {
        colorPref = ctx.getSharedPreferences( Prefs.COLORS_PREFS, Activity.MODE_PRIVATE );
        SharedPreferences.Editor editor = colorPref.edit();
        editor.putInt( BGR_COLORS, bgrColor );
        editor.putInt( FGR_COLORS, fgrColor );
        editor.putInt( CUR_COLORS, curColor );
        editor.putInt( SEL_COLORS, selColor );
        editor.putInt( SFG_COLORS, sfgColor );
        editor.putInt( TTL_COLORS, ttlColor );
        editor.putInt( BTN_COLORS, btnColor );
        editor.commit();
    }

    public final void restore() {
        colorPref = ctx.getSharedPreferences( Prefs.COLORS_PREFS, Activity.MODE_PRIVATE );
        bgrColor = colorPref.getInt( BGR_COLORS, bgrColor );
        fgrColor = colorPref.getInt( FGR_COLORS, fgrColor );
        curColor = colorPref.getInt( CUR_COLORS, curColor );
        selColor = colorPref.getInt( SEL_COLORS, selColor );
        sfgColor = colorPref.getInt( SFG_COLORS, sfgColor );
        ttlColor = colorPref.getInt( TTL_COLORS, ttlColor );
        btnColor = colorPref.getInt( BTN_COLORS, btnColor );
    }
    
    public final void storeTypeColors() {
        colorPref = ctx.getSharedPreferences( Prefs.COLORS_PREFS, Activity.MODE_PRIVATE );
        SharedPreferences.Editor editor = colorPref.edit();
        for( int i = 1; i <= ftColors.size(); i++ ) {
            FileTypeColor ftc = ftColors.get( i - 1 );
            ftc.store( editor, i );
        }
        editor.commit();
    }
    public final int restoreTypeColors() {
        try {
            colorPref = ctx.getSharedPreferences( Prefs.COLORS_PREFS, Activity.MODE_PRIVATE );
            if( ftColors == null  )
                ftColors = new ArrayList<FileTypeColor>( 5 );
            else
                ftColors.clear();
            for( int i = 1; i < 999; i++ ) {
                FileTypeColor ftc = new FileTypeColor();
                if( !ftc.restore( ctx, colorPref, i ) ) break;
                ftColors.add( ftc );
            }
            return ftColors.size();
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return 0;
    }

    public final int addTypeColor() {
        ftColors.add( new FileTypeColor( "", fgrColor ) );
        return ftColors.size();        
    }
}
