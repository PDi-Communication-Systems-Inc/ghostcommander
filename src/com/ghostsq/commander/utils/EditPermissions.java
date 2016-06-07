package com.ghostsq.commander.utils;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapterBase;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

public abstract class EditPermissions extends Activity implements View.OnClickListener {
    public final static String TAG = "EditPermissions";    
    private     CheckBox    urc, uwc, uxc, usc, grc, gwc, gxc, gsc, orc, owc, oxc, otc;
    private     EditText    ue, ge;
    protected   Permissions p;
    protected   String      file_path;
    
    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        requestWindowFeature( Window.FEATURE_LEFT_ICON );
        setContentView( R.layout.perms );
        getWindow().setFeatureDrawableResource( Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_dialer );
        Intent i = getIntent();
        String perm = i.getStringExtra( "perm" );
        file_path   = i.getStringExtra( "path" );
        if( perm == null || file_path == null ) {
            finish();
            return;
        }
        p = new Permissions( perm );
        populateControls();
    }

    private final void populateControls() {
        try {
            View pv = findViewById( R.id.perms );
            if( pv != null ) {
                TextView fnv = (TextView)pv.findViewById( R.id.file_name );
                fnv.setText( file_path );
                {
                    urc = (CheckBox)pv.findViewById( R.id.UR );
                    if( p.ur ) urc.setChecked( true );
                    uwc = (CheckBox)pv.findViewById( R.id.UW );
                    if( p.uw ) uwc.setChecked( true );
                    uxc = (CheckBox)pv.findViewById( R.id.UX );
                    usc = (CheckBox)pv.findViewById( R.id.US );
                    if( p.ux ) uxc.setChecked( true );
                    if( p.us ) usc.setChecked( true );
                } {
                    grc = (CheckBox)pv.findViewById( R.id.GR );
                    if( p.gr ) grc.setChecked( true );
                    gwc = (CheckBox)pv.findViewById( R.id.GW );
                    if( p.gw ) gwc.setChecked( true );
                    gxc = (CheckBox)pv.findViewById( R.id.GX );
                    gsc = (CheckBox)pv.findViewById( R.id.GS );
                    if( p.gx ) gxc.setChecked( true );
                    if( p.gs ) gsc.setChecked( true );
                } {
                    orc = (CheckBox)pv.findViewById( R.id.OR );
                    if( p.or ) orc.setChecked( true );
                    owc = (CheckBox)pv.findViewById( R.id.OW );
                    if( p.ow ) owc.setChecked( true );
                    oxc = (CheckBox)pv.findViewById( R.id.OX );
                    otc = (CheckBox)pv.findViewById( R.id.OT );
                    if( p.ox ) oxc.setChecked( true );
                    if( p.ot ) otc.setChecked( true );
                } {
                    ue = (EditText)pv.findViewById( R.id.user_edit );
                    ge = (EditText)pv.findViewById( R.id.group_edit );
                    ue.setText( p.user );
                    ge.setText( p.group );
                }
                Button ob = (Button)pv.findViewById( R.id.ok );
                Button cb = (Button)pv.findViewById( R.id.cancel );
                ob.setOnClickListener( this );
                cb.setOnClickListener( this );
            }
        } catch( Exception e ) {
            Log.e( TAG, "file: " + file_path, e );
        }
    }
    @Override
    public void onClick( View bv ) {
        try {
            if( bv.getId() == R.id.ok ) {
                Permissions np = new Permissions( null ); 
                np.ur = urc.isChecked(); 
                np.uw = uwc.isChecked(); 
                np.ux = uxc.isChecked(); 
                np.us = usc.isChecked(); 
                np.gr = grc.isChecked(); 
                np.gw = gwc.isChecked(); 
                np.gx = gxc.isChecked(); 
                np.gs = gsc.isChecked(); 
                np.or = orc.isChecked(); 
                np.ow = owc.isChecked(); 
                np.ox = oxc.isChecked(); 
                np.ot = otc.isChecked();
                np.user  = ue.getText().toString();
                np.group = ge.getText().toString();
                apply( np );
            }
            else {
                setResult( RESULT_CANCELED );
                finish();
            }
        } catch( Exception e ) {
            Log.e( TAG, "file: " + file_path, e );
        }
    }
    
    protected abstract void apply( Permissions np );

    protected class DoneHandler extends Handler {
        public DoneHandler() {
        }

        @Override
        public void handleMessage( Message msg ) {
            try {
                if( msg != null && msg.what < 0 ) {
                    Intent in = new Intent( Commander.NOTIFY_ACTION );
                    in.putExtra( Commander.MESSAGE_EXTRA, (Parcelable)msg );
                    EditPermissions.this.setResult( RESULT_OK, in );
                    EditPermissions.this.finish();
                }
            } catch( Exception e ) {
                Log.e( TAG, "", e );
            }
        }
    };    

}
