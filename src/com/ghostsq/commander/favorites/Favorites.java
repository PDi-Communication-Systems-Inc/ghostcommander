package com.ghostsq.commander.favorites;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import com.ghostsq.commander.Panels;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.HomeAdapter;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.ForwardCompat.PubPathType;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

public class Favorites extends ArrayList<Favorite> 
{
    private static final long serialVersionUID = 1L;
    private final static String old_sep = ",", sep = ";";
    private final String TAG = getClass().getSimpleName();
    private final Context c;

    public Favorites( Context c_ ) {
        c = c_;
    }
    
    public final void addToFavorites( Uri u, Credentials crd, String comment ) {
        removeFromFavorites( u );
        if( crd == null && Favorite.isPwdScreened( u ) )
            crd = searchForPassword( u );
        Favorite f = new Favorite( u, crd );
        add( f );
        if( comment != null )
            f.setComment( comment ); 
    }
    public final void removeFromFavorites( Uri u ) {
        int pos = findIgnoreAuth( u );
        if( pos >= 0 )
            remove( pos );
        else
            Log.w( TAG, "Can't find in the list of favs:" + u );
    }

    public final int findIgnoreAuth( Uri u ) {
        try {
            if( u != null ) {
                u = Utils.addTrailngSlash( Utils.updateUserInfo( u, null ) );
                //Log.v( TAG, "looking for URI:" + u );
                for( int i = 0; i < size(); i++ ) {
                    Favorite f = get( i );
                    if( f == null ) {
                        Log.e( TAG, "A fave is null!" );
                        continue;
                    }
                    Uri fu = f.getUri(); 
                    if( fu == null ) {
                        Log.e( TAG, "A fave URI is null!" );
                        continue;
                    }
                    fu = Utils.addTrailngSlash( fu );
                    //Log.v( TAG, "probing URI:" + fu );
                    if( fu.equals( u ) )
                        return i;
                }
            }
        } catch( Exception e ) {
            Log.e( TAG, "Uri: " + Favorite.screenPwd( u ), e );
        }
        return -1;
    }
   
    public final Credentials searchForPassword( Uri u ) {
        try {
            String ui = u.getUserInfo(); 
            if( ui != null && ui.length() > 0 ) {
                String user = ui.substring( 0, ui.indexOf( ':' ) );
                String host = u.getHost();
                String schm = u.getScheme();
                String path = u.getPath();
                if( path == null || path.length() == 0 ) path = "/"; else Utils.mbAddSl( path );
                int best = -1;
                for( int i = 0; i < size(); i++ ) {
                    try {
                        Favorite f = get( i );
                        if( user.equalsIgnoreCase( f.getUserName() ) ) {
                            Uri fu = f.getUri();
                            if( schm.equals( fu.getScheme() ) ) {
                                if( host.equalsIgnoreCase( fu.getHost() ) ) {
                                    best = i;
                                    String fp = fu.getPath();
                                    if( fp == null || path.length() == 0 ) fp = "/"; else Utils.mbAddSl( fp );
                                    if( path.equalsIgnoreCase( fp ) )
                                        break;
                                }
                            }
                        }
                    } catch( Exception e ) {}
                }
                if( best >= 0 ) {
                    Favorite f = get( best );
                    return f.borrowPassword( u );
                }
            }
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
        Log.w( TAG, "Faild to find a suitable Favorite with password!!!" );
        return null;
    }
       
    public final String getAsString() {
        int sz = size();
        String[] a = new String[sz]; 
        for( int i = 0; i < sz; i++ ) {
            String fav_str = get( i ).toString();
            if( fav_str == null ) continue;
            a[i] = escape( fav_str );
        }
        String s = Utils.join( a, sep );
        //Log.v( TAG, "Joined favs: " + s );
        return s;
    }
    
    public final void setFromOldString( String stored ) {
        try {
            if( stored != null && stored.length() != 0 ) { 
                clear();
                String use_sep = old_sep;
                String[] favs = stored.split( use_sep );
                for( int i = 0; i < favs.length; i++ ) {
                    if( favs[i] != null && favs[i].length() > 0 )
                        add( new Favorite( favs[i], null ) );
                }
            }
            if( isEmpty() ) {
                add( new Favorite( HomeAdapter.DEFAULT_LOC, c.getString( R.string.home ) ) );
                add( new Favorite( Panels.DEFAULT_LOC, c.getString( R.string.default_uri_cmnt ) ) );
                if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ) {
                    add( new Favorite( ForwardCompat.getPath( PubPathType.DOWNLOADS ),"Downloads" ) );
                    add( new Favorite( ForwardCompat.getPath( PubPathType.DCIM ),     "Camera" ) );
                    add( new Favorite( ForwardCompat.getPath( PubPathType.PICTURES ), "Pictures" ) );
                    add( new Favorite( ForwardCompat.getPath( PubPathType.MUSIC ),    "Music" ) );
                    add( new Favorite( ForwardCompat.getPath( PubPathType.MOVIES ),   "Movies" ) );
                }
            }
        } catch( Throwable e ) {
            Log.e( TAG, null, e );
        }
    }

    public final void setFromString( String stored ) {
        if( stored == null ) return;
        clear();
        String use_sep = sep;
        String[] favs = stored.split( use_sep );
        try {
            for( int i = 0; i < favs.length; i++ ) {
                String stored_fav = unescape( favs[i] );
                //Log.v( TAG, "fav: " + stored_fav );
                add( new Favorite( stored_fav ) );
            }
        } catch( NoSuchElementException e ) {
            Log.e( TAG, null, e );
        }
        if( isEmpty() )
            add( new Favorite( "home:", c.getString( R.string.home ) ) );
    }

    private final String unescape( String s ) {
        return s.replace( "%3B", sep );
    }
    private final String escape( String s ) {
        return s.replace( sep, "%3B" );
    }
}
