package com.ghostsq.commander.adapters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.adapters.CommanderAdapter.Item;
import com.ghostsq.commander.adapters.Engine;
import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.Utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;
import android.text.format.Formatter;
import android.util.Log;

public final class FSEngines {
    private   final static String TAG = "FSEngines";

    public interface IFileItem {
        public File f();
    }
    
    public static class ListEngine extends Engine {
        private String      pass_back_on_done;
        private FSAdapter   a;
        private File[]      files_ = null;
        private File        dir = null;
        
        ListEngine( FSAdapter a, Handler h, String pass_back_on_done_ ) {
            setHandler( h );
            this.a = a;
            pass_back_on_done = pass_back_on_done_;
        }
        public File getDirFile() {
            return dir;
        }       
        public File[] getFiles() {
            return files_;
        }       
        @Override
        public void run() {
            String err_msg = null;
            String dir_name = a.getDir();
            while( true ) {
                dir = new File( dir_name );
                files_ = dir.listFiles();
                if( files_ != null ) break;
                if( err_msg == null )
                    err_msg = a.ctx.getString( R.string.no_such_folder, dir_name );
                if( dir == null || ( dir_name = dir.getParent() ) == null ) {
                    Log.e( TAG, "Wrong folder '" + dir_name + "'" );
                    sendProgress( a.s( R.string.inv_path ), Commander.OPERATION_FAILED, pass_back_on_done );
                    return;
                }
            }
            sendProgress( null, Commander.OPERATION_COMPLETED, pass_back_on_done );
        }
    }
    
	public static class CalcSizesEngine extends Engine {
		private   IFileItem[] mList;
        protected CommanderAdapterBase cab;
        protected int  num = 0, dirs = 0, depth = 0;

        CalcSizesEngine( CommanderAdapterBase cab, IFileItem[] list ) {
            this.cab = cab;
            mList = list;
            setName( ".CalcSizesEngine" );
        }
        @Override
        public void run() {
        	try {
        	    cab.Init( null );
        	    Context c = cab.ctx;
                StringBuffer result = new StringBuffer( );
        	    if( mList != null && mList.length > 0 ) {
        	        sendProgress();
    				long sum = getSizes( mList );
    				if( sum < 0 ) {
    				    sendProgress( "Interrupted", Commander.OPERATION_FAILED );
    				    return;
    				}
    				if( ( cab.mode & CommanderAdapter.MODE_SORTING) == CommanderAdapter.SORT_SIZE )
      				    cab.reSort();
                    if( mList.length == 1 ) {
                        Item item = (Item)mList[0];
                        if( item.dir ) {
                            result.append( c.getString( R.string.sz_folder, item.name, num ) );
                            if( dirs > 0 )
                                result.append( c.getString( R.string.sz_dirnum, dirs, ( dirs > 1 ? c.getString( R.string.sz_dirsfx_p ) : c.getString( R.string.sz_dirsfx_s ) ) ) );
                        }
                        else
                            result.append( c.getString( R.string.sz_file, item.name ) );
                    } else
                        result.append( c.getString( R.string.sz_files, num ) );
                    if( sum > 0 )
                        result.append( c.getString( R.string.sz_Nbytes, Formatter.formatFileSize( c, sum ).trim() ) );
                    if( sum > 1024 )
                        result.append( c.getString( R.string.sz_bytes, sum ) );
                    if( mList.length == 1 ) {
                        Item item = (Item)mList[0];
                        result.append( c.getString( R.string.sz_lastmod ) );
                        result.append( "&#xA0;" );
                        String date_s = Utils.formatDate( item.date, c );
                        result.append( date_s );
                        File f = mList[0].f(); 
                        if( f.isFile() ) {
                            String ext  = Utils.getFileExt( item.name );
                            String mime = Utils.getMimeByExt( ext );
                            result.append( "\n" );
                            if( mime != null && !"*/*".equals( mime ) )
                                result.append( "\n<b>MIME:</b>\n&#xA0;<small>" + mime + "</small>" );
                            String[] hashes = Utils.getHash( f, new String[] { "MD5", "SHA-1" } );
                            if( hashes != null ) {
                                result.append( "\n<b>MD5:</b>\n&#xA0;<small>"   + hashes[0] + "</small>" );
                                result.append( "\n<b>SHA-1:</b>\n&#xA0;<small>" + hashes[1] + "</small>" );
                            }
                            if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR &&
                                Utils.getCategoryByExt( Utils.getFileExt( item.name ) ) == Utils.C_IMAGE ) { 
                                    result.append( "\n\n<hr/>" );
                                    result.append( ForwardCompat.getImageFileInfoHTML( f.getAbsolutePath() ) );
                            }
                        }
                    }
        	    }
                try {
                    if( cab instanceof FSAdapter ) {
                        result.append( "\n\n<hr/>" );
                        StatFs stat = new StatFs( cab.toString() );
                        long block_size = stat.getBlockSize();
                        result.append( c.getString( R.string.sz_total, Formatter.formatFileSize( cab.ctx, stat.getBlockCount() * block_size ), 
                                                                       Formatter.formatFileSize( cab.ctx, stat.getAvailableBlocks() * block_size ) ) );
                    }
                } catch( Exception e ) {
                }
                String str = result.toString();
				sendReport( str );
			} catch( Exception e ) {
			    Log.e( TAG, "", e );
				sendProgress( e.getMessage(), Commander.OPERATION_FAILED );
			}
        }
    	protected final long getSizes( IFileItem[] list ) throws Exception {
    	    long count = 0;
    		for( int i = 0; i < list.length; i++ ) {
                if( isStopReq() ) return -1;
    			IFileItem f = list[i];
    			Item item = (Item)f;
    			if( item.dir ) {
    				dirs++;
    				if( depth++ > 20 )
    					throw new Exception( cab.s( R.string.too_deep_hierarchy ) );
    				File[] subfiles = f.f().listFiles();
    				if( subfiles != null ) {
        				int l = subfiles.length;
        				FileItem[] subfiles_ex = new FileItem[l];
        				for( int j = 0; j < l; j++ )
        				    subfiles_ex[j] = new FileItem( subfiles[j] );
        				long sz = getSizes( subfiles_ex );
        				if( sz < 0 ) return -1;
        				item.size = sz;
        				count += item.size;
    				}
    				depth--;
    			}
    			else {
    				num++;
    				count += item.size;
    			}
    		}
    		return count;
    	}
    }
	
	public static class AskEngine extends Engine {
	    private FSAdapter fsa;
        private String msg;
        private File   from, to;
        
	    AskEngine( FSAdapter fsa, Handler h_, String msg_, File from_, File to_ ) {
	        super.setHandler( h_ );
	        this.fsa = fsa;
	        msg = msg_;
	        from = from_;
	        to = to_;
	    }
	    @Override
        public void run() {
            try {
                int resolution = askOnFileExist( msg, fsa.commander );
                if( ( resolution & Commander.REPLACE ) != 0 ) {
                    if( to.delete() && from.renameTo( to ) )
                        sendResult( "ok" );
                }
            } catch( InterruptedException e ) {
                e.printStackTrace();
            }
        }
    }

	public static class DeleteEngine extends Engine {
	    private CommanderAdapterBase cab;
		private File[] mList;

        DeleteEngine( CommanderAdapterBase cab, FileItem[] list ) {
            this( cab, new File[list.length] );
            for( int i = 0; i < list.length; i++ )
                mList[i] = list[i].f();
        }
        DeleteEngine( CommanderAdapterBase cab, File[] list ) {
            setName( ".DeleteEngine" );
            this.cab = cab;
            mList = list;
        }
        @Override
        public void run() {
            try {
                cab.Init( null );
                int cnt = deleteFiles( mList );
                if( mList.length == cnt && cnt == 1 ) {
                    String msg = mList[0].getName() + " " + cab.ctx.getString( R.string.was ) + " " + cab.ctx.getString( R.string.deleted );
                            sendResult( msg );
                    return;
                }
                sendResult( Utils.getOpReport( cab.ctx, cnt, R.string.deleted ) );
            }
            catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED_REFRESH_REQUIRED );
            }
        }
        private final int deleteFiles( File[] l ) throws Exception {
    	    if( l == null ) return 0;
            int cnt = 0;
            int num = l.length;
            double conv = 100./num; 
            for( int i = 0; i < num; i++ ) {
                sleep( 1 );
                if( isStopReq() )
                    throw new Exception( cab.s( R.string.canceled ) );
                File f = l[i];
                sendProgress( cab.ctx.getString( R.string.deleting, f.getName() ), (int)(cnt * conv) );
                if( f.isDirectory() )
                    cnt += deleteFiles( f.listFiles() );
                if( f.delete() )
                    cnt++;
                else {
                    error( cab.ctx.getString( R.string.cant_del, f.getName() ) );
                    break;
                }
                
                String ext = Utils.getFileExt( f.getName() );
                String mime = Utils.getMimeByExt( ext );
                if( mime != null ) {
                    Uri content_uri = null;
                    if( mime.startsWith( "image/" ) ) content_uri = Images.Media.EXTERNAL_CONTENT_URI;
                    if( mime.startsWith( "audio/" ) ) content_uri = Audio.Media.EXTERNAL_CONTENT_URI;
                    if( mime.startsWith( "video/" ) ) content_uri = Video.Media.EXTERNAL_CONTENT_URI;
                    if( content_uri != null ) {
                        ContentResolver cr = cab.ctx.getContentResolver();
                        try {
                            cr.delete( content_uri, MediaColumns.DATA + "=?", new String[]{ f.getAbsolutePath() });
                        } catch( Exception e ) {}
                    }
                }                
            }
            return cnt;
        }
    }

    public static class CopyEngine extends CalcSizesEngine {
        private String  mDest;
        private int     counter = 0;
        private long    totalBytes = 0;
        private double  conv;
        private File[]  fList = null;
        private boolean move, del_src_dir, destIsFullName;
        private byte[]  buf;
        private static final int BUFSZ = 524288;
        private PowerManager.WakeLock wakeLock;
        private ArrayList<String> to_scan;

        CopyEngine( CommanderAdapterBase cab, File[] list, String dest, int move_mode, boolean dest_is_full_name ) {
        	super( cab, null );
       	    setName( ".CopyEngine" );
        	fList = list;
            mDest = dest;
            move = ( move_mode & CommanderAdapter.MODE_MOVE ) != 0;
            del_src_dir = ( move_mode & CommanderAdapter.MODE_DEL_SRC_DIR ) != 0;
            destIsFullName = dest_is_full_name;
            buf = new byte[BUFSZ];
                        
            PowerManager pm = (PowerManager)cab.ctx.getSystemService( Context.POWER_SERVICE );
            wakeLock = pm.newWakeLock( PowerManager.PARTIAL_WAKE_LOCK, TAG );
            
            if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO )
                to_scan = new ArrayList<String>(); 
        }
        @Override
        public void run() {
        	sendProgress( cab.ctx.getString( R.string.preparing ), 0, 0 );
        	try {
                int l = fList.length;
                FileItem[] x_list = new FileItem[l];
                
                File src_dir_f = null;
                boolean in_same_src = true;
                for( int j = 0; j < l; j++ ) {
                    x_list[j] = new FileItem( fList[j] );
                    if( in_same_src ) {
                        File parent_f = fList[j].getParentFile();
                        if( src_dir_f == null )
                            src_dir_f = parent_f;
                        else
                            in_same_src = src_dir_f.equals( parent_f );
                    }
                }
                wakeLock.acquire();
				long sum = getSizes( x_list );
				conv = 100 / (double)sum;
				int num = copyFiles( fList, mDest, destIsFullName );
				if( del_src_dir && in_same_src && src_dir_f != null ) {
				    File[] to_delete = new File[1];
				    to_delete[0] = src_dir_f; 
				    DeleteEngine de = new DeleteEngine( cab, to_delete );
				    de.start();
				}
				wakeLock.release();
				// XXX: assume (move && !del_src_dir)==true when copy from app: to the FS 
	            String report = Utils.getOpReport( cab.ctx, num, move && !del_src_dir ? R.string.moved : R.string.copied );
	            sendResult( report );
	            
                if( to_scan != null && to_scan.size() > 0 ) {
                    String[] to_scan_a = new String[to_scan.size()];
                    to_scan.toArray( to_scan_a );
                    ForwardCompat.scanMedia( cab.ctx, to_scan_a );
                }

			} catch( Exception e ) {
				sendProgress( e.getMessage(), Commander.OPERATION_FAILED_REFRESH_REQUIRED );
				return;
			}
        }
        private final int copyFiles( File[] list, String dest, boolean dest_is_full_name ) throws InterruptedException {
            File file = null;
            for( int i = 0; i < list.length; i++ ) {
                boolean existed = false;
                InputStream  is = null;
                OutputStream os = null;
                File outFile = null;
                file = list[i];
                if( file == null ) {
                    error( cab.ctx.getString( R.string.unkn_err ) );
                    break;
                }
                String uri = file.getAbsolutePath();
                try {
                    if( isStopReq() ) {
                        error( cab.ctx.getString( R.string.canceled ) );
                        break;
                    }
                    long last_modified = file.lastModified();
                    String fn = file.getName();
                    outFile = dest_is_full_name ? new File( dest ) : new File( dest, fn );
                    String out_dir_path = new File( dest ).getCanonicalPath();
                    if( file.isDirectory() ) {
                        if( out_dir_path.startsWith( file.getCanonicalPath() ) ) {
                            error( cab.ctx.getString( R.string.cant_copy_to_itself, file.getName() ) );
                            break;
                        }
                        if( depth++ > 40 ) {
                            error( cab.ctx.getString( R.string.too_deep_hierarchy ) );
                            break;
                        }
                        else
                        if( outFile.exists() || outFile.mkdir() ) {
                            copyFiles( file.listFiles(), outFile.getAbsolutePath(), false );
                            if( errMsg != null )
                            	break;
                        }
                        else
                            error( cab.ctx.getString( R.string.cant_md, outFile.getAbsolutePath() ) );
                        depth--;
                        counter++;
                    }
                    else {
                        if( existed = outFile.exists() ) {
                            int res = askOnFileExist( cab.ctx.getString( R.string.file_exist, outFile.getAbsolutePath() ), cab.commander );
                            if( res == Commander.SKIP )  continue;
                            if( res == Commander.REPLACE ) {
                                if( outFile.equals( file ) )
                                    continue;
                                else
                                    outFile.delete();
                            }
                            if( res == Commander.ABORT ) break;
                        }
                        if( move ) {    // first try to move by renaming
                            long len = file.length();
                            if( file.renameTo( outFile ) ) {
                                counter++;
                                totalBytes += len;
                                int  so_far = (int)(totalBytes * conv);
                                sendProgress( outFile.getName() + " " + cab.ctx.getString( R.string.moved ), so_far, 0 );
                                continue;
                            }
                        }
                        
                        is = new FileInputStream( file );
                        os = new FileOutputStream( outFile );
                        long copied = 0, size  = file.length();
                        
                        long start_time = 0;
                        int  speed = 0;
                        int  so_far = (int)(totalBytes * conv);
                        
                        String sz_s = Utils.getHumanSize( size, false );
                        int fnl = fn.length();
                        String rep_s = cab.ctx.getString( R.string.copying, 
                               fnl > CUT_LEN ? "\u2026" + fn.substring( fnl - CUT_LEN ) : fn );
                        int  n  = 0; 
                        long nn = 0;
                        
                        while( true ) {
                            if( nn == 0 ) {
                                start_time = System.currentTimeMillis();
                                sendProgress( rep_s + sizeOfsize( copied, sz_s ), so_far, (int)(totalBytes * conv), speed );
                            }
                            n = is.read( buf );
                            if( n < 0 ) {
                                long time_delta = System.currentTimeMillis() - start_time;
                                if( time_delta > 0 ) {
                                    speed = (int)(MILLI * nn / time_delta );
                                    sendProgress( rep_s + sizeOfsize( copied, sz_s ), so_far, (int)(totalBytes * conv), speed );
                                }
                                break;
                            }
                            os.write( buf, 0, n );
                            nn += n;
                            copied += n;
                            totalBytes += n;
                            if( isStopReq() ) {
                                Log.d( TAG, "Interrupted!" );
                                error( cab.ctx.getString( R.string.canceled ) );
                                return counter;
                            }
                            long time_delta = System.currentTimeMillis() - start_time;
                            if( time_delta > DELAY ) {
                                speed = (int)(MILLI * nn / time_delta);
                                //Log.v( TAG, "bytes: " + nn + " time: " + time_delta + " speed: " + speed );
                                nn = 0;
                            }
                        }
                        is.close();
                        os.close();
                        is = null;
                        os = null;
                        
                        if( i >= list.length-1 )
                        	sendProgress( cab.ctx.getString( R.string.copied_f, fn ) + sizeOfsize( copied, sz_s ), (int)(totalBytes * conv) );
                        if( to_scan != null ) {
                            String ext = Utils.getFileExt( outFile.getName() );
                            String mime = Utils.getMimeByExt( ext );
                                if( mime != null && ( mime.startsWith( "image/" ) || mime.startsWith( "audio/" ) || mime.startsWith( "video/" ) ) )
                                to_scan.add( outFile.getAbsolutePath() );
                        }
// debug only, to remove!
//Log.v( TAG, c.getString( R.string.copied_f, fn ) );
                        counter++;
                    }
                    if( move )
                        file.delete();
                    outFile.setLastModified( last_modified );
                    
                    final int GINGERBREAD = 9;
                    if( android.os.Build.VERSION.SDK_INT >= GINGERBREAD )
                        ForwardCompat.setFullPermissions( outFile );
                }
                catch( SecurityException e ) {
                    Log.e( TAG, "", e );
                    error( cab.ctx.getString( R.string.sec_err, e.getMessage() ) );
                }
                catch( FileNotFoundException e ) {
                    Log.e( TAG, "", e );
                    error( cab.ctx.getString( R.string.not_accs, e.getMessage() ) );
                }
                catch( ClosedByInterruptException e ) {
                    Log.e( TAG, "", e );
                    error( cab.ctx.getString( R.string.canceled ) );
                }
                catch( IOException e ) {
                    Log.e( TAG, "", e );
                    String msg = e.getMessage();
                    error( cab.ctx.getString( R.string.acc_err, uri, msg != null ? msg : "" ) );
                }
                catch( RuntimeException e ) {
                    Log.e( TAG, "", e );
                    error( cab.ctx.getString( R.string.rtexcept, uri, e.getMessage() ) );
                }
                finally {
                    try {
                        if( is != null )
                            is.close();
                        if( os != null )
                            os.close();
                        if( !move && errMsg != null && outFile != null && !existed ) {
                            Log.i( TAG, "Deleting failed output file" );
                            outFile.delete();
                        }
                    }
                    catch( IOException e ) {
                        error( cab.ctx.getString( R.string.acc_err, uri, e.getMessage() ) );
                    }
                }
            }
            return counter;
        }
    }
}
