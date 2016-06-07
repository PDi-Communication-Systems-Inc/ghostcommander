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
import java.util.List;

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
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.widget.AdapterView;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class ContentAdapter extends CommanderAdapterBase implements Engines.IReciever {
    private final static String TAG    = "ContentAdapter";    // MediaStore
    public  final static String SCHEME = "content:";

    public  final static int FILES; 
    public  final static int AUDIO; 
    public  final static int ALBUMS; 
    public  final static int ARTISTS; 
    public  final static int GENRES; 
    public  final static int PLAYLISTS; 
    public  final static int VIDEO; 
    public  final static int IMAGES;
    private final static int[] parent_types;

    static {
        FILES    = Utils.mbAddSl(   MediaStore.Files.getContentUri( "external" ).getPath() ).hashCode();
        AUDIO    = Utils.mbAddSl(    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.getPath() ).hashCode(); 
        ALBUMS   = Utils.mbAddSl(   MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI.getPath() ).hashCode(); 
        GENRES   = Utils.mbAddSl(   MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI.getPath() ).hashCode(); 
        ARTISTS  = Utils.mbAddSl(  MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI.getPath() ).hashCode(); 
        PLAYLISTS= Utils.mbAddSl(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI.getPath() ).hashCode();
        IMAGES   = Utils.mbAddSl(   MediaStore.Images.Media.EXTERNAL_CONTENT_URI.getPath() ).hashCode(); 
        VIDEO    = Utils.mbAddSl(    MediaStore.Video.Media.EXTERNAL_CONTENT_URI.getPath() ).hashCode(); 
        parent_types = new int[] { ALBUMS, ARTISTS, GENRES, PLAYLISTS };
    }
    
    
    private   int    content_type;
    private   Uri    content_uri;
    protected Item[] items;
    private   ThumbnailsThread tht = null;
    
    public ContentAdapter( Context ctx_ ) {
        super( ctx_ );
        items = null;
    }

    @Override
    public String getScheme() {
        return "content";
    }
    
    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case LOCAL:
        case F8:
            return true;
        case SEND:
        case FS:
        case F2:
        case F6:
            return false;
        default: return super.hasFeature( feature );
        }
    }
    
    @Override
    public String toString() {
        Uri u = getUri();
        return u != null ? u.toString() : null;
    }

    @Override
    public int setMode( int mask, int val ) {
//        if( ( mask & ( MODE_WIDTH | MODE_DETAILS | MODE_ATTR ) ) == 0 )
        if( ( mask & ( MODE_WIDTH ) ) == 0 )
            super.setMode( mask, val );
        return mode;
    }    
    
    
    @Override
    public Uri getUri() {
        return content_uri;
    }

    @Override
    public void setUri( Uri uri ) {
        content_uri = uri;
        content_type = getType( uri );
    }

    public final static void populateHomeContextMenu( Context ctx, ContextMenu menu ) {
        final String vs = ctx.getString( R.string.view_title ); 
        menu.add( 0, ALBUMS,    0, vs + " \"Albums\"" );
        menu.add( 0, ARTISTS,   0, vs + " \"Artists\"" );
        menu.add( 0, GENRES,    0, vs + " \"Genres\"" );
        menu.add( 0, PLAYLISTS, 0, vs + " \"Playlists\"" );
        return;
    }
    
    public final static int getType( Uri uri ) {
        if( uri == null ) return -1;
        return Utils.mbAddSl( uri.getPath() ).hashCode();
    }

    public final static Uri getUri( int id ) {
        if( id == FILES )     return MediaStore.Files.getContentUri( "external" );
        if( id == AUDIO )     return MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        if( id == ALBUMS )    return MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
        if( id == ARTISTS )   return MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
        if( id == GENRES )    return MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;
        if( id == PLAYLISTS ) return MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
        if( id == VIDEO )     return MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        if( id == IMAGES )    return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        return null;
    }

    public final static int getIconId( int id ) {
        if( id == AUDIO )     return R.drawable.audio;
        if( id == VIDEO )     return R.drawable.video;
        if( id == IMAGES )    return R.drawable.image;
        return R.drawable.unkn;
    }
    
    private final static String[] getProjection( int id ) {
        if( id == ALBUMS ) return new String[] {
                              MediaStore.Audio.Albums._ID,
                              MediaStore.Audio.Albums.ALBUM,
                              MediaStore.Audio.Albums.NUMBER_OF_SONGS, 
                              MediaStore.Audio.Albums.ARTIST 
                            };
        if( id == ARTISTS ) return new String[] { 
                                MediaStore.Audio.Artists._ID,
                                MediaStore.Audio.Artists.ARTIST,
                                MediaStore.Audio.Artists.NUMBER_OF_TRACKS,
                                MediaStore.Audio.Artists.NUMBER_OF_ALBUMS
                            };
        if( id == GENRES )  return new String[] { 
                                MediaStore.Audio.Genres._ID,
                                MediaStore.Audio.Genres.NAME
                            };
        if( id == PLAYLISTS ) return new String[] { 
                                MediaStore.Audio.Playlists._ID,
                                MediaStore.Audio.Playlists.NAME
                            };
        return new String[] {
             MediaStore.MediaColumns._ID,
             MediaStore.MediaColumns.TITLE,
             MediaStore.MediaColumns.DATA,
             MediaStore.MediaColumns.DATE_MODIFIED,
             MediaStore.MediaColumns.SIZE
        };
    }

    private final static String getQueryParamName( int id ) {
        if( id == ALBUMS )    return "album";
        if( id == GENRES )    return "genre";
        if( id == ARTISTS )   return "artist";
        if( id == PLAYLISTS ) return "playlist";
        return null;
    }

    private final String getField( int id ) {
        if( id == content_type ) return BaseColumns._ID;
        if( id == ALBUMS )    return MediaStore.Audio.AudioColumns.ALBUM_ID;
        if( id == GENRES )    return MediaStore.Audio.Genres.Members.GENRE_ID;
        if( id == ARTISTS )   return MediaStore.Audio.AudioColumns.ARTIST_ID;
        if( id == PLAYLISTS ) return MediaStore.Audio.Playlists.Members.PLAYLIST_ID;
        return null;
    }
    
    @Override
    public boolean readSource( Uri new_uri, String pass_back_on_done ) {
        try {
            if( new_uri != null ) {
                setUri( new_uri );
            } else {
                if( content_uri == null )
                    return false;
            }
            
             String[] projection = getProjection( content_type );
    	     ContentResolver cr = ctx.getContentResolver();
             if( cr == null ) return false;
             
             StringBuilder     sb = new StringBuilder();
             ArrayList<String> sp = new ArrayList<String>();
             for( int i = 0; i < parent_types.length; i++ ) {
                 String parent_id = content_uri.getQueryParameter( getQueryParamName( parent_types[i] ) );
                 if( parent_id != null ) {
                     if( sb.length() > 0 ) sb.append( " and " );
                     sb.append( getField( parent_types[i] ) + " = ? " );
                     sp.add( parent_id );
                 }
             }
             Cursor cursor = cr.query( content_uri, projection, sb.length() > 0 ? sb.toString() : null, 
                                      sp.size() > 0 ? sp.toArray( new String[sp.size()] ) : null, null );
             if( cursor != null ) {
                try {
                   if( cursor.getCount() > 0 ) {
                      cursor.moveToFirst();
                      ArrayList<Item>   tmp_list = new ArrayList<Item>();
                      int icon_id = getIconId( content_type );
                      int pci = cursor.getColumnIndex( MediaStore.MediaColumns.DATA );
                      int sci = cursor.getColumnIndex( MediaStore.MediaColumns.SIZE );
                      int dci = cursor.getColumnIndex( MediaStore.MediaColumns.DATE_MODIFIED );
                      do {
                          Item item = new Item();
                          Uri item_uri = content_uri.buildUpon().appendEncodedPath( "" + cursor.getLong( 0 ) ).build();
                          item.origin = item_uri; 
                          item.name = cursor.getString( 1 );
                          if( item.name == null ) {
                              //Log.e( TAG, "Item " + item_uri + " has no name" );
                              item.name = "(?)";
                          }
                          item.dir = content_type == ALBUMS || content_type == ARTISTS || content_type == GENRES;
                          
                          if( MediaStore.MediaColumns.TITLE.equals( projection[1] ) ) {
                              item.attr = cursor.getString( pci );
                              item.size = cursor.getLong( sci );
                              item.date = new Date( cursor.getLong( dci ) * 1000 );

                              File f = new File( item.attr );
                              if( !f.exists() ) {
                                  item.colorCache = 0xFFFF0000;
                                  item.icon_id    = R.drawable.bad;
                              }
                              else
                                  item.icon_id = icon_id;
                          }
                          
                          tmp_list.add( item );
                      } while( cursor.moveToNext() );
                      cursor.close();
                      
                      
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
    	    //startThumbnailCreation();
            notify( pass_back_on_done );
            return true;
        } catch( Exception e ) {
            Log.e( TAG, "outer", e );
            notify( e.getMessage(), Commander.OPERATION_FAILED );
        } catch( OutOfMemoryError err ) {
            Log.e( TAG, "Out Of Memory", err );
            notify( s( R.string.oom_err ), Commander.OPERATION_FAILED );
		}
		return false;
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
        if( position == 0 ) {
            for( int i = 0; i < parent_types.length; i++ ) {
                 String parent_id = content_uri.getQueryParameter( getQueryParamName( parent_types[i] ) );
                 if( parent_id != null ) {
                     commander.Navigate( getUri( parent_types[i] ), null, null );
                     return;  
                 }
            }
            commander.Navigate( Uri.parse( "home:" ), null, null );
            return;
        }
        
        Item item = items[position - 1];
        if( item.dir ) {
            String qp = getQueryParamName( content_type );
            if( qp == null ) return;
            
            List<String> ups = ((Uri)item.origin).getPathSegments();
            if( ups == null || ups.size() == 0 ) return;
            String ctr_id = ups.get( ups.size()-1 );
            
            Uri.Builder bld = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon();
            bld.encodedQuery( qp + "=" + ctr_id );
            commander.Navigate( bld.build(), null, null );
        }
        else
            ;//commander.Open( Uri.parse( Utils.escapePath( dirName + item.name ) ), null );
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
            String dirName = Utils.mbAddSl( content_uri.getPath() );
            return position == 0 ? (new File( dirName )).getParent() : dirName + items[position - 1].name;
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
    }
	
	@Override
    public boolean renameItem( int position, String newName, boolean copy ) {
        if( position <= 0 || position > items.length ) 
            return false;
        try {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues cv = new ContentValues();
            // ????????????? cv.put( MediaStore.MediaColumns.DATA, newName );
            
            Item item = items[position-1];
            return 1 == cr.update( (Uri)item.origin, cv, null, null );
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
            ContentResolver cr = ctx.getContentResolver();
            InputStream is = cr.openInputStream( u );
            is.skip( skip );
            return is;
        } catch( Exception e ) {
            Log.e( TAG, u.toString(), e );
        }
        return null;
    }
    
    @Override
    public OutputStream saveContent( Uri u ) {
        return null;
    }
    
	@Override
	public boolean createFile( String fileURI ) {
		return false;
	}

	@Override
    public void createFolder( String new_name ) {
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
                String dirName = Utils.mbAddSl( ContentAdapter.this.content_uri.getPath() );
                int cnt = deleteItems( dirName, mList );
                sendResult( Utils.getOpReport( ctx, cnt, R.string.deleted ) );
            }
            catch( Exception e ) {
                sendProgress( e.getMessage(), Commander.OPERATION_FAILED_REFRESH_REQUIRED );
            }
        }
        private final int deleteItems( String base_path, Item[] l ) throws Exception {
    	    if( l == null ) return 0;
            int cnt = 0;
            int num = l.length;
            double conv = 100./num;

            for( int i = 0; i < num; i++ ) {
                sleep( 1 );
                if( isStopReq() )
                    throw new Exception( s( R.string.canceled ) );
                Item f = l[i];
                sendProgress( ctx.getString( R.string.deleting, f.name ), (int)(cnt * conv) );
                {
                     Uri c_uri = (Uri)f.origin;
                     if( c_uri != null ) {
                         cnt += cr.delete( c_uri, null, null );
                     }
                }
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
		return false;
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
