package com.ghostsq.commander.adapters;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.Panels;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.Utils;

public class ZipAdapter extends CommanderAdapterBase {
    public    static final String TAG = "ZipAdapter";
    protected static final int BLOCK_SIZE = 100000;
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public  Uri          uri = null;
    public  ZipFile      zip = null;
    public  ZipEntry[] items = null;
    private ZipEntry   cachedEntry = null;

    public ZipAdapter( Context ctx_ ) {
        super( ctx_ );
        parentLink = PLS;
    }
    @Override
    public String getScheme() {
        return "zip";
    }
    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case REAL:
            return true;
        case F4:
        case SZ:
            return false;
        default: return super.hasFeature( feature );
        }
    }
    @Override
    public boolean readSource( Uri tmp_uri, String pass_back_on_done ) {
        try {
            if( tmp_uri != null )
                uri = tmp_uri;
            if( uri == null )
                return false;
            if( reader != null ) { // that's not good.
                if( reader.isAlive() ) {
                    commander.showInfo( ctx.getString( R.string.busy ) );
                    reader.interrupt();
                    Thread.sleep( 500 );      
                    if( reader.isAlive() ) 
                        return false;      
                }
            }
            Log.v( TAG, "reading " + uri );
            notify( Commander.OPERATION_STARTED );
            reader = new ListEngine( readerHandler, pass_back_on_done );
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
    class EnumEngine extends Engine {

        protected EnumEngine() {
        }
        protected EnumEngine( Handler h ) {
            super.setHandler( h );
        }
        protected final ZipEntry[] GetFolderList( String fld_path ) {
            if( zip == null ) return null;
            if( fld_path == null ) fld_path = ""; 
            else
                if( fld_path.length() > 0 && fld_path.charAt( 0 ) == SLC ) 
                    fld_path = fld_path.substring( 1 );                                 
            int fld_path_len = fld_path.length();
            if( fld_path_len > 0 && fld_path.charAt( fld_path_len - 1 ) != SLC ) { 
                fld_path = fld_path + SLC;
                fld_path_len++;
            }
            Enumeration<? extends ZipEntry> entries = zip.entries();
            if( entries == null )
                return null;
            ArrayList<ZipEntry> array = new ArrayList<ZipEntry>();
            while( entries.hasMoreElements() ) {
                if( isStopReq() ) return null;
                ZipEntry e = entries.nextElement();
                if( e != null ) {
                    String entry_name = fixName( e );
                    //Log.v( TAG, "Found an Entry: " + entry_name );
                    if( entry_name == null || fld_path.compareToIgnoreCase(entry_name) == 0 ) 
                        continue;
                    /* There are at least two kinds of zips - with dedicated folder entry and without one.
                     * The code below should process both.
                     * Do not change until you fully understand how it works.
                     */
                    if( fld_path.regionMatches( true, 0, entry_name, 0, fld_path_len ) ) {
                        int sl_pos = entry_name.indexOf( SLC, fld_path_len );
                        if( sl_pos > 0 ) {
                            String sub_dir = entry_name.substring( fld_path_len, sl_pos+1 );
                            int    sub_dir_len = sub_dir.length();
                            boolean not_yet = true;
                            for( int i = 0; i < array.size(); i++ ) {
                                String a_name = fixName( array.get( i ) );
                                if( a_name.regionMatches( fld_path_len, sub_dir, 0, sub_dir_len ) ) {
                                    not_yet = false;
                                    break;
                                }
                            }
                            if( not_yet ) {  // a folder
                                ZipEntry sur_fld = new ZipEntry( entry_name.substring( 0, sl_pos+1 ) );
                                byte[] eb = { 1, 2 };
                                sur_fld.setExtra( eb );
                                array.add( sur_fld );
                            }
                        }
                        else
                            array.add( e ); // a leaf
                    }
                }
            }
            return array.toArray( new ZipEntry[array.size()] );
        }
    }    
    class ListEngine extends EnumEngine {
        private ZipEntry[] items_tmp = null;
        public  String pass_back_on_done;
        ListEngine( Handler h, String pass_back_on_done_ ) {
        	super( h );
        	pass_back_on_done = pass_back_on_done_;
        }
        public ZipEntry[] getItems() {
            return items_tmp;
        }       
        @Override
        public void run() {
            String zip_path = null;
            try {
            	if( uri != null ) {
            	    zip_path = uri.getPath(); 
                	if( zip_path != null ) {
                  	    zip = new ZipFile( zip_path );
                    	String cur_path = null;
                    	try {
                    	    cur_path = uri.getFragment();
                    	}
                    	catch( NullPointerException e ) {
                    	    // it happens only when the Uri is built by Uri.Builder
                    	    Log.e( TAG, "uri.getFragment()", e );
                    	}
                	    items_tmp = GetFolderList( cur_path );
                	    if( items_tmp != null ) { 
                            ZipItemPropComparator comp = new ZipItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
                            Arrays.sort( items_tmp, comp );
                            sendProgress( null, Commander.OPERATION_COMPLETED, pass_back_on_done );
                            return;
                	    }
                	}
                }
            }
            catch( ZipException e ) {
                Log.e( TAG, zip_path, e );
                sendProgress( ctx.getString( R.string.cant_open ), Commander.OPERATION_FAILED, pass_back_on_done );
                return;
            }
            catch( Exception e ) {
                Log.e( TAG, zip_path, e );
                sendProgress( e.getLocalizedMessage(), Commander.OPERATION_FAILED, pass_back_on_done );
                return;
            }
            finally {
            	super.run();
            }
            sendProgress( ctx.getString( R.string.cant_open ), Commander.OPERATION_FAILED, pass_back_on_done );
        }
    }
    @Override
    protected void onReadComplete() {
        if( reader instanceof ListEngine ) {
            ListEngine list_engine = (ListEngine)reader;
            ZipEntry[] tmp_items = list_engine.getItems();
            if( tmp_items != null && ( mode & MODE_HIDDEN ) == HIDE_MODE ) {
                int cnt = 0;
                for( int i = 0; i < tmp_items.length; i++ )
                    if( tmp_items[i].getName().charAt( 0 ) != '.' )
                        cnt++;
                items = new ZipEntry[cnt];
                int j = 0;
                for( int i = 0; i < tmp_items.length; i++ )
                    if( tmp_items[i].getName().charAt( 0 ) != '.' )
                        items[j++] = tmp_items[i]; 
            }
            else
                items = tmp_items;
            numItems = items != null ? items.length + 1 : 1; 
            notifyDataSetChanged();
        }
    }
    
    @Override
    public String toString() {
        return uri != null ? Uri.decode( uri.toString() ) : "";
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
	public void reqItemsSize( SparseBooleanArray cis ) {
		notify( "Not supported.", Commander.OPERATION_FAILED );
	}
	
    public boolean unpackZip( File zip_file ) {   // to the same folder
        try {
            if( !checkReadyness() ) return false;
            zip = new ZipFile( zip_file );
            notify( Commander.OPERATION_STARTED );
            commander.startEngine( new CopyFromEngine( zip_file.getParentFile() ) );
            return true;
        } catch( Exception e ) {
            notify( "Exception: " + e.getMessage(), Commander.OPERATION_FAILED );
        }
        return false;
    }
    
	
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        try {
            if( zip == null )
                throw new RuntimeException( "Invalid ZIP" );
            ZipEntry[] subItems = bitsToItems( cis );
            if( subItems == null ) 
                throw new RuntimeException( "Nothing to extract" );
            if( !checkReadyness() ) return false;
            Engines.IReciever recipient = null;
            File dest = null;
            if( to instanceof FSAdapter  ) {
                dest = new File( to.toString() );
                if( !dest.exists() ) dest.mkdirs();
                if( !dest.isDirectory() )
                    throw new RuntimeException( ctx.getString( R.string.inv_dest ) );
            } else {
                dest = new File( createTempDir() );
                recipient = to.getReceiver(); 
            }
            notify( Commander.OPERATION_STARTED );
            commander.startEngine( new CopyFromEngine( subItems, dest, recipient ) );
            return true;
        }
        catch( Exception e ) {
            commander.showError( "Exception: " + e.getMessage() );
        }
        return false;
    }

    class CopyFromEngine extends EnumEngine 
    {
	    private File       dest_folder;
	    private ZipEntry[] mList = null;
	    private String     base_pfx;
	    private int        base_len; 

	    CopyFromEngine( ZipEntry[] list, File dest, Engines.IReciever recipient_ ) {
	        recipient = recipient_;    // member of a superclass
	    	mList = list;
	        dest_folder = dest;
            try {
                base_pfx = uri.getFragment();
                if( base_pfx == null )
                    base_pfx = "";
                base_len = base_pfx.length(); 
            }
            catch( NullPointerException e ) {
                Log.e( TAG, "", e );
            }
	    }
	    
        CopyFromEngine( File dest ) {
            dest_folder = dest;
        }
	    
	    @Override
	    public void run() {
	        sendProgress( ZipAdapter.this.ctx.getString( R.string.wait ), 1, 1 );
	        synchronized( ZipAdapter.this ) {
	            if( mList == null ) {
	                mList = GetFolderList( "" );
	                if( mList == null ) {
	                    sendProgress( ctx.getString( R.string.cant_open ), Commander.OPERATION_FAILED );
	                    return;
	                }
	            }
    	    	int total = copyFiles( mList, "" );
                if( recipient != null ) {
                    sendReceiveReq( dest_folder );
                    return;
                }
    			sendResult( Utils.getOpReport( ctx, total, R.string.unpacked ) );
	        }
	        super.run();
	    }
	    private final int copyFiles( ZipEntry[] list, String path ) {
	        int counter = 0;
	        try {
	            long dir_size = 0, byte_count = 0;
	            for( int i = 0; i < list.length; i++ ) {
                    ZipEntry f = list[i];	            
                    if( !f.isDirectory() )
                        dir_size += f.getSize();
	            }
	            double conv = 100./(double)dir_size;
	        	for( int i = 0; i < list.length; i++ ) {
	        		ZipEntry entry = list[i];
	        		if( entry == null ) continue;
	        		String entry_name_fixed = fixName( entry );
	        		if( entry_name_fixed == null ) continue;
        		    String file_name = new File( entry_name_fixed ).getName();
        		    File   dest_file = new File( dest_folder, path + file_name );
        			String rel_name = entry_name_fixed.substring( base_len );
        			
        			if( entry.isDirectory() ) {
        				if( !dest_file.mkdir() ) {
        					if( !dest_file.exists() || !dest_file.isDirectory() ) {
	        					errMsg = "Can't create folder \"" + dest_file.getAbsolutePath() + "\"";
	        					break;
        					}
        				}
        				ZipEntry[] subItems = GetFolderList( entry_name_fixed );
	                    if( subItems == null ) {
	                    	errMsg = "Failed to get the file list of the subfolder '" + rel_name + "'.\n";
	                    	break;
	                    }
        				counter += copyFiles( subItems, rel_name );
        				if( errMsg != null ) break;
        			}
        			else {
                        if( dest_file.exists()  ) {
                            int res = askOnFileExist( ctx.getString( R.string.file_exist, dest_file.getAbsolutePath() ), commander );
                            if( res == Commander.ABORT ) break;
                            if( res == Commander.SKIP )  continue;
                            if( res == Commander.REPLACE ) {
                                if( !dest_file.delete() ) {
                                    error( ctx.getString( R.string.cant_del, dest_file.getAbsoluteFile() ) );
                                    break;
                                }
                            }
                        }
        				InputStream in = zip.getInputStream( entry );
        				FileOutputStream out = new FileOutputStream( dest_file );
        	            byte buf[] = new byte[BLOCK_SIZE];
        	            int  n = 0;
        	            int  so_far = (int)(byte_count * conv);
        	            
        	            int fnl = rel_name.length();
        	            String unp_msg = ctx.getString( R.string.unpacking, 
        	                    fnl > CUT_LEN ? "\u2026" + rel_name.substring( fnl - CUT_LEN ) : rel_name ); 
        	            while( true ) {
        	                n = in.read( buf );
        	                if( n < 0 ) break;
        	                out.write( buf, 0, n );
        	                byte_count += n;
        	                sendProgress( unp_msg, so_far, (int)(byte_count * conv) );
                            if( stop || isInterrupted() ) {
                                in.close();
                                out.close();
                                dest_file.delete();
                                errMsg = "File '" + dest_file.getName() + "' was not completed, delete.";
                                break;
                            }
        	            }
                        in.close();
                        out.close();
        			}
                    final int GINGERBREAD = 9;
                    if( android.os.Build.VERSION.SDK_INT >= GINGERBREAD )
                        ForwardCompat.setFullPermissions( dest_file );
                    long entry_time = entry.getTime();
                    if( entry_time > 0 )
                        dest_file.setLastModified( entry_time );
        			        			
                    if( stop || isInterrupted() ) {
                        error( ctx.getString( R.string.canceled ) );
                        break;
                    }
                    if( i >= list.length-1 )
                        sendProgress( ctx.getString( R.string.unpacked_p, rel_name ), (int)(byte_count * conv) );
        			counter++;
	        	}
	    	}
			catch( Exception e ) {
				Log.e( TAG, "copyFiles()", e );
				error( "Exception: " + e.getMessage() );
			}
	        return counter;
	    }
	}
	    
	@Override
	public boolean createFile( String fileURI ) {
		notify( "Operation not supported", Commander.OPERATION_FAILED );
		return false;
	}
    @Override
    public void createFolder( String string ) {
        notify( "Not supported", Commander.OPERATION_FAILED );
    }

    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        try {
        	if( !checkReadyness() ) return false;
        	ZipEntry[] to_delete = bitsToItems( cis );
        	if( to_delete != null && zip != null && uri != null ) {
        	    notify( Commander.OPERATION_STARTED );
                commander.startEngine( new DelEngine( new File( uri.getPath() ), to_delete ) );
	            return true;
        	}
        }
        catch( Exception e ) {
            Log.e( TAG, "deleteItems()", e );
        }
        notify( null, Commander.OPERATION_FAILED );
        return false;
    }

    class DelEngine extends Engine {
        private String[]   flat_names_to_delete = null;
        private File       zipFile;
        DelEngine( File zipFile_, ZipEntry[] list ) {
            zipFile = zipFile_;
            flat_names_to_delete = new String[list.length];
            for( int i=0; i < list.length; i++ ) {
                ZipEntry z = list[i];
                flat_names_to_delete[i] = z.getName().replace( "/", "" );
            }
        }
        @Override
        public void run() {
            if( zip == null ) return;
            sendProgress( ZipAdapter.this.ctx.getString( R.string.wait ), 1, 1 );
            synchronized( ZipAdapter.this ) {
                Init( null );
                File old_file = new File( zipFile.getAbsolutePath() + "_tmp_" + ( new Date() ).getSeconds() + ".zip" );
                try {
                    ZipFile zf = new ZipFile( zipFile );
                    int removed = 0, processed = 0, num_entries = zf.size();
                    final String del = ctx.getString( R.string.deleting_a );

                    if( !zipFile.renameTo( old_file ) ) {
                        error( "could not rename the file " + zipFile.getAbsolutePath() + " to " + old_file.getAbsolutePath() );
                    } else {
                        ZipInputStream  zin = new ZipInputStream(  new FileInputStream( old_file ) );
                        ZipOutputStream out = new ZipOutputStream( new FileOutputStream( zipFile ) );

                        byte[] buf = new byte[BLOCK_SIZE];
                        ZipEntry entry = zin.getNextEntry();
                        while( entry != null ) {
                            if( isStopReq() )
                                break;
                            String name = entry.getName();
                            String flat_name = name.replace( "/", "" );
                            boolean spare_this = true;
                            
                            for( int i=0; i < flat_names_to_delete.length; i++ ) {
                                if( isStopReq() )
                                    break;
                                if( flat_name.equals( flat_names_to_delete[i] ) ) {
                                    spare_this = false;
                                    removed++;
                                    break;
                                }
                            }
                            if( spare_this ) {
                                int pp = ++processed * 100 / num_entries;
                                long total_size = entry.getSize(), bytes_saved = 0;
                                // Add ZIP entry to output stream.
                                out.putNextEntry( new ZipEntry( name ) );
                                // Transfer bytes from the ZIP file to the output file
                                int len;
                                while( ( len = zin.read( buf ) ) > 0 ) {
                                    if( isStopReq() )
                                        break;
                                    out.write( buf, 0, len );
                                    bytes_saved += len;
                                    sendProgress( del, pp, (int)( bytes_saved * 100 / total_size ) );
                                }
                            }
                            entry = zin.getNextEntry();
                        }
                        // Close the streams        
                        zin.close();
                        try {
                            out.close();
                        } catch( Exception e ) {
                            Log.e( TAG, "DelEngine.run()->out.close()", e );
                        }
                        if( isStopReq() ) {
                            zipFile.delete();
                            old_file.renameTo( zipFile );
                            processed = 0;
                            error( s( R.string.interrupted ) );
                        } else {
                            old_file.delete();
                            zip = null;
                            sendResult( Utils.getOpReport( ctx, removed, R.string.deleted ) );
                            return;
                        }
                    }
                } catch( Exception e ) {
                    error( e.getMessage() );
                }
                sendResult( Utils.getOpReport( ctx, 0, R.string.deleted ) );
                super.run();
            }
        }
    }
    
    @Override
    public Uri getItemUri( int position ) {
        if( uri == null ) return null;
        if( items == null || position > items.length ) return null;
        return uri.buildUpon().encodedFragment( fixName( items[position-1] ) ).build();
    }
    
    @Override
    public String getItemName( int position, boolean full ) {
        if( items != null && position > 0 && position <= items.length ) {
            if( full ) {
                if( uri != null ) {
                    Uri item_uri = getItemUri( position );
                    if( item_uri != null )
                        return item_uri.toString();
                }
            }
            else
                return new File( fixName( items[position-1] ) ).getName();
        }
        return null;
    }
    @Override
    public void openItem( int position ) {
        if( position == 0 ) { // ..
            if( uri == null ) return;
        	String cur = null; 
        	try {
                cur = uri.getFragment();
            } catch( Exception e ) {
            }
        	if( cur == null || cur.length() == 0 ||
        	                 ( cur.length() == 1 && cur.charAt( 0 ) == SLC ) ) {
        	    File zip_file = new File( uri.getPath() );
        	    String parent_dir = Utils.escapePath( zip_file.getParent() );
        	    commander.Navigate( Uri.parse( parent_dir != null ? parent_dir : Panels.DEFAULT_LOC ), null, 
        	            zip_file.getName() );
        	}
        	else {
        	    File cur_f = new File( cur );
        	    String parent_dir = cur_f.getParent();
        	    commander.Navigate( uri.buildUpon().fragment( parent_dir != null ? parent_dir : "" ).build(), null, cur_f.getName() );
        	}
            return;
        }
        if( items == null || position < 0 || position > items.length )
            return;
        ZipEntry item = items[position - 1];
        
        if( item.isDirectory() ) {
            /*
            String cur = null;    
            try {
                cur = uri.getFragment();
            }
            catch( NullPointerException e ) {}
        	if( cur == null ) 
        	    cur = "";
        	else
        	    if( cur.length() == 0 || cur.charAt( cur.length()-1 ) != SLC )
        	        cur += SLS;
        	*/
            commander.Navigate( uri.buildUpon().fragment( fixName( item ) ).build(), null, null );
        } else {
            commander.Open( uri.buildUpon().fragment( fixName( item ) ).build(), null );
        }
    }

    @Override
    public boolean receiveItems( String[] uris, int move_mode ) {
    	try {
    		if( !checkReadyness() ) return false;
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
            
            zip = null;
            items = null;
            
            commander.startEngine( new CopyToEngine( list, new File( uri.getPath() ), uri.getFragment(), move_mode ) );
            return true;
		} catch( Exception e ) {
			notify( "Exception: " + e.getMessage(), Commander.OPERATION_FAILED );
		}
		return false;
    }

    public boolean createZip( File[] list, String zip_fn ) {
        try {
            if( !checkReadyness() ) return false;
            notify( Commander.OPERATION_STARTED );
            commander.startEngine( new CopyToEngine( list, new File( zip_fn ) ) );
            return true;
        } catch( Exception e ) {
            notify( "Exception: " + e.getMessage(), Commander.OPERATION_FAILED );
        }
        return false;
    }
    
    class CopyToEngine extends Engine {
        private File[]  topList; 
        private int     basePathLen;
        private File    zipFile;
        private String  destPath;
        private long    totalSize = 0;
        private boolean newZip = false;
        private boolean move = false;
        private boolean del_src_dir = false;
        private String  prep;
        
        /**
         *  Add files to existing zip 
         */
        CopyToEngine( File[] list, File zip_file, String dest_sub, int move_mode_ ) {
            topList = list;
            zipFile = zip_file;
            if( dest_sub != null )
                destPath = dest_sub.endsWith( SLS ) ? dest_sub : dest_sub + SLS;
            else
                destPath = "";
            basePathLen = list.length > 0 ? list[0].getParent().length() + 1 : 0;
            move = ( move_mode_ & MODE_MOVE ) != 0;
            del_src_dir = ( move_mode_ & CommanderAdapter.MODE_DEL_SRC_DIR ) != 0;
        }
        /**
         *  Create a new shiny ZIP 
         */
        CopyToEngine( File[] list, File zip_file ) {  
            topList = list;
            zipFile = zip_file;
            destPath = "";
            basePathLen = list.length > 0 ? list[0].getParent().length() + 1 : 0;
            newZip = true;
            prep = ZipAdapter.this.ctx.getString( R.string.preparing );
        }
        @Override
        public void run() {
            int num_files = 0;
            try {
                sendProgress( prep, 1, 1 );
                synchronized( ZipAdapter.this ) {
                    Init( null );
                    ArrayList<File> full_list = new ArrayList<File>( topList.length );
                    totalSize = addToList( topList, full_list );
                    sendProgress( prep, 2, 2 );
                    num_files = addFilesToZip( full_list );
                    if( del_src_dir ) {
                        File src_dir = topList[0].getParentFile();
                        if( src_dir != null )
                            src_dir.delete();
                    }
                }
            } catch( Exception e ) {
                error( "Exception: " + e.getMessage() );
            }
    		sendResult( Utils.getOpReport( ctx, num_files, R.string.packed ) );
            super.run();
        }
        // adds files to the global full_list, and returns the total size 
        private final long addToList( File[] sub_list, ArrayList<File> full_list ) {
            long total_size = 0;
            try {
                for( int i = 0; i < sub_list.length; i++ ) {
                    if( stop || isInterrupted() ) {
                        errMsg = "Canceled";
                        break;
                    }
                    File f = sub_list[i];
                    if( f != null && f.exists() ) {
                        if( f.isFile() ) {
                            total_size += f.length();
                            full_list.add( f );
                        }
                        else
                        if( f.isDirectory() ) {
                            long dir_sz = addToList( f.listFiles(), full_list );
                            if( errMsg != null ) break;
                            if( dir_sz == 0 )
                                full_list.add( f );
                            else
                                total_size += dir_sz; 
                        }
                    }
                }
            }
            catch( Exception e ) {
                Log.e( TAG, "addToList()", e );
                errMsg = "Exception: " + e.getMessage();
            }
            return total_size;
        }
                
        // the following method was based on the one from http://snippets.dzone.com/posts/show/3468
        private final int addFilesToZip( ArrayList<File> files ) throws IOException {
            File old_file = null;
            try {
                byte[] buf = new byte[BLOCK_SIZE];
                ZipOutputStream out;
                if( newZip ) {
                   out = new ZipOutputStream( new FileOutputStream( zipFile ) );
                }
                else {
                   ZipFile zf = new ZipFile( zipFile );
                   int  num_entries = zf.size();
                   long total_size = zipFile.length(), bytes_saved = 0;

                   old_file = new File( zipFile.getAbsolutePath() + "_tmp_" + (new Date()).getSeconds() + ".zip" );
                   if( !zipFile.renameTo( old_file ) )
                       throw new RuntimeException("could not rename the file " + zipFile.getAbsolutePath() + " to " + old_file.getAbsolutePath() );
                   ZipInputStream  zin = new ZipInputStream(  new FileInputStream( old_file ) );
                   out = new ZipOutputStream( new FileOutputStream( zipFile ) );
                   
                   int e_i = 0, pp;
                   
                   ZipEntry entry = zin.getNextEntry();
                   while( entry != null ) {
                       if( isStopReq() ) break;
                       pp = e_i++ * 100 / num_entries;
                       sendProgress( prep, pp, 0 );
                       String name = entry.getName();  // in this case the name is not corrupted! no need to fix
                       boolean notInFiles = true;
                       for( File f : files ) {
                           if( isStopReq() ) break;
                           String f_path = f.getAbsolutePath();
                           if( f_path.regionMatches( true, basePathLen, name, 0, name.length() ) ) {
                               notInFiles = false;
                               break;
                           }
                       }
                       if( notInFiles ) {
                           // Add ZIP entry to output stream.
                           out.putNextEntry( new ZipEntry( name ) );
                           // Transfer bytes from old ZIP file to the output file
                           int len;
                           while( (len = zin.read( buf )) > 0 ) {
                               if( isStopReq() ) break;
                               out.write(buf, 0, len);
                               bytes_saved += len;
                               sendProgress( prep, pp, (int)(bytes_saved * 100 / total_size) );
                           }
                       }
                       entry = zin.getNextEntry();
                   }
                   // Close the streams        
                   zin.close();

                   if( isStopReq() ) {
                       out.close();
                       zipFile.delete();
                       old_file.renameTo( zipFile );
                       return 0;
                   }
                 }           
                 double conv = PERC/(double)totalSize;
                 long   byte_count = 0;
                 // Compress the files
                 int i;
                 for( i = 0; i < files.size(); i++ ) {
                       if( isStopReq() ) break;
                       File f = files.get( i );
                       // Add ZIP entry to output stream.
                       String fn = f.getAbsolutePath();
                       String rfn = destPath + fn.substring( basePathLen );
                       if( f.isDirectory() ) {
                           out.putNextEntry( new ZipEntry( rfn + SLS ) );
                       }
                       else {
                           ZipEntry ze = new ZipEntry( rfn );
                           ze.setTime( f.lastModified() );
                           out.putNextEntry( ze );
                           // Transfer bytes from the file to the ZIP file
                           int fnl = fn.length();
                           String pack_s = ctx.getString( R.string.packing, 
                                   fnl > CUT_LEN ? "\u2026" + fn.substring( fnl - CUT_LEN ) : fn );
                           InputStream in = new FileInputStream( f );
                           int len;
                           int  so_far = (int)(byte_count * conv);
                           while( (len = in.read( buf )) > 0 ) {
                               if( isStopReq() ) break;
                               out.write(buf, 0, len);
                               byte_count += len;
                               sendProgress( pack_s, so_far, (int)(byte_count * conv) );
                           }
                           // Complete the entry
                           in.close();
                       }
                       out.closeEntry();
                       //Log.v( TAG, "Packed: " + rfn );
                       if( move )
                           f.delete();
                 }
                 // Complete the ZIP file
                 out.close();
                 if( isStopReq() ) {
                       zipFile.delete();
                       if( !newZip ) 
                           old_file.renameTo( zipFile );
                       return 0;
                 }
                 if( !newZip ) 
                      old_file.delete();
                 return i;
            }
            catch( Exception e ) {
                error( e.getMessage() );
                e.printStackTrace();
                if( !newZip ) {
                    zipFile.delete();
                    if( !newZip && old_file != null ) 
                        old_file.renameTo( zipFile );
                }
                return 0;
            }
       }
    }
    @Override
    public boolean renameItem( int position, String new_name, boolean copy ) {
        ZipEntry to_rename = items[position-1];
        if( to_rename != null && zip != null && Utils.str( new_name ) ) {
            notify( Commander.OPERATION_STARTED );
            Engine eng = new RenameEngine( new File( uri.getPath() ), to_rename, new_name, copy );
            commander.startEngine( eng );
            return true;
        }
        return false;
    }

    class RenameEngine extends Engine {
        private File       zipFile;
        private ZipEntry   ren_entry;
        private String     new_name;
        private boolean    copy = false;
        RenameEngine( File zipFile, ZipEntry ren_entry, String new_name, boolean copy ) {
            this.zipFile = zipFile;
            this.ren_entry = ren_entry;
            this.new_name = new_name;
            this.copy = copy;
        }
        @Override
        public void run() {
            if( zip == null ) return;
            sendProgress( ZipAdapter.this.ctx.getString( R.string.wait ), 1, 1 );
            synchronized( ZipAdapter.this ) {
                Init( null );
                File old_file = new File( zipFile.getAbsolutePath() + "_tmp_" + ( new Date() ).getSeconds() + ".zip" );
                try {
                    ZipFile zf = new ZipFile( zipFile );
                    int processed = 0, num_entries = zf.size();
                    String old_flat_name = ren_entry.getName().replace( "/", "" );
                    final String report_str = ctx.getString( R.string.packing, zf.getName() );

                    if( !zipFile.renameTo( old_file ) ) {
                        error( "could not rename the file " + zipFile.getAbsolutePath() + " to " + old_file.getAbsolutePath() );
                    } else {
                        ZipInputStream  zin = new ZipInputStream(  new FileInputStream( old_file ) );
                        ZipOutputStream out = new ZipOutputStream( new FileOutputStream( zipFile ) );

                        byte[] buf = new byte[BLOCK_SIZE];
                        ZipEntry entry = zin.getNextEntry();
                        while( entry != null ) {
                            if( isStopReq() )
                                break;
                            String name = entry.getName();
                            String flat_name = name.replace( "/", "" );
                            if( old_flat_name.equals( flat_name ) ) {
                                int sl_pos = name.lastIndexOf( '/' );
                                if( sl_pos >= 0 )
                                    name = name.substring( 0, sl_pos+1 ) + this.new_name;
                                else
                                    name = this.new_name;
                            }
                            int pp = ++processed * 100 / num_entries;
                            // Add ZIP entry to output stream.
                            out.putNextEntry( new ZipEntry( name ) );
                            // Transfer bytes from the ZIP file to the output file
                            int len;
                            long total_size = entry.getSize(), bytes_saved = 0;
                            while( ( len = zin.read( buf ) ) > 0 ) {
                                if( isStopReq() )
                                    break;
                                out.write( buf, 0, len );
                                bytes_saved += len;
                                sendProgress( report_str, pp, (int)( bytes_saved * 100 / total_size ) );
                            }
                            entry = zin.getNextEntry();
                        }
                        // Close the streams        
                        zin.close();
                        try {
                            out.close();
                        } catch( Exception e ) {
                            Log.e( TAG, "", e );
                        }
                        if( isStopReq() ) {
                            zipFile.delete();
                            old_file.renameTo( zipFile );
                            processed = 0;
                            error( s( R.string.interrupted ) );
                        } else {
                            old_file.delete();
                            zip = null;
                        }
                    }
                } catch( Exception e ) {
                    error( e.getMessage() );
                }
                sendResult( ctx.getString( R.string.done ) );
                super.run();
            }
        }
    }
    
    
	@Override
	public void prepareToDestroy() {
	    super.prepareToDestroy();
		items = null;
	}

    /*
     * BaseAdapter implementation
     */

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        item.name = "";
        {
            if( position == 0 ) {
                item.name = parentLink;
            }
            else {
                if( items != null && position > 0 && position <= items.length ) {
                    ZipEntry zip_entry = items[position - 1];
                    item.dir = zip_entry.isDirectory();
                    String name = fixName( zip_entry );
                    
                    int lsp = name.lastIndexOf( SLC, item.dir ? name.length() - 2 : name.length() );
                    item.name = lsp > 0 ? name.substring( lsp + 1 ) : name;
                    item.size = zip_entry.getSize();
                    long item_time = zip_entry.getTime();
                    item.date = item_time > 0 ? new Date( item_time ) : null;
                }
            }
        }
        return item;
    }
    
    private final String fixName( ZipEntry entry ) {
        try {
            String entry_name = entry.getName();
            
            if( android.os.Build.VERSION.SDK_INT >= 10 )
                return entry_name; // already fixed?
            
            byte[] ex = entry.getExtra();
            if( ex != null && ex.length == 2 && ex[0] == 1 && ex[1] == 2 ) 
                return entry_name;
            byte bytes[] = entry_name.getBytes( "iso-8859-1" );
            return new String( bytes );
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }
    
    private final ZipEntry[] bitsToItems( SparseBooleanArray cis ) {
    	try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    counter++;
            ZipEntry[] subItems = new ZipEntry[counter];
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
        // FIXME check that the zip file is processed by some other engine!!!!!!!!!!!!
        /*
        if( ??? ) {
        	notify( ctx.getString( R.string.busy ), Commander.OPERATION_FAILED );
        	return false;
        }
        */
    	return true;
    }
    public class ZipItemPropComparator implements Comparator<ZipEntry> {
        int type;
        boolean case_ignore, ascending;
        
        public ZipItemPropComparator( int type_, boolean case_ignore_, boolean ascending_ ) {
            type = type_;
            case_ignore = case_ignore_;
            ascending = ascending_;
        }
		@Override
		public int compare( ZipEntry f1, ZipEntry f2 ) {
            boolean f1IsDir = f1.isDirectory();
            boolean f2IsDir = f2.isDirectory();
            if( f1IsDir != f2IsDir )
                return f1IsDir ? -1 : 1;
            int ext_cmp = 0;
            switch( type ) {
            case CommanderAdapter.SORT_EXT:
                ext_cmp = case_ignore ? 
                        Utils.getFileExt( f1.getName() ).compareToIgnoreCase( Utils.getFileExt( f2.getName() ) ) :
                        Utils.getFileExt( f1.getName() ).compareTo( Utils.getFileExt( f2.getName() ) );
                break;
            case CommanderAdapter.SORT_SIZE:
                ext_cmp = f1.getSize() - f2.getSize() < 0 ? -1 : 1;
                break;
            case CommanderAdapter.SORT_DATE:
                ext_cmp = f1.getTime() - f2.getTime() < 0 ? -1 : 1;
                break;
            }
            if( ext_cmp == 0 )
                ext_cmp = case_ignore ? f1.getName().compareToIgnoreCase( f2.getName() ) : f1.getName().compareTo( f2.getName() );
            return ascending ? ext_cmp : -ext_cmp;
		}
    }

    @Override
    protected void reSort() {
        if( items == null ) return;
        ZipItemPropComparator comp = new ZipItemPropComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Arrays.sort( items, comp );
    }
    
    @Override
    public Item getItem( Uri u ) {
        try {
            String zip_path = u.getPath();
            if( zip_path == null ) return null;
            String opened_zip_path = uri != null ? uri.getPath() : null;
            if( opened_zip_path == null )
                zip = new ZipFile( zip_path );
            else if( !zip_path.equalsIgnoreCase( opened_zip_path ) )
                return null;    // do not want to reopen the current zip to something else!
            String entry_name = u.getFragment();
            if( entry_name != null ) {
                ZipEntry zip_entry = zip.getEntry( entry_name );
                if( zip_entry != null ) {
                    String name = fixName( zip_entry );
                    Item item = new Item();
                    item.dir = zip_entry.isDirectory();
                    int lsp = name.lastIndexOf( SLC, item.dir ? name.length() - 2 : name.length() );
                    item.name = lsp > 0 ? name.substring( lsp + 1 ) : name;
                    item.size = zip_entry.getSize();
                    long item_time = zip_entry.getTime();
                    item.date = item_time > 0 ? new Date( item_time ) : null;
                    return item;
                }
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    public InputStream getContent( Uri u, long offset ) {
        try {
            String zip_path = u.getPath();
            if( zip_path == null ) return null;
            String opened_zip_path = uri != null ? uri.getPath() : null;
            if( opened_zip_path == null || zip == null )
                zip = new ZipFile( zip_path );
            else if( !zip_path.equalsIgnoreCase( opened_zip_path ) )
                return null;    // do not want to reopen the current zip to something else!
            String entry_name = u.getFragment();
            if( entry_name != null ) {
                cachedEntry = zip.getEntry( entry_name );
                if( cachedEntry != null ) {
                    InputStream is = zip.getInputStream( cachedEntry );
                    if( offset > 0 )
                        is.skip( offset );
                    return is;
                }
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }
    @Override
    public void closeStream( Closeable is ) {
        if( is != null ) {
            try {
                is.close();
            } catch( IOException e ) {
                e.printStackTrace();
            }
        }
    }
}
