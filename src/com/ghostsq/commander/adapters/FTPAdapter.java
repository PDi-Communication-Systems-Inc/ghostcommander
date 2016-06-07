package com.ghostsq.commander.adapters;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.adapters.Engines.IReciever;
import com.ghostsq.commander.adapters.FTPEngines.CalcSizesEngine;
import com.ghostsq.commander.adapters.FTPEngines.CopyFromEngine;
import com.ghostsq.commander.adapters.FTPEngines.ListEngine;
import com.ghostsq.commander.adapters.FTPEngines.RenEngine;
import com.ghostsq.commander.favorites.Favorite;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.LsItem.LsItemPropComparator;
import com.ghostsq.commander.utils.FTP;
import com.ghostsq.commander.utils.LsItem;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.widget.AdapterView;

public class FTPAdapter extends CommanderAdapterBase implements Engines.IReciever {
    private final static String TAG = "FTPAdapter";
    private FTP      ftp;
    private Uri      uri = null;
    private LsItem[] items = null;
    private Timer    heartBeat;
    private boolean  noHeartBeats = false;
    private FTPCredentials theUserPass = null;
    private final static int CHMOD_CMD = 36793;

    public FTPAdapter( Context ctx_ ) {
        super( ctx_ );
        ftp = new FTP();
    }
    @Override
    public void Init( Commander c ) {
        super.Init( c );
    }
    
    @Override
    public String getScheme() {
        return "ftp";
    }

    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case REAL:
            return true;
        default: return super.hasFeature( feature );
        }
    }
    
    @Override
    protected int getPredictedAttributesLength() {
        return 25;
    }
    
    class Noop extends TimerTask {
		@Override
		public void run() {
			if( !noHeartBeats && reader == null && ftp.isLoggedIn() )
				try {
				    //Log.v( TAG, "FTP NOOP" );
                    ftp.heartBeat();
                } catch( InterruptedException e ) {
                    e.printStackTrace();
                }
		}
    }

    @Override
    public void setCredentials( Credentials crd ) {
        theUserPass = crd != null ? new FTPCredentials( crd ) : null;
    }
    @Override
    public Credentials getCredentials() {
        if( theUserPass == null || theUserPass.isNotSet() )
            return null;
        return theUserPass;
    }
        
    @Override
    public boolean readSource( Uri tmp_uri, String pass_back_on_done ) {
        try {
            boolean need_reconnect = false;
            if( tmp_uri != null ) { 
                String new_user_info = tmp_uri.getUserInfo();
                if( uri == null ) 
                    need_reconnect = true;
                else if( !tmp_uri.getHost().equalsIgnoreCase( uri.getHost() ) ) {
                    need_reconnect = true;
                    if( theUserPass != null && !theUserPass.dirty ) theUserPass = null;
                }
                else if( new_user_info != null  ) {
                    if( theUserPass == null )
                        need_reconnect = true;
                    else if( theUserPass != null && !theUserPass.equals( new FTPCredentials( new_user_info ) ) )
                        need_reconnect = true;
                }
                else
                    if( theUserPass != null )
                        need_reconnect = theUserPass.dirty; 
                if( uri != null ) 
                    synchronized( uri ) {
                        setUri( tmp_uri );
                    }
                else
                    setUri( tmp_uri );
            }
            else
                if( uri == null )
                    return false;
            if( reader != null ) { // that's not good.
                Log.w( TAG, "reader's existed!" );
                if( reader.isAlive() ) {
                    Log.e( TAG, "reader's busy!" );
                        return false;      
                }
            }
            if( items == null )
                numItems = 1;
            notify( Commander.OPERATION_STARTED );
            Log.v( TAG, "Creating and starting the reader..." );
            reader = new ListEngine( this, readerHandler, ftp, need_reconnect, pass_back_on_done );
            reader.start();
            
            if( heartBeat == null ) {
                heartBeat = new Timer( "FTP Heartbeat", true );
                heartBeat.schedule( new Noop(), 120000, 40000 );
            }
            return true;
        }
        catch( Exception e ) {
            commander.showError( e.getLocalizedMessage() );
            e.printStackTrace();
        }
        notify( ftp.getLog(), Commander.OPERATION_FAILED );
        return false;
    }
 
    @Override
    protected void onReadComplete() {
        Log.v( TAG, "UI thread finishes the items obtaining. reader=" + reader );
        if( reader instanceof ListEngine ) {
            ListEngine list_engine = (ListEngine)reader;
            items = null;
            parentLink = !Utils.str( list_engine.path ) || list_engine.path.equals( SLS ) ? SLS : PLS;
            if( ( mode & MODE_HIDDEN ) == HIDE_MODE ) {
                LsItem[] tmp_items = list_engine.getItems();
                if( tmp_items != null ) {
                    int cnt = 0;
                    for( int i = 0; i < tmp_items.length; i++ )
                        if( tmp_items[i].getName().charAt( 0 ) != '.' )
                            cnt++;
                    items = new LsItem[cnt];
                    int j = 0;
                    for( int i = 0; i < tmp_items.length; i++ )
                        if( tmp_items[i].getName().charAt( 0 ) != '.' )
                            items[j++] = tmp_items[i];
                }
            }
            else
                items = list_engine.getItems();
            numItems = items != null ? items.length + 1 : 1;
            notifyDataSetChanged();
            if( theUserPass != null )
                theUserPass.dirty = false; 
        }
    }
    
    @Override
    public String toString() {
        if( uri == null )
            return "";
        String ui = uri.getUserInfo();
        if( ui != null && theUserPass == null )
            return Favorite.screenPwd( uri );
        if( theUserPass == null || theUserPass.isNotSet() )
            return uri.toString();
        return Favorite.screenPwd( Utils.getUriWithAuth( uri, theUserPass ) );    
    }
    /*
     * CommanderAdapter implementation
     */
    @Override
    public Uri getUri() {
        return Utils.updateUserInfo( uri, null );
    }

    private final void  setFTPMode( Uri uri_ ) {
        String active_s = uri_.getQueryParameter( "a" );
        boolean a_set = Utils.str( active_s );
        if( ( mode & MODE_CLONE ) == NORMAL_MODE || a_set )
            ftp.setActiveMode( a_set && ( "1".equals( active_s ) || "true".equals( active_s ) || "yes".equals( active_s ) ) );

        String charset_s = uri_.getQueryParameter( "e" );
        boolean e_set = Utils.str( charset_s );
        if( ( mode & MODE_CLONE ) == NORMAL_MODE || e_set ) {
            Charset charset = null;
            try {
                charset = Charset.forName( charset_s );
            } catch( Exception e ) {}
            ftp.setCharset( charset );
        }
    }
    
    @Override
    public void setUri( Uri uri_ ) {
        uri = uri_;
        try {
            setFTPMode( uri );
        } catch( Exception e ) {
            Log.e( TAG,  "Uri: " + uri_, e );
        }  
    }

    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        try {
            super.populateContextMenu( menu, acmi, num );
            if( acmi.position > 0 )
                menu.add( 0, CHMOD_CMD, 0, R.string.permissions );
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }    

    @Override
    public void doIt( int command_id, SparseBooleanArray cis ) {
        try {
            if( CHMOD_CMD == command_id ) {
                LsItem[] items_todo = bitsToItems( cis );
                boolean selected_one = items_todo != null && items_todo.length > 0 && items_todo[0] != null;
                if( selected_one ) {
                    Intent i = new Intent( ctx, EditFTPPermissions.class );
                    i.putExtra( "perm", items_todo[0].getAttr() );
                    i.putExtra( "path", Utils.mbAddSl( uri.getPath() ) + items_todo[0].getName() );
                    i.putExtra( "uri",  Utils.getUriWithAuth( uri, theUserPass ) );
                    commander.issue( i, Commander.ACTIVITY_REQUEST_FOR_NOTIFY_RESULT );
                }
                else
                    commander.showError( commander.getContext().getString( R.string.select_some ) );
            }
        } catch( Exception e ) {
            Log.e( TAG, "Can't do the command " + command_id, e );
        }
    }
    
    @Override
    public void reqItemsSize( SparseBooleanArray cis ) {
        try {
            LsItem[] subItems = bitsToItems( cis );
            notify( Commander.OPERATION_STARTED );
            CalcSizesEngine cse = new FTPEngines.CalcSizesEngine( commander, theUserPass, uri, subItems, ftp.getActiveMode(), ftp.getCharset() );
            commander.startEngine( cse );
        }
        catch(Exception e) {
        }
    }
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        String err_msg = null;
        try {
            LsItem[] subItems = bitsToItems( cis );
            if( subItems == null ) {
                notify( s( R.string.copy_err ), Commander.OPERATION_FAILED );
                return false;
            } 
            if( !checkReadyness() ) return false;
            Engines.IReciever recipient = null;
            File dest = null;
            if( move && to instanceof FTPAdapter && subItems.length == 1 && !subItems[0].isDirectory() ) {
                Uri to_uri = to.getUri();
                if( to_uri.getHost().equalsIgnoreCase( uri.getHost() ) ) {
                    notify( Commander.OPERATION_STARTED );
                    String loc_name = subItems[0].getName();
                    String old_name = Utils.mbAddSl( uri.getPath() ) + loc_name;  
                    String new_name = Utils.mbAddSl( to_uri.getPath() ) + loc_name;
                    RenEngine re = new RenEngine( ctx, theUserPass, uri, old_name, new_name, ftp.getActiveMode(), ftp.getCharset() );
                    commander.startEngine( re );
                    return true;
                }
            } 
            if( to instanceof FSAdapter  ) {
                dest = new File( to.toString() );
                if( !dest.exists() ) dest.mkdirs();
                if( !dest.isDirectory() )
                    throw new RuntimeException( s( R.string.inv_dest ) );
            } else {
                dest = new File( createTempDir() );
                recipient = to.getReceiver(); 
            }
            notify( Commander.OPERATION_STARTED );
            CopyFromEngine cfe = new CopyFromEngine( commander, theUserPass, uri, subItems, dest, move, recipient, ftp.getActiveMode(), ftp.getCharset() );
            commander.startEngine( cfe );
            return true;
        }
        catch( Exception e ) {
            err_msg = e.getLocalizedMessage();
        }
        notify( err_msg, Commander.OPERATION_FAILED );
        return false;
    }
	    
	@Override
	public boolean createFile( String fileURI ) {
		notify( "Operation not supported on a FTP folder.", Commander.OPERATION_FAILED );
		return false;
	}
    @Override
    public void createFolder( String name ) {
        notify( Commander.OPERATION_STARTED );
        commander.startEngine( new MkDirEngine( name ) );
    }

    class MkDirEngine extends Engine {
        private String name;
        
        MkDirEngine( String name_ ) {
            name = name_;
        }
        @Override
        public void run() {
            ftp.clearLog();
            try {
                ftp.makeDir( name );
                sendResult( "" );
                return;
            } catch( Exception e ) {
            }
            error( ctx.getString( R.string.ftp_mkdir_failed, name, ftp.getLog() ) );            
            if( !noErrors() )
                sendResult( "" );
            else
                sendRefrReq( name );
        }
    }    
    
    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        try {
        	if( !checkReadyness() ) return false;
        	LsItem[] subItems = bitsToItems( cis );
        	if( subItems != null ) {
        	    notify( Commander.OPERATION_STARTED );
                commander.startEngine( new FTPEngines.DelEngine( ctx, theUserPass, uri, subItems, ftp.getActiveMode(), ftp.getCharset() ) );
	            return true;
        	}
        }
        catch( Exception e ) {
            commander.showError( e.getLocalizedMessage() );
        }
        return false;
    }

    
    @Override
    public Uri getItemUri( int position ) {
        Uri u = getUri();
        if( u == null ) return null;
        return u.buildUpon().appendEncodedPath( getItemName( position, false ) ).build();
    }
    @Override
    public String getItemName( int position, boolean full ) {
        if( items != null && position > 0 && position <= items.length ) {
            if( full ) {
                String path = toString();
                if( path != null && path.length() > 0 ) {
                    if( path.charAt( path.length() - 1 ) != SLC )
                        path += SLS;
                    return path + items[position-1].getName();
                }
            }
            return items[position-1].getName();
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
	                // passing null instead of credentials keeps the current authentication session
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
            Uri item_uri = uri.buildUpon().appendEncodedPath( item.getName() ).build();
            commander.Navigate( item_uri, null, null );
        }
        else {
            Uri auth_item_uri = getUri().buildUpon().appendEncodedPath( item.getName() ).build();
            commander.Open( auth_item_uri, theUserPass );
        }
    }

    @Override
    public boolean receiveItems( String[] uris, int move_mode ) {
    	try {
            if( uris == null || uris.length == 0 ) {
                notify( s( R.string.copy_err ), Commander.OPERATION_FAILED );
                return false;
            }
            File[] list = Utils.getListOfFiles( uris );
            if( list == null ) {
                notify( "Something wrong with the files", Commander.OPERATION_FAILED );
                return false;
            }
            notify( Commander.OPERATION_STARTED );
            boolean move = ( move_mode & MODE_MOVE ) != 0;
            boolean del_src_dir = ( move_mode & CommanderAdapter.MODE_DEL_SRC_DIR ) != 0;
            commander.startEngine( new FTPEngines.CopyToEngine( ctx, theUserPass, uri, list, move, del_src_dir, ftp.getActiveMode(), ftp.getCharset() ) );
            return true;
		} catch( Exception e ) {
			notify( e.getLocalizedMessage(), Commander.OPERATION_FAILED );
		}
		return false;
    }
    
    @Override
    public boolean renameItem( int position, String new_name, boolean copy ) {
        try {
            if( copy ) {
                notify( s( R.string.not_supported ), Commander.OPERATION_FAILED );
            }
            if( items == null || position <= 0 || position > items.length )
                return false;
            String old_name = getItemName( position, false );
            if( old_name != null ) {
                notify( Commander.OPERATION_STARTED );
                RenEngine re = new RenEngine( ctx, theUserPass, uri, old_name, new_name, ftp.getActiveMode(), ftp.getCharset() );
                commander.startEngine( re );
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return false;
    }
    
	@Override
	public void prepareToDestroy() {
	    if( heartBeat != null ) {
    		heartBeat.cancel();
    		heartBeat.purge();
    		heartBeat = null;
	    }
        super.prepareToDestroy();
        
		new Thread( new Runnable() {
                @Override
                public void run() {
                    ftp.disconnect( false );
                }
            }, "FTP disconnect" ).start();
		items = null;
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
                    LsItem ls_item = items[position - 1];
                    item.dir = ls_item.isDirectory();
                    item.name = item.dir ? SLS + ls_item.getName() : ls_item.getName();
                    
                    String lnk = ls_item.getLinkTarget();
                    if( lnk != null ) { 
                        item.name += LsItem.LINK_PTR + lnk;
                        if( !item.dir ) item.icon_id = R.drawable.link;
                    }
                    item.size = !item.dir || ls_item.length() > 0 ? ls_item.length() : -1;
                    item.date = ls_item.getDate();
                    item.attr = ls_item.getAttr();
                }
            }
        }
        return item;
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
                if( cis.valueAt( i ) )
                	subItems[j++] = items[ cis.keyAt( i ) - 1 ];
            return subItems;
		} catch( Exception e ) {
		    Log.e( TAG, "", e );
		}
		return null;
    }
    private final boolean checkReadyness()   
    {
        if( !ftp.isLoggedIn() ) {
        	notify( s( R.string.ftp_nologin ), Commander.OPERATION_FAILED );
        	return false;
        }
    	return true;
    }

    public static class FTPCredentials extends Credentials {
        public boolean dirty = true;
        public FTPCredentials( String userName, String password ) {
            super( userName, password );
        }
        public FTPCredentials( String newUserInfo ) {
            super( newUserInfo == null ? ":" : newUserInfo );
        }
        public FTPCredentials( Credentials c ) {
            super( c );
        }
        public String getUserName() {
            String u = super.getUserName();
            return u == null || u.length() == 0 ? "anonymous" : u;
        }
        public String getPassword() {
            String u = super.getUserName();
            String p = u == null || u.length() == 0 ? "user@host.com" : super.getPassword();
            return p != null ? p : "";
        }
        public final boolean isNotSet() {
            String u = super.getUserName();
            if( u == null || u.length() == 0 ) return true;
            String p = super.getPassword();
            if( p == null ) return true;
            return false;
        }
    }
    @Override
    protected void reSort() {
        if( items == null || items.length < 1 ) return;
        LsItemPropComparator comp = items[0].new LsItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Arrays.sort( items, comp );
    }

    
    @Override
    public Item getItem( Uri u ) {
        try {
            setFTPMode( u );
            if( theUserPass == null || theUserPass.isNotSet() )
                theUserPass = new FTPCredentials( u.getUserInfo() );
            if( ftp.connectAndLogin( u, theUserPass.getUserName(), theUserPass.getPassword(), false ) > 0 ) {
                List<String> segs = u.getPathSegments();
                if( segs.size() == 0 ) {
                    Item item = new Item( "/" );
                    item.dir = true;
                    return item;
                }
                String prt_path = ""; 
                for( int i = 0; i < segs.size()-1; i++ ) {
                    prt_path += "/" + segs.get( i );
                }
                LsItem[] subItems = ftp.getDirList( prt_path, true );
                if( subItems != null ) {
                    String fn = segs.get( segs.size() - 1 );
                    for( int i = 0; i < subItems.length; i++ ) {
                        LsItem ls_item = subItems[i];
                        String ifn = ls_item.getName();
                        if( fn.equals( ifn ) ) {
                            Item item = new Item( ifn );
                            item.size = ls_item.length();
                            item.date = ls_item.getDate();
                            item.dir = ls_item.isDirectory();
                            return item;
                        }
                    }
                }
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public InputStream getContent( Uri u, long skip ) {
        try {
            if( uri != null && !uri.getHost().equals( u.getHost() ) )
                return null;
            if( theUserPass == null || theUserPass.isNotSet() )
                theUserPass = new FTPCredentials( u.getUserInfo() );
            setFTPMode( u );
            if( ftp.connectAndLogin( u, theUserPass.getUserName(), theUserPass.getPassword(), false ) > 0 ) {
                noHeartBeats = true;
                return ftp.prepRetr( u.getPath(), skip );
            }
        } catch( Exception e ) {
            Log.e( TAG, u.getPath(), e );
        }
        return null;
    }
    @Override
    public OutputStream saveContent( Uri u ) {
        try {
            if( uri != null && !uri.getHost().equals( u.getHost() ) )
                return null;
            if( theUserPass == null || theUserPass.isNotSet() )
                theUserPass = new FTPCredentials( u.getUserInfo() );
            setFTPMode( u );
            if( ftp.connectAndLogin( u, theUserPass.getUserName(), theUserPass.getPassword(), false ) > 0 ) {
                noHeartBeats = true;
                return ftp.prepStore( u.getPath() );
            }
        } catch( Exception e ) {
            Log.e( TAG, u.getPath(), e );
        }
        return null;
    }
    @Override
    public void closeStream( Closeable s ) {
        try {
            noHeartBeats = false;
            if( s != null )
                s.close();
            ftp.doneWithData();
        } catch( IOException e ) {
            e.printStackTrace();
        }
    }
    @Override
    public IReciever getReceiver() {
        return this;
    }
}
