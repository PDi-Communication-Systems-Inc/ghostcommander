package com.ghostsq.commander.adapters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.FTPAdapter.FTPCredentials;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.LsItem;
import com.ghostsq.commander.utils.FTP;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Handler;
import android.text.format.Formatter;
import android.util.Log;

public final class FTPEngines {

    private static abstract class FTPEngine extends Engine 
    {
        protected Context        ctx;
        protected FTPCredentials crd;
        protected FTP            ftp;
        protected Uri            uri;
        FTPEngine( Context ctx_, FTPCredentials crd_, Uri uri_, FTP ftp_ ) {    // borrows FTP object
            if( crd_ == null )
                crd_ = new FTPCredentials( uri_.getUserInfo() ); 
            this.ctx = ctx_;
            this.crd = crd_;
            this.uri = uri_;
            this.ftp = ftp_;
        }
        FTPEngine( Context ctx_, FTPCredentials crd_, Uri uri_, boolean active, Charset cs ) {
            this( ctx_, crd_, uri_, new FTP() );
            ftp.setActiveMode( active );
            ftp.setCharset( cs );
        }
    }    

    public static class ListEngine extends FTPEngine {
        private int mode = 0;
        private boolean ascending;
        private boolean needReconnect;
        private LsItem[] items_tmp;
        public  String pass_back_on_done;
        public  String path;
        
        ListEngine( FTPAdapter a, Handler h, FTP ftp_, boolean need_reconnect_, String pass_back_on_done_ ) {
            super( a.ctx, (FTPCredentials)a.getCredentials(), a.getUri(), ftp_ );
            setHandler( h );
            needReconnect = need_reconnect_;
            pass_back_on_done = pass_back_on_done_;
            this.mode = a.getMode();
            this.ascending = ( this.mode & CommanderAdapter.MODE_SORT_DIR ) == CommanderAdapter.SORT_ASC;
        }
        public LsItem[] getItems() {
            return items_tmp;
        }       
        @Override
        public void run() {
            try {
                if( uri == null ) {
                    sendProgress( "Wrong URI", Commander.OPERATION_FAILED );
                    return;
                }
                Log.i( TAG, "ListEngine started" );
                threadStartedAt = System.currentTimeMillis();
                ftp.clearLog();
                if( needReconnect  && ftp.isLoggedIn() ) {
                    ftp.disconnect( false );
                }
                if( crd == null || crd.isNotSet() )
                    crd = new FTPCredentials( uri.getUserInfo() );
                int cl_res = ftp.connectAndLogin( uri, crd.getUserName(), crd.getPassword(), true );
                if( cl_res < 0 ) {
                    if( cl_res == FTP.NO_LOGIN ) 
                        sendLoginReq( uri.toString(), crd, pass_back_on_done );
                    return;
                }
                if( cl_res == FTP.LOGGED_IN )
                    sendProgress( ctx.getString( R.string.ftp_connected,  
                            uri.getHost(), crd.getUserName() ), Commander.OPERATION_STARTED );

                if( ftp.isLoggedIn() ) {
                    //Log.v( TAG, "ftp is logged in" );
                    items_tmp = ftp.getDirList( null, ( mode & CommanderAdapter.MODE_HIDDEN ) == CommanderAdapter.SHOW_MODE );
                    path = ftp.getCurrentDir();
                    boolean need_restore_wd = false;
                    if( path != null ) 
                        for( LsItem lsi : items_tmp ) {
                            String name = lsi.getName();
                            if( name == null ) continue;
                            String lt = lsi.getLinkTarget();
                            if( !Utils.str( lt ) ) continue;
                            if( lt.charAt( 0 ) != '/' )
                                lt = Utils.mbAddSl( path ) + lt;
                            need_restore_wd = true;
                            if( ftp.setCurrentDir( lt ) )
                                lsi.setDirectory();
                        }
                        if( need_restore_wd )
                            ftp.setCurrentDir( path );
                        synchronized( uri ) {
                            uri = uri.buildUpon().encodedPath( path ).build();
                        }
                    if( items_tmp != null  ) {
                        //Log.v( TAG, "Got the items list" );
                        if( items_tmp.length > 0 ) {
                            LsItem.LsItemPropComparator comp = 
                                items_tmp[0].new LsItemPropComparator( mode & CommanderAdapter.MODE_SORTING, (mode & CommanderAdapter.MODE_CASE) != 0, ascending );
                            Arrays.sort( items_tmp, comp );
                        }
                        
                        //Log.v( TAG, "items list sorted" );
                        sendProgress( tooLong( 8 ) ? ftp.getLog() : null, Commander.OPERATION_COMPLETED, pass_back_on_done );
                        return;
                    }
                    else
                        Log.e( TAG, "Can't get the items list" );
                }
                else
                    Log.e( TAG, "Did not log in." );
            }
            catch( UnknownHostException e ) {
                ftp.debugPrint( "Unknown host:\n" + e.getLocalizedMessage() );
            }
            catch( IOException e ) {
                ftp.debugPrint( "IO exception:\n" + e.getLocalizedMessage() );
                Log.e( TAG, "", e );
            }
            catch( Exception e ) {
                ftp.debugPrint( e.getLocalizedMessage() );
                Log.e( TAG, "", e );
            }
            finally {
                super.run();
            }
            ftp.disconnect( true );
            sendProgress( ftp.getLog(), Commander.OPERATION_FAILED, pass_back_on_done );
        }
    }
        
    
    private static abstract class CopyEngine extends FTPEngine implements FTP.ProgressSink 
    {
        private   long      startTime;
        private   long      curFileLen = 0, curFileDone = 0, secDone = 0;
        protected WifiLock  wifiLock;
        protected String    progressMessage = null;

        CopyEngine( Context ctx_, FTPCredentials crd_, Uri uri_, boolean active, Charset cs ) {
            super( ctx_, crd_, uri_, active, cs );
            startTime = System.currentTimeMillis();
            WifiManager manager = (WifiManager)ctx.getSystemService( Context.WIFI_SERVICE );
            wifiLock = manager.createWifiLock( android.os.Build.VERSION.SDK_INT >= 12 ? 3 : WifiManager.WIFI_MODE_FULL, TAG );
            wifiLock.setReferenceCounted( false );
        }
        protected void setCurFileLength( long len ) {
            curFileDone = 0;
            curFileLen  = len;
        }
        
        @Override
        public boolean completed( long size, boolean done ) throws InterruptedException {
            if( curFileLen > 0 ) {
                curFileDone += size;
                secDone += size;
                long cur_time = System.currentTimeMillis();
                long time_delta = cur_time - startTime;
                if( done || time_delta > DELAY ) {    // once a sec. only
                    int  speed = (int)( MILLI * secDone / time_delta ); 
                    sendProgress( progressMessage, (int)( curFileDone * 100 / curFileLen ), -1, speed );
                    startTime = cur_time;
                    secDone = 0;
                }
            }
            //Log.v( TAG, progressMessage + " " + size );
            if( isStopReq() ) {
                error( ctx.getString( R.string.canceled ) );
                return false;
            }
            Thread.sleep( 1 );
            return true;
        }
    }

  // --- CopyFromEngine ---
    
  public static class CopyFromEngine extends CopyEngine 
  {
        private LsItem[]  mList;
        private File      dest_folder;
        private boolean   move;
        private Commander commander;

        CopyFromEngine( Commander c, FTPCredentials crd_, Uri uri_, LsItem[] list, File dest, boolean move_, Engines.IReciever recipient_, boolean active, Charset cs ) {
            super( c.getContext(), crd_, uri_, active, cs );
            commander = c;
            mList = list;
            dest_folder = dest;
            move = move_;
            recipient = recipient_;
        }
        @Override
        public void run() {
            try {
                if( crd == null || crd.isNotSet() )
                    crd = new FTPCredentials( uri.getUserInfo() );
                
                if( ftp.connectAndLogin( uri, crd.getUserName(), crd.getPassword(), true ) < 0 ) {
                    error( ctx.getString( R.string.ftp_nologin ) );
                    sendResult( "" );
                    return;
                }
                
                wifiLock.acquire();
                int total = copyFiles( mList, "" );
                wifiLock.release();
                
                if( recipient != null ) {
                      sendReceiveReq( dest_folder );
                      return;
                }
                sendResult( Utils.getOpReport( ctx, total, R.string.downloaded ) );
            } catch( InterruptedException e ) {
                sendResult( ctx.getString( R.string.canceled ) );
            } catch( Exception e ) {
                error( ctx.getString( R.string.failed ) + e.getLocalizedMessage() );
                e.printStackTrace();
            }
            super.run();
        }
    
        private final int copyFiles( LsItem[] list, String path ) throws InterruptedException {
            int counter = 0;
            try {
                for( int i = 0; i < list.length; i++ ) {
                    if( stop || isInterrupted() ) {
                        error( ctx.getString( R.string.interrupted ) );
                        break;
                    }
                    LsItem f = list[i];
                    if( f != null ) {
                        String pathName = path + f.getName();
                        File dest = new File( dest_folder, pathName );
                        if( f.isDirectory() ) {
                            if( !dest.mkdir() ) {
                                if( !dest.exists() || !dest.isDirectory() ) {
                                    errMsg = "Can't create folder \"" + dest.getCanonicalPath() + "\"";
                                    break;
                                }
                            }
                            LsItem[] subItems = ftp.getDirList( pathName, true );
                            if( subItems == null ) {
                                errMsg = "Failed to get the file list of the subfolder '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog();
                                break;
                            }
                            counter += copyFiles( subItems, pathName + File.separator );
                            if( errMsg != null ) break;
                            if( move && !ftp.rmDir( pathName ) ) {
                                errMsg = "Failed to remove folder '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog();
                                break;
                            }
                        }
                        else {
                            if( dest.exists()  ) {
                                int res = askOnFileExist( ctx.getString( R.string.file_exist, dest.getAbsolutePath() ), commander );
                                if( res == Commander.ABORT ) break;
                                if( res == Commander.SKIP )  continue;
                                if( res == Commander.REPLACE ) {
                                    if( !dest.delete() ) {
                                        error( ctx.getString( R.string.cant_del, dest.getAbsoluteFile() ) );
                                        break;
                                    }
                                }
                            }
                            int pnl = pathName.length();
                            progressMessage = ctx.getString( R.string.retrieving, 
                                    pnl > CUT_LEN ? "\u2026" + pathName.substring( pnl - CUT_LEN ) : pathName ); 
                            sendProgress( progressMessage, 0 );
                            setCurFileLength( f.length() );
                            FileOutputStream out = new FileOutputStream( dest );
                            ftp.clearLog();
                            if( !ftp.retrieve( pathName, out, this ) ) {
                                error( "Can't download file '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog() );
                                dest.delete();
                                break;
                            }
                            else if( move ) {
                                if( !ftp.delete( pathName ) ) {
                                    error( "Can't delete file '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog() );
                                    break;
                                }
                            }
                            progressMessage = "";
                        }
                        Date ftp_file_date = f.getDate();
                        if( ftp_file_date != null )
                            dest.setLastModified( ftp_file_date.getTime() );
                        
                        final int GINGERBREAD = 9;
                        if( android.os.Build.VERSION.SDK_INT >= GINGERBREAD )
                            ForwardCompat.setFullPermissions( dest );
                        
                        counter++;
                    }
                }
            }
            catch( RuntimeException e ) {
                error( "Runtime Exception: " + e.getMessage() );
                Log.e( TAG, "", e );
            }
            catch( IOException e ) {
                error( "Input-Output Exception: " + e.getMessage() );
                Log.e( TAG, "", e );
            }
            return counter;
        }
    }

      // --- CopyToEngine ---
  
    public static class CopyToEngine extends CopyEngine {
        private   File[]  mList;
        private   int     basePathLen;
        private   boolean move = false;
        private   boolean del_src_dir = false;
        
        CopyToEngine( Context ctx_, FTPCredentials crd_, Uri uri_, File[] list, boolean move_, boolean del_src_dir_, boolean active, Charset cs ) {
            super( ctx_, crd_, uri_, active, cs );
            mList = list;
            basePathLen = list[0].getParent().length();
            if( basePathLen > 1 ) basePathLen++;
            move = move_; 
            del_src_dir = del_src_dir_;
        }

        @Override
        public void run() {
            try {
                if( ftp.connectAndLogin( uri, crd.getUserName(), crd.getPassword(), true ) < 0 ) {
                    error( ctx.getString( R.string.ftp_nologin ) );
                    sendResult( "" );
                    return;
                }

                wifiLock.acquire();
                int total = copyFiles( mList );
                wifiLock.release();
                if( del_src_dir ) {
                    File src_dir = mList[0].getParentFile();
                    if( src_dir != null )
                        src_dir.delete();
                }
                sendResult( Utils.getOpReport( ctx, total, R.string.uploaded ) );
                return;
            } catch( Exception e ) {
                error( e.getLocalizedMessage() );
            }
            finally {            
                super.run();
            }
            sendResult( "" );
        }
        private final int copyFiles( File[] list ) throws InterruptedException {
            if( list == null ) return 0;
            int counter = 0;
            try {
                for( int i = 0; i < list.length; i++ ) {
                    if( stop || isInterrupted() ) {
                        error( ctx.getString( R.string.interrupted ) );
                        break;
                    }
                    File f = list[i];
                    if( f != null && f.exists() ) {
                        if( f.isFile() ) {
                            String pathName = f.getAbsolutePath();
                            int pnl = pathName.length();
                            progressMessage = ctx.getString( R.string.uploading, 
                                    pnl > CUT_LEN ? "\u2026" + pathName.substring( pnl - CUT_LEN ) : pathName );
                            sendProgress( progressMessage, 0 );
                            String fn = f.getAbsolutePath().substring( basePathLen );
                            FileInputStream in = new FileInputStream( f );
                            setCurFileLength( f.length() );
                            ftp.clearLog();
                            if( !ftp.store( fn, in, this ) ) {
                                error( ctx.getString( R.string.ftp_upload_failed, f.getName(), ftp.getLog() ) );
                                break;
                            }
                            progressMessage = "";
                        }
                        else
                        if( f.isDirectory() ) {
                            ftp.clearLog();
                            String toCreate = f.getAbsolutePath().substring( basePathLen );
                            if( !ftp.makeDir( toCreate ) ) {
                                error( ctx.getString( R.string.ftp_mkdir_failed, toCreate, ftp.getLog() ) );
                                break;
                            }
                            counter += copyFiles( f.listFiles() );
                            if( errMsg != null ) break;
                        }
                        counter++;
                        if( move && !f.delete() ) {
                            error( ctx.getString( R.string.cant_del, f.getCanonicalPath() ) );
                            break;
                        }
                    }
                }
            }
            catch( IOException e ) {
                error( "IOException: " + e.getMessage() );
                Log.e( TAG, "", e );
            }
            return counter;
        }
    }

    // --- DelEngine ---
    
    static class DelEngine extends FTPEngine {
        LsItem[] mList;
        
        DelEngine( Context ctx_, FTPCredentials crd_, Uri uri_, LsItem[] list, boolean active, Charset cs ) {
            super( ctx_, crd_, uri_, active, cs );
            mList = list;
        }
        @Override
        public void run() {
            try {
                if( ftp.connectAndLogin( uri, crd.getUserName(), crd.getPassword(), true ) < 0 ) {
                    error( ctx.getString( R.string.ftp_nologin ) );
                    sendResult( "" );
                    return;
                }
                int total = delFiles( mList, "" );
                sendResult( Utils.getOpReport( ctx, total, R.string.deleted ) );
                super.run();
            } catch( Exception e ) {
                Log.e( TAG, "", e );
            }
        }
        private final int delFiles( LsItem[] list, String path ) {
            int counter = 0;
            try {
                for( int i = 0; i < list.length; i++ ) {
                    if( stop || isInterrupted() ) {
                        error( ctx.getString( R.string.interrupted ) );
                        break;
                    }
                    LsItem f = list[i];
                    if( f != null ) {
                        String pathName = path + f.getName();
                        if( f.isDirectory() ) {
                            LsItem[] subItems = ftp.getDirList( pathName, true );
                            counter += delFiles( subItems, pathName + File.separator );
                            if( errMsg != null ) break;
                            ftp.clearLog();
                            if( !ftp.rmDir( pathName ) ) {
                                error( "Failed to remove folder '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog() );
                                break;
                            }
                        }
                        else {
                            sendProgress( ctx.getString( R.string.deleting, pathName ), i * 100 / list.length );
                            ftp.clearLog();
                            if( !ftp.delete( pathName ) ) {
                                error( "Failed to delete file '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog() );
                                break;
                            }
                        }
                        counter++;
                    }
                }
            }
            catch( Exception e ) {
                Log.e( TAG, "delFiles()", e );
                error( e.getLocalizedMessage() );
            }
            return counter;
        }
    }
    
    // --- RenEngine  ---
    
    static class RenEngine extends FTPEngine {
        private String oldName, newName;
        
        RenEngine( Context ctx_, FTPCredentials crd_, Uri uri_, String oldName_, String newName_, boolean active, Charset cs ) {
            super( ctx_, crd_, uri_, active, cs );
            oldName = oldName_; 
            newName = newName_;
        }
        @Override
        public void run() {
            try {
                if( ftp.connectAndLogin( uri, crd.getUserName(), crd.getPassword(), true ) < 0 ) {
                    error( ctx.getString( R.string.ftp_nologin ) );
                    sendResult( "" );
                    return;
                }
                if( !ftp.rename( oldName, newName ) )
                    error( ctx.getString( R.string.failed ) + ftp.getLog() );
                sendResult( "" );
                super.run();
            } catch( Exception e ) {
                Log.e( TAG, "", e );
            }
        }
    }

    static class ChmodEngine extends FTPEngine {
        private String chmod;
        
        ChmodEngine( Context ctx_, Uri uri_, String chmod_, Charset cs ) {
            super( ctx_, null, uri_, false, cs );
            chmod = chmod_;
        }
        @Override
        public void run() {
            try {
                if( ftp.connectAndLogin( uri, crd.getUserName(), crd.getPassword(), true ) < 0 ) {
                    error( ctx.getString( R.string.ftp_nologin ) );
                    sendResult( "" );
                    return;
                }
                if( !ftp.site( chmod ) )
                    error( ctx.getString( R.string.failed ) + ftp.getLog() );
                sendResult( "" );
                super.run();
            } catch( Exception e ) {
                Log.e( TAG, "", e );
            }
        }
    }
    
    public static class CalcSizesEngine extends FTPEngine {
        private int num = 0, dirs = 0, depth = 0;
        private LsItem[]  list;
    
        CalcSizesEngine( Commander c, FTPCredentials crd_, Uri uri_, LsItem[] list_, boolean active, Charset cs ) {
            super( c.getContext(), crd_, uri_, active, cs );
            list = list_;
        }

        @Override
        public void run() {
            try {
                if( crd == null || crd.isNotSet() )
                    crd = new FTPCredentials( uri.getUserInfo() );
                
                if( ftp.connectAndLogin( uri, crd.getUserName(), crd.getPassword(), true ) < 0 ) {
                    error( ctx.getString( R.string.ftp_nologin ) );
                    sendResult( "" );
                    return;
                }
                sendProgress();
                long total = getSizes( list, "" );
                
                StringBuffer result = new StringBuffer();
                if( list.length == 1 ) {
                    LsItem f = list[0];
                    if( f.isDirectory() ) {
                        result.append( ctx.getString( Utils.RR.sz_folder.r(), f.getName(), num ) );
                        if( dirs > 0 )
                            result.append( ctx.getString( Utils.RR.sz_dirnum.r(), dirs, ( dirs > 1 ? ctx.getString( Utils.RR.sz_dirsfx_p.r() ) : ctx.getString( Utils.RR.sz_dirsfx_s.r() ) ) ) );
                    }
                    else 
                        result.append( ctx.getString( Utils.RR.sz_file.r(), f.getName() ) );
                } else {
                    result.append( ctx.getString( Utils.RR.sz_files.r(), num ) );
                    if( dirs > 0 )
                        result.append( ctx.getString( Utils.RR.sz_dirnum.r(), dirs, ( dirs > 1 ? ctx.getString( Utils.RR.sz_dirsfx_p.r() ) : ctx.getString( Utils.RR.sz_dirsfx_s.r() ) ) ) );
                }
                if( total > 0 )
                    result.append( ctx.getString( Utils.RR.sz_Nbytes.r(), Formatter.formatFileSize( ctx, total ).trim() ) );
                if( total > 1024 )
                    result.append( ctx.getString( Utils.RR.sz_bytes.r(), total ) );
                if( list.length == 1 ) {
                    LsItem f = list[0];
                    result.append( ctx.getString( Utils.RR.sz_lastmod.r() ) );
                    result.append( " " );
                    result.append( "<small>" );
                    result.append( Utils.formatDate( f.getDate(), ctx ) );
                    result.append( "</small>" );
                }
                sendReport( result.toString() );            
                super.run();
            } catch( InterruptedException e ) {
                sendResult( ctx.getString( R.string.canceled ) );
            } catch( Exception e ) {
                error( ctx.getString( R.string.failed ) + e.getLocalizedMessage() );
                e.printStackTrace();
            }
            super.run();
        }
    
        protected final long getSizes( LsItem[] list, String path ) throws Exception {
            long total = 0;
            try {
                for( int i = 0; i < list.length; i++ ) {
                    if( stop || isInterrupted() ) {
                        error( ctx.getString( R.string.interrupted ) );
                        break;
                    }
                    LsItem f = list[i];
                    if( f != null ) {
                        String pathName = path + f.getName();
                        if( f.isDirectory() ) {
                            dirs++;
                            if( depth++ > 20 )
                                throw new Exception( ctx.getString( R.string.too_deep_hierarchy ) );
                            LsItem[] subItems = ftp.getDirList( pathName, true );
                            --depth;
                            if( subItems == null ) {
                                error( "Failed to get the file list of the subfolder '" + pathName + "'.\n FTP log:\n\n" + ftp.getLog() );
                                break;
                            }
                            total += getSizes( subItems, pathName + File.separator );
                            if( errMsg != null ) break;
                        }
                        else {
                            total += f.length();
                            num++;
                        }
                    }
                }
            }
            catch( RuntimeException e ) {
                error( "Runtime Exception: " + e.getMessage() );
                Log.e( TAG, "", e );
            }
            return total;
        }
    }
    
}
