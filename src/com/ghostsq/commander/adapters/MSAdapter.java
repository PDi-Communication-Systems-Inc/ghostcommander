package com.ghostsq.commander.adapters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Arrays;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.adapters.Engine;
import com.ghostsq.commander.adapters.Engines.IReciever;
import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.MediaFile;
import com.ghostsq.commander.utils.Utils;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.widget.AdapterView;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class MSAdapter extends CommanderAdapterBase implements Engines.IReciever {
    private final static String TAG    = "MSAdapter";    // MediaStore
    public  final static String SCHEME = "ms:";
    public  final static int FILES     = MediaStore.Files.class.hashCode(); 
    public  final static int AUDIO     = MediaStore.Audio.class.hashCode(); 
    public  final static int VIDEO     = MediaStore.Video.class.hashCode(); 
    public  final static int IMAGES    = MediaStore.Images.class.hashCode(); 
    
    private   Uri    baseContentUri;
    private   Uri    ms_uri;
    protected Item[] items;
    private   ThumbnailsThread tht = null;
    
    public MSAdapter( Context ctx_ ) {
        super( ctx_ );
        items = null;
    }

    @Override
    public String getScheme() {
        return "ms";
    }
    
    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case FS:
        case LOCAL:
        case REAL:
        case SEND:
            return true;
        case F2:
        case F6:
            return false;
        default: return super.hasFeature( feature );
        }
    }
    
    @Override
    public String toString() {
        return getUri().toString();
    }

    /*
     * CommanderAdapter implementation
     */

    @Override
    public Uri getUri() {
        try {
            return ms_uri;
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void setUri( Uri uri ) {
        ms_uri = uri;
        String fr = ms_uri.getFragment();
        if( "Albums".equalsIgnoreCase( fr ) )
            baseContentUri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
        else
        if( "Artists".equalsIgnoreCase( fr ) )
            baseContentUri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
        else
        if( "Genres".equalsIgnoreCase( fr ) )
            baseContentUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;
        else
        if( "Playlists".equalsIgnoreCase( fr ) )
            baseContentUri = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
        else
        if( "Audio".equalsIgnoreCase( fr ) )
            baseContentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        else
        if( "Video".equalsIgnoreCase( fr ) )
            baseContentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        else
        if( "Images".equalsIgnoreCase( fr ) )
            baseContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        else
            baseContentUri = MediaStore.Files.getContentUri( "external" );
    }

    public final static void populateHomeContextMenu( Context ctx, ContextMenu menu ) {
        final String vs = ctx.getString( R.string.view_title ); 
        menu.add( 0, FILES,     0, vs + " \"Files\"" );
        menu.add( 0, AUDIO,     0, vs + " \"Audio\"" );
        menu.add( 0, VIDEO,     0, vs + " \"Video\"" );
        menu.add( 0, IMAGES,    0, vs + " \"Images\"" );
        return;
    }
    
    public final static String getFragment( int id ) {
        if( id == FILES )     return "Files";
        if( id == AUDIO )     return "Audio";
        if( id == VIDEO )     return "Video";
        if( id == IMAGES )    return "Images";
        return null;
    }
    
   private Uri getContentUri( String fullname ) {
        final String[] projection = {
             MediaStore.MediaColumns._ID,
             MediaStore.MediaColumns.DATA
        };
        Cursor cursor=null;
        ContentResolver cr = null;
      try {
         cr = ctx.getContentResolver();
         if( cr == null) return null;
         
         final String selection = MediaStore.MediaColumns.DATA + " = ? ";
         String[] selectionParams = new String[1];
         selectionParams[0] = fullname;
         cursor = cr.query( baseContentUri, projection, selection, selectionParams, null );
         if( cursor!=null ) {
            try {
               if( cursor.getCount() > 0 ) {
                  cursor.moveToFirst();
                  int  dci = cursor.getColumnIndex( MediaStore.MediaColumns.DATA );
                  String s = cursor.getString( dci );
                  if( !s.equals(fullname) )
                     return null;
                  int ici = cursor.getColumnIndex( MediaStore.MediaColumns._ID );
                  long id = cursor.getLong( ici );
                  return MediaStore.Files.getContentUri( "external", id );
               } 
            } catch( Throwable e ) {
               Log.e( TAG, "on result", e );
            }
            finally {
                cursor.close();
            }
         }
      } catch( Throwable e ) {
         Log.e( TAG, "on query", e );
      }
      return null;
   }     

    private void enumAudio() {
        Cursor cursor = null;
        try {
            ContentResolver cr = ctx.getContentResolver();
            cursor = cr.query(  MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, new String[] { 
                                MediaStore.Audio.Artists.ARTIST,
                                MediaStore.Audio.Artists.NUMBER_OF_TRACKS,
                                MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
                                MediaStore.Audio.Artists._ID }, 
                      null, null, null );

            if( cursor != null && cursor.getCount() > 0 ) {
                cursor.moveToFirst();
                do {
                    Log.v( TAG, "   " + cursor.getString( 0 ) + 
                                " ! " + cursor.getInt( 1 ) + 
                                " ! " + cursor.getInt( 2 ) +
                                " ! " + cursor.getInt( 3 ) );
                } while( cursor.moveToNext() );
            }
        } catch( Throwable e ) {
            Log.e( TAG, "on query", e );
        } finally {
            if( cursor != null )
                cursor.close();
        }
        try {
            ContentResolver cr = ctx.getContentResolver();
            cursor = cr.query( MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, new String[] { 
                              MediaStore.Audio.Albums.ALBUM,
                              MediaStore.Audio.Albums.NUMBER_OF_SONGS, 
                              MediaStore.Audio.Albums.ARTIST, 
                              MediaStore.Audio.Albums._ID }, 
                              null, null, null );

            if( cursor != null && cursor.getCount() > 0 ) {
                cursor.moveToFirst();
                do {
                    Log.v( TAG, "   " + cursor.getString( 0 ) + 
                                " ! " + cursor.getInt( 1 ) + 
                                " ! " + cursor.getString( 2 ) + 
                                " ! " + cursor.getInt( 3 ) );
                } while( cursor.moveToNext() );
            }
        } catch( Throwable e ) {
            Log.e( TAG, "on query", e );
        } finally {
            if( cursor != null )
                cursor.close();
        }
    }    
   
    @Override
    public boolean readSource( Uri new_uri, String pass_back_on_done ) {
        final String[] projection = {
                 MediaStore.MediaColumns._ID,
                 MediaStore.MediaColumns.DATA,
                 MediaStore.MediaColumns.DATE_MODIFIED,
                 MediaStore.MediaColumns.MIME_TYPE,
                 MediaStore.MediaColumns.SIZE,
                 MediaStore.MediaColumns.TITLE
        };
        try {
            if( new_uri != null ) {
                setUri( new_uri );
            } else {
                if( ms_uri == null )
                    setUri( Uri.parse( SCHEME + Environment.getExternalStorageDirectory().getAbsolutePath() ) );
            }
            String dirName = Utils.mbAddSl( ms_uri.getPath() );  
    	    parentLink = !Utils.str( dirName ) || SLS.equals( dirName ) ? SLS : PLS;
    	     ContentResolver cr = ctx.getContentResolver();
             if( cr == null ) return false;
             final String selection = MediaStore.MediaColumns.DATA + " like ? ";
             String[] selectionParams = new String[1];
             selectionParams[0] = dirName + "%";
             Cursor cursor = cr.query( baseContentUri, projection, selection, selectionParams, null );
             if( cursor != null ) {
                try {
                   if( cursor.getCount() > 0 ) {
                      cursor.moveToFirst();
                      ArrayList<Item>   tmp_list = new ArrayList<Item>();
                      ArrayList<String> subdirs  = new ArrayList<String>();
                      int ici = cursor.getColumnIndex( MediaStore.MediaColumns._ID );
                      int pci = cursor.getColumnIndex( MediaStore.MediaColumns.DATA );
                      int sci = cursor.getColumnIndex( MediaStore.MediaColumns.SIZE );
                      int mci = cursor.getColumnIndex( MediaStore.MediaColumns.MIME_TYPE );
                      int dci = cursor.getColumnIndex( MediaStore.MediaColumns.DATE_MODIFIED );
                      int cdl = Utils.mbAddSl( dirName ).length();
                    
                      boolean show_missed = Utils.str( ms_uri.getFragment() );
                      
                      do {
                          String path = cursor.getString( pci );
                          //Log.d( TAG, path );
                          if( path == null || !path.startsWith( dirName ) ) continue;
                          boolean missed = false;
                          File real_file = show_missed ? new File( path ) : null;
                          if( real_file != null && !real_file.exists() )
                              missed = true;
                          else {
                              int end_pos = path.indexOf( "/", cdl );
                              if( end_pos > 0 && path.length() > end_pos ) {
                                  String subdir = path.substring( cdl-1, end_pos );
                                  if( subdirs.indexOf( subdir ) < 0 )
                                      subdirs.add( subdir );
                                  continue;
                              }
                          }
                          String name = path.substring( cdl );
                          if( !Utils.str( name ) ) continue;
                          File f = new File( dirName, name );
                          Item item = new Item();
                          if( missed ) {
                              item.colorCache = 0xFFFF0000;
                              item.icon_id    = R.drawable.bad;
                          }
                          item.dir = f.isDirectory();
                          item.origin = MediaStore.Files.getContentUri( "external", cursor.getLong( ici ) );
                          item.name = ( item.dir ? "/" : "" ) + name;
                          item.size = cursor.getLong( sci );
                          item.date = new Date( cursor.getLong( dci ) * 1000 );
                          item.attr = cursor.getString( mci );
                          if( item.dir ) item.size = -1;
                          tmp_list.add( item );
                      } while( cursor.moveToNext() );
                      cursor.close();
                      
                      for( String sd : subdirs ) {
                          boolean has = false;
                          for( Item item : tmp_list ) {
                              if( item.name.equals( sd ) ) {
                                  has = true;
                                  break;
                              }
                          }
                          if( !has ) {
                              Item item = new Item();
                              item.dir = true;
                              item.name = sd;
                              tmp_list.add( item );
                          }                          
                      }
                      
                      items = new Item[tmp_list.size()];
                      tmp_list.toArray( items );
                      reSort( items );
                   }
                   else
                       items = new Item[0];
                   super.setCount( items.length );
                } catch( Throwable e ) {
                    Log.e( TAG, "inner", e );
                }
            }     	    
    	    startThumbnailCreation();
            notify( pass_back_on_done );
            return true;
        } catch( Exception e ) {
            Log.e( TAG, "outer", e );
        } catch( OutOfMemoryError err ) {
            Log.e( TAG, "Out Of Memory", err );
            notify( s( R.string.oom_err ), Commander.OPERATION_FAILED );
		}
		return false;
    }

    protected void startThumbnailCreation() {
        if( thumbnail_size_perc > 0 ) {
            //Log.i( TAG, "thumbnails " + thumbnail_size_perc );
            if( tht != null )
                tht.interrupt();
            tht = new ThumbnailsThread( this, new Handler() {
                public void handleMessage( Message msg ) {
                    notifyDataSetChanged();
                } }, Utils.mbAddSl( ms_uri.getPath() ), items );
            tht.start();
        }
    }
    
    @Override
    protected void reSort() {
        reSort( items );
    }
    public void reSort( Item[] items_ ) {
        if( items_ == null ) return;
        ItemComparator comp = new ItemComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Arrays.sort( items_, comp );
    }
   
    @Override
    public void openItem( int position ) {
        String dirName = Utils.mbAddSl( ms_uri.getPath() );
        if( position == 0 ) {
            if( parentLink == SLS || dirName == null ) 
                commander.Navigate( Uri.parse( HomeAdapter.DEFAULT_LOC ), null, null );
            else {
                String path = ms_uri.getPath();
                int len_ = path.length()-1;
                if( len_ > 0 ) {
                    if( path.charAt( len_ ) == SLC )
                        path = path.substring( 0, len_ );
                    path = path.substring( 0, path.lastIndexOf( SLC ) );
                    if( path.length() == 0 )
                        path = SLS;
                    commander.Navigate( ms_uri.buildUpon().encodedPath( path ).build(), null, ms_uri.getLastPathSegment() );
                } else
                    commander.Navigate( Uri.parse( HomeAdapter.DEFAULT_LOC ), null, null );
            }
        }
        else {
            Item item = items[position - 1];
            if( item.dir )
                commander.Navigate( ms_uri.buildUpon().appendEncodedPath( Utils.escapePath( item.name.replaceAll( "/", "" ) ) ).build(), null, null );
            else
                commander.Open( Uri.parse( Utils.escapePath( dirName + item.name ) ), null );
        }
    }

    @Override
    public Uri getItemUri( int position ) {
        try {
            String item_name = getItemName( position, true );
            return Uri.parse( SCHEME + Utils.escapePath( item_name ) );
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }
    @Override
    public String getItemName( int position, boolean full ) {
        if( position < 0 || items == null || position > items.length )
            return position == 0 ? parentLink : null;
        if( full ) {
            String dirName = Utils.mbAddSl( ms_uri.getPath() );
            return position == 0 ? (new File( dirName )).getParent() : dirName + items[position - 1].name.replace( "/", "" );
        }
        else {
            if( position == 0 ) return parentLink; 
            String name = items[position - 1].name;
            if( name != null )
                return name.replace( "/", "" );
        }
        return null;
    }

    @Override
    public void reqItemsSize( SparseBooleanArray cis ) {
        
        //enumAudio();
        
        Item[] list = bitsToItems( cis );
        if( list == null || list.length != 1 )
            return;
        notify( Commander.OPERATION_STARTED );

        String[] projection = null;

        String fr = ms_uri.getFragment();
        if( "Audio".equalsIgnoreCase( fr ) ) {
            String[] audio_projection = {
                MediaStore.MediaColumns.DATA,
                MediaStore.Audio.AudioColumns.ALBUM, 
                MediaStore.Audio.AudioColumns.ALBUM_ID, 
                MediaStore.Audio.AudioColumns.ARTIST, 
                MediaStore.Audio.AudioColumns.ARTIST_ID 
            };
            projection = audio_projection;
        } else if( "Video".equalsIgnoreCase( fr ) ) {
            String[] video_projection = {
                MediaStore.MediaColumns.DATA,
                MediaStore.Video.VideoColumns.ALBUM, 
                MediaStore.Video.VideoColumns.ARTIST 
            };
            projection = video_projection;
        } else if( "Images".equalsIgnoreCase( fr ) ) {
            String[] images_projection = {
                MediaStore.MediaColumns.DATA,
                MediaStore.Images.ImageColumns.ORIENTATION 
            };
            projection = images_projection;
        } else {
            String[] files_projection = {
                MediaStore.MediaColumns.DATA,
                MediaStore.Files.FileColumns.MEDIA_TYPE
            };
            projection = files_projection;
        }
/*
         final String selection = MediaStore.MediaColumns.DATA + " = ? ";
         String[] selectionParams = new String[1];
         selectionParams[0] = list[0].name;
*/
        
        Cursor cursor = null;
        ContentResolver cr = null;
        try {
            cr = ctx.getContentResolver();
//            cursor = cr.query( baseContentUri, projection, selection, selectionParams, null );
            cursor = cr.query( (Uri)list[0].origin, projection, null, null, null );
            if( cursor != null && cursor.getCount() > 0 ) {
                StringBuilder sb = new StringBuilder();
                do {
                    cursor.moveToFirst();
                    for( String col : projection ) {
                        sb.append( col );
                        sb.append( ": " );
                        sb.append( cursor.getString( cursor.getColumnIndex( col ) ) );
                        sb.append( "\n" );
                    }
                } while( cursor.moveToNext() );
                notify( sb.toString(), Commander.OPERATION_COMPLETED, 0 );
                return;
            }
        } catch( Throwable e ) {
            Log.e( TAG, "on query", e );
        }
        finally {
            cursor.close();
        }
        notify( Commander.OPERATION_FAILED );
    }
	
	@Override
    public boolean renameItem( int position, String newName, boolean copy ) {
        if( position <= 0 || position > items.length ) 
            return false;
        try {
            String dirName = Utils.mbAddSl( ms_uri.getPath() );
            ContentResolver cr = ctx.getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put( MediaStore.MediaColumns.DATA, dirName + newName );
            final String selection = MediaStore.MediaColumns.DATA + " = ? ";
            String[] selectionParams = new String[1];
            Item item = items[position-1];
            selectionParams[0] = dirName + item.name.replaceAll( "/", "" );
            if( item.dir )
                selectionParams[0] = Utils.mbAddSl( selectionParams[0] );
            return 1 == cr.update( baseContentUri, cv, selection, selectionParams );
        }
        catch( Exception e ) {
            commander.showError( ctx.getString( R.string.sec_err, e.getMessage() ) );
        }
        return false;
    }
	
    @Override
    public Item getItem( Uri u ) {
        try {
            if( !"ms".equals( u.getScheme() ) ) return null; 
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
            Uri c_uri = getContentUri( u.getPath() );
            ContentResolver cr = ctx.getContentResolver();
            InputStream is = cr.openInputStream( c_uri );
            if( skip > 0 )
                is.skip( skip );
            return is;
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    public OutputStream saveContent( Uri u ) {
        try {
            Uri c_uri = getContentUri( u.getPath() );
            ContentResolver cr = ctx.getContentResolver();
            return cr.openOutputStream( c_uri );
        } catch( FileNotFoundException e ) {
            Log.e( TAG, u.getPath(), e );
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
            MediaFile mf = new MediaFile( ctx, new File( Utils.mbAddSl( ms_uri.getPath() ), new_name ) );
            if( mf.mkdir() )
                notifyRefr( new_name );
            else {
                String err_str = ctx.getString( R.string.cant_md, new_name );
                if( android.os.Build.VERSION.SDK_INT >= 19 )
                    err_str += "\n" + ctx.getString( R.string.not_supported );
                notify( err_str, Commander.OPERATION_FAILED );
            }
        } catch( IOException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public boolean createFolderAbs( String abs_new_name ) {
      if( android.os.Build.VERSION.SDK_INT >= 19 )
          return false;
      String fn;
      Uri uri;
      ContentResolver cr;
      try {
         cr = ctx.getContentResolver();
         fn = Utils.mbAddSl( abs_new_name ) + "/dummy.jpg";
         ContentValues cv = new ContentValues();
         cv.put( MediaStore.MediaColumns.DATA, fn );
         cv.put( MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE );
         uri = cr.insert( MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv );
         if( uri != null ) {
             try {
                cr.delete( uri, null, null );
             } catch( Throwable e ) {
                 Log.e( TAG, "delete dummy file", e );
             }
             return true;
          } 
      } catch( Throwable e ) {
         Log.e( TAG, abs_new_name, e );
      }
      return false;
    }
	
    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
    	try {
        	Item[] list = bitsToItems( cis );
        	if( list != null ) {
        		notify( Commander.OPERATION_STARTED );
        		commander.startEngine( new DeleteEngine( list ) );
        	}
		} catch( Exception e ) {
		    notify( e.getMessage(), Commander.OPERATION_FAILED );
		}
        return false;
    }

	class DeleteEngine extends Engine {
		private Item[] mList;
        ContentResolver cr;

        DeleteEngine( Item[] list ) {
            setName( ".DeleteEngine" );
            mList = list;
        }
        @Override
        public void run() {
            try {
                Init( null );
                cr = ctx.getContentResolver();
                String dirName = Utils.mbAddSl( MSAdapter.this.ms_uri.getPath() );
                int cnt = deleteFiles( dirName, mList );
                sendResult( Utils.getOpReport( ctx, cnt, R.string.deleted ) );
            }
            catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED_REFRESH_REQUIRED );
            }
        }
        
        private final int deleteFiles( String base_path, Item[] l ) throws Exception {
            if( l == null ) return 0;
            int cnt = 0;
            int num = l.length;
            double conv = 100./num;
            boolean db_only = Utils.str( ms_uri.getFragment() );

            ContentValues cv = new ContentValues();
            cv.put( MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE );
            for( int i = 0; i < num; i++ ) {
                sleep( 1 );
                if( isStopReq() )
                    throw new Exception( s( R.string.canceled ) );
                Item f = l[i];
                sendProgress( ctx.getString( R.string.deleting, f.name ), (int)(cnt * conv) );
                if( f.dir ) {
                     final String selection = MediaStore.MediaColumns.DATA + " like ? ";
                     String[] selectionParams = new String[1];
                     selectionParams[0] = Utils.mbAddSl( base_path + l[i].name.replaceAll( "/", "" ) ) + "%";
                     if( !db_only )
                         cr.update( baseContentUri, cv, selection, selectionParams );
                     cnt += cr.delete( baseContentUri, selection, selectionParams );
                }
                {
                     Uri c_uri = (Uri)l[i].origin;
                     if( c_uri != null ) {
                         if( !db_only )
                             cr.update( c_uri, cv, null, null );                          
                         cnt += cr.delete( c_uri, null, null );
                     }
                }
                /*
                {
                    error( ctx.getString( R.string.cant_del, f.name ) );
                    break;
                }
                */
            }
            return cnt;
        }
    }

    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        boolean ok = to.receiveItems( bitsToNames( cis ), MODE_COPY );
        if( !ok ) {
            notify( Commander.OPERATION_FAILED );
            return ok;
        }
        return ok;
    }

    @Override
    public boolean receiveItems( String[] uris, int move_mode ) {
    	try {
            if( uris == null || uris.length == 0 )
            	return false;
            File[] list = Utils.getListOfFiles( uris );
            if( list != null ) {
                notify( Commander.OPERATION_STARTED );
                String dirName = Utils.mbAddSl( ms_uri.getPath() );
                commander.startEngine( new CopyEngine( list, dirName, move_mode ) );
	            return true;
            }
		} catch( Exception e ) {
		    e.printStackTrace();
		}
		return false;
    }

    class CopyEngine extends Engine {
        private String  mDest;
        private int     counter = 0, delerr_counter = 0, depth = 0;
        private long    totalBytes = 0;
        private double  conv;
        private File[]  fList = null;
        private ArrayList<String> to_scan;
        private boolean move, del_src_dir;
        private byte[]  buf;
        private static final int BUFSZ = 524288;
        private PowerManager.WakeLock wakeLock;

        CopyEngine( File[] list, String dest, int move_mode ) {
            super( null );
            setName( ".CopyEngine" );
            fList = list;
            mDest = dest;
            move = ( move_mode & MODE_MOVE ) != 0;
            del_src_dir = ( move_mode & MODE_DEL_SRC_DIR ) != 0;
            buf = new byte[BUFSZ];
            to_scan = new ArrayList<String>();                        
            PowerManager pm = (PowerManager)ctx.getSystemService( Context.POWER_SERVICE );
            wakeLock = pm.newWakeLock( PowerManager.PARTIAL_WAKE_LOCK, TAG );
        }
        @Override
        public void run() {
            sendProgress( ctx.getString( R.string.preparing ), 0, 0 );
            try {
                int l = fList.length;
                Item[] x_list = new Item[l];
                wakeLock.acquire();
//                long sum = getSizes( x_list );
//                conv = 100 / (double)sum;
                int num = copyFiles( fList, Utils.mbAddSl( mDest ) );

                if( del_src_dir ) {
                    File src_dir = fList[0].getParentFile();
                    if( src_dir != null )
                        src_dir.delete();
                }

                String[] to_scan_a = new String[to_scan.size()];
                to_scan.toArray( to_scan_a );
                ForwardCompat.scanMedia( ctx, to_scan_a );
                wakeLock.release();
                // XXX: assume (move && !del_src_dir)==true when copy from app: to the FS
                if( delerr_counter == counter ) move = false;  // report as copy
                String report = Utils.getOpReport( ctx, num, move && !del_src_dir ? R.string.moved : R.string.copied );
                sendResult( report );
            } catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED_REFRESH_REQUIRED );
                return;
            }
        }
        private final int copyFiles( File[] list, String dest ) throws InterruptedException {
            File file = null;
            for( int i = 0; i < list.length; i++ ) {
                boolean existed = false;
                InputStream  is = null;
                OutputStream os = null;
                //File outFile = null;
                file = list[i];
                if( file == null ) {
                    error( ctx.getString( R.string.unkn_err ) );
                    break;
                }
                String out_full_name = null;
                try {
                    if( isStopReq() ) {
                        error( ctx.getString( R.string.canceled ) );
                        break;
                    }
                    String fn = file.getName();
                    out_full_name = dest + fn;
                    if( file.isDirectory() ) {
                        if( depth++ > 40 ) {
                            error( ctx.getString( R.string.too_deep_hierarchy ) );
                            break;
                        }
                        File out_dir_file = new File( out_full_name );
                        if( !out_dir_file.exists() ) {
                            MediaFile mf = new MediaFile( ctx, new File( out_full_name ) );
                            if( !mf.mkdir() ) {
                                error( ctx.getString( R.string.not_supported ) );
                                break;
                            }
                        }
                        copyFiles( file.listFiles(), Utils.mbAddSl( out_full_name ) );
                        if( errMsg != null )
                            break;
                        depth--;
                        counter++;
                    }
                    else {
                        ContentResolver cr = ctx.getContentResolver();
                        Uri content_uri = getContentUri( out_full_name );
                        if( content_uri != null ) {
                            int res = askOnFileExist( ctx.getString( R.string.file_exist, out_full_name ), commander );
                            if( res == Commander.SKIP )  continue;
                            if( res == Commander.REPLACE ) {
                                Log.v( TAG, "Overwritting file " + out_full_name );
                            }
                            if( res == Commander.ABORT ) break;
                        } else {
                            ContentValues cv = new ContentValues();
                            cv.put( MediaStore.MediaColumns.DATA, out_full_name );
                            content_uri = cr.insert( baseContentUri, cv );
                        }                        
                        
                        is = new FileInputStream( file );
                        os = cr.openOutputStream( content_uri );
                        long copied = 0, size  = file.length();
                        
                        long start_time = 0;
                        int  speed = 0;
                        int  so_far = (int)(totalBytes * conv);
                        
                        String sz_s = Utils.getHumanSize( size );
                        int fnl = fn.length();
                        String rep_s = ctx.getString( R.string.copying, 
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
                                error( ctx.getString( R.string.canceled ) );
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
                            sendProgress( ctx.getString( R.string.copied_f, fn ) + sizeOfsize( copied, sz_s ), (int)(totalBytes * conv) );
                        to_scan.add( out_full_name );
                        counter++;
                    }
                    if( move ) {
                        if( !file.delete() ) {
                            sendProgress( ctx.getString( R.string.cant_del, fn ), -1 );
                            delerr_counter++;
                        }
                    }
                }
                catch( SecurityException e ) {
                    Log.e( TAG, "", e );
                    error( ctx.getString( R.string.sec_err, e.getMessage() ) );
                }
                catch( FileNotFoundException e ) {
                    Log.e( TAG, "", e );
                    error( ctx.getString( R.string.not_accs, e.getMessage() ) );
                }
                catch( ClosedByInterruptException e ) {
                    Log.e( TAG, "", e );
                    error( ctx.getString( R.string.canceled ) );
                }
                catch( IOException e ) {
                    Log.e( TAG, "", e );
                    String msg = e.getMessage();
                    error( ctx.getString( R.string.acc_err, out_full_name, msg != null ? msg : "" ) );
                }
                catch( RuntimeException e ) {
                    Log.e( TAG, "", e );
                    error( ctx.getString( R.string.rtexcept, out_full_name, e.getMessage() ) );
                }
                finally {
                    try {
                        if( is != null )
                            is.close();
                        if( os != null )
                            os.close();
                    }
                    catch( IOException e ) {
                        error( ctx.getString( R.string.acc_err, out_full_name, e.getMessage() ) );
                    }
                }
            }
            return counter;
        }
    }
        
    @Override
	public void prepareToDestroy() {
        super.prepareToDestroy();
		items = null;
	}

    public final Item[] bitsToItems( SparseBooleanArray cis ) {
        try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) && cis.keyAt( i ) > 0)
                    counter++;
            Item[] res = new Item[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) ) {
                    int k = cis.keyAt( i );
                    if( k > 0 )
                        res[j++] = items[ k - 1 ];
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
                return items[position - 1];
            }
            else {
                item = new Item();
                item.name = "???";
            }
        }
        return item;
    }

    @Override
    public IReciever getReceiver() {
        return this;
    }
}
