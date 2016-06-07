package com.ghostsq.commander.root;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.TextViewer;
import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.adapters.Engines;
import com.ghostsq.commander.adapters.FSAdapter;
import com.ghostsq.commander.utils.LsItem;
import com.ghostsq.commander.utils.Permissions;
import com.ghostsq.commander.utils.Utils;
import com.ghostsq.commander.utils.LsItem.LsItemPropComparator;
import com.ghostsq.commander.root.MountsListEngine;
import com.ghostsq.commander.root.MountsListEngine.MountItem;

public class RootAdapter extends CommanderAdapterBase {
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    private final static String TAG = "RootAdapter";
    public static final String DEFAULT_LOC = "root:";
    private final static int CHMOD_CMD = 36793, CMD_CMD = 39716, REBOOT = 43599, RECOVERY = 43394;
    private Uri uri = null;
    private LsItem[] items = null;
    private int attempts = 0;
    private MountsListEngine systemMountReader;
    private String systemMountMode;
    private final static String SYSTEM_PATH = "/system";
    private ContentEngine contentEngine;
    private File tmp_f, dst_f;

    public RootAdapter( Context ctx_ ) {
        super( ctx_, SHOW_ATTR | NARROW_MODE );
    }
    @Override
    public String getScheme() {
        return "root";
    }
    @Override
    public int setMode( int mask, int val ) {
        if( ( mask & ( MODE_WIDTH ) ) == 0 )
            return super.setMode( mask, val );
        return mode;
    }
   
    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case REAL:
        case SZ:
        case MOUNT:
        case REMOUNT:
            return true;
        default: return super.hasFeature( feature );
        }
    }
    
    class ListEngine extends ExecEngine {
        private LsItem[] items_tmp;
        private String pass_back_on_done;
        private Uri src;
        private ArrayList<LsItem>  array;
        private final static String EOL = "_EOL_";
        
        ListEngine( Context ctx, Handler h, Uri src_, String pass_back_on_done_ ) {
        	super( ctx );
        	setHandler( h );
            src = src_;
        	pass_back_on_done = pass_back_on_done_;
        }
        public LsItem[] getItems() {
            return items_tmp;
        }       
        public Uri getUri() {
            return src;
        }
        @Override
        public void run() {
            String msg = null;
            if(	!getList( true ) ) {
                Log.w( TAG, "su failed. let's try just sh" );
                errMsg = null;
                msg = commander.getContext().getString( R.string.no_root );
                if( !getList( false ) )
                    error( commander.getContext().getString( R.string.cant_cd, src.getPath() ) );
            }
            if( errMsg != null && errMsg.indexOf( ".android_secure" ) > 0 ) errMsg = null;
            doneReading( msg, pass_back_on_done );
        }
        private boolean getList( boolean su ) {
            if( !su ) sh = "sh";
            String path = src.getPath();
            if( path == null ) {
                path = SLS;
                src = src.buildUpon().encodedPath( path ).build();
            } else
                path = Utils.mbAddSl( path );
            parentLink = path == null || path.length() == 0 || path.equals( SLS ) ? SLS : "..";
            array = new ArrayList<LsItem>();
            // the option -s is not supported on some releases (1.6)
            String to_execute = "ls " + ( ( mode & MODE_HIDDEN ) != HIDE_MODE ? "-a ":"" ) + "-l " + ExecEngine.prepFileName( path ) + " ; echo " + EOL;
            
            if( !execute( to_execute, false, su ? 5000 : 500 ) ) // 'busybox ls -l' always outs UID/GID as numbers, not names!  
                return false;   

            if( !isStopReq() ) {
                int sz = array != null ? array.size() : 0;
                items_tmp = new LsItem[sz];
                if( sz > 0 ) {
                    array.toArray( items_tmp );
                    LsItem.LsItemPropComparator comp = 
                        items_tmp[0].new LsItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
                    Arrays.sort( items_tmp, comp );
                }
                                
                return true;
            }
            return false;
        }
        @Override
        protected boolean procInput( BufferedReader br ) throws IOException, Exception {
            while( br.ready() ) {
                if( isStopReq() ) break; 
                String ln = br.readLine();
                if( ln == null || ln.startsWith( EOL ) ) break;
                LsItem item = new LsItem( ln );
                if( item.isValid() && !"..".equals( item.getName() ) && !".".equals( item.getName() ) ) {
                    String link_target = item.getLinkTarget();
                    if( Utils.str( link_target ) ) {
                        try {
                            File ltf = new File( link_target );
                            if( ltf.isDirectory() )
                                item.setDirectory();
                        } catch( Throwable e ) {
                        }
                    }
                    array.add( item ); // a problem - if the item is a symlink - how to know is it a dir or a file???
                }
            }
            return array.size() > 0;
       }
    }
    @Override
    protected void onReadComplete() {
        try {
            attempts = 0;
            if( reader instanceof ListEngine ) {
                ListEngine list_engine = (ListEngine)reader;
                items = list_engine.getItems();
                uri = list_engine.getUri();
                numItems = items != null ? items.length + 1 : 1;
                notifyDataSetChanged();
                
                String path = uri.getPath();
                if( path != null && path.startsWith( SYSTEM_PATH ) ) {
                    // know the /system mount state
                    systemMountReader = new MountsListEngine( commander.getContext(), readerHandler, false );
                    systemMountReader.start();
                }
            } else
            if( systemMountReader != null ) {
                MountItem[] mounts = systemMountReader.getItems();
                if( mounts != null ) {
                    boolean remount = systemMountReader.toRemount();
                    systemMountReader = null;
                    for( MountItem m : mounts ) {
                        String mp = m.getMountPoint();
                        if( SYSTEM_PATH.equals( mp ) ) {
                            if( remount ) {
                                RemountEngine re = new RemountEngine( commander.getContext(), simpleHandler, m );
                                re.start();
                            }
                            else
                                systemMountMode = m.getMode();
                            break;
                        }
                    }
                }
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }
    
    @Override
    public String toString() {
        if( uri != null ) {
            if( systemMountMode != null ) {
                String path = uri.getPath();
                try {
                    return uri.buildUpon().fragment( path != null && path.startsWith( SYSTEM_PATH ) ? systemMountMode : null ).build().toString();
                } catch( Exception e ) {}
            }            
            return uri.buildUpon().fragment( " " ).build().toString();
        }
        return "";
    }
    /*
     * CommanderAdapter implementation
     */
    @Override
    public Uri getUri() {
        return uri;
    }
    @Override
    public void setUri( Uri uri_ ) {
        uri = uri_;
    }
    
    @Override
    public boolean readSource( Uri tmp_uri, String pass_back_on_done ) {
        try {
            if( tmp_uri == null )
                tmp_uri = uri;
            if( tmp_uri == null )
                return false;
            uri = tmp_uri;  // since the Superuser application can break the execution,
                            // it's important to keep the uri 
            if( reader != null ) {
                if( attempts++ < 2 ) {
                    commander.showInfo( "Busy..." );
                    return false;
                }
                if( reader.reqStop() ) { // that's not good.
                    Thread.sleep( 500 ); // will it end itself?
                    if( reader.isAlive() ) {
                        Log.e( TAG, "Busy!" );
                        return false;
                    }
                }
            }
            
            notify( Commander.OPERATION_STARTED );
            reader = new ListEngine( commander.getContext(), readerHandler, tmp_uri, pass_back_on_done );
            reader.start();
            return true;
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e );
            e.printStackTrace();
        }
        notify( "Fail", Commander.OPERATION_FAILED );
        return false;
    }
	@Override
	public void reqItemsSize( SparseBooleanArray cis ) {
	    if( uri == null ) return;
        try {
            LsItem[] s_items = bitsToItems( cis );
            if( s_items != null && s_items.length > 0 ) {
                String path = Utils.mbAddSl( uri.getPath() );
                StringBuilder sb = new StringBuilder( 128 );
                sb.append( "stat " );
                for( int i = 0; i < s_items.length; i++ )
                    sb.append( " " ).append( ExecEngine.prepFileName( path + s_items[i].getName() ) );
                sb.append( " ; df" );
                ExecEngine ee = new ExecEngine( ctx, null, sb.toString(), true,  500 );
                ee.setHandler( new Handler() {
                        @Override
                        public void handleMessage( Message msg ) {
                            try {
                                Intent in = new Intent( ctx, TextViewer.class );
                                in.setData( Uri.parse( TextViewer.STRURI ) );
                                if( msg.obj instanceof Bundle )
                                    in.putExtra( TextViewer.STRKEY, ((Bundle)msg.obj).getString( Commander.MESSAGE_STRING ) );
                                commander.issue( in, 0 );
                            } catch( Exception e ) {
                                Log.e( TAG, null, e );
                            }
                        }
                    } );
                ee.start();
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
	}
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        try {
            LsItem[] subItems = bitsToItems( cis );
            if( subItems != null ) {
                Engines.IReciever recipient = null;
                String to_path = null;
            	if( to instanceof FSAdapter || to instanceof RootAdapter ) {
            	    Uri to_uri = to.getUri();
            	    if( to_uri != null )
            	        to_path = to_uri.getPath();
            	    to = null;
            	} else {
                    to_path = createTempDir();
                    recipient = to.getReceiver(); 
            	}
                if( to_path != null ) {
                    notify( Commander.OPERATION_STARTED );
                    commander.startEngine( new CopyFromEngine( commander.getContext(), subItems, to_path, move, recipient ) );
                    return true;
                }
            }
        	notify( "Failed to proceed.", Commander.OPERATION_FAILED );
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e );
        }
        return false;
    }
    
    class CopyFromEngine extends ExecEngine {
        private int counter = 0;
	    private LsItem[] list;
	    private String   dest_folder;
	    private boolean  move;
	    private String   src_base_path;
        private String   uid;
	    CopyFromEngine( Context ctx, LsItem[] list_, String dest, boolean move_, Engines.IReciever recipient_ ) {
	    	super( ctx );
	    	recipient = recipient_;
	        list = list_;
	        dest_folder = dest;
	        move = move_;
	        src_base_path = uri.getPath();
	        if( src_base_path == null || src_base_path.length() == 0 )
	            src_base_path = SLS;
	        else
	        if( src_base_path.charAt( src_base_path.length()-1 ) != SLC )
	            src_base_path += SLS;
	        if( recipient != null )
                try {
        	        PackageManager pm = ctx.getPackageManager();
        	        if( pm != null ) {
        	            ApplicationInfo ai;
                        ai = pm.getApplicationInfo( ctx.getPackageName(), 0 );
                        if( ai != null )
                            uid = "" + ai.uid;
        	        }
        	    } catch( NameNotFoundException e ) {
                    e.printStackTrace();
                }
	    }
	    @Override
	    public void run() {
	        try {
	            boolean ok = execute();
                if( counter > 0 && recipient != null ) {
                    File temp_dir = new File( dest_folder );
                    File[] temp_content = temp_dir.listFiles();
                    String[] paths = new String[temp_content.length];
                    for( int i = 0; i < temp_content.length; i++ )
                        paths[i] = temp_content[i].getAbsolutePath();
                    sendReceiveReq( paths );
                    return;
                }
                if( !ok )
                    counter = 0;
            }
            catch( Exception e ) {
                error( "Exception: " + e );
            }
            sendResult( counter > 0 ? Utils.getOpReport( commander.getContext(), counter, move ? R.string.moved : R.string.copied ) : "" );
        }
       
        @Override
        protected boolean cmdDialog( OutputStreamWriter os, BufferedReader is, BufferedReader es )  { 
            try {
                int num = list.length;
                double conv = 100./(double)num;
                String esc_dest = ExecEngine.prepFileName( dest_folder );
                for( int i = 0; i < num; i++ ) {
                    LsItem f = list[i];
                    if( f == null ) continue;
                    String file_name = f.getName();
                    String full_name = src_base_path + file_name;
                    String cmd = move ? " mv -f" : ( f.isDirectory() ? " cp -a" : " cp -p" );
                    String to_exec = cmd + " " + ExecEngine.prepFileName( full_name ) 
                                         + " " + esc_dest;
                    outCmd( true, to_exec, os );
                    if( procError( es ) ) return false;
                    try {
                        File    dst_file = new File( dest_folder, f.getName() );
                        String  dst_path = ExecEngine.prepFileName( dst_file.getAbsolutePath() ); 
                        Permissions perm = uid != null ? new Permissions( uid, uid, "-rw-rw----" ) :
                                                         new Permissions( f.getAttr() );
                        String chown_cmd = "chown " + perm.generateChownString().append(" ").append( dst_path ).toString();
                        outCmd( uid != null, chown_cmd, os );
                        String chmod_cmd = "chmod " + perm.generateChmodString().append(" ").append( dst_path ).toString();
                        outCmd( true, chmod_cmd, os );
                        procError( es );
                    } catch( Exception e ) {
                        Log.w( TAG, "chmod/chown failed", e );
                    }
                    sendProgress( "'" + file_name + "'", (int)(i * conv) );
                    counter++;
                }
                return true;
            } catch( Exception e ) {
                error( e.getMessage() );
            }
            return false;
	    }
	}
	    
	@Override
	public boolean createFile( String fileURI ) {
		notify( "Operation is not supported.", Commander.OPERATION_FAILED );
		return false;
	}
    @Override
    public void createFolder( String new_name ) {
        if( uri == null ) return;
        MkDirEngine mde = new MkDirEngine( commander.getContext(), simpleHandler, new_name );
        mde.start();
    }
    
    class MkDirEngine extends ExecEngine {
        String new_name, full_name;
        MkDirEngine( Context ctx, Handler h, String new_name_ ) {
            super( ctx );
            setHandler( h );
            new_name = new_name_;
            full_name = uri.getPath() + SLS + new_name;
        }
        
        @Override
        public void run() {
            try {
                String cmd = "mkdir " + ExecEngine.prepFileName( full_name );
                execute( cmd, true, 100 );
            } catch( Exception e ) {
                error( "Exception: " + e );
            }
            if( noErrors() )
                sendRefrReq( new_name );
            else
                sendResult( ctx.getString( R.string.cant_md, full_name ) );
        }
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        try {
        	LsItem[] subItems = bitsToItems( cis );
        	if( subItems != null ) {
        	    notify( Commander.OPERATION_STARTED );
                commander.startEngine( new DelEngine( commander.getContext(), subItems ) );
	            return true;
        	}
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e );
        }
        return false;
    }

    class DelEngine extends ExecEngine {
        private String   src_base_path;
        private LsItem[] mList;
        private int counter = 0;
        
        DelEngine( Context ctx, LsItem[] list ) {
        	super( ctx );
            mList = list;
            src_base_path = uri.getPath();
            if( src_base_path == null || src_base_path.length() == 0 )
                src_base_path = SLS;
            else
            if( src_base_path.charAt( src_base_path.length()-1 ) != SLC )
                src_base_path += SLS;
        }

        @Override
        public void run() {
            if( !execute() )
                counter = 0;
            sendResult( counter > 0 ? Utils.getOpReport( commander.getContext(), counter, R.string.deleted ) : "" );
        }
       
        @Override
        protected boolean cmdDialog( OutputStreamWriter os, BufferedReader is, BufferedReader es ) { 
            try {
                int num = mList.length;
                double conv = 100./num;
                for( int i = 0; i < num; i++ ) {
                    LsItem f = mList[i];
                    String full_name = src_base_path + f.getName();
                    sendProgress( "Deleting " + full_name, (int)(counter * conv) );
                    String to_exec = "rm " + ( f.isDirectory() ? "-r " : "" ) + prepFileName( full_name );
                    outCmd( false, to_exec, os );
                    if( procError( es ) ) return false;
                    counter++;
                }
                return true;
            } catch( Exception e ) {
                error( e.getMessage() );
            }
            return false;
        }
    }
    @Override
    public Uri getItemUri( int position ) {
        if( uri == null ) return null;
        return uri.buildUpon().appendEncodedPath( getItemName( position, false ) ).build();
    }
    @Override
    public String getItemName( int position, boolean full ) {
        if( items != null && position > 0 && position <= items.length ) {
            if( full ) {
                Uri item_uri = getItemUri( position );
                if( item_uri != null )
                    return item_uri.toString();
            }
            else return items[position-1].getName();
        }
        return null;
    }
    @Override
    public void openItem( int position ) {
        if( position == 0 ) { // ..
            if( uri != null && parentLink != SLS ) {
            	String path = uri.getPath();
                int len_ = path.length()-1;
                if( len_ > 0 ) {
	                if( path.charAt( len_ ) == SLC )
	                	path = path.substring( 0, len_ );
	                path = path.substring( 0, path.lastIndexOf( SLC ) );
	                if( path.length() == 0 )
	                	path = SLS;
	                commander.Navigate( uri.buildUpon().path( path ).build(), null, uri.getLastPathSegment() );
                }
            }
            return;
        }
        if( items == null || position < 0 || position > items.length )
            return;
        LsItem item = items[position - 1];
        
        if( item.isDirectory() ) {
        	String cur = uri.getPath();
            if( cur == null || cur.length() == 0 ) 
                cur = SLS;
            else
            	if( cur.charAt( cur.length()-1 ) != SLC )
            		cur += SLS;
            commander.Navigate( uri.buildUpon().appendEncodedPath( item.getName() ).build(), null, null );
        }
        else
            new CmdDialog( ctx, item, this );
    }

    @Override
    public boolean receiveItems( String[] full_names, int move_mode ) {
    	try {
            if( full_names == null || full_names.length == 0 ) {
            	notify( "Nothing to copy", Commander.OPERATION_FAILED );
            	return false;
            }
            notify( Commander.OPERATION_STARTED );
            commander.startEngine( new CopyToEngine( commander.getContext(), full_names, 
                                     ( move_mode & MODE_MOVE ) != 0, uri.getPath(), false ) );
            return true;
		} catch( Exception e ) {
			notify( "Exception: " + e, Commander.OPERATION_FAILED );
		}
		return false;
    }
    
    class CopyToEngine extends ExecEngine {
        private String[] src_full_names;
        private String   dest;
        private boolean move = false;
        private boolean quiet;
        private boolean permByDest = false;
        private int counter = 0;
        
        CopyToEngine( Context ctx, String[] list, boolean move_, String dest_, boolean quiet_ ) {
        	super( ctx );
        	src_full_names = list;
        	dest = dest_;
            move = move_;
            quiet = quiet_;
        }

        public final void setPermByDest() {
            permByDest = true;
        }
        
        @Override
        public void run() {
            if( !execute() )
                counter = 0;
            if( quiet ) {
                if( noErrors() ) {
                    File df = new File( dest );
                    sendRefrReq( df.getName() );
                }
                else
                    sendResult( null );
            }
            else
                sendResult( counter > 0 ? Utils.getOpReport( commander.getContext(), counter, move ? R.string.moved : R.string.copied ) : "" );
        }
       
        @Override
        protected boolean cmdDialog( OutputStreamWriter os, BufferedReader is, BufferedReader es ) { 
            try {
                String cmd = move ? " mv" : " cp -a";
                String esc_dest = prepFileName( dest );
                int num = src_full_names.length;
                double conv = 100./(double)num;
                for( int i = 0; i < num; i++ ) {
                    String full_name = src_full_names[i];
                    if( full_name == null ) continue;
                    File src_file = new File( full_name );
                    File dst_file = new File( dest, src_file.getName() );
                    String esc_dst_fn = prepFileName( dst_file.getAbsolutePath() ); 
                    String esc_src_fn = prepFileName( full_name );
                    LsItem probe_item = null;
                    if( permByDest || !move ) {
                        String probe_fn = permByDest ? esc_dst_fn : esc_src_fn;
                        String ls_cmd = "ls -l " + probe_fn;
                        outCmd( false, ls_cmd, os );
                        String str = null; 
                        while( is.ready() ) {
                            str = is.readLine();
                            if( str != null && str.trim().length() > 0 )
                                Log.v( TAG, ">>>" + str ); 
                        }
                        if( str != null )
                            probe_item = new LsItem( str ); 
                    }                    
                    String to_exec = cmd + " " + esc_src_fn + " " + esc_dest;
                    outCmd( true, to_exec, os );
                    if( procError( es ) ) return false;
                    if( probe_item != null ) {
                        Permissions src_p = new Permissions( probe_item.getAttr() );
                        String chown_cmd = "chown " + src_p.generateChownString().append(" ").append( esc_dst_fn ).toString();
                        outCmd( false, chown_cmd, os );
                        String chmod_cmd = "chmod " + src_p.generateChmodString().append(" ").append( esc_dst_fn ).toString();
                        outCmd( true, chmod_cmd, os );
                    }
                    if( !quiet ) sendProgress( full_name + "   ", (int)(i * conv) );
                    counter++;
                }
                return true;
            } catch( Exception e ) {
                error( e.getMessage() );
            }
            return false;
        }
    }
    
    @Override
    public boolean renameItem( int position, String newName, boolean copy ) {
        if( position <= 0 || position > items.length )
            return false;
        try {
            LsItem from = items[position - 1];
            String[] a = new String[1];
            a[0] = uri.getPath() + SLS + from.getName();
            String to = uri.getPath() + SLS + newName;
            notify( Commander.OPERATION_STARTED );
            if( copy ) {
                // TODO
                return false;
            }
            
            commander.startEngine( new CopyToEngine( commander.getContext(), a, true, to, true ) );
            return true;
        } catch( Exception e ) {
            notify( "Exception: " + e, Commander.OPERATION_FAILED );
        }
        return false;
    }

    /*
     * BaseAdapter implementation
     */

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        item.name = "???";
        {
            if( position == 0 ) {
                item.name = parentLink;
            }
            else {
                if( items != null && position > 0 && position <= items.length ) {
                    LsItem curItem;
                    curItem = items[position - 1];
                    item.dir = curItem.isDirectory();
                    item.name = item.dir ? SLS + curItem.getName() : curItem.getName();
                    String lnk = curItem.getLinkTarget();
                    if( lnk != null ) { 
                        item.name += LsItem.LINK_PTR + lnk;
                        item.icon_id = item.dir ? R.drawable.folder : R.drawable.link;
                    }
                    item.size = curItem.isDirectory() ? -1 : curItem.length();
                    item.date = curItem.getDate();
                    item.attr = curItem.getAttr();

                    if( ".apk".equals( Utils.getFileExt( item.name ) ) ) {
                        try {
                            PackageManager pm = ctx.getPackageManager();
                            String path = Utils.mbAddSl( uri.getPath() );
                            PackageInfo info = pm.getPackageArchiveInfo( path + item.name, 0 );
                            item.setIcon( info != null ? pm.getApplicationIcon( info.packageName ) :
                                                         pm.getDefaultActivityIcon() );
                        }
                        catch( Exception e ) {
                        }
                    }                    
                    
                }
            }
        }
        return item;
    }

    @Override
    protected int getPredictedAttributesLength() {
        return 28;   // "---------- system   system"
    }
    
    private final LsItem[] bitsToItems( SparseBooleanArray cis ) {
    	try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    counter++;
            LsItem[] subItems = new LsItem[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) ) {
                    int k = cis.keyAt( i );
                    if( k > 0 )
                        subItems[j++] = items[ k - 1 ];
                }
            return subItems;
		} catch( Exception e ) {
		    Log.e( TAG, "bitsToNames()'s Exception: " + e );
		}
		return null;
    }
    
    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        try {
            if( acmi.position > 0 )
                menu.add( 0, CHMOD_CMD, 0, R.string.perms_label );
            menu.add( 0, CMD_CMD, 0, commander.getContext().getString( R.string.execute_command ) ); 
            super.populateContextMenu( menu, acmi, num );
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }    

    public final static void populateHomeContextMenu( Context ctx, ContextMenu menu ) {
        menu.add( 0, REBOOT,    0, "Reboot" );
        menu.add( 0, RECOVERY,  0, "Recovery" );
        return;
    }
    
    @Override
    public void doIt( int command_id, SparseBooleanArray cis ) {
        try {
            if( CHMOD_CMD == command_id || CMD_CMD == command_id ) {
                LsItem[] items_todo = bitsToItems( cis );
                boolean selected_one = items_todo != null && items_todo.length > 0 && items_todo[0] != null;
                if( CHMOD_CMD == command_id ) {
                    if( selected_one ) {
                        Intent i = new Intent( ctx, EditRootPermissions.class );
                        i.putExtra( "perm", items_todo[0].getAttr() );
                        i.putExtra( "path", Utils.mbAddSl( uri.getPath() ) + items_todo[0].getName() );
                        commander.issue( i, Commander.ACTIVITY_REQUEST_FOR_NOTIFY_RESULT );
                    }
                    else
                        commander.showError( commander.getContext().getString( R.string.select_some ) );
                }
                else if( CMD_CMD == command_id )
                    new CmdDialog( commander.getContext(), selected_one ? items_todo[0] : null, this );
            } else if( R.id.remount == command_id ) {
                if( reader != null && reader.isAlive() ) {
                    commander.showError( commander.getContext().getString( R.string.busy ) );
                    return;
                }
                systemMountReader = new MountsListEngine( commander.getContext(), readerHandler, true );
                systemMountReader.start();
            } else if( REBOOT == command_id ) {
                execute( "reboot", false );
            } else if( RECOVERY == command_id ) {
                execute( "reboot recovery", false );
            }
        } catch( Exception e ) {
            Log.e( TAG, "Can't do the command " + command_id, e );
        }
    }
    
    public void execute( String command, boolean bb ) {
        String where = uri != null ? uri.getPath() : null;
        ExecEngine ee = new ExecEngine( commander.getContext(), where, command, bb, 500 );
        commander.startEngine( ee );
    }

    public final String getBusyBoxPath() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( ctx );
        return sharedPref.getString( "busybox_path", "busybox" ) + " ";            
    }
    
    public void executeToViewer( String command, boolean bb ) {
        ExecEngine ee = new ExecEngine( ctx, uri.getPath(), command, bb, 500 );
        ee.setHandler( new Handler() {
                @Override
                public void handleMessage( Message msg ) {
                    try {
                        String str = ((Bundle)msg.obj).getString( Commander.MESSAGE_STRING );
                        if( !Utils.str( str ) ) {
                            msg.obj = ""; 
                            commander.notifyMe( msg );
                        }
                        else {
                            Intent in = new Intent( ctx, TextViewer.class );
                            in.setData( Uri.parse( TextViewer.STRURI ) );
                            in.putExtra( TextViewer.STRKEY, str );
                            commander.issue( in, 0 );
                        }
                    } catch( Exception e ) {
                        e.printStackTrace();
                    }
                }
            } );
        ee.start();
    }    
    
    class CmdDialog implements OnClickListener {
        private LsItem   item;
        private RootAdapter owner;
        private EditText ctv;
        private CheckBox bbc;
        CmdDialog( Context c, LsItem item_, RootAdapter owner_ ) {
            try {
                if( uri == null  ) return;
                owner = owner_;
                item = item_;
                LayoutInflater factory = LayoutInflater.from( c );
                View cdv = factory.inflate( R.layout.command, null );
                if( cdv != null ) {
                    bbc = (CheckBox)cdv.findViewById( R.id.use_busybox );
                    ctv = (EditText)cdv.findViewById( R.id.command_text );
                    ctv.setText( item != null ? item.getName() : "" );
                    new AlertDialog.Builder( c )
                        .setTitle( "Run Command" )
                        .setView( cdv )
                        .setPositiveButton( R.string.dialog_ok, this )
                        .setNegativeButton( R.string.dialog_cancel, this )
                        .show();
                }
            } catch( Exception e ) {
                Log.e( TAG, "CmdDialog()", e );
            }
        }
        @Override
        public void onClick( DialogInterface idialog, int whichButton ) {
            if( whichButton == DialogInterface.BUTTON_POSITIVE )
                owner.executeToViewer( ctv.getText().toString(), bbc.isChecked() );
            idialog.dismiss();
        }
    }

    @Override
    protected void reSort() {
        if( items == null || items.length < 1 ) return;
        LsItemPropComparator comp = items[0].new LsItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Arrays.sort( items, comp );
    }
    
    /* --- ContentEngine --- */
    
    class ContentEngine extends Thread {
        private String file_path;
        private InputStream  is = null;
        private OutputStream os = null;
        private boolean open_done = false;
        private boolean may_close = false;
        
        ContentEngine( String file_path_ ) {
            file_path = file_path_;
        }

        protected final String getSuPath() {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( RootAdapter.this.ctx );
            return sharedPref.getString( "su_path", "su" );
        }     
        
        @Override
        public void run() {
            setName( "ContentEngine" );
            OutputStreamWriter osw = null;
            BufferedReader     ebr = null;
            try {
                Process process = Runtime.getRuntime().exec( getSuPath() );
                os = process.getOutputStream();
                ebr = new BufferedReader( new InputStreamReader( process.getErrorStream() ) );
                osw = new OutputStreamWriter( os );
                is = process.getInputStream();
                
                osw.write( "cat " + ExecEngine.prepFileName( file_path ) + "\n" );
                osw.flush();
                for( int i = 0; i < 5; i++ ) {
                    Thread.sleep( 10 );
                    if( is.available() > 0 ) break;
                    //Log.v( TAG, "Waiting the stream starts " + i );
                }
                boolean empty = is.available() <= 0; 
                synchronized( this ) {
                    open_done = true;
                }
                for( int i = 0; i < 4; i++ ) {
                    //Log.v( TAG, "Waiting loop " + i );
                    synchronized( this ) {
                        //Log.v( TAG, "Waiting the stream can be closed " + i );
                        wait( 500 );
                        if( empty ) {
                            //Log.v( TAG, "We know the stream is empty, so won't let other thread waste precious time!" );
                            break;
                        }
                        if( may_close ) {
                            //Log.v( TAG, "Reading finished, now may be closed" );
                            break;
                        }
                        /*
                        try {
                            Log.v( TAG, "Checking is there any data " + i );
                            if( is.available() > 0 ) // there still data
                                i = 0;
                        }
                        catch( IOException e ) {
                            Log.e( TAG, "waiting " + i, e );
                        }
                        */
                    }
                }
                osw.write( "exit\n" );
                osw.flush();
                //Log.v( TAG, "Waitng the process exits" );
                process.waitFor();
                //Log.v( TAG, "The process has exited" );
                if( process.exitValue() != 0 ) {
                    Log.e( TAG, "Exit code " + process.exitValue() );
                }
                if( ebr.ready() ) {
                    String err_str = ebr.readLine();
                    if( err_str.trim().length() > 0 ) {
                        Log.e( TAG, "Error:\n" + err_str );
                    }
                }
            }
            catch( Exception e ) {
                Log.e( TAG, null, e );
            }
            finally {
                try {
                    if( osw != null ) osw.close();
                    if( ebr != null ) ebr.close();
                    if( is  != null ) is.close();
                } catch( IOException e ) {
                    e.printStackTrace();
                }
            }
        }
        
        public synchronized boolean waitUntilOpen() {
            try {
                for( int i = 0; i < 50; i++ ) {
                    if( open_done )
                        return true;
                    wait( 100 ); 
                }
            } catch( InterruptedException e ) {}
            return false;
        }
        public InputStream getInput() {
            return waitUntilOpen() ? is : null;
        }
        public OutputStream getOutput() {
            return waitUntilOpen() ? os : null;
        }
        
        public synchronized void close() {
            may_close = true;
            notify();
        }
        
    }
    
    @Override
    public Item getItem( Uri u ) {
        try {
            ExecEngine ee = new ExecEngine( null, null, "ls -l -d " + u.getPath(), false, 100 );
            ee.start();
            StringBuilder sb = null;
            for( int i = 0; i < 10; i++ )
                sb = ee.getResult();
            if( sb == null ) return null;
            LsItem ls_item = new LsItem( sb.toString() );
            Item item = new Item( ls_item.getName() );
            item.size = ls_item.length();
            item.date = ls_item.getDate();
            item.dir = ls_item.isDirectory();
            return item;
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    public InputStream getContent( Uri u ) {
        try {
            if( u == null ) return null;
            String path = u.getPath();
            contentEngine = new ContentEngine( path );
            contentEngine.start();
            InputStream is = contentEngine.getInput();
            if( is == null ) 
                contentEngine.close();
            return is;
        } catch( Throwable e ) {
            Log.e( TAG, u.toString(), e );
        }
        return null;
    }
    
    @Override
    public OutputStream saveContent( Uri u ) {
        try {
            if( u == null ) return null;
            String path = u.getPath();
            
            dst_f = new File( path );
            File root_f = ctx.getDir( "root", Context.MODE_PRIVATE );
            if( root_f == null )
                return null;
            tmp_f = new File( root_f, dst_f.getName() );
            return new FileOutputStream( tmp_f );
        } catch( Throwable e ) {
            Log.e( TAG, u.toString(), e );
        }
        return null;
    }
    
    
    @Override
    public void closeStream( Closeable s ) {
        if( s instanceof FileOutputStream ) {
            if( tmp_f == null || dst_f == null ) return;
            
            CopyToEngine cte = new CopyToEngine( ctx, new String[] { tmp_f.getAbsolutePath() },
                    true, dst_f.getParent(), true );
            cte.setPermByDest();
            cte.start();
            return;
        }
        if( contentEngine != null ) {
            contentEngine.close();
            contentEngine = null;
        }
    }
}
