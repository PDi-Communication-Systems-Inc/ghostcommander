package com.ghostsq.commander.adapters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Arrays;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.adapters.Engines.IReciever;
import com.ghostsq.commander.adapters.FSEngines.AskEngine;
import com.ghostsq.commander.adapters.FSEngines.CalcSizesEngine;
import com.ghostsq.commander.adapters.FSEngines.CopyEngine;
import com.ghostsq.commander.adapters.FSEngines.DeleteEngine;
import com.ghostsq.commander.adapters.FSEngines.ListEngine;
import com.ghostsq.commander.adapters.FileItem;
import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.widget.AdapterView;

public class FSAdapter extends CommanderAdapterBase implements Engines.IReciever {
    private   final static String TAG = "FSAdapter";

    private String     dirName;
    protected FileItem[] items;
    
    ThumbnailsThread tht = null;
    
    public FSAdapter( Context ctx_ ) {
        super( ctx_ );
        dirName = null;
        items = null;
    }

    @Override
    public String getScheme() {
        return "";
    }
    
    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case FS:
        case LOCAL:
        case REAL:
        case SF4:
        case SEARCH:
        case SEND:
            return true;
        default: return super.hasFeature( feature );
        }
    }
    
    @Override
    public String toString() {
        return Utils.mbAddSl( dirName );
    }

    public String getDir() {
        return dirName;
    }

    /*
     * CommanderAdapter implementation
     */

    @Override
    public Uri getUri() {
        try {
            return Uri.parse( Utils.escapePath( toString() ) );
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void setUri( Uri uri ) {
        String schm = uri.getScheme();
        if( Utils.str( schm ) && !"file".equals( schm ) ) return;
        dirName = Utils.mbAddSl( uri.getPath() );
    }
    
    @Override
    public boolean readSource( Uri d, String pass_back_on_done ) {
    	try {
            if( d != null )
                dirName = d.getPath();
            if( dirName == null ) {
                notify( s( R.string.inv_path ) + ": " + ( d == null ? "null" : d.toString() ), Commander.OPERATION_FAILED );
                return false;
            }
            reader = new ListEngine( this, readerHandler, pass_back_on_done );
            reader.start();
            return true;
        } catch( Exception e ) {
            Log.e( TAG, "readSource() excception", e );
        } catch( OutOfMemoryError err ) {
            Log.e( TAG, "Out Of Memory", err );
            notify( s( R.string.oom_err ), Commander.OPERATION_FAILED );
		}
		return false;
    }

    @Override
    protected void onReadComplete() {
        if( reader instanceof ListEngine ) {
            ListEngine le = (ListEngine)reader;
            File dir = le.getDirFile();
            if( dir == null ) return;
            dirName = dir.getAbsolutePath(); 
            items = filesToItems( le.getFiles() );
            parentLink = dir.getParent() == null ? SLS : PLS;
            notifyDataSetChanged();
            startThumbnailCreation();
        }
    }
    
    protected void startThumbnailCreation() {
        if( thumbnail_size_perc > 0 ) {
            //Log.i( TAG, "thumbnails " + thumbnail_size_perc );
            if( tht != null )
                tht.interrupt();
            tht = new ThumbnailsThread( this, new Handler() {
                public void handleMessage( Message msg ) {
                    notifyDataSetChanged();
                } }, dirName, items );
            tht.start();
        }
    }
    
    protected FileItem[] filesToItems( File[] files_ ) {
        int num_files = files_.length;
        int num = num_files;
        boolean hide = ( mode & MODE_HIDDEN ) == HIDE_MODE;
        if( hide ) {
            int cnt = 0;
            for( int i = 0; i < num_files; i++ )
                if( !files_[i].isHidden() ) cnt++;
            num = cnt;
        }
        FileItem[] items_ = new FileItem[num];
        int j = 0;
        for( int i = 0; i < num_files; i++ ) {
            if( !hide || !files_[i].isHidden() ) {
                FileItem file_ex = new FileItem( files_[i] );
                items_[j++] = file_ex;
            }
        }
        reSort( items_ );
        return items_;
    }
    
    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        try {
            if( acmi.position != 0 ) {
                Item item = (Item)getItem( acmi.position );
                if( !item.dir && ".zip".equals( Utils.getFileExt( item.name ) ) ) {
                    menu.add( 0, R.id.open, 0, R.string.open );
                    menu.add( 0, R.id.extract, 0, R.string.extract_zip );
                }
                if( item.dir && num == 1 && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO )
                    menu.add( 0, R.id.rescan_dir, 0, R.string.rescan );
            }
            super.populateContextMenu( menu, acmi, num );
        } catch( Exception e ) {
            Log.e( TAG, "", e );
        }
    }

    @Override
    public void doIt( int command_id, SparseBooleanArray cis ) {
        if( R.id.rescan_dir == command_id ) {
            FileItem[] list = bitsToFilesEx( cis );
            if ( list == null || list.length == 0 ) return;
            
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences( ctx );
            MediaScanEngine mse = new MediaScanEngine( ctx, list[0].f().getAbsoluteFile(), sp.getBoolean( "scan_all", false ) );
            mse.setHandler( new SimpleHandler() );
            commander.startEngine( mse );
        }
    }
    
    @Override
    public void openItem( int position ) {
        if( position == 0 ) {
            if( parentLink == SLS ) 
                commander.Navigate( Uri.parse( HomeAdapter.DEFAULT_LOC ), null, null );
            else {
                if( dirName == null ) return;
                File cur_dir_file = new File( dirName );
                String parent_dir = cur_dir_file.getParent();
                commander.Navigate( Uri.parse( Utils.escapePath( parent_dir != null ? parent_dir : DEFAULT_DIR ) ), null,
                                    cur_dir_file.getName() );
            }
        }
        else {
            File file = items[position - 1].f();
            if( file == null ) return;
            Uri open_uri = Uri.parse( Utils.escapePath( file.getAbsolutePath() ) );
            if( file.isDirectory() )
                commander.Navigate( open_uri, null, null );
            else
                commander.Open( open_uri, null );
        }
    }

    @Override
    public Uri getItemUri( int position ) {
        try {
            String item_name = getItemName( position, true );
            return Uri.parse( Utils.escapePath( item_name ) );
        } catch( Exception e ) {
            Log.e( TAG, "No item in the position " + position );
        }
        return null;
    }
    @Override
    public String getItemName( int position, boolean full ) {
        if( position < 0 || items == null || position > items.length )
            return position == 0 ? parentLink : null;
        if( full )
            return position == 0 ? ( new File( dirName ) ).getParent() : items[position - 1].f().getAbsolutePath();
        else {
            if( position == 0 ) return parentLink;
            FileItem item = items[position - 1];
            String name = item.name;
            if( name != null && item.dir && !(this instanceof FindAdapter) ) {
                return name.replace( "/", "" );
            } else
                return name;
        }
    }
	@Override
	public void reqItemsSize( SparseBooleanArray cis ) {
        try {
        	FileItem[] list = bitsToFilesEx( cis );
    		notify( Commander.OPERATION_STARTED );
    		commander.startEngine( new CalcSizesEngine( this, list ) );
		}
        catch(Exception e) {
		}
	}

	
	@Override
    public boolean renameItem( int position, String newName, boolean copy ) {
        if( position <= 0 || position > items.length )
            return false;
        try {
            if( copy ) {
                // newName could be just name
                notify( Commander.OPERATION_STARTED );
                File[] list = { items[position - 1].f() };
                String dest_name;
                if( newName.indexOf( SLC ) < 0 ) {
                    dest_name = dirName;
                    if( dest_name.charAt( dest_name.length()-1 ) != SLC )
                        dest_name += SLS;
                    dest_name += newName;
                }
                else
                    dest_name = newName;
                commander.startEngine( new CopyEngine( this, list, dest_name, MODE_COPY, true ) );
                return true;
            }
            boolean ok = false;
            File f = items[position - 1].f();
            File new_file = new File( dirName, newName );
            if( new_file.exists() ) {
                if( f.equals( new_file ) ) {
                    commander.showError( s( R.string.rename_err ) );
                    return false;
                }
                String old_ap =        f.getAbsolutePath();
                String new_ap = new_file.getAbsolutePath();
                if( old_ap.equalsIgnoreCase( new_ap ) ) {
                    File tmp_file = new File( dirName, newName + "_TMP_" );
                    ok = f.renameTo( tmp_file );
                    ok = tmp_file.renameTo( new_file );
                } else {
                    AskEngine ae = new AskEngine( this, simpleHandler, ctx.getString( R.string.file_exist, newName ), f, new_file );
                    commander.startEngine( ae );
                    //commander.showError( s( R.string.rename_err ) );
                    return true;
                }
            }
            else
                ok = f.renameTo( new_file );
            if( ok )
                notifyRefr( newName );
            else
                notify( s( R.string.error ), Commander.OPERATION_FAILED );
            return ok;
        }
        catch( SecurityException e ) {
            commander.showError( ctx.getString( R.string.sec_err, e.getMessage() ) );
            return false;
        }
    }
	
    @Override
    public Item getItem( Uri u ) {
        try {
            File f = new File( u.getPath() );
            if( f.exists() ) {
                Item item = new Item( f.getName() );
                item.size = f.length();
                item.date = new Date( f.lastModified() );
                item.dir = f.isDirectory();
                return item;
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }
	
    @Override
    public InputStream getContent( Uri u, long skip ) {
        try {
            String path = u.getPath();
            File f = new File( path );
            if( f.exists() && f.isFile() ) {
                FileInputStream fis = new FileInputStream( f );
                if( skip > 0 )
                    fis.skip( skip );
                return fis;
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    public OutputStream saveContent( Uri u ) {
        if( u != null ) {
            File f = new File( u.getPath() );
            try {
                return new FileOutputStream( f );
            } catch( FileNotFoundException e ) {
                Log.e( TAG, u.getPath(), e );
            }
        }
        return null;
    }
    
	@Override
	public boolean createFile( String fileURI ) {
		try {
			File f = new File( fileURI );
			boolean ok = f.createNewFile();
			notify( null, ok ? Commander.OPERATION_COMPLETED_REFRESH_REQUIRED : Commander.OPERATION_FAILED );
			return ok;     
		} catch( Exception e ) {
		    commander.showError( ctx.getString( R.string.cant_create, fileURI, e.getMessage() ) );
		}
		return false;
	}
    @Override
    public void createFolder( String new_name ) {
        
        try {
            if( (new File( dirName, new_name )).mkdir() ) {
                notifyRefr( new_name );
                return;
            }
        } catch( Exception e ) {
            Log.e( TAG, "createFolder", e );
        }
        notify( ctx.getString( R.string.cant_md, new_name ), Commander.OPERATION_FAILED );
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
    	try {
        	FileItem[] list = bitsToFilesEx( cis );
        	if( list != null ) {
        		notify( Commander.OPERATION_STARTED );
        		commander.startEngine( new DeleteEngine( this, list ) );
        	}
		} catch( Exception e ) {
		    notify( e.getMessage(), Commander.OPERATION_FAILED );
		}
        return false;
    }


    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        boolean ok = to.receiveItems( bitsToNames( cis ), move ? MODE_MOVE : MODE_COPY );
        if( !ok ) notify( Commander.OPERATION_FAILED );
        return ok;
    }

    @Override
    public boolean receiveItems( String[] uris, int move_mode ) {
    	try {
            if( uris == null || uris.length == 0 )
            	return false;
            File dest_file = new File( dirName );
            if( dest_file.exists() ) {
                if( !dest_file.isDirectory() )
                    return false;
            }
            else {
                if( !dest_file.mkdirs() )
                    return false;
            }
            File[] list = Utils.getListOfFiles( uris );
            if( list != null ) {
                notify( Commander.OPERATION_STARTED );
                commander.startEngine( new CopyEngine( this, list, dirName, move_mode, false ) );
	            return true;
            }
		} catch( Exception e ) {
		    e.printStackTrace();
		}
		return false;
    }
    @Override
	public void prepareToDestroy() {
        super.prepareToDestroy();
        if( tht != null )
            tht.interrupt();
		items = null;
	}

    private final FileItem[] bitsToFilesEx( SparseBooleanArray cis ) {
        try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) && cis.keyAt( i ) > 0)
                    counter++;
            FileItem[] res = new FileItem[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) ) {
                    int k = cis.keyAt( i );
                    if( k > 0 )
                        res[j++] = items[ k - 1 ];
                }
            return res;
        } catch( Exception e ) {
            Log.e( TAG, "bitsToFilesEx()", e );
        }
        return null;
    }

    public final File[] bitsToFiles( SparseBooleanArray cis ) {
        try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) && cis.keyAt( i ) > 0)
                    counter++;
            File[] res = new File[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) ) {
                    int k = cis.keyAt( i );
                    if( k > 0 )
                        res[j++] = items[ k - 1 ].f();
                }
            return res;
        } catch( Exception e ) {
            Log.e( TAG, "bitsToFiles()", e );
        }
        return null;
    }

    @Override
    protected int getPredictedAttributesLength() {
        return 10;   // "1024x1024"
    }
    
    /*
     *  ListAdapter implementation
     */

    @Override
    public int getCount() {
        if( items == null )
            return 1;
        return items.length + 1;
    }

    @Override
    public Object getItem( int position ) {
        Item item = null;
        if( position == 0 ) {
            item = new Item();
            item.name = parentLink;
            item.dir = true;
        }
        else {
            if( items != null && position <= items.length ) {
                synchronized( items ) {
                    try {
                        return items[position - 1];
                    } catch( Exception e ) {
                        Log.e( TAG, "getItem(" + position + ")", e );
                    }
                }
            }
            else {
                item = new Item();
                item.name = "???";
            }
        }
        return item;
    }

    @Override
    protected void reSort() {
        if( items == null ) return;
        synchronized( items ) {
            reSort( items );
        }
    }
    public void reSort( FileItem[] items_ ) {
        if( items_ == null ) return;
        ItemComparator comp = new ItemComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Arrays.sort( items_, comp );
    }

    @Override
    public IReciever getReceiver() {
        return this;
    }
}
