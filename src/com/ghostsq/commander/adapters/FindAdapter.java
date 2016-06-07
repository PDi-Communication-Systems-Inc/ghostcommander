package com.ghostsq.commander.adapters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Date;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.FileItem;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.SparseBooleanArray;

public class FindAdapter extends FSAdapter {
    public final static String TAG = "FindAdapter";
    private Uri uri;

    public FindAdapter( Context ctx_ ) {
        super( ctx_ );
        parentLink = PLS;
    }

    @Override
    public void Init( Commander c ) {
        super.Init( c );
        if( c != null )
            readerHandler = new SearchHandler();
    }    
    
    @Override
    public String getScheme() {
        return "find";
    }

    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case LOCAL:
        case SZ:
        case SEARCH:
        case SEND:
            return true;
        default: return super.hasFeature( feature );
        }
    }
    @Override
    public int setMode( int mask, int mode_ ) {
        mode_ &= ~MODE_WIDTH;
        return super.setMode( mask, mode_ );
    }

    protected class SearchHandler extends ReaderHandler {
        private long lastTime = System.currentTimeMillis();;
    
        @Override
        public void handleMessage( Message msg ) {
            try {
                if( msg.what == Commander.OPERATION_IN_PROGRESS ) {
                    long cur_time = System.currentTimeMillis();
                    if( cur_time - lastTime > 1000 ) {
                        onFileFound();
                        lastTime = cur_time;
                    }
                }
                super.handleMessage( msg );
            } catch( Exception e ) {
                e.printStackTrace();
            }
        }
    };
    
    @Override
    public boolean readSource( Uri uri_, String pass_back_on_done ) {
        try {
            if( reader != null ) reader.reqStop();
            if( uri_ != null )
                uri = uri_;
            if( uri == null )
                return false;
            if( uri.getScheme().compareTo( "find" ) == 0 ) {
                String  path   = uri.getPath();
                String match   = uri.getQueryParameter( "q" );
                 
                if( path != null && path.length() > 0 && match != null && match.length() > 0  ) {
                    notify( Commander.OPERATION_STARTED );
                    SearchEngine se = new SearchEngine( readerHandler, match, path, pass_back_on_done );
                    reader = se;
                    String  dirs_s  = uri.getQueryParameter( "d" );
                    String  files_s = uri.getQueryParameter( "f" );
                    boolean dirs  =  dirs_s != null && "1".equals(  dirs_s );
                    boolean files = files_s != null && "1".equals( files_s );
                    if( dirs != files )
                        se.setTypes( files );

                    String olo_s = uri.getQueryParameter( "o" );
                    if( olo_s != null && "1".equals( olo_s ) )
                        se.olo = true;
                    
                    se.content = uri.getQueryParameter( "c" );
                    se.larger_than = Utils.parseHumanSize( uri.getQueryParameter( "l" ) );
                    long st = Utils.parseHumanSize( uri.getQueryParameter( "s" ) );
                    if( st > 0 )
                        se.smaller_than = st;
                    java.text.DateFormat df = DateFormat.getDateFormat( ctx );
                    try {
                        se.after_date  = df.parse( uri.getQueryParameter( "a" ) );
                    } catch( Exception e ) {}
                    try {
                        se.before_date = df.parse( uri.getQueryParameter( "b" ) );
                    } catch( Exception e ) {}
                    
                    commander.startEngine( reader );
                    return true;
                }
            }
        } catch( Exception e ) {
            Log.e( TAG, "FindAdapter.readSource() exception: ", e );
        }
        Log.e( TAG, "FindAdapter unable to read by the URI '" + ( uri == null ? "null" : uri.toString() ) + "'" );
        uri = null;
        return false;
    }
    
    @Override
    public void openItem( int position ) {
        if( position == 0 ) { // ..
            if( uri != null ) {
                commander.Navigate( Uri.parse( uri.getPath() ), null, null );
            }
            return;
        }
        super.openItem( position );
    }
    
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        return to.receiveItems( bitsToNames( cis ), move ? MODE_MOVE : MODE_COPY );
    }

    @Override
    public boolean createFile( String fileURI ) {
        commander.showError( ctx.getString( R.string.not_supported ) );
        return false;
    }

    @Override
    public void createFolder( String string ) {
        commander.showError( ctx.getString( R.string.not_supported ) );
    }


    @Override
    public Uri getUri() {
        return uri;
    }
    @Override
    public String toString() {
        return uri != null ? Uri.decode( uri.toString() ) : "";
    }

    @Override
    public boolean receiveItems( String[] fileURIs, int move_mode ) {
        notify( ctx.getString( R.string.not_supported ),Commander.OPERATION_FAILED );
        return false;
    }

    class SearchEngine extends Engine {
        private String[] cards;
        private String  path; 
        private int     depth = 0;
        private String  pass_back_on_done;
        public  String  match, content;
        public  boolean olo = false;
        public  long    larger_than, smaller_than; 
        public  Date    after_date, before_date;
        private boolean dirs = true, files = true; 
        private ArrayList<File> result;
        private int     progress = 0;
        
        SearchEngine( Handler h, String match_, String path_, String pass_back_on_done_ ) {
            super.setHandler( h );
            if( match_.indexOf( '*' ) >= 0 ){
                cards = Utils.prepareWildcard( match_ );
                match = null;
            }
            else {
                cards = null;
                match = match_.toLowerCase();
            }
            path = path_;
            pass_back_on_done = pass_back_on_done_;
            larger_than = 0; 
           smaller_than = Long.MAX_VALUE; 
        }
        
        final void setTypes( boolean files_only ) {
            if( files_only ) {
                files = true;
                dirs = false;
            } else {
                files = false;
                dirs = true;
            } 
        }

        @Override
        public void setHandler( Handler h ) {
            // has its own handler
        }
            
        @Override
        public void run() {
            try {
                Init( null );
                result = new ArrayList<File>();
                searchInFolder( new File( path ) );
                sendProgress( tooLong( 8 ) ? ctx.getString( R.string.search_done ) : null, 
                        Commander.OPERATION_COMPLETED, pass_back_on_done );
            } catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED, pass_back_on_done );
            }
        }
        protected final void searchInFolder( File dir ) throws Exception {
            try {
                String dir_path = dir.getAbsolutePath();
                if( dir_path.compareTo( "/sys" ) == 0 ) return;
                if( dir_path.compareTo( "/dev" ) == 0 ) return;
                if( dir_path.compareTo( "/proc" ) == 0 ) return;
                File[] subfiles = dir.listFiles();
                if( subfiles == null || subfiles.length == 0 )
                    return;
                double conv = 100./subfiles.length;
                for( int i = 0; i < subfiles.length; i++ ) {
                    sleep( 1 );
                    if( stop || isInterrupted() ) 
                        throw new Exception( ctx.getString( R.string.interrupted ) );
                    File f = subfiles[i];
                    int np = (int)(i * conv);
                    if( np == 0 || np - 1 > progress )
                        sendProgress( f.getAbsolutePath(), progress = np );
                    //Log.v( TAG, "Looking at file " + f.getAbsolutePath() );
                    addIfMatched( f );
                    if( !olo && f.isDirectory() ) {
                        if( depth++ > 30 )
                            throw new Exception( ctx.getString( R.string.too_deep_hierarchy ) );
                        searchInFolder( f );
                        depth--;
                    }
                }
            } catch( Exception e ) {
                Log.e( TAG, "Exception on search: ", e );
            }
        }
        
        private final void addIfMatched( File f ) {
            if( f == null ) return;
            try {
                boolean dir = f.isDirectory();
                if( dir ) { 
                    if( !dirs  ) return;
                } else {
                    if( !files ) return;
                }
                long modified = f.lastModified();
                if(  after_date != null && modified <  after_date.getTime() ) return;  
                if( before_date != null && modified > before_date.getTime() ) return;  

                long size = f.length();
                if( size < larger_than || size > smaller_than ) 
                    return;
                
                if( cards != null && !Utils.match( f.getName(), cards ) ) 
                    return;
                if( match != null && !f.getName().toLowerCase().contains( match ) )
                    return;
                if( content != null && !dir && !searchInsideFile( f, content ) )
                    return;
                result.add( f );
            }
            catch( Exception e ) {
                Log.e( TAG, f.getName(), e );
            }
        }

        private final boolean searchInsideFile( File f, String s ) {
            try {
                BufferedReader br = new BufferedReader( new FileReader( f ) ); 
                final  int  l = s.length();
                int    ch = 0;
                int    cnt = 0, p = 0;
                double conv = 100./f.length();
                while( true ) {
                    for( int i = 0; i < l; i++ ) {
                        ch = br.read();
                        if( ch == -1 )
                            return false;
                        if( ch != s.charAt( i ) ) {
                            if( i > 0 )
                                br.reset();
                            break;
                        }
                        if( i == 0 )
                            br.mark( l );
                        if( i >= l-1 )
                            return true;
                    }
                    int np = (int)(cnt++ * conv);
                    if( np - 10 > p )
                        sendProgress( f.getAbsolutePath(), progress, p = np );
                    sleep( 1 );
                }
            }
            catch( InterruptedException e ) {
            }
            catch( Exception e ) {
                Log.e( TAG, "File: " + f.getName() + ", str=" + s, e );
            }
            return false;
        }
        
        public final FileItem[] getItems( int mode ) {
            if( result == null ) return null;
            File[] files_ = new File[result.size()];
            result.toArray( files_ );
            return filesToItems( files_ );
        }       
    }
    protected void onReadComplete() {
        onFileFound();
        startThumbnailCreation();
    }
    protected void onFileFound() {  
        try {
            if( reader instanceof SearchEngine ) {
                SearchEngine list_engine = (SearchEngine)reader;
                items = list_engine.getItems( mode );
                numItems = items != null ? items.length + 1 : 1;
                notifyDataSetChanged();
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    @Override
    public Object getItem( int position ) {
        try {
            Object o = super.getItem( position );
            if( o != null ) {
                if( o instanceof FileItem ) {
                    FileItem fi = (FileItem)o;
                    fi.name = fi.f().getAbsolutePath();
                }
                return o;
            }
        } catch( Exception e ) {
            Log.e( TAG, "getItem() Exception" );
        }
        return null;
    }    
}
