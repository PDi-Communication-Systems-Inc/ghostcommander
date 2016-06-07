package com.ghostsq.commander.utils;

import java.io.File;
import java.io.IOException;

import com.ghostsq.commander.R;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Video;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ListView;

public class ForwardCompat
{
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public static void disableOverScroll( View view ) {
        view.setOverScrollMode( View.OVER_SCROLL_NEVER );
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public static void setFullPermissions( File file ) {
        file.setWritable( true, false );
        file.setReadable( true, false );
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    public static void smoothScrollToPosition( ListView flv, int pos ) {
        flv.smoothScrollToPosition( pos );
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public static Drawable getLogo( PackageManager pm, ApplicationInfo pai ) {
        return pai.logo == 0 ? null : pm.getApplicationLogo( pai );
    }
    
    @TargetApi(Build.VERSION_CODES.FROYO)
    public static void scanMedia( final Context ctx, String[] to_scan_a ) {
        MediaScannerConnection.scanFile( ctx, to_scan_a, null, 
             new MediaScannerConnection.OnScanCompletedListener() {
                @Override
                public void onScanCompleted( final String path, final Uri uri ) {
                    File f = new File( path );
                    if( f.isFile() && f.length() == 0 ) {
                        if( ctx.getContentResolver().delete( uri, null, null ) > 0 ) {
                            Log.w( "scanMedia()", "Deleteing " + path );
                            f.delete();
                        }
                    }
                } 
             } );                    
    }
    
    @TargetApi(Build.VERSION_CODES.FROYO)
    public static File getExternalFilesDir( Context ctx ) { 
        return ctx.getExternalFilesDir( null );
    }
    
    @TargetApi(Build.VERSION_CODES.FROYO)
    public static String toBase64( byte[] in ) {
        return Base64.encodeToString( in, Base64.DEFAULT );
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    public static byte[] fromBase64( String in ) {
        return Base64.decode( in, Base64.DEFAULT );
    }

    public enum PubPathType {
        DOWNLOADS,
        DCIM,
        PICTURES,
        MUSIC,
        MOVIES          
    }
    
    @TargetApi(Build.VERSION_CODES.FROYO)
    public static String getPath( PubPathType ppt ) {
        String pps = null;
        switch( ppt ) {
        case DOWNLOADS: pps =  Environment.DIRECTORY_DOWNLOADS; break;
        case DCIM:      pps =  Environment.DIRECTORY_DCIM; break;
        case PICTURES:  pps =  Environment.DIRECTORY_PICTURES; break;
        case MUSIC:     pps =  Environment.DIRECTORY_MUSIC; break;
        case MOVIES:    pps =  Environment.DIRECTORY_MOVIES;
        }
        
        return Environment.getExternalStoragePublicDirectory( pps ).getAbsolutePath();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static SharedPreferences getDefaultSharedPreferences( Context ctx ) {
        return ctx.getSharedPreferences( ctx.getPackageName() + "_preferences", Context.MODE_MULTI_PROCESS );
    }    
    
    @TargetApi(Build.VERSION_CODES.ECLAIR)
    public static float getImageFileOrientationDegree( String path ) { 
        try {
            ExifInterface exif = new ExifInterface( path );
            int ov = exif.getAttributeInt( ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED );
            float degrees = 0;
            switch( ov ) {
            case ExifInterface.ORIENTATION_ROTATE_90:  degrees =  90; break;
            case ExifInterface.ORIENTATION_ROTATE_270: degrees = 270; break;
            }
            return degrees;
        } catch( IOException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return 0;
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    public static String getImageFileInfoHTML( String path ) { 
        try {
            StringBuilder sb = new StringBuilder( 100 );
            ExifInterface exif = new ExifInterface( path );
            int ov = exif.getAttributeInt( ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED );
            String os = null;
            switch( ov ) {
            case ExifInterface.ORIENTATION_NORMAL:          os = "Normal";      break;
            case ExifInterface.ORIENTATION_ROTATE_90:       os =  "90°";        break;
            case ExifInterface.ORIENTATION_ROTATE_270:      os = "270°";        break;
            case ExifInterface.ORIENTATION_ROTATE_180:      os = "180°";        break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: os = "Hor.flip";    break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:   os = "Ver.flip";    break;
            case ExifInterface.ORIENTATION_TRANSPOSE:       os = "Transposed";  break;
            case ExifInterface.ORIENTATION_TRANSVERSE:      os = "Transversed"; break;
            }
            final int INV = -1;
            if( os != null ) sb.append( "<b>Orientation:</b> " ).append( os );
            int wi = exif.getAttributeInt( ExifInterface.TAG_IMAGE_WIDTH, INV );
            if( wi > 0 ) sb.append( "<br/><b>Width:</b> " ).append( wi );
            int li = exif.getAttributeInt( ExifInterface.TAG_IMAGE_LENGTH, INV );
            if( li > 0 ) sb.append( "<br/><b>Height:</b> " ).append( li );
            if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB )
                getImageFileExtraInfo( exif, sb );
            int fe = exif.getAttributeInt( ExifInterface.TAG_FLASH, INV );
            if( fe > 0 ) {
                sb.append( "<br/><b>Flash:</b> " ).append( fe );
                int wb = exif.getAttributeInt( ExifInterface.TAG_WHITE_BALANCE, INV );
                if( wb != INV ) {
                    String ws = null; 
                    if( wb == ExifInterface.WHITEBALANCE_AUTO )   ws = "Auto";
                    if( wb == ExifInterface.WHITEBALANCE_MANUAL ) ws = "Manual";
                    if( ws != null ) sb.append( "<br/><b>WB:</b> " ).append( ws );
                }
            }
            String ma = exif.getAttribute( ExifInterface.TAG_MAKE );
            if( ma != null ) sb.append( "<br/><b>Make:</b> " ).append( ma );
            String mo = exif.getAttribute( ExifInterface.TAG_MODEL );
            if( mo != null ) sb.append( "<br/><b>Model:</b> " ).append( mo );
            String dt = exif.getAttribute( ExifInterface.TAG_DATETIME );
            if( dt != null ) sb.append( "<br/><b>Date:</b> " ).append( dt );
            return sb.toString();
        } catch( IOException e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void getImageFileExtraInfo( ExifInterface exif, StringBuilder sb ) { 
        String ap = exif.getAttribute( ExifInterface.TAG_APERTURE );
        if( ap != null ) sb.append( "<br/><b>Aperture:</b> f" ).append( ap );
        String ex = exif.getAttribute( ExifInterface.TAG_EXPOSURE_TIME );
        if( ex != null ) sb.append( "<br/><b>Exposure:</b> " ).append( ex ).append( "s" );
        String fl = exif.getAttribute( ExifInterface.TAG_FOCAL_LENGTH );
        if( fl != null ) sb.append( "<br/><b>Focal length:</b> " ).append( fl );
        String is = exif.getAttribute( ExifInterface.TAG_ISO );
        if( is != null ) sb.append( "<br/><b>ISO level:</b> " ).append( is );
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    public static Bitmap createVideoThumbnail( String path ) {
        return ThumbnailUtils.createVideoThumbnail( path, MediaStore.Images.Thumbnails.MINI_KIND );        
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    public static Bitmap getVideoThumbnail( ContentResolver cr, long id, int sample_size ) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample_size;
        return Video.Thumbnails.getThumbnail( cr, id, Video.Thumbnails.MINI_KIND, options );        
    }

    // http://stackoverflow.com/questions/14853039/how-to-tell-whether-an-android-device-has-hard-keys
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static boolean hasSoftKeys( Activity c ) {
        boolean hasSoftwareKeys = true;
    
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 ){
            Display d = c.getWindowManager().getDefaultDisplay();
    
            DisplayMetrics realDisplayMetrics = new DisplayMetrics();
            d.getRealMetrics( realDisplayMetrics );
    
            int realHeight = realDisplayMetrics.heightPixels;
            int realWidth = realDisplayMetrics.widthPixels;
    
            DisplayMetrics displayMetrics = new DisplayMetrics();
            d.getMetrics(displayMetrics);
    
            int displayHeight = displayMetrics.heightPixels;
            int displayWidth  = displayMetrics.widthPixels;
    
            hasSoftwareKeys = (realWidth - displayWidth) > 0 || (realHeight - displayHeight) > 0;
        } else {
            boolean hasMenuKey = ViewConfiguration.get(c).hasPermanentMenuKey();
            boolean hasBackKey = KeyCharacterMap.deviceHasKey( KeyEvent.KEYCODE_BACK );
            hasSoftwareKeys = !hasMenuKey && !hasBackKey;
        }
        return hasSoftwareKeys;
    }    

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static boolean hasPermanentMenuKey( Context ctx ) {
        return ViewConfiguration.get( ctx ).hasPermanentMenuKey();
    }
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static void setupActionBar( Activity a ) {
        a.getActionBar().setDisplayShowTitleEnabled( false );
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static Notification buildNotification( Context ctx, String str, PendingIntent pi ) {
         return new Notification.Builder( ctx )
                 .setContentTitle( str )
                 .setContentText( str )
                 .setSmallIcon( R.drawable.icon )
                 .setContentIntent( pi )
                 .build();
    }
    
}
