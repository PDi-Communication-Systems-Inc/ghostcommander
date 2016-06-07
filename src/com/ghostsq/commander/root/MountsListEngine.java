package com.ghostsq.commander.root;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;

import android.R.bool;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

class MountsListEngine extends ExecEngine {
    
    public class MountItem {
        private static final String TAG = "MountItem"; 
        private String  dev = "", mntp = "", type = "", opts = "", r1 = "", r2 = "";
        public MountItem( String string ) {
            String[] flds = string.split( " " );
            if( flds.length < 4 ) {
                dev = "???";
            }
            if( flds[1].equals( "on" ) && flds[3].equals( "type" ) ) {
                dev  = flds.length > 0 ? flds[0] : "";
                mntp = flds.length > 2 ? flds[2] : ""; 
                type = flds.length > 4 ? flds[4] : ""; 
                opts = flds.length > 5 ? flds[5] : "";
                
                if( opts.length() > 1 && opts.charAt( 0 ) == '(' && opts.charAt( opts.length()-1 ) == ')' )
                    opts = opts.substring( 1, opts.length()-1 );
            } else {
                dev  = flds.length > 0 ? flds[0] : "";
                mntp = flds.length > 1 ? flds[1] : ""; 
                type = flds.length > 2 ? flds[2] : ""; 
                opts = flds.length > 3 ? flds[3] : ""; 
                r1   = flds.length > 4 ? flds[4] : "";
                r2   = flds.length > 5 ? flds[5] : "";
            }
        }
        public boolean isValid() {
            return dev.length() > 0 && mntp.length() > 0;
        }
        public String getName() {
            return dev + " " + mntp;
        }
        public String getOptions() {
            return opts;
        }
        public String getMode() {
            if( opts != null ) {
                try {
                    String[] flds = opts.split( "," );
                    for( String f : flds )
                        if( f.equals( "rw" ) || f.equals( "ro" ) ) return f;
                } catch( Exception e ) {
                    Log.e( TAG, "opts=" + opts, e );
                }
            }
            return null;
        }
        public String getRest() {
            return type + " " + opts + " " + r1 + " " + r2;
        }
        public String getMountPoint() {
            return mntp;
        }
    }
    
    private MountItem[]           items_tmp;
    private ArrayList<MountItem>  array = new ArrayList<MountItem>();
    private String  pass_back_on_done;
    private boolean system, remount;
    MountsListEngine( Context ctx, Handler h, String pass_back_on_done_ ) {
        super( ctx );
        setHandler( h );
        pass_back_on_done = pass_back_on_done_;
        system = false;
    }
    MountsListEngine( Context ctx, Handler h, boolean remount_ ) {    // to return the "/system" mount only
        super( ctx );
        setHandler( h );
        pass_back_on_done = null;
        system = true;
        remount = remount_;
    }
    public final boolean toRemount() {
        return remount;
    }
    public final MountItem[] getItems() {
        return items_tmp;
    }       
    @Override
    public void run() {
        String msg = null;
        if( !getList( true ) ) {
            Log.w( TAG, "su failed. let's try just sh" );
            errMsg = null;
            msg = context.getString( R.string.no_root );
            getList( false );
        }
        doneReading( msg, pass_back_on_done );
    }
    private final boolean getList( boolean su ) {
        if( !su ) sh = "sh";
        if( !execute( "mount", false, 1500 ) ) {
            Log.d( TAG, "Executing mount has failed!" );
            return false;
        }

        int sz = array.size();
        items_tmp = new MountItem[sz];
        if( sz > 0 )
            array.toArray( items_tmp );
        return true;
    }        

    @Override
    protected boolean procInput( BufferedReader br ) throws IOException, Exception {
        while( br.ready() ) {
            if( isStopReq() ) 
                throw new Exception();
            String ln = br.readLine();
            if( ln == null ) break;
            if( ln.length() == 0 ) continue;
            if( system && ln.indexOf( "/system " ) < 0 ) continue;
            MountItem item = new MountItem( ln );
            if( item.isValid() )
                array.add( item );
        }
        return array.size() > 0;
   }
}
