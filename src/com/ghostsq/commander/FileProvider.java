package com.ghostsq.commander;

import java.io.File;
import java.io.FileNotFoundException;

import com.ghostsq.commander.utils.Utils;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

public class FileProvider extends ContentProvider {
    private static final String TAG = "FileProvider";
    public static final String URI_PREFIX = "content://com.ghostsq.commander.FileProvider";
    public static final String AUTHORITY = "com.ghostsq.commander.FileProvider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType( Uri uri ) {
        String ext  = Utils.getFileExt( uri.getPath() );
        String mime = Utils.getMimeByExt( ext );
        return mime;
    }

    @Override
    public Cursor query( Uri uri, String[] as, String s, String[] as1, String s1 ) {
        Log.v( TAG, "query( " + uri + " )" );
        if( uri.toString().startsWith( URI_PREFIX ) ) {
            if( as == null || as.length == 0) {
                as = new String [] {
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE };
            } 
            MatrixCursor c = new MatrixCursor( as );
            MatrixCursor.RowBuilder row = c.newRow();
            File f = new File( uri.getPath() );
            if( !f.exists() || !f.isFile() )
                throw new RuntimeException( "No file name specified: " + uri );
            
            for( String col : as ) {
                if( MediaStore.MediaColumns.DATA.equals( col ) ) {
                    row.add( f.getAbsolutePath() );
                } else if( MediaStore.MediaColumns.MIME_TYPE.equals( col ) ) {
                    row.add( getType( uri ) );
                } else if( MediaStore.MediaColumns.DISPLAY_NAME.equals( col ) ) {
                    row.add( f.getName() );
                } else if( MediaStore.MediaColumns.SIZE.equals( col ) ) {
                    row.add( f.length() );
                } else {
                    // Unsupported or unknown columns are filled up with null
                    row.add(null);
                }
            }            
            return c;
        } else
            throw new RuntimeException( "Unsupported URI" );
    }
    
    @Override
    public ParcelFileDescriptor openFile( Uri uri, String mode ) throws FileNotFoundException {
        //Log.v( TAG, "openFile( " + uri + " )" );
        File file = new File( uri.getPath() );
        if( !file.exists() ) throw new FileNotFoundException();
        ParcelFileDescriptor parcel = ParcelFileDescriptor.open( file, ParcelFileDescriptor.MODE_READ_ONLY );
        return parcel;
    }

    @Override
    public int update( Uri uri, ContentValues contentvalues, String s, String[] as ) {
        return 0;
    }

    @Override
    public int delete( Uri uri, String s, String[] as ) {
        return 0;
    }

    @Override
    public Uri insert( Uri uri, ContentValues contentvalues ) {
        return null;
    }
}
