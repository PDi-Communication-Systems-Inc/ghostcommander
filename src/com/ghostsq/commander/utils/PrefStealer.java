package com.ghostsq.commander.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class PrefStealer {
    private final static String TAG = "PrefStealer";
    private Context ctx;
    private File    tmp_pref_file;
    
    public PrefStealer( Context ctx ) {
        this.ctx = ctx;
    }

    public SharedPreferences StealFrom( String package_name ) {
        try {
            File main_f = ctx.getFilesDir().getParentFile();
            String spn = package_name + "_preferences";
            String sp_p = "/shared_prefs/";
            File org = new File( main_f.getParentFile(), package_name + sp_p + spn + ".xml" );
            if( org.exists() ) {
                String tmp_spn = spn + "_" + (int)( 9999 * Math.random() );
                tmp_pref_file = new File( main_f, sp_p + tmp_spn + ".xml" );
                FileInputStream  fis = new FileInputStream( org );
                FileOutputStream fos = new FileOutputStream( tmp_pref_file );
                Utils.copyBytes( fis, fos );
                fis.close();
                fos.close();
                return ctx.getSharedPreferences( tmp_spn, Context.MODE_PRIVATE );
            }
        } catch( IOException e ) {
            Log.e( TAG, package_name, e );
        }
        return null;
    }
    
    public void close() {
        if( tmp_pref_file != null )
            tmp_pref_file.delete();
    }
}
