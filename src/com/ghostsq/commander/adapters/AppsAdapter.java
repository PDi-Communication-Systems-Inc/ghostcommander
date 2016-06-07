package com.ghostsq.commander.adapters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;


import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.TextViewer;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.utils.MnfUtils;
import com.ghostsq.commander.utils.Utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.widget.AdapterView;

public class AppsAdapter extends CommanderAdapterBase {
    private final static String TAG = "AppsAdapter";
    public static final String DEFAULT_LOC = "apps:";
    public static final int LAUNCH_CMD = 9176, MANAGE_CMD = 7161, CREATE_APP_SHORTCUT = 3123; 
    // Java compiler creates a thunk function to access to the private owner class member from a subclass
    // to avoid that all the member accessible from the subclasses are public
    public final PackageManager     pm = ctx.getPackageManager();
    public  PackageInfo[]           pkgInfos = null;
    private final String MANIFEST = "Manifest", ACTIVITIES = "Activities", PROVIDERS = "Providers", SERVICES = "Services";  
    private       String MANAGE = "Manage", SHORTCUTS = "Shortcuts";
    private Item[]                  compItems = null;
    private ActivityInfo[]          actInfos = null;
    private ProviderInfo[]          prvInfos = null;
    private ServiceInfo[]           srvInfos = null;
    private ResolveInfo[]           resInfos = null;
    private IntentFilter[]          intFilters = null;
    private MnfUtils                manUtl = null;
    
    private Uri uri;
    
    public AppsAdapter( Context ctx_ ) {
        super( ctx_, DETAILED_MODE | NARROW_MODE | SHOW_ATTR );
        parentLink = PLS;
        MANAGE    = s( R.string.manage );
        SHORTCUTS = "Shortcuts"; //s( R.string.shortcuts );
    }

    @Override
    public String getScheme() {
        return "apps";
    }

    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case SORTING:
        case  F5:
        case  F8:
        case  SZ:
        case  SEL_UNS:
        case  SEND:
        case  CHKBL:
            return true;
        default: return super.hasFeature( feature );
        }
    }
    
    @Override
    public int setMode( int mask, int val ) {
        if( ( mask & ( MODE_WIDTH /*| MODE_DETAILS | MODE_ATTR*/ ) ) == 0 )
            super.setMode( mask, val );
        return mode;
    }    
    
    class ListEngine extends Engine {
        private PackageInfo[] items_tmp;
        public  String        pass_back_on_done;
        ListEngine( Handler h, String pass_back_on_done_ ) {
            super.setHandler( h );
            pass_back_on_done = pass_back_on_done_;
        }
        public PackageInfo[] getItems() {
            return items_tmp;
        }       
        @Override
        public void run() {
            try {
                Init( null );
                List<PackageInfo> all_packages = pm.getInstalledPackages( 0 );
                items_tmp = new PackageInfo[all_packages.size()];
                all_packages.toArray( items_tmp );
                Arrays.sort( items_tmp, new PackageInfoComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending ) );
                sendProgress( null, Commander.OPERATION_COMPLETED, pass_back_on_done );
            }
            catch( Exception e ) {
                sendProgress( "Fail", Commander.OPERATION_FAILED, pass_back_on_done );
            }
            catch( OutOfMemoryError err ) {
                sendProgress( "Out Of Memory", Commander.OPERATION_FAILED, pass_back_on_done );
            }
            finally {
                super.run();
            }
        }
    }
    @Override
    protected void onReadComplete() {
        if( reader instanceof ListEngine ) {
            ListEngine list_engine = (ListEngine)reader;
            pkgInfos = list_engine.getItems();
            reSort();
            setCount( pkgInfos != null ? pkgInfos.length+1 : 1 );
        }
    }
    
    @Override
    public String toString() {
        return uri.toString();
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
    public boolean readSource( Uri tmp_uri, String pbod ) {
        try {
            dirty = true;
            setCount( 1 );
            compItems = null;            
            pkgInfos = null;
            actInfos = null;
            prvInfos = null;
            srvInfos = null;
            resInfos = null;
            intFilters = null;
            super.setMode( ATTR_ONLY, 0 );
            if( reader != null ) {
                if( reader.reqStop() ) { // that's not good.
                    Thread.sleep( 500 );      // will it end itself?
                    if( reader.isAlive() ) {
                        Log.e( TAG, "Busy!" );
                        return false;
                    }
                }
            }
            if( tmp_uri != null )
                uri = tmp_uri;
            String a = uri.getAuthority(); 
            if( a == null || a.length() == 0 ) {    // enumerate the applications
                manUtl = null;
                notify( Commander.OPERATION_STARTED );
                reader = new ListEngine( readerHandler, pbod );
                reader.start();
                
                return true;
            }
            String path = uri.getPath();
            if( path == null || path.length() <= 1 ) {
                ArrayList<Item> ial = new ArrayList<Item>(); 
                setCount( 1 );
                Item manage_item = new Item( MANAGE );
                manage_item.setIcon( pm.getApplicationIcon( "com.android.settings" ) );
                manage_item.icon_id = R.drawable.and;
                ial.add( manage_item );
                Item manifest_item = new Item( MANIFEST );
                manifest_item.icon_id = R.drawable.xml;
                ial.add( manifest_item );
                PackageInfo pi = pm.getPackageInfo( a, PackageManager.GET_ACTIVITIES | 
                                                       PackageManager.GET_PROVIDERS | 
                                                       PackageManager.GET_SERVICES );
                if( pi.activities != null && pi.activities.length > 0 ) {
                    Item activities_item = new Item( ACTIVITIES );
                    activities_item.dir = true;
                    activities_item.size = pi.activities.length;
                    ial.add( activities_item );
                }
                if( pi.providers != null && pi.providers.length > 0 ) {
                    Item providers_item = new Item( PROVIDERS );
                    providers_item.dir = true;
                    providers_item.size = pi.providers.length;
                    ial.add( providers_item );
                }
                if( pi.services != null && pi.services.length > 0 ) {
                    Item services_item = new Item( SERVICES );
                    services_item.dir = true;
                    services_item.size = pi.services.length;
                    ial.add( services_item );
                }
                Item shortcuts_item = new Item( SHORTCUTS );
                shortcuts_item.dir = true;
                ial.add( shortcuts_item );
                
                // all items were created
                
                compItems = new Item[ial.size()];
                ial.toArray( compItems );
                setCount( compItems.length + 1 );
                notify( pbod );
                return true;
            }
            else { // the URI path contains something
                super.setMode( 0, ATTR_ONLY );
                List<String> ps = uri.getPathSegments();
                if( ps != null && ps.size() >= 1 ) {
                    if( SHORTCUTS.equals( ps.get( 0 ) ) ) {
                        Intent[] ins = new Intent[2];  
                        ins[0] = new Intent( Intent.ACTION_CREATE_SHORTCUT );
                        ins[1] = new Intent( Intent.ACTION_MAIN );
                        resInfos = getResolvers( ins, a );
                        if( resInfos != null )
                            setCount( resInfos.length + 1 );
                    } else 
                    if( ps.size() >= 2 && ACTIVITIES.equals( ps.get( 0 ) ) ) {
                        if( manUtl == null )
                            manUtl = new MnfUtils( pm, a );
                        intFilters = manUtl.getIntentFilters( ps.get( 1 ) );
                        if( intFilters != null )
                            setCount( intFilters.length + 1 );
                    } else {
                        PackageInfo pi = pm.getPackageInfo( a, PackageManager.GET_ACTIVITIES | 
                                                               PackageManager.GET_PROVIDERS | 
                                                               PackageManager.GET_SERVICES );
                        if( ACTIVITIES.equals( ps.get( 0 ) ) ) {
                            actInfos = pi.activities != null ? pi.activities : new ActivityInfo[0];
                            reSort();
                            setCount( actInfos.length + 1 );
                        } else if( PROVIDERS.equals( ps.get( 0 ) ) ) {
                            prvInfos = pi.providers != null ? pi.providers : new ProviderInfo[0];
                            setCount( prvInfos.length + 1 );
                        } else if( SERVICES.equals( ps.get( 0 ) ) ) {
                            srvInfos = pi.services != null ? pi.services : new ServiceInfo[0];
                            setCount( srvInfos.length + 1 );
                        }
                    }
                    notify( pbod );
                    return true;
                }
            }
        }
        catch( Exception e ) {
            Log.e( TAG, uri != null ? uri.toString() : null, e );
            notify( uri != null ? s( R.string.failed ) + s( R.string.pkg_name ) + ":\n" + uri.getAuthority() : 
                                  s( R.string.fail ), pbod );
            return false;
        }
        notify( s( R.string.fail ), pbod );
        return false;
    }
    
   
    @Override
    protected void reSort() {
        if( pkgInfos != null ) { 
            PackageInfoComparator comp = new PackageInfoComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
            Arrays.sort( pkgInfos, comp );
        }
        else if( actInfos != null ) {
            ActivityInfoComparator comp = new ActivityInfoComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
            Arrays.sort( actInfos, comp );
        }
        else if( prvInfos != null ) {
        }
        else {
        }
    }
    
    private final ResolveInfo[] getResolvers( Intent[] ins, String package_name ) {
        try {
            final int fl = PackageManager.GET_INTENT_FILTERS | PackageManager.GET_RESOLVED_FILTER;
            List<ResolveInfo> tmp_list = new ArrayList<ResolveInfo>();
            for( Intent in : ins ) {
                List<ResolveInfo> resolves = pm.queryIntentActivities( in, fl );
                for( ResolveInfo res : resolves ) {
                    if( package_name.equals( res.activityInfo.applicationInfo.packageName ) )
                        tmp_list.add( res );
                }
            }
            if( tmp_list.size() > 0 ) {
                ResolveInfo[] ret = new ResolveInfo[tmp_list.size()];
                return tmp_list.toArray( ret );
            }
        } catch( Exception e ) {
            Log.e( TAG, "For: " + package_name, e );
        }
        return null;
    }    
    
    private static <T> ArrayList<T> bitsToItems( SparseBooleanArray cis, T[] items ) {
        try {
            if( items == null ) return null;
            ArrayList<T> al = new ArrayList<T>();
            for( int i = 0; i < cis.size(); i++ ) {
                if( cis.valueAt( i ) ) {
                    int k = cis.keyAt( i );
                    if( k > 0 )
                        al.add( items[ k - 1 ] );
                }
            }
            return al;
        } catch( Exception e ) {
            Log.e( TAG, "bitsToNames()'s Exception: " + e );
        }
        return null;
    }
    
    private String[] flagsStrs = {
        "SYSTEM",
        "DEBUGGABLE",
        "HAS_CODE",
        "PERSISTENT",
        "FACTORY_TEST",
        "ALLOW_TASK_REPARENTING",
        "ALLOW_CLEAR_USER_DATA",
        "UPDATED_SYSTEM_APP",
        "TEST_ONLY",
        "SUPPORTS_SMALL_SCREENS",
        "SUPPORTS_NORMAL_SCREENS",
        "SUPPORTS_LARGE_SCREENS",
        "RESIZEABLE_FOR_SCREENS",
        "SUPPORTS_SCREEN_DENSITIES",
        "VM_SAFE_MODE",
        "ALLOW_BACKUP",
        "KILL_AFTER_RESTORE",
        "RESTORE_ANY_VERSION",
        "EXTERNAL_STORAGE",
        "SUPPORTS_XLARGE_SCREENS",
        "NEVER_ENCRYPT",
        "FORWARD_LOCK",
        "CANT_SAVE_STATE" 
    };

    private final String getGroupName( int gid ) {
        switch( gid ) {
        case     0: return "root";           /* traditional unix root user */
        case  1000: return "system";         /* system server */
        case  1001: return "radio";          /* telephony subsystem, RIL */
        case  1002: return "bluetooth";      /* bluetooth subsystem */
        case  1003: return "graphics";       /* graphics devices */
        case  1004: return "input";          /* input devices */
        case  1005: return "audio";          /* audio devices */
        case  1006: return "camera";         /* camera devices */
        case  1007: return "log";            /* log devices */
        case  1008: return "compass";        /* compass device */
        case  1009: return "mount";          /* mountd socket */
        case  1010: return "wifi";           /* wifi subsystem */
        case  1011: return "adb";            /* android debug bridge (adbd) */
        case  1012: return "install";        /* group for installing packages */
        case  1013: return "media";          /* mediaserver process */
        case  1014: return "dhcp";           /* dhcp client */
        case  1015: return "sdcard_rw";      /* external storage write access */
        case  1016: return "vpn";            /* vpn system */
        case  1017: return "keystore";       /* keystore subsystem */
        case  1018: return "usb";            /* USB devices */
        case  1019: return "drm";            /* DRM server */
        case  1020: return "available";      /* available for use */
        case  1021: return "gps";            /* GPS daemon */
        case  1023: return "media_rw";       /* internal media storage write access */
        case  1024: return "mtp";            /* MTP USB driver access */
        case  1025: return "nfc";            /* nfc subsystem */
        case  1026: return "drmrpc";         /* group for drm rpc */                    
        case  2000: return "shell";          /* adb and debug shell user */
        case  2001: return "cache";          /* cache access */
        case  2002: return "diag";           /* access to diagnostic resources */
        case  3001: return "net_bt_admin";   /* bluetooth: create any socket */
        case  3002: return "net_bt";         /* bluetooth: create sco, rfcomm or l2cap sockets */
        case  3003: return "inet";           /* can create AF_INET and AF_INET6 sockets */
        case  3004: return "net_raw";        /* can create raw INET sockets */
        case  3005: return "net_admin";
        case  9998: return "misc";           /* access to misc storage */
        case  9999: return "nobody";
        default:    return gid >= 10000 ? "app_" + ( gid - 10000 ) : "?";
        }
    }    
    
    @Override
    public void reqItemsSize( SparseBooleanArray cis ) {
        ArrayList<PackageInfo> pl = null;
        if( pkgInfos == null ) {
            pl = new ArrayList<PackageInfo>( 1 );
            try {
                pl.add( pm.getPackageInfo( uri.getAuthority(), 0 ) );
            } catch( Exception e ) {
                Log.e( TAG, uri.getAuthority(), e );
            }
        }
        else
           pl = bitsToItems( cis, pkgInfos );
        if( pl == null || pl.size() == 0 ) {
            notErr();
            return;
        }
        final String cs = ": ";
        StringBuffer sb = new StringBuffer( 1024 );
        for( int i = 0; i < pl.size(); i++ ) {
            try {
                PackageInfo pi = pm.getPackageInfo( pl.get( i ).packageName, PackageManager.GET_GIDS | 
                                                                             PackageManager.GET_PERMISSIONS ); // PackageManager.GET_SIGNATURES
                if( pi == null )
                    continue;
                String v = null;
                int    vc = 0;
                String size = null;
                String date = null;
                String flags = null;
                String gids = null;            
                try {
                    v  = pi.versionName;
                    vc = pi.versionCode;
                    if( pi.gids != null && pi.gids.length > 0 ) {
                        StringBuffer gsb = new StringBuffer( 128 );
                        for( int gi = 0; gi < pi.gids.length; gi++ ) {
                            if( gi > 0 )
                                gsb.append( ", " );
                            int g = pi.gids[gi];
                            gsb.append( g ).append( "(" ).append( getGroupName( g ) ).append( ")" );
                        }
                        gids = gsb.toString();
                    }
                    
                } catch( Exception e ) {}

                sb.append( s( R.string.pkg_name ) ).append( cs ).append( pi.packageName );
                if( v != null ) 
                  sb.append( "\n" ).append( s( R.string.version ) ).append( cs ).append( v );
                if( vc > 0 )
                  sb.append( "\n" ).append( s( R.string.version_code ) ).append( cs ).append( vc );
                
                ApplicationInfo ai = pi.applicationInfo;
                if( ai != null ) {
                    File asdf = new File( ai.sourceDir );
                    date = getLocalDateTimeStr( new Date( asdf.lastModified() ) );
                    size = Utils.getHumanSize( asdf.length() );
                    StringBuffer fsb = new StringBuffer( 512 );
                    int ff = ai.flags;
                    for( int fi = 0; fi < flagsStrs.length; fi++ ) {
                        if( ( ( 1<<fi ) & ff ) != 0 ) {
                            if( fsb.length() > 0 )
                                fsb.append( " | " );
                            fsb.append( flagsStrs[fi] );
                        }
                    }
                    fsb.append( " (" ).append( Integer.toHexString( ff ) ).append( ")" );
                    flags = fsb.toString();

                    sb.append( "\n" ).append( s( R.string.target_sdk ) ).append( cs ).append( ai.targetSdkVersion );
                    sb.append( "\n" ).append( "UID" ).append( cs ).append( ai.uid );
                    if( gids != null )
                      sb.append( "\n" ).append( "GIDs" ).append( cs ).append( gids );
                    sb.append( "\n" ).append( s( R.string.location ) ).append( cs ).append( ai.sourceDir );
                    if( date != null )
                      sb.append( "\n" ).append( s( R.string.inst_date ) ).append( cs ).append( date );
                    if( size  != null )
                      sb.append( "\n" ).append( s( R.string.pkg_size ) ).append( cs ).append( size );
                    sb.append( "\n" ).append( s( R.string.process ) ).append( cs ).append( ai.processName );
                    if( ai.className != null )
                      sb.append( "\n" ).append( s( R.string.app_class ) ).append( cs ).append( ai.className );
                    if( ai.taskAffinity != null )
                      sb.append( "\n" ).append( s( R.string.affinity ) ).append( cs ).append( ai.taskAffinity );
                }
                StringBuffer psb = new StringBuffer( 512 );
                if( pi.requestedPermissions != null ) {
                    for( int rpi = 0; rpi < pi.requestedPermissions.length; rpi++ ) {
                        if( rpi > 0 )
                            psb.append( ", " );
                        String p = pi.requestedPermissions[rpi];
                        if( p.startsWith( "android.permission." ) )
                            p = p.substring( 19 );
                        psb.append( p );
                    }
                }
                if( pi.permissions != null ) {
                    psb.append( "\nDeclared:\n" );
                    for( int dpi = 0; dpi < pi.permissions.length; dpi++ ) {
                        if( dpi > 0 )
                            psb.append( ", " );
                        psb.append( pi.permissions[dpi].toString() );
                    }
                }
                
                if( psb.length() > 0 )
                  sb.append( "\n" ).append( s( R.string.permissions ) ).append( cs ).append( psb.toString() );
                if( flags != null )
                  sb.append( "\n\n" ).append( s( R.string.flags ) ).append( cs ).append( flags );
                sb.append( "\n" );
                if( pi.signatures != null ) {
                    sb.append( "\nSignatures:\n" );
                    for( int si = 0; si < pi.signatures.length; si++ ) {
                        if( si > 0 )
                            sb.append( ", " );
                        sb.append( pi.signatures[si].toCharsString() );
                    }
                }
                
            }
            catch( Exception e ) {
                e.printStackTrace();
            }
        }
        notify( sb.toString(), Commander.OPERATION_COMPLETED, Commander.OPERATION_REPORT_IMPORTANT );
    }

    class CopyFromEngine extends Engine 
    {
        private ArrayList<PackageInfo> pl;

        CopyFromEngine( ArrayList<PackageInfo> list_, Engines.IReciever recipient_ ) {
            super( recipient_ );
            pl = list_;
        }
        @Override
        public void run() {
            String tmp_path = Utils.mbAddSl( createTempDir() );
            for( int i = 0; i < pl.size(); i++ ) {
                ApplicationInfo ai = pl.get( i ).applicationInfo;
                if( ai != null ) {
                    try {
                        PackageInfo pi = pm.getPackageInfo( pl.get( i ).packageName, PackageManager.GET_GIDS | 
                                                                                     PackageManager.GET_PERMISSIONS ); // PackageManager.GET_SIGNATURES
                        String tmp_n = tmp_path + pi.packageName + "_" + pi.versionName + ".apk";
                        FileInputStream  fis = new FileInputStream( ai.sourceDir );
                        FileOutputStream fos = new FileOutputStream( tmp_n );
                        Utils.copyBytes( fis, fos );
                    } catch( Exception e ) {
                        Log.e( TAG, "tmp apk creation", e );
                    }
                }
            }
            sendReceiveReq( new File( tmp_path ) );
            return;
        }
    }    
    
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
        if( pkgInfos != null ) { 
            ArrayList<PackageInfo> pl = bitsToItems( cis, pkgInfos );
            if( pl == null || pl.size() == 0 ) {
                notify( s( R.string.copy_err ), Commander.OPERATION_FAILED );
                return false;
            }
            notify( Commander.OPERATION_STARTED );
            commander.startEngine( new CopyFromEngine( pl, to.getReceiver() ) );
            return true;
        }
        if( compItems != null ) {
            ArrayList<Item> il = bitsToItems( cis, compItems );
            if( il != null ) {
                for( int i = 0; i < il.size(); i++ ) {
                    if( MANIFEST.equals( il.get( i ).name ) ) {
                        try {
                            ApplicationInfo ai = pm.getApplicationInfo( uri.getAuthority(), 0 );
                            if( manUtl == null )
                                manUtl = new MnfUtils( pm, ai.packageName );
                            String m = manUtl.extractManifest();
                            if( m != null && m.length() > 0 ) {
                                String tmp_fn = ai.packageName + ".xml";
                                FileOutputStream fos = ctx.openFileOutput( tmp_fn, Context.MODE_WORLD_WRITEABLE|Context.MODE_WORLD_READABLE);
                                if( fos != null ) {
                                    OutputStreamWriter osw = new OutputStreamWriter( fos );
                                    osw.write( m );
                                    osw.close();
                                    String[] paths = new String[1];
                                    paths[0] = ctx.getFileStreamPath( tmp_fn ).getAbsolutePath();
                                    boolean ok = to.receiveItems( paths, MODE_COPY );
                                    if( !ok ) notify( Commander.OPERATION_FAILED );
                                    return ok;
                                }
                            }
                        } catch( Exception e ) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return notErr();
    }
        
    @Override
    public boolean createFile( String fileURI ) {
        return notErr();
    }

    @Override
    public void createFolder( String new_name ) {
        notErr();
    }
    @Override
    public boolean deleteItems( SparseBooleanArray cis ) {
        if( pkgInfos == null ) return notErr();
        ArrayList<PackageInfo> al = bitsToItems( cis, pkgInfos );
        if( al == null ) return false;
        for( int i = 0; i < al.size(); i++ ) {
            Intent in = new Intent( Intent.ACTION_DELETE, Uri.parse( "package:" + al.get( i ).packageName ) );
            commander.issue( in, 0 );
        }
        return true;
    }
    @Override
    public boolean receiveItems( String[] full_names, int move_mode ) {
        return notErr();
    }
    
    @Override
    public boolean renameItem( int position, String newName, boolean c ) {
        return notErr();
    }

    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        try {
            if( acmi.position > 0 ) { 
                if( pkgInfos != null ) {
                    ApplicationInfo ai = pkgInfos[acmi.position-1].applicationInfo;
                    if( ai != null ) {
                        String name = ai.loadLabel( pm ).toString();
                        menu.add( 0, LAUNCH_CMD, 0, ctx.getString( R.string.launch ) + " \"" + name + "\"" );
                    }
                    menu.add( 0, MANAGE_CMD, 0, MANAGE );
                    menu.add( 0, Commander.SEND_TO, 0, R.string.send_to );
                }
                else if( resInfos != null ) {
                    ResolveInfo ri = resInfos[acmi.position-1];
                    if( ri.filter != null && ri.filter.matchAction( Intent.ACTION_MAIN ) )
                        menu.add( 0, CREATE_APP_SHORTCUT, 0, ctx.getString( R.string.shortcut ) );
                }
            }
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
        super.populateContextMenu( menu, acmi, num );
    }    

    @Override
    public void doIt( int command_id, SparseBooleanArray cis ) {
        try {
            if( pkgInfos != null ) {
                ArrayList<PackageInfo> pl = bitsToItems( cis, pkgInfos );
                if( pl == null || pl.size() == 0 ) return;
                ApplicationInfo ai = pl.get(0).applicationInfo;
                if( ai == null ) return;
                if( MANAGE_CMD == command_id ) {
                    managePackage( ai.packageName );
                    return;
                }
                if( LAUNCH_CMD == command_id ) {
                    Intent in = pm.getLaunchIntentForPackage( ai.packageName );
                    commander.issue( in, 0 );
                    return;
                }
            }
            else if( resInfos != null ) {
                if( CREATE_APP_SHORTCUT == command_id ) {
                    ArrayList<ResolveInfo> rl = bitsToItems( cis, resInfos );
                    if( rl == null || rl.size() == 0 ) return;
                    for( int i = 0; i < rl.size(); i++ ) {
                        ActivityInfo ai = rl.get( i ).activityInfo;
                        if( ai != null ) {
                            Bitmap ico = null;
                            Drawable drawable = ai.loadIcon( pm );
                            if( drawable instanceof BitmapDrawable ) {
                                BitmapDrawable bd = (BitmapDrawable)drawable;
                                ico = bd.getBitmap();
                            }
                            createDesktopShortcut( new ComponentName( ai.applicationInfo.packageName, ai.name ), 
                                                                      ai.loadLabel( pm ).toString(), ico );
                        }
                    }
                    return;
                }
            }
        } catch( Exception e ) {
            Log.e( TAG, "Can't do the command " + command_id, e );
        }
    }
    
    @Override
    public void openItem( int position ) {
        try {
            if( pkgInfos != null  ) {
                if( position == 0 ) {
                    commander.Navigate( Uri.parse( HomeAdapter.DEFAULT_LOC ), null, null );
                }
                else if( position <= pkgInfos.length ) {
                    PackageInfo pi = pkgInfos[position - 1];
                    commander.Navigate( uri.buildUpon().authority( pi.packageName ).build(), null, null );
                }
            } else if( actInfos != null ) {
                if( position == 0 ) {
                    commander.Navigate( uri.buildUpon().path( null ).build(), null, ACTIVITIES );
                }
                else if( position <= actInfos.length ) {
                    ActivityInfo act = actInfos[position-1];
                    if( act.exported )
                        commander.Navigate( uri.buildUpon().appendPath( act.name ).build(), null, null );
                    else
                        commander.showInfo( s( R.string.not_exported ) );
                }
            } else if( prvInfos != null || srvInfos != null ) {
                if( position == 0 ) {
                    commander.Navigate( uri.buildUpon().path( null ).build(), null, PROVIDERS );
                }
                else if( position <= prvInfos.length ) {
                    // ???
                }
            } else if( resInfos != null ) {
                if( position == 0 ) {
                    List<String> paths = uri.getPathSegments();
                    if( paths == null )
                        commander.Navigate( uri.buildUpon().path( null ).build(), null, null );
                    String p = paths.size() > 1 ? paths.get( paths.size()-2 ) : null; 
                    String n = paths.get( paths.size()-1 );
                    commander.Navigate( uri.buildUpon().path( p ).build(), null, n );
                }
                else if( position <= resInfos.length ) {
                    ResolveInfo  ri = resInfos[position - 1];
                    ActivityInfo ai = ri.activityInfo;
                    if( ri.filter.hasAction( Intent.ACTION_CREATE_SHORTCUT ) ) {
                        Intent in = new Intent( Intent.ACTION_CREATE_SHORTCUT );
                        in.setComponent( new ComponentName( ai.packageName, ai.name ) );
                        commander.issue( in, R.id.create_shortcut );
                    }
                    else if( ri.filter.hasAction( Intent.ACTION_MAIN ) ) {
                        Intent in = new Intent(Intent.ACTION_MAIN );
                        in.setComponent( new ComponentName( ai.packageName, ai.name ) );
                        commander.issue( in, 0 );
                        /*
                        Bitmap ico = null;
                        Drawable drawable = ai.loadIcon( pm );
                        if( drawable instanceof BitmapDrawable ) {
                            BitmapDrawable bd = (BitmapDrawable)drawable;
                            ico = bd.getBitmap();
                        }
                        createDesktopShortcut( new ComponentName( ai.packageName, ai.name ), 
                                ai.loadLabel( pm ).toString(), ico );
                        */
                    }
                }
            } else 
            if( intFilters != null ) {
                if( position == 0 ) {
                    List<String> paths = uri.getPathSegments();
                    if( paths == null )
                        commander.Navigate( uri.buildUpon().path( null ).build(), null, null );
                    String p = paths.size() > 1 ? paths.get( paths.size()-2 ) : null; 
                    String n = paths.get( paths.size()-1 );
                    commander.Navigate( uri.buildUpon().path( p ).build(), null, n );
                }
                else {
                    // ???
                }
            } else {
                if( position == 0 ) {
                    commander.Navigate( Uri.parse( DEFAULT_LOC ), null, uri.getAuthority() );
                    return;
                }
                String name = getItemName( position, false );
                if( MANAGE.equals( name ) ) {
                    managePackage( uri.getAuthority() );    
                }
                else if( MANIFEST.equals( name ) ) {
                    String a = uri.getAuthority();
                    ApplicationInfo ai = pm.getApplicationInfo( a, 0 );
                    if( manUtl == null )
                        manUtl = new MnfUtils( pm, a );
                    String m = manUtl.extractManifest();
                    if( m != null ) {
                        Intent in = new Intent( ctx, TextViewer.class );
                        in.setData( Uri.parse( TextViewer.STRURI ) );
                        in.putExtra( TextViewer.STRKEY, m );
                        commander.issue( in, 0 );
                    }
                }
                else 
                    commander.Navigate( uri.buildUpon().path( name ).build(), null, null );
            }
        } catch( Exception e ) {
            Log.e( TAG, uri.toString() + " " + position, e );
        }
    }

    private final void managePackage( String p ) {
        try {
            Intent in = new Intent( Intent.ACTION_VIEW );
            in.setClassName( "com.android.settings", "com.android.settings.InstalledAppDetails" );
            in.putExtra( "com.android.settings.ApplicationPkgName", p );
            in.putExtra( "pkg", p );

            List<ResolveInfo> acts = pm.queryIntentActivities( in, 0 );
            if( acts.size( ) > 0 )
                commander.issue( in, 0 );
            else {
                in = new Intent( "android.settings.APPLICATION_DETAILS_SETTINGS", Uri.fromParts( "package", p, null ) );
                acts = pm.queryIntentActivities( in, 0 );
                if( acts.size( ) > 0 )
                    commander.issue( in, 0 );
                else {
                    Log.e( TAG, "Failed to resolve activity for InstalledAppDetails" );
                }
            }
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
    }
    
    @Override
    public String getItemName( int position, boolean full ) {
        if( position < 0 )
            return null;
        if( position == 0 )
            return parentLink;
        try {
            int idx = position - 1;
            if( pkgInfos != null ) {
                return position <= pkgInfos.length ? pkgInfos[idx].packageName : null;
            }
            if( compItems != null ) {
                return position <= compItems.length ? compItems[idx].name : null;
            }
            if( actInfos != null ) {
                return position <= actInfos.length ? actInfos[idx].name : null;
            }
            if( prvInfos != null ) {
                return position <= prvInfos.length ? prvInfos[idx].toString() : null;
            }
            if( srvInfos != null ) {
                return position <= srvInfos.length ? srvInfos[idx].toString() : null;
            }
            if( resInfos != null ) {
                return position <= resInfos.length ? resInfos[idx].toString() : null;
            }
            if( intFilters != null ) {
                return position <= intFilters.length ? intFilters[idx].toString() : null;
            }
        }
        catch( Exception e ) {
            Log.e( TAG, "pos=" + position, e );
        }
        return null;
    }

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        if( position == 0 ) {
            item.name = parentLink;
            item.dir = true;
            return item;
        }
        item.name = "???";
        if( pkgInfos != null ) { 
            if( position >= 0 && position <= pkgInfos.length ) {
                PackageInfo pi = pkgInfos[position - 1];
                ApplicationInfo ai = pi.applicationInfo;
                item.dir = false;
                item.name = ai != null ? ai.loadLabel( pm ).toString() : pi.packageName;
                item.sel = false;
                File asdf = new File( ai.sourceDir );
                item.date = new Date( asdf.lastModified() );
                item.size = asdf.length();
                if( ai != null )
                    item.attr = ai.packageName;
                item.setIcon( ai.loadIcon( pm ) );
                item.origin = new File( ai.sourceDir );
            }
        }
        else if( actInfos != null ) {
            if( position <= actInfos.length ) {
                ActivityInfo ai = actInfos[position - 1];
                item.name = ai.loadLabel( pm ).toString();
                if( !ai.exported )
                    item.name += " - private"; 
                item.attr = ai.name;
                item.setIcon( ai.loadIcon( pm ) );
            }
        }
        else if( prvInfos != null ) {
            if( position <= prvInfos.length ) {
                ProviderInfo pi = prvInfos[position - 1];
                item.name = pi.loadLabel( pm ).toString(); 
                item.attr = pi.name;
                item.setIcon( pi.loadIcon( pm ) );
            }
        }
        else if( srvInfos != null ) {
            if( position <= srvInfos.length ) {
                ServiceInfo si = srvInfos[position - 1];
                item.name = si.loadLabel( pm ).toString(); 
                item.attr = si.name;
                item.setIcon( si.loadIcon( pm ) );
            }
        }
        else if( resInfos != null ) {
            try {
                if( position <= resInfos.length ) {
                    ResolveInfo ri = resInfos[position - 1];
                    IntentFilter inf = ri.filter;
                    if( inf != null ) {
                        ActivityInfo ai = ri.activityInfo;
                        item.name = ai.loadLabel( pm ).toString(); 
                        item.attr = ai.name;
                        item.setIcon( ai.loadIcon( pm ) );
                        if( ri.filter.hasAction( Intent.ACTION_CREATE_SHORTCUT )  )
                            item.name += " - CREATE_SHORTCUT";
                        if( ri.filter.hasAction( Intent.ACTION_MAIN ) )
                            item.name += " - MAIN";
                    }
                    else {
                        item.name = ri.loadLabel( pm ).toString();
                        item.attr = ri.toString();
                    }
                    item.setIcon( ri.loadIcon( pm ) );
                }
            }
            catch( Exception e ) {
                Log.e( TAG, "pos=" + position, e );
            }
        }
        else if( intFilters != null ) {
            if( position <= intFilters.length ) {
                IntentFilter inf = intFilters[position - 1];
                String action = inf.getAction( 0 );
                item.name = action != null ? action : inf.toString();
                StringBuilder sb = new StringBuilder( 128 );
                int n = inf.countDataTypes();
                if( n > 0 ) {
                    sb.append( "types=" );
                    for( int i = 0; i< n; i++ ) {
                        if( i != 0 )
                            sb.append( ", " );
                        String dt = inf.getDataType( i ); 
                        sb.append( dt );
                    }
                    sb.append( "; " );
                }
                n = inf.countCategories();
                if( n > 0 ) {
                    sb.append( "categories=" );
                    for( int i = 0; i< n; i++ ) {
                        if( i != 0 )
                            sb.append( ", " );
                        String ct = inf.getCategory( i ); 
                        sb.append( ct );
                    }
                    sb.append( "; " );
                }
                
                n = inf.countDataSchemes();
                if( n > 0 ) {
                    sb.append( "schemes=" );
                    for( int i = 0; i< n; i++ ) {
                        if( i != 0 )
                            sb.append( ", " );
                        String ds = inf.getDataScheme( i ); 
                        sb.append( ds );
                    }
                }
                item.attr = sb.toString();
            }            
        }
        else {
            if( position <= compItems.length )
                return compItems[position-1];
        }
        return item;
    }

    @Override
    protected int getPredictedAttributesLength() {
        return 36;   // "com.softwaremanufacturer.productname"
    }

    private class PackageInfoComparator implements Comparator<PackageInfo> {
        int     type;
        boolean ascending;
        ApplicationInfo.DisplayNameComparator aidnc;
        
        public PackageInfoComparator( int type_, boolean case_ignore_, boolean ascending_ ) {
            aidnc = new ApplicationInfo.DisplayNameComparator( pm );
            type = type_;
            ascending = ascending_;
        }
        @Override
        public int compare( PackageInfo pi1, PackageInfo pi2 ) {
            int ext_cmp = 0;
            try {
                switch( type ) {
                case CommanderAdapter.SORT_EXT:
                    if( pi1.packageName != null )
                        ext_cmp = pi1.packageName.compareTo( pi2.packageName );
                    break;
                case CommanderAdapter.SORT_SIZE:  {
                        File asdf1 = new File( pi1.applicationInfo.sourceDir );
                        File asdf2 = new File( pi2.applicationInfo.sourceDir );
                        ext_cmp = asdf1.length() - asdf2.length() < 0 ? -1 : 1;
                    }
                    break;
                case CommanderAdapter.SORT_DATE: {
                        File asdf1 = new File( pi1.applicationInfo.sourceDir );
                        File asdf2 = new File( pi2.applicationInfo.sourceDir );
                        ext_cmp = asdf1.lastModified() - asdf2.lastModified() < 0 ? -1 : 1;
                    }
                    break;
                }
                if( ext_cmp == 0 )
                    ext_cmp = aidnc.compare( pi1.applicationInfo, pi2.applicationInfo );
            } catch( Exception e ) {}
            return ascending ? ext_cmp : -ext_cmp;
        }
    }
    private class ActivityInfoComparator implements Comparator<ActivityInfo> {
        private int     type;
        private boolean ascending;
        public  final PackageManager pm_;
        
        public ActivityInfoComparator( int type_, boolean case_ignore_, boolean ascending_ ) {
            pm_ = pm;
            type = type_;
            ascending = ascending_;
        }
        @Override
        public int compare( ActivityInfo ai1, ActivityInfo ai2 ) {
            int ext_cmp = 0;
            try {
                switch( type ) {
                case CommanderAdapter.SORT_EXT:
                    if( ai1.packageName != null )
                        ext_cmp = ai1.name.compareTo( ai2.name );
                    break;
                }
                if( ext_cmp == 0 ) {
                    String cn1 = ai1.loadLabel( pm_ ).toString();
                    String cn2 = ai2.loadLabel( pm_ ).toString();
                    ext_cmp = cn1.compareTo( cn2 );
                }
            } catch( Exception e ) {
            }
            return ascending ? ext_cmp : -ext_cmp;
        }
    }
    private final void createDesktopShortcut( ComponentName cn, String name, Bitmap ico ) {
        Intent shortcutIntent = new Intent();
        shortcutIntent.setComponent( cn );
        shortcutIntent.setData( uri );
        Intent intent = new Intent();
        intent.putExtra( Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent );
        intent.putExtra( Intent.EXTRA_SHORTCUT_NAME, name );
        if( ico != null )
            intent.putExtra( Intent.EXTRA_SHORTCUT_ICON, ico );
        intent.setAction( "com.android.launcher.action.INSTALL_SHORTCUT" );
        ctx.sendBroadcast( intent );
    }
}
