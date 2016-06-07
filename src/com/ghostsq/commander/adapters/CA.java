package com.ghostsq.commander.adapters;

import java.io.File;
import java.lang.reflect.Method;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.root.MountAdapter;
import com.ghostsq.commander.root.RootAdapter;
import com.ghostsq.commander.utils.Utils;

import dalvik.system.DexClassLoader;

/**
 * <code>CA</code> class
 * @author Ghost Squared (ghost.sq2@gmail.com)
 * 
 * This class is a "database" of all implemented adapters.
 * It keeps all the properties, hashes etc, to let the right adapter be instantiated
 * 
 */
final public class CA {
    public static final String TAG = "CA";

    // URI schemes hash codes
    protected static final int    home_schema_h =   "home".hashCode();  
    protected static final int     zip_schema_h =    "zip".hashCode();  
    protected static final int     ftp_schema_h =    "ftp".hashCode();  
    protected static final int    sftp_schema_h =   "sftp".hashCode();  
    protected static final int     smb_schema_h =    "smb".hashCode();  
    protected static final int    find_schema_h =   "find".hashCode();  
    protected static final int    root_schema_h =   "root".hashCode();  
    protected static final int     mnt_schema_h =  "mount".hashCode();  
    protected static final int    apps_schema_h =   "apps".hashCode();
    protected static final int    favs_schema_h =   "favs".hashCode();
    protected static final int    file_schema_h =   "file".hashCode();
    protected static final int      ms_schema_h =     "ms".hashCode();
    protected static final int content_schema_h ="content".hashCode();

    public final static boolean isLocal( String scheme ) {
        return scheme == null || scheme.length() == 0 || "file".equals( scheme ) || "find".equals( scheme );
    }
    
    public final static CommanderAdapter CreateAdapter( Uri uri, Commander c ) {
        CommanderAdapter ca = CreateAdapterInstance( uri, c.getContext() );
        if( ca != null )
            ca.Init( c );
        return ca;
    }    

    /**
     * @param scheme - the URI prefix
     * @param c      - current context
     * @return an instance of the adapter. 
     *   ??? should it return FSAdapter or null on a failure???
     */
    public final static CommanderAdapter CreateAdapterInstance( Uri uri, Context c ) {
        String scheme = uri.getScheme();
        if( !Utils.str( scheme ) ) return new FSAdapter( c );
        final int scheme_h = scheme.hashCode();
        if(   file_schema_h == scheme_h ) return new FSAdapter( c );
        if(   home_schema_h == scheme_h ) return new HomeAdapter( c );
        if(    zip_schema_h == scheme_h ) return new ZipAdapter( c ); 
        if(    ftp_schema_h == scheme_h ) return new FTPAdapter( c ); 
        if(   find_schema_h == scheme_h ) return new FindAdapter( c );
        if(   root_schema_h == scheme_h ) return new RootAdapter( c );
        if(    mnt_schema_h == scheme_h ) return new MountAdapter( c );
        if(   apps_schema_h == scheme_h ) return new AppsAdapter( c ); 
        if(   favs_schema_h == scheme_h ) return new FavsAdapter( c ); 
        if(     ms_schema_h == scheme_h ) return new MSAdapter( c ); 
        if(content_schema_h == scheme_h ) {
                return new ContentAdapter( c );
        }
        CommanderAdapter ca = CreateExternalAdapter( c, scheme );
        return ca == null ? new FSAdapter( c ) : ca;
    }
    
    /**
     * @param ctx - current context
     * @param scheme - the suffix of the plugin's package name
     * @return an instance of the adapter or null on failure
     */
//    @SuppressLint("NewApi")     // all of the sudden, lint considers the DexClassLoader.loadClass() as from a higher API, but according to the docs, the method belongs to API level 1
    @SuppressLint("NewApi")
    public static CommanderAdapter CreateExternalAdapter( Context ctx, String scheme ) {
        try {
            File dex_f = ctx.getDir( scheme, Context.MODE_PRIVATE );
            if( dex_f == null || !dex_f.exists() ) {
                Log.w( TAG, "app.data storage is not accessable, trying to use the SD card" );
                File sd = Environment.getExternalStorageDirectory();
                if( sd == null ) return null; // nowhere to store the dex :(
                dex_f = new File( sd, "temp" );
                if( !dex_f.exists() )
                    dex_f.mkdir();
            }
            String pkg_name = "com.ghostsq.commander." + ( "smb".equals( scheme ) ? "samba" : scheme ); 
            ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo( pkg_name, 0 );
            
            Log.i( TAG, scheme + " package is " + ai.sourceDir );
            
            ClassLoader pcl = ctx.getClass().getClassLoader();
            DexClassLoader cl = new DexClassLoader( ai.sourceDir, dex_f.getAbsolutePath(), null, pcl );
            //
            Class<?> creatorClass = cl.loadClass( pkg_name + "." + scheme );
            Log.i( TAG, "Class has been loaded " + creatorClass.toString() );
            
            try {
                File[] list = dex_f.listFiles();
                for( int i = 0; i < list.length; i++ )
                    list[i].delete();
            }
            catch( Exception e ) {
                Log.w( TAG, "Can't remove the plugin's .dex: ", e );
            }
            Method cim = creatorClass.getMethod( "createInstance", Context.class );
            if( cim != null )
                return (CommanderAdapter)cim.invoke( null, ctx ); 
        }
        catch( Throwable e ) {
            Log.e( TAG, "This class can't be created: " + scheme, e );
        }
        return null;
    }    

    public final static int getDrawableIconId( String scheme ) {
        if( !Utils.str( scheme ) ) return R.drawable.folder;
        final int scheme_h = scheme.hashCode();
        if(  home_schema_h == scheme_h )  return R.drawable.icon;
        if(   zip_schema_h == scheme_h )  return R.drawable.zip; 
        if(   ftp_schema_h == scheme_h )  return R.drawable.ftp; 
        if(  sftp_schema_h == scheme_h )  return R.drawable.sftp; 
        if(   smb_schema_h == scheme_h )  return R.drawable.smb; 
        if(  root_schema_h == scheme_h )  return R.drawable.root;
        if(   mnt_schema_h == scheme_h )  return R.drawable.mount;
        if(  apps_schema_h == scheme_h )  return R.drawable.android; 
        return R.drawable.folder;
    }
}
