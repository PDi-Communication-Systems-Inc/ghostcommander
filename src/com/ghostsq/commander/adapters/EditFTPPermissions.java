package com.ghostsq.commander.adapters;

import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.EditPermissions;
import com.ghostsq.commander.utils.Permissions;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;

public class EditFTPPermissions extends EditPermissions {
    private Uri uri;
    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        
        setTitle( "chmod" );
        View usv = findViewById( R.id.US );
        if( usv != null ) usv.setVisibility( View.GONE );
        View gsv = findViewById( R.id.GS );
        if( gsv != null ) gsv.setVisibility( View.GONE );
        View otv = findViewById( R.id.OT );
        if( otv != null ) otv.setVisibility( View.GONE );
        View obv = findViewById( R.id.owner_block );
        if( obv != null ) obv.setVisibility( View.GONE );
        
        Intent i = getIntent();
        uri = i.getParcelableExtra( "uri" );
    }    
    @Override
    protected void apply( Permissions np ) {
        String cmd = null;
        String a = np.generateChmodStringOct( true );
        if( a != null && a.length() > 0 ) {
            cmd = "CHMOD " + a + " " + file_path;
            FTPEngines.ChmodEngine ce = new FTPEngines.ChmodEngine( this, uri, cmd, null );
            ce.setHandler( new EditPermissions.DoneHandler() );
            ce.start();
        }
    }
}

