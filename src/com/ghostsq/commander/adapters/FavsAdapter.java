package com.ghostsq.commander.adapters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.adapters.Engines.IReciever;
import com.ghostsq.commander.favorites.Favorite;
import com.ghostsq.commander.favorites.FavDialog;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

public class FavsAdapter extends CommanderAdapterBase {
    private final static String TAG = "FavsAdapter";
    private final static int CREATE_FAVE_SHORTCUT = 3423; 
    private ArrayList<Favorite> favs;
    
    public FavsAdapter( Context ctx_ ) {
        super( ctx_, DETAILED_MODE | NARROW_MODE | SHOW_ATTR | ATTR_ONLY );
        favs = null;
        numItems = 1;
    }

    @Override
    public String getScheme() {
        return "favs";
    }

    public void setFavorites( ArrayList<Favorite> favs_ ) {
        favs = favs_;
        numItems = favs.size() + 1; 
    }
    
    @Override
    public int setMode( int mask, int val ) {
        if( ( mask & ( MODE_WIDTH | MODE_DETAILS | MODE_ATTR ) ) == 0 )
            super.setMode( mask, val );
        return mode;
    }    
    
    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case  F2:
        case  F4:
        case  F8:
        case  SORTING:
        case  MOUNT:
        case  REFRESH:
        case  CHKBL:
            return true;
        case  BY_DATE:
        case  ADD_FAV:
        case  FAVS:
            return false;
        default: return super.hasFeature( feature );
        }
    }
    
    @Override
    public String toString() {
        return "favs:";
    }
    /*
     * CommanderAdapter implementation
     */
    @Override
    public Uri getUri() {
        return Uri.parse( toString() );
    }
    @Override
    public void setUri( Uri uri ) {
    }

    @Override
    public boolean readSource( Uri tmp_uri, String pbod ) {
        numItems = favs.size() + 1;
        notifyDataSetChanged();
        notify( pbod );
        return true;
    }

    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        if( num <= 1 ) {
            menu.add( 0, Commander.OPEN,       0, s( R.string.go_button ) );
            menu.add( 0, R.id.F2,              0, s( R.string.rename_title ) );
            menu.add( 0, R.id.F4,              0, s( R.string.edit_title ) );
            menu.add( 0, R.id.F8,              0, s( R.string.delete_title ) );
            menu.add( 0, CREATE_FAVE_SHORTCUT, 0, s( R.string.shortcut ) );
        }
    }    
    @Override
    public void reqItemsSize( SparseBooleanArray cis ) {
        notErr();
    }
    @Override
    public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move ) {
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
        ArrayList<Favorite> favs_to_remove = new ArrayList<Favorite>( numItems-1 );
        for( int i = 0; i < cis.size(); i++ )
            if( cis.valueAt( i ) ) {
                int k = cis.keyAt( i );
                if( k > 0 ) {
                    favs_to_remove.add( favs.get( k - 1 ) );
                }
            }
        
        for( int i = 0; i < favs_to_remove.size(); i++ )
            favs.remove( favs_to_remove.get(  i ) );
        numItems = favs.size() + 1;
        notifyDataSetChanged();
        notify( Commander.OPERATION_COMPLETED );
        return true;
    }
    
    @Override
    public void openItem( int position ) {
        if( position == 0 ) {
            commander.Navigate( Uri.parse( "home:" ), null, null );
            return;
        }
        if( favs == null || position < 0 || position > numItems )
            return;
        Favorite f = favs.get( position - 1 );
        if( f != null )
            commander.Navigate( f.getUri(), f.getCredentials(), null );
    }

    @Override
    public boolean receiveItems( String[] full_names, int move_mode ) {
        return notErr();
    }

    @Override
    public boolean renameItem( int p, String newName, boolean c  ) {
        if( favs != null && p > 0 && p <= favs.size() ) {
            favs.get( p-1 ).setComment( newName );
            notifyDataSetChanged();
            return true;
        }
        return false;
    }

    @Override
    public void doIt( int command_id, SparseBooleanArray cis ) {
        if( CREATE_FAVE_SHORTCUT == command_id ) {
            int k = 0, n = favs.size();
            for( int i = 0; i < cis.size(); i++ ) {
                k = cis.keyAt( i );
                if( cis.valueAt( i ) && k > 0 && k <= n )
                    break;
            }
            if( k > 0 )
                createDesktopShortcut( favs.get( k - 1 ) );
        }
    }
    
    public void editItem( int p ) {
        if( favs != null && p > 0 && p <= favs.size() ) {
            new FavDialog( ctx, favs.get( p-1 ), this );
        }
    }    

    public void invalidate() {
        notifyDataSetChanged();
        notify( Commander.OPERATION_COMPLETED );
    }    

    private final void createDesktopShortcut( Favorite f ) {
        if( f == null ) return;
        Uri uri = f.getUri();
        Intent shortcutIntent = new Intent();
        shortcutIntent.setClassName( ctx, commander.getClass().getName() );
        shortcutIntent.setAction( Intent.ACTION_VIEW );
        shortcutIntent.setData( uri );
        Credentials crd = f.getCredentials();
        if( crd != null )
            shortcutIntent.putExtra( Credentials.KEY, crd );

        Intent intent = new Intent();
        intent.putExtra( Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent );
        String name = f.getComment();
        if( name == null || name.length() == 0 )
            name = f.getUriString( true );
        intent.putExtra( Intent.EXTRA_SHORTCUT_NAME, name );
        int ic_id = CA.getDrawableIconId( uri != null ? uri.getScheme() : "" );
        Parcelable iconResource = Intent.ShortcutIconResource.fromContext( ctx, ic_id );
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
        intent.setAction( "com.android.launcher.action.INSTALL_SHORTCUT" );
        ctx.sendBroadcast( intent );
    }
    
    @Override
    public String getItemName( int p, boolean full ) {
        if( favs != null && p > 0 && p <= favs.size() ) {
            Favorite f = favs.get( p - 1 );
            String comm = f.getComment();
            return comm != null && comm.length() > 0 ? comm : full ? f.getUriString( true ) : "";
        }
        return null;
    }
    

    /*
     * BaseAdapter implementation
     */

    @Override
    public Object getItem( int position ) {
        Item item = new Item();
        if( position == 0 ) {
            item = new Item();
            item.name = parentLink;
            item.dir = true;
        }
        else {
            if( favs != null && position > 0 && position <= favs.size() ) {
                Favorite f = favs.get( position - 1 );
                if( f != null ) {
                    item.dir = false;
                    item.name = f.getUriString( true );
                    item.size = -1;
                    item.sel = false;
                    item.date = null;
                    item.attr = f.getComment();
                    Uri uri = f.getUri();
                    item.icon_id = CA.getDrawableIconId( uri != null ? uri.getScheme() : "" ); 
                    
                }
            }
        }
        return item;
    }

    @Override
    protected void reSort() {
        if( favs == null ) return;
        FavoriteComparator comp = new FavoriteComparator( mode & MODE_SORTING, (mode & MODE_CASE) != 0, ascending );
        Collections.sort( favs, comp );
    }
    
    public class FavoriteComparator implements Comparator<Favorite> {
        int     type;
        boolean case_ignore, ascending;

        public FavoriteComparator( int type_, boolean case_ignore_, boolean ascending_ ) {
            type = type_;
            case_ignore = case_ignore_;
            ascending = ascending_;
        }
        public int compare( Favorite f1, Favorite f2 ) {
            if( f1 == null || f2 == null ) {
                Log.w( TAG, "a Fav is null!" );
                return 0;
            }
            int ext_cmp = 0;
            switch( type ) { 
            case SORT_EXT: {
                    String c1 = f1.getComment();
                    if( c1 == null ) c1 = "";
                    String c2 = f2.getComment();
                    if( c2 == null ) c2 = "";
                    ext_cmp = c1.compareTo( c2 );
                }
                break;
            case SORT_SIZE: {
                    ext_cmp = getWeight( f1.getUri() ) - getWeight( f2.getUri() ) > 0 ? 1 : -1;
                }
                break;
            case SORT_DATE:
                break;
            }
            if( ext_cmp == 0 ) {
                Uri u1 = f1.getUri();
                Uri u2 = f2.getUri();
                if( u1 != null )
                    ext_cmp = u1.compareTo( u2 );
            }
            return ascending ? ext_cmp : -ext_cmp;
        }
        private int getWeight( Uri u ) {
            int w = 0;
            if( u != null ) {
                w++;
                String s = u.getScheme();
                if( s != null ) {
                    w++;
                    if( "ftp".equals( s ) ) w++; else
                    if( "smb".equals( s ) ) w+=2;
                }
            }
            return w;
        }
    }
}
