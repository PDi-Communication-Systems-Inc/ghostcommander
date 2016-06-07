package com.ghostsq.commander;

import java.io.File;
import java.util.ArrayList;

import com.ghostsq.commander.R;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.CommanderAdapterBase;
import com.ghostsq.commander.adapters.FSAdapter;
import com.ghostsq.commander.adapters.FavsAdapter;
import com.ghostsq.commander.adapters.HomeAdapter;
import com.ghostsq.commander.adapters.ZipAdapter;
import com.ghostsq.commander.adapters.CommanderAdapter.Feature;
import com.ghostsq.commander.favorites.Favorite;
import com.ghostsq.commander.favorites.Favorites;
import com.ghostsq.commander.favorites.LocationBar;
import com.ghostsq.commander.root.RootAdapter;
import com.ghostsq.commander.toolbuttons.ToolButton;
import com.ghostsq.commander.toolbuttons.ToolButtons;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.Utils;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView.OnScrollListener;

public class Panels implements AdapterView.OnItemSelectedListener, 
                               AdapterView.OnItemClickListener, 
                               ListView.OnScrollListener, 
                               View.OnClickListener, 
                               View.OnLongClickListener, 
                               View.OnTouchListener, 
                               View.OnFocusChangeListener, 
                               View.OnKeyListener {
    private final static String TAG = "Panels";
    public  static final String DEFAULT_LOC = Environment.getExternalStorageDirectory().getAbsolutePath();
    public  final static int LEFT = 0, RIGHT = 1;
    private int current = LEFT;
    private final int titlesIds[] = { R.id.left_dir, R.id.right_dir };
    private ListHelper list[] = { null, null };
    public  FileCommander c;
    public  View mainView, toolbar = null;
    private LockableScrollView hsv;
    public  PanelsView panelsView = null;
    public  boolean sxs, fingerFriendly = false;
    private boolean panels_sliding = true, arrowsLegacy = false, warnOnRoot = true, rootOnRoot = false, toolbarShown = false;
    public  boolean volumeLegacy = true;
    private boolean selAtRight = true, disableOpenSelectOnly = false;
    private float selWidth = 0.5f, downX = 0, downY = 0, x_start = -1;
    public  int scroll_back = 50, fnt_sz = 12;
    private StringBuffer quickSearchBuf = null;
    private Toast quickSearchTip = null;
    private Favorites favorites;
    private LocationBar locationBar;
    private CommanderAdapter destAdapter = null;
    public  ColorsKeeper ck;
    private float density = 1;

    public Panels(FileCommander c_, boolean sxs_) {
        c = c_;
        density = c.getResources().getDisplayMetrics().density;
        ck = new ColorsKeeper( c );
        current = LEFT;
        c.setContentView( R.layout.alt );
        mainView = c.findViewById( R.id.main );

        hsv = (LockableScrollView)c.findViewById( R.id.hrz_scroll );
        hsv.setHorizontalScrollBarEnabled( false );
        hsv.setSmoothScrollingEnabled( true );
        hsv.setOnTouchListener( this );
        if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD )
            ForwardCompat.disableOverScroll( hsv );

        panelsView = ( (PanelsView)c.findViewById( R.id.panels ) );
        panelsView.init( c.getWindowManager() );
        initList( LEFT );
        initList( RIGHT );

        favorites = new Favorites( c );
        locationBar = new LocationBar( c, this, favorites );

        setLayoutMode( sxs_ );
        // highlightCurrentTitle();

        TextView left_title = (TextView)c.findViewById( titlesIds[LEFT] );
        if( left_title != null ) {
            left_title.setOnClickListener( this );
            left_title.setOnLongClickListener( this );
        }
        TextView right_title = (TextView)c.findViewById( titlesIds[RIGHT] );
        if( right_title != null ) {
            right_title.setOnClickListener( this );
            right_title.setOnLongClickListener( this );
        }
        try {
            quickSearchBuf = new StringBuffer();
            quickSearchTip = Toast.makeText( c, "", Toast.LENGTH_SHORT );
        } catch( Exception e ) {
            c.showMessage( "Exception on creating quickSearchTip: " + e );
        }
        focus();
    }

    public final boolean getLayoutMode() {
        return sxs;
    }

    public final void setLayoutMode( boolean sxs_ ) {
        sxs = sxs_;
        SharedPreferences shared_pref = PreferenceManager.getDefaultSharedPreferences( c );
        applySettings( shared_pref, false );
        scroll_back = (int)( c.getWindowManager().getDefaultDisplay().getWidth() * 2. / 10 );
        if( panelsView != null )
            panelsView.setMode( sxs_ );
    }
  
    public final int getCurrent() {
        return current;
    }
    public final int getOpposite() {
        return opposite();
    }

    public final void showToolbar( boolean show ) {
        toolbarShown = show;
    }

    private final Drawable createButtonStates() {
        try {
            float bbb = Utils.getBrightness( ck.btnColor );
            int sc = Utils.shiftBrightness( ck.btnColor, 0.2f );
            StateListDrawable states = new StateListDrawable();
            GradientDrawable bpd = Utils.getShadingEx( ck.btnColor, 1f );
            bpd.setStroke( 1, sc );
            bpd.setCornerRadius( 2 );
            GradientDrawable bnd = Utils.getShadingEx( ck.btnColor, bbb < 0.4f ? 0f : 0.6f );
            bnd.setStroke( 1, sc );
            bnd.setCornerRadius( 2 );
            states.addState( new int[] { android.R.attr.state_pressed }, bpd );
            states.addState( new int[] {}, bnd );
            return states;
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }

    public final void setToolbarButtons( CommanderAdapter ca ) {
        try {
            if( ca == null )
                return;
            if( toolbarShown ) {
                if( toolbar == null ) {
                    LayoutInflater inflater = (LayoutInflater)c.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
                    toolbar = inflater.inflate( R.layout.toolbar, (ViewGroup)mainView, true ).findViewById( R.id.toolbar );
                }
                if( toolbar == null ) {
                    Log.e( TAG, "Toolbar inflation has failed!" );
                    return;
                }
                toolbar.setVisibility( View.INVISIBLE );

                ViewGroup tb_holder = (ViewGroup)toolbar;
                tb_holder.removeAllViews();
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );

                boolean keyboard = sharedPref.getBoolean( "show_hotkeys", true )
                        || c.getResources().getConfiguration().keyboard != Configuration.KEYBOARD_NOKEYS;

                Utils.changeLanguage( c );
                ToolButtons tba = new ToolButtons();
                tba.restore( sharedPref, c );
                int bfs = fnt_sz + ( fingerFriendly ? 2 : 1 );
                for( int i = 0; i < tba.size(); i++ ) {
                    ToolButton tb = tba.get( i );
                    int bid = tb.getId();
                    if( tb.isVisible() && ca.hasFeature( ToolButton.getFeature( bid ) ) ) {
                        String caption = "";
                        if( keyboard ) {
                            char ch = tb.getBoundKey();
                            if( ch != 0 )
                                caption = ch + " ";
                        }
                        caption += tb.getCaption();
                        
                        LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams( LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT );
                        Button b = null;
                        if( !ck.isButtonsDefault() ) { 
                            if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB )
                                b = new Button( c, null, fingerFriendly ? android.R.style.Widget_Holo_Button :
                                                                          android.R.style.Widget_Button_Small );
                            else
                                b = new Button( c, null );
                            int c_length = caption.length();
                            int hp = c_length < 6   ? (int)( ( 12 - c_length ) * density) : 6;
                            int vp = fingerFriendly ? (int)(10 * density) : 6;
                            b.setPadding( hp, vp, hp, vp );
                            float bbb = Utils.getBrightness( ck.btnColor );
                            b.setTextColor( bbb > 0.8f ? 0xFF000000 : 0xFFFFFFFF );
                            b.setTextSize( bfs );
                            Drawable bd = createButtonStates();
                            if( bd != null )
                                b.setBackgroundDrawable( bd );
                            else
                                b.setBackgroundResource( R.drawable.tool_button );
                            lllp.rightMargin = 2;
                        } else { // default 
                            int style_id = fingerFriendly ? android.R.attr.buttonStyle : 
                                                            android.R.attr.buttonStyleSmall;
                            b = new Button( c, null, style_id );
                            lllp.rightMargin = -2; // a button has invisible
                                                   // background around it
                        }
                        b.setLayoutParams( lllp );

                        b.setId( bid );
                        b.setFocusable( false );
                        b.setText( caption );
                        b.setOnClickListener( c );
                        tb_holder.addView( b );
                    }
                }
                toolbar.setVisibility( View.VISIBLE );
            } else {
                if( toolbar != null )
                    toolbar.setVisibility( View.GONE );
            }
        } catch( Exception e ) {
            Log.e( TAG, "setToolbarButtons() exception", e );
        }
    }

    public final void focus() {
        list[current].focus();
    }

    // View.OnFocusChangeListener implementation
    @Override
    public void onFocusChange( View v, boolean f ) {
        //Log.v( TAG, "focus has changed " + (f?"to ":"from ") + v );
        ListView flv = list[opposite()].flv;
        boolean opp = flv == v;
        if( f && opp ) {
            setPanelCurrent( opposite(), true );
        }
    }

    public ArrayList<Favorite> getFavorites() {
        return favorites;
    }

    public final boolean isCurrent( int q ) {
        return ( current == LEFT && q == LEFT ) || ( current == RIGHT && q == RIGHT );
    }

    private final void initList( int which ) {
        list[which] = new ListHelper( which, this );
        setPanelTitle( "", which );
    }

    public final void setPanelTitle( String s, int which ) {
        try {
            TextView title = (TextView)c.findViewById( titlesIds[which] );
            if( title != null ) {
                int p_width = mainView.getWidth();
                if( p_width > 0 )
                    title.setMaxWidth( p_width / 2 );
                if( s == null ) {
                    title.setText( c.getString( R.string.fail ) );
                } else {
                    title.setText( Utils.unEscape( Favorite.screenPwd( s ) ) );
                }
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    private final GradientDrawable getShading( int color ) {
        float drop;
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD ) drop = 0.6f; else
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB )   drop = ck.isButtonsDefault() ? 0.8f : 0.6f;
        else drop = ck.isButtonsDefault() ? 1.0f : 0.8f;
        return Utils.getShadingEx( color, drop );
    }

    private final void refreshPanelTitles() {
        try {
            CommanderAdapter cur_ca = getListAdapter( true );
            CommanderAdapter opp_ca = getListAdapter( false );
            if( cur_ca != null )
                setPanelTitle( cur_ca.toString(), current );
            if( opp_ca != null )
                setPanelTitle( opp_ca.toString(), opposite() );
            highlightCurrentTitle();
        } catch( Exception e ) {
            Log.e( TAG, "refreshPanelTitle()", e );
        }
    }
   
    private final void highlightCurrentTitle() {
        if( mainView == null )
            return;
        View title_bar = mainView.findViewById( R.id.titles );
        if( title_bar != null ) {
            int h = title_bar.getHeight();
            if( h == 0 )
                h = 30;
            int bg_color = ck.ttlColor;
            Drawable d = getShading( bg_color );
            if( d != null )
                title_bar.setBackgroundDrawable( d );
            else
                title_bar.setBackgroundColor( bg_color );
        }
        highlightTitle( opposite(), false );
        highlightTitle( current, true );
    }

    private final void highlightTitle( int which, boolean on ) {
        TextView title = (TextView)mainView.findViewById( titlesIds[which] );
        if( title != null ) {
            if( on ) {
                int bg_color = ck.selColor;
                String tt = title.getText().toString();
                if( tt.startsWith( "root:" ) )
                    bg_color = 0xFFFF0000;  
                Drawable d = getShading( bg_color );
                if( d != null )
                    title.setBackgroundDrawable( d );
                else
                    title.setBackgroundColor( bg_color );
                title.setTextColor( ck.sfgColor );
            } else {
                title.setBackgroundColor( ck.selColor & 0x0FFFFFFF );
                float[] fgr_hsv = new float[3];
                Color.colorToHSV( ck.fgrColor, fgr_hsv );
                float[] ttl_hsv = new float[3];
                Color.colorToHSV( ck.ttlColor, ttl_hsv );
                fgr_hsv[1] *= 0.5;
                fgr_hsv[2] = ( fgr_hsv[2] + ttl_hsv[2] ) / 2;
                title.setTextColor( Color.HSVToColor( fgr_hsv ) );
            }
        } else
            Log.e( TAG, "title view was not found!" );
    }

    public final int getSingle( boolean touched ) {
        return list[current].getSingle( touched );
    }

    public final void setSelection( int i ) {
        setSelection( current, i, 0 );
    }

    public final void setSelection( int which, int i, int y_ ) {
        list[which].setSelection( i, y_ );
    }

    public final void setSelection( int which, String name ) {
        list[which].setSelection( name );
    }

    public final File getCurrentFile() {
        try {
            CommanderAdapter ca = getListAdapter( true );
            if( ca.hasFeature( Feature.SEND ) ) {
                int pos = getSingle( true );
                if( pos < 0 )
                    return null;
                String full_name = ca.getItemName( pos, true );
                if( full_name != null )
                    return new File( full_name );
            }
        } catch( Exception e ) {
            Log.e( TAG, "getCurrentFile()", e );
        }
        return null;
    }

    private final int opposite() {
        return 1 - current;
    }

    public final CommanderAdapter getListAdapter( boolean forCurrent ) {
        return list[forCurrent ? current : opposite()].getListAdapter();
    }

    public final int getWidth() {
        return mainView.getWidth();
    }

    public final void applyColors() {
        ck.restore();
        if( sxs ) {
            View div = mainView.findViewById( R.id.divider );
            if( div != null )
                div.setBackgroundColor( ck.ttlColor );
        }
        list[LEFT].applyColors( ck );
        list[RIGHT].applyColors( ck );

        ck.restoreTypeColors();
        CommanderAdapterBase.setTypeMaskColors( ck );
        highlightCurrentTitle();
    }

    public final void applySettings( SharedPreferences sharedPref, boolean init ) {
        try {
            applyColors();
            String fnt_sz_s = sharedPref.getString( "font_size", "12" );
            try {
                fnt_sz = Integer.parseInt( fnt_sz_s );
            } catch( NumberFormatException e ) {
            }

            String ffs = sharedPref.getString( "finger_friendly_a", "y" );
            boolean ff = false;
            if( "a".equals( ffs ) ) {
                Display disp = c.getWindowManager().getDefaultDisplay();
                Configuration config = c.getResources().getConfiguration();
                ff = config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES || disp.getWidth() < disp.getHeight();
            } else
                ff = "y".equals( ffs );

            setFingerFriendly( ff, fnt_sz );
            warnOnRoot = sharedPref.getBoolean( "prevent_root", true );
            rootOnRoot = sharedPref.getBoolean( "root_root", false );
            panels_sliding = sharedPref.getBoolean( "panels_sliding", true );
            hsv.setScrollable( panels_sliding );
            arrowsLegacy = sharedPref.getBoolean( "arrow_legc", false );
            volumeLegacy = sharedPref.getBoolean( "volume_legc", true );
            toolbarShown = sharedPref.getBoolean( "show_toolbar", true );
            selAtRight = sharedPref.getBoolean( Prefs.SEL_ZONE + "_right", true );
            selWidth = sharedPref.getInt( Prefs.SEL_ZONE + "_width", 50 ) / 100f;
            if( !init ) {
                list[LEFT].applySettings( sharedPref );
                list[RIGHT].applySettings( sharedPref );
                // setPanelCurrent( current );
            }
            setToolbarButtons( getListAdapter( true ) );
        } catch( Exception e ) {
            Log.e( TAG, "applySettings()", e );
        }
    }

    public void changeSorting( int sort_mode ) {
        CommanderAdapter ca = getListAdapter( true );

        int cur_mode = ca.setMode( 0, 0 );
        boolean asc = ( cur_mode & CommanderAdapter.MODE_SORT_DIR ) == CommanderAdapter.SORT_ASC;
        int sorted = cur_mode & CommanderAdapter.MODE_SORTING;
        storeChoosedItems();
        if( sorted == sort_mode )
            ca.setMode( CommanderAdapter.MODE_SORT_DIR, asc ? CommanderAdapter.SORT_DSC : CommanderAdapter.SORT_ASC );
        else
            ca.setMode( CommanderAdapter.MODE_SORTING | CommanderAdapter.MODE_SORT_DIR, sort_mode | CommanderAdapter.SORT_ASC );
        reStoreChoosedItems();
        list[current].adapterMode = ca.getMode() & ( CommanderAdapter.MODE_SORTING | CommanderAdapter.MODE_SORT_DIR );
    }

    public void toggleHidden() {
        CommanderAdapter ca = getListAdapter( true );

        int cur_mode = ca.setMode( 0, 0 );
        int new_mode = ( cur_mode & CommanderAdapter.MODE_HIDDEN ) == CommanderAdapter.SHOW_MODE ? CommanderAdapter.HIDE_MODE
                : CommanderAdapter.SHOW_MODE;
        ca.setMode( CommanderAdapter.MODE_HIDDEN, new_mode );
        refreshList( current, true, null );
    }

    public final void refreshLists( String posto ) {
        int was_current = current, was_opp = 1 - was_current;
        refreshList( current, true, posto );
        if( sxs )
            refreshList( was_opp, false, null );
        else
            list[was_opp].setNeedRefresh();
    }

    public final void refreshList( int which, boolean was_current, String posto ) {
        list[which].refreshList( was_current, posto );
    }
    public final void swapPanels() {
        ListAdapter  left_a =  list[LEFT].flv.getAdapter();
        ListAdapter right_a = list[RIGHT].flv.getAdapter();
         list[LEFT].flv.setAdapter( right_a );
        list[RIGHT].flv.setAdapter(  left_a );
        boolean left_cur = current == LEFT;
         list[LEFT].refreshList(  left_cur, null );
        list[RIGHT].refreshList( !left_cur, null );
    }

    public final void redrawLists() {
        list[current].askRedrawList();
        if( sxs )
            list[opposite()].askRedrawList();
        list[current].focus();
    }

    public void setFingerFriendly( boolean finger_friendly, int font_size ) {
        fingerFriendly = finger_friendly;
        try {
            for( int p = LEFT; p <= RIGHT; p++ ) {
                TextView title = (TextView)c.findViewById( titlesIds[p] );
                if( title != null ) {
                    title.setTextSize( font_size );
                    int vm = 0, hm = (int)(8 * density);;
                    if( finger_friendly )
                        vm = (int)(10 * density);
                    else
                        vm = (int)(4 * density);
                    title.setPadding( hm, vm, hm, vm );
                }
                if( list[p] != null )
                    list[p].setFingerFriendly( finger_friendly );
            }
            locationBar.setFingerFriendly( finger_friendly, font_size, density );
        } catch( Exception e ) {
            Log.e( TAG, null, e );
        }
    }

    public final void makeOtherAsCurrent() {
        CommanderAdapter ca = getListAdapter( true );
        NavigateInternal( opposite(), ca.getUri(), ca.getCredentials(), null );
    }

    public final void makeOtherAsCurDirItem() {
        CommanderAdapter ca = getListAdapter( true );
        int pos = list[current].getCurPos();
        Uri u = ca.getItemUri( pos );
        if( u != null )
            NavigateInternal( opposite(), u, ca.getCredentials(), null );
    }

    public final void togglePanelsMode() {
        setLayoutMode( !sxs );
    }

    public final void togglePanels( boolean refresh ) {
        // Log.v( TAG, "toggle" );
        setPanelCurrent( opposite() );
    }

    public final void setPanelCurrent( int which ) {
        setPanelCurrent( which, false );
    }

    public final void setPanelCurrent( int which, boolean dont_focus ) {
        //Log.v( TAG, "setPanelCurrent: " + which + " dnf:" + dont_focus );
        if( !dont_focus && panelsView != null ) {
            panelsView.setMode( sxs );
        }
        current = which;
        if( !sxs ) {
            final int dir = current == LEFT ? HorizontalScrollView.FOCUS_LEFT : HorizontalScrollView.FOCUS_RIGHT;
            //Log.v( TAG, "do fullScroll: " + dir );
            if( dont_focus )
                hsv.fullScroll( dir );
            else {
                hsv.post( new Runnable() {
                    public void run() {
                        //Log.v( TAG, "async fullScroll: " + dir );
                        hsv.fullScroll( dir );
                    }
                } );
            }
        } else if( !dont_focus )
            list[current].focus();
        highlightCurrentTitle();
        setToolbarButtons( getListAdapter( true ) );
        if( list[current].needRefresh() )
            refreshList( current, false, null );
    }

    public final void showSizes( boolean touched ) {
        storeChoosedItems();
        getListAdapter( true ).reqItemsSize( getMultiple( touched ) );
    }

    public final void checkItems( boolean set, String mask, boolean dir, boolean file ) {
        list[current].checkItems( set, mask, dir, file );
    }

    class NavDialog implements OnClickListener {
        private final Uri sdcard = Uri.parse( DEFAULT_LOC );
        protected int which;
        protected String posTo;
        protected Uri uri;

        NavDialog(Context c, int which_, Uri uri_, String posTo_) {
            which = which_;
            uri = uri_;
            posTo = posTo_;
            LayoutInflater factory = LayoutInflater.from( c );
            new AlertDialog.Builder( c ).setIcon( android.R.drawable.ic_dialog_alert )
                    .setTitle( R.string.confirm )
                    .setView( factory.inflate( R.layout.rootmpw, null ) )
                    // .setMessage( c.getString( R.string.nav_warn, uri ) )
                    .setPositiveButton( R.string.dialog_ok, this )
                    .setNeutralButton( R.string.dialog_cancel, this )
                    .setNegativeButton( R.string.home, this ).show();
        }

        @Override
        public void onClick( DialogInterface idialog, int whichButton ) {
            if( whichButton == DialogInterface.BUTTON_POSITIVE ) {
                warnOnRoot = false;
                if( rootOnRoot )
                    uri = uri.buildUpon().scheme( "root" ).build();
                NavigateInternal( which, uri, null, posTo );
            } else if( whichButton == DialogInterface.BUTTON_NEUTRAL ) {
                NavigateInternal( which, sdcard, null, null );
            } else
                NavigateInternal( which, Uri.parse( HomeAdapter.DEFAULT_LOC ), null, null );
            idialog.dismiss();
        }
    }

    protected final boolean isSafeLocation( String path ) {
        return path.startsWith( DEFAULT_LOC ) || path.startsWith( "/sdcard" ) || path.startsWith( "/mnt/" );
    }

    public final void Navigate( int which, Uri uri, Credentials crd, String posTo ) {
        if( uri == null )
            return;
        String scheme = uri.getScheme(), path = uri.getPath();

        if( ( scheme == null || scheme.equals( "file" ) ) && ( path == null || !isSafeLocation( path ) ) ) {
            if( warnOnRoot ) {
                CommanderAdapter ca = list[which].getListAdapter();
                if( ca != null && ca.hasFeature( Feature.FS ) ) {
                    String cur_path = ca.toString();
                    if( cur_path != null && isSafeLocation( cur_path ) ) {
                        try {
                            new NavDialog( c, which, uri, posTo );
                        } catch( Exception e ) {
                            Log.e( TAG, "Navigate()", e );
                        }
                        return;
                    }
                }
            } else if( rootOnRoot )
                uri = uri.buildUpon().scheme( "root" ).build();
        }
        NavigateInternal( which, uri, crd, posTo );
    }

    private final void NavigateInternal( int which, Uri uri, Credentials crd, String posTo ) {
        ListHelper list_h = list[which];
        list_h.Navigate( uri, crd, posTo, which == current );
    }

    public final void recoverAfterRefresh( String item_name, int which ) {
        try {
            if( which >= 0 )
                list[which].recoverAfterRefresh( item_name );
            else
                list[current].recoverAfterRefresh( which == current );
            refreshPanelTitles();
            // setPanelCurrent( current, true ); the current panel is set by set
            // focus
        } catch( Exception e ) {
            Log.e( TAG, "refreshList()", e );
        }
    }

    public void login( Credentials crd, int which_panel ) {
        if( which_panel < 0 )
            which_panel = current;
        CommanderAdapter ca = list[which_panel].getListAdapter();
        if( ca != null ) {
            ca.setCredentials( crd );
            list[which_panel].refreshList( true, null );
        }
    }

    public final void terminateOperation() {
        CommanderAdapter a = getListAdapter( true );
        a.terminateOperation();
        if( a == destAdapter )
            destAdapter = null;
        CommanderAdapter p = getListAdapter( false );
        p.terminateOperation();
        if( p == destAdapter )
            destAdapter = null;
        if( null != destAdapter ) {
            destAdapter.terminateOperation();
            destAdapter = null;
        }
    }

    public final void Destroy() {
        Log.i( TAG, "Destroing adapters" );
        try {
            getListAdapter( false ).prepareToDestroy();
            getListAdapter( true ).prepareToDestroy();
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    // called from context menu only
    public final void tryToSend() {
        SharedPreferences shared_pref = PreferenceManager.getDefaultSharedPreferences( c );
        boolean use_content = shared_pref.getBoolean( "send_content", true );
        SparseBooleanArray cis = getMultiple( true );
        int num = cis.size();
        if( num > 1 ) {
            CommanderAdapter ca = getListAdapter( true );
            if( ca == null )
                return;
            if( !ca.hasFeature( Feature.SEND ) ) {
                c.showError( c.getString( R.string.on_fs_only ) );
                return;
            }

            ArrayList<Uri> uris = new ArrayList<Uri>();
            Intent in = new Intent();
            in.setAction( android.content.Intent.ACTION_SEND_MULTIPLE );
            in.setType( "*/*" );
            for( int i = 0; i < num; i++ ) {
                if( cis.valueAt( i ) ) {
                    String esc_fn = Utils.escapePath( ca.getItemName( cis.keyAt( i ), true ) );
                    Uri uri = Uri.parse( use_content ? FileProvider.URI_PREFIX + esc_fn : "file://" + esc_fn );
                    uris.add( uri );
                }
            }
            in.putParcelableArrayListExtra( Intent.EXTRA_STREAM, uris );
            c.startActivity( Intent.createChooser( in, c.getString( R.string.send_title ) ) );
        } else {
            File f = getCurrentFile();
            if( f != null ) {
                Intent in = new Intent( Intent.ACTION_SEND );
                in.setType( "*/*" );
                in.putExtra( Intent.EXTRA_SUBJECT, f.getName() );

                String esc_fn = Utils.escapePath( f.getAbsolutePath() );
                Uri uri = Uri.parse( use_content ? FileProvider.URI_PREFIX + esc_fn : "file://" + esc_fn );
                in.putExtra( Intent.EXTRA_STREAM, uri );
                c.startActivity( Intent.createChooser( in, c.getString( R.string.send_title ) ) );
            }
        }
    }

    public final void tryToOpen() {
        File f = getCurrentFile();
        if( f != null ) {
            Intent intent = new Intent( Intent.ACTION_VIEW );
            Uri u = Uri.fromFile( f );
            intent.setDataAndType( u, "*/*" );
            Log.d( TAG, "Open uri " + u.toString() + " intent: " + intent.toString() );
            if (Build.VERSION.SDK_INT == 19) {
                // This will open the "Complete action with" dialog if the user doesn't have a default app set.
                c.startActivity( intent );
            } else {
                c.startActivity( Intent.createChooser( intent, c.getString( R.string.open_title ) ) );
            }
        }
    }

    public final void copyName() {
        try {
            CommanderAdapter ca = getListAdapter( true );
            if( ca == null )
                return;
            ClipboardManager clipboard = (ClipboardManager)c.getSystemService( Context.CLIPBOARD_SERVICE );
            int pos = getSingle( true );
            if( pos >= 0 ) {
                String in = ca.getItemName( pos, true );
                if( in != null ) {
                    if( in.startsWith( RootAdapter.DEFAULT_LOC ) )
                        in = Uri.parse( in ).getPath();
                    clipboard.setText( in );
                }
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    public final Uri getFolderUriWithAuth( boolean active ) {
        CommanderAdapter ca = getListAdapter( active );
        if( ca == null ) return null;
        Uri u = ca.getUri();
        if( u != null ) {
            Credentials crd = ca.getCredentials();
            if( crd != null )
                return Utils.getUriWithAuth( u, crd );
        }
        return u;
    }
    public final Credentials getCredentials( boolean active ) {
        CommanderAdapter ca = getListAdapter( active );
        if( ca == null ) return null;
        return ca.getCredentials();
    }
    
    public final void createDesktopShortcut() {
        File f = getCurrentFile();
        if( f == null )
            return;
        String esc_fn = Utils.escapePath( f.getAbsolutePath() );
        Uri uri = Uri.parse( "file://" + esc_fn );
        Intent shortcutIntent = new Intent( Intent.ACTION_VIEW );
        shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        String name = f.getName();
        String mime;
        int    dr_id;
        if( f.isDirectory() ) {
            mime = "inode/directory";
            dr_id = R.drawable.folder;
        } else {
            String ext = Utils.getFileExt( name );
            mime = Utils.getMimeByExt( ext );
            dr_id = CommanderAdapterBase.getIconId( name );
        }
        shortcutIntent.setDataAndType( uri, mime );

        Intent intent = new Intent();
        intent.putExtra( Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent );
        intent.putExtra( Intent.EXTRA_SHORTCUT_NAME, name );
        
        Parcelable iconResource = Intent.ShortcutIconResource.fromContext( c, dr_id );
        intent.putExtra( Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource );
        intent.setAction( "com.android.launcher.action.INSTALL_SHORTCUT" );
        c.sendBroadcast( intent );
    }

    public final void addToFavorites( Uri u, Credentials crd, String comment ) {
        favorites.addToFavorites( u, crd, comment );
    }

    public final void addCurrentToFavorites() {
        CommanderAdapter ca = getListAdapter( true );
        if( ca == null )
            return;
        Uri u = ca.getUri();
        favorites.addToFavorites( u, ca.getCredentials(), null );
        c.showMessage( c.getString( R.string.fav_added, Favorite.screenPwd( u ) ) );
    }

    public final void faveSelectedFolder() {
        CommanderAdapter ca = getListAdapter( true );
        if( ca == null ) return;
        int pos = getSingle( true );
        if( pos < 0 ) return;
        Uri u = ca.getItemUri( pos );
        if( u == null ) return;
        String descr = ca.getItemName( pos, false );
        if( descr != null ) {
            int sl_p = descr.indexOf( '/' );
            if( sl_p >= 0 ) descr = descr.substring( sl_p + 1 );
        }
        favorites.addToFavorites( u, ca.getCredentials(), descr );
        c.showMessage( c.getString( R.string.fav_added, Favorite.screenPwd( u ) ) );
    }

    public final void openForEdit( String file_name, boolean touched ) {
        CommanderAdapter ca = getListAdapter( true );
        if( ca == null || !ca.hasFeature( Feature.F4 ) ) {
            c.showMessage( c.getString( R.string.edit_err ) );
            return;
        }
        if( ca instanceof FavsAdapter ) {
            FavsAdapter fa = (FavsAdapter)ca;
            int pos = getSingle( true );
            if( pos > 0 )
                fa.editItem( pos );
            return;
        }
        try {
            Uri u;
            long size = 0;
            if( file_name == null || file_name.length() == 0 ) {
                int pos = getSingle( touched );
                CommanderAdapter.Item item = (CommanderAdapter.Item)( (ListAdapter)ca ).getItem( pos );
                if( item == null ) {
                    c.showError( c.getString( R.string.cant_open ) );
                    return;
                }
                if( item.dir ) {
                    c.showError( c.getString( R.string.cant_open_dir, item.name ) );
                    return;
                }
                size = item.size;
                file_name = item.name;
                u = ca.getItemUri( pos );
            } else
                u = Uri.parse( file_name );
            if( u == null )
                return;
            u = u.buildUpon().encodedPath( u.getEncodedPath().replace( " ", "%20" ) ).build();
            final String GC_EDITOR = Editor.class.getName();
            String full_class_name = GC_EDITOR;
            if( ca instanceof FSAdapter ) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
                full_class_name = sharedPref.getString( "editor_activity", GC_EDITOR );
                if( !GC_EDITOR.equals( full_class_name ) )
                    u = u.buildUpon().scheme( "file" ).authority( "" ).build();
            }
            Intent i = new Intent( Intent.ACTION_EDIT );
            if( !Utils.str( full_class_name ) )
                full_class_name = GC_EDITOR;
            int last_dot_pos = full_class_name.lastIndexOf( '.' );
            if( last_dot_pos < 0 ) {
                c.showMessage( "Invalid class name: " + full_class_name );
                full_class_name = GC_EDITOR;
                last_dot_pos = full_class_name.lastIndexOf( '.' );
            }
            
            if( GC_EDITOR.equals( full_class_name ) && size > 1000000 ) {
                c.showError( c.getString( R.string.too_big_file, file_name ) );
                return;
            }
            
            i.setClassName( full_class_name.substring( 0, last_dot_pos ), full_class_name );
            i.setDataAndType( u, "text/plain" );
            Credentials crd = ca.getCredentials();
            if( crd != null )
                i.putExtra( Credentials.KEY, crd );
            Log.d( TAG, "Open uri " + u.toString() + " intent: " + i.toString() );
            c.startActivity( i );
        } catch( ActivityNotFoundException e ) {
            c.showMessage( "Activity Not Found: " + e );
        } catch( IndexOutOfBoundsException e ) {
            c.showMessage( "Bad activity class name: " + e );
        }
    }

    public final void openForView( boolean touched ) {
        int pos = getSingle( touched );
        if( pos < 0 )
            return;
        String name = null;
        try {
            CommanderAdapter ca = getListAdapter( true );
            Uri uri = ca.getItemUri( pos );
            if( uri == null )
                return;
            CommanderAdapter.Item item = (CommanderAdapter.Item)( (ListAdapter)ca ).getItem( pos );
            if( item.dir ) {
                showSizes( touched );
                return;
            }
            String mime = Utils.getMimeByExt( Utils.getFileExt( item.name ) );
            if( mime == null )
                mime = "application/octet-stream";
            Intent i = createImageViewIntent( uri, mime, ca, pos );
            i.setClass( c, mime.startsWith( "image/" ) ? PictureViewer.class : TextViewer.class );
            c.startActivity( i );
        } catch( Exception e ) {
            Log.e( TAG, "Can't view the file " + name, e );
        }
    }

    public final Intent createImageViewIntent( Uri uri, String mime, CommanderAdapter ca, int pos ) {
        Intent i = new Intent( Intent.ACTION_VIEW );
        i.setDataAndType( uri, mime );
        Credentials crd = ca.getCredentials();
        if( crd != null )
            i.putExtra( Credentials.KEY, crd );
        i.putExtra( "position", pos );
        i.putExtra( "mode", ca.getMode() );
        return i;
    }    
    
    public final String getActiveItemsSummary( boolean touched ) {
        return list[current].getActiveItemsSummary( touched );
    }

    public final SparseBooleanArray getMultiple( boolean touch ) {
        return list[current].getMultiple( touch );
    }

    /**
     * @return 0 - nothing selected, 1 - a file, -1 - a folder, otherwise the
     *         number public final int getNumItemsSelectedOrChecked() { int
     *         checked = getNumItemsChecked(); return checked; }
     */
    public final String getSelectedItemName( boolean touched ) {
        return getSelectedItemName( false, touched );
    }

    public final String getSelectedItemName( boolean full, boolean touched ) {
        int pos = getSingle( touched );
        return pos < 0 ? null : getListAdapter( true ).getItemName( pos, full );
    }

    public final void quickSearch( char ch ) {
        CommanderAdapter a = getListAdapter( true );
        if( a != null ) {
            quickSearchBuf.append( ch );
            String s = quickSearchBuf.toString();
            showTip( s );

            int n = ( (ListAdapter)a ).getCount();
            for( int i = 1; i < n; i++ ) {
                String name = a.getItemName( i, false );
                if( name == null )
                    continue;
                if( s.regionMatches( true, 0, name, 0, s.length() ) ) {
                    setSelection( i );
                    return;
                }
            }
        }
    }

    private final void showTip( String s ) {
        try {
            if( !sxs || current == LEFT )
                quickSearchTip.setGravity( Gravity.BOTTOM | Gravity.LEFT, 5, 10 );
            else
                quickSearchTip.setGravity( Gravity.BOTTOM, 10, 10 );
            quickSearchTip.setText( s );
            quickSearchTip.show();
        } catch( RuntimeException e ) {
            c.showMessage( "RuntimeException: " + e );
        }
    }

    public final void resetQuickSearch() {
        quickSearchBuf.delete( 0, quickSearchBuf.length() );
    }

    public final void openGoPanel() {
        locationBar.openGoPanel( current, getFolderUriWithAuth( true ) );
    }

    public final void operationFinished() {
        if( null != destAdapter )
            destAdapter = null;
    }

    public final void copyFiles( String dest, boolean move, boolean touch ) {
        try {
            final String SLS = File.separator;
            final char   SLC = SLS.charAt( 0 );
            if( dest == null )
                return;
            SparseBooleanArray items = getMultiple( touch );
            CommanderAdapter cur_adapter = getListAdapter( true );
            Uri dest_uri = Uri.parse( dest );
            if( Favorite.isPwdScreened( dest_uri ) ) {
                dest_uri = Favorite.borrowPassword( dest_uri, getFolderUriWithAuth( false ) );
                if( dest_uri == null ) {
                    c.showError( c.getString( R.string.inv_dest ) );
                    return;
                }
            }
            if( Utils.getCount( items ) == 1 && !"..".equals( dest ) ) {
                int pos = Utils.getPosition( items, 0 );
                if( pos <= 0 )
                    return;
                final boolean COPY = true;
                boolean make_copy = false;
                if( dest.indexOf( SLC ) < 0 )  // just a file name to copy to
                    make_copy = true;
                else if( cur_adapter.hasFeature( Feature.FS ) && dest.charAt( dest.length()-1 ) != SLC ) {
                    if( dest.charAt( 0 ) == SLC ) { // local FS
                        File dest_file = new File( dest );
                        if( !dest_file.exists() || !dest_file.isDirectory() )
                            make_copy = true;
                    }
                }
                if( make_copy ) {
                    cur_adapter.renameItem( pos, dest, COPY );
                    return;
                }
            }

            CommanderAdapter oth_adapter = getListAdapter( false );
            Uri oth_uri = null;
            boolean create_new_adapter = false;
            if( oth_adapter == null ) 
                create_new_adapter = true;
            else {
                oth_uri = oth_adapter.getUri();
                create_new_adapter = oth_uri == null ||
                      !Utils.equals( oth_uri.getScheme(), dest_uri.getScheme() ) ||
                      !Utils.equals( oth_uri.getHost(),   dest_uri.getHost() )   ||
                      !Utils.equals( Utils.mbAddSl( oth_uri.getPath() ), Utils.mbAddSl( dest_uri.getPath() ) );
            }
            if( create_new_adapter ) {
                if( "..".equals( dest ) ) {
                    oth_adapter = CA.CreateAdapter( cur_adapter.getUri(), c );
                    Uri cur_uri = cur_adapter.getUri();
                    String p = cur_uri.getEncodedPath();
                    if( !Utils.str( p ) || "/".equals( p ) ) {
                        c.showError( c.getString( R.string.inv_dest ) );
                        return;
                    }
                    int len_ = p.length()-1;
                    if( p.charAt( len_ ) == SLC )
                        p = p.substring( 0, len_ );
                    p = p.substring( 0, p.lastIndexOf( SLC ) );
                    if( p.length() == 0 )
                        p = "/";
                    oth_adapter.setUri( cur_uri.buildUpon().encodedPath( p ).build() );
                    oth_adapter.setCredentials( cur_adapter.getCredentials() );
                } else {
                    if( dest_uri == null ) {
                        c.showError( c.getString( R.string.inv_dest ) );
                        return;
                    }
                    oth_adapter = CA.CreateAdapter( dest_uri, c );
                    if( oth_adapter == null ) {
                        c.showError( c.getString( R.string.inv_dest ) );
                        return;
                    }
                    if( oth_uri != null ) {
                        oth_adapter.setUri( oth_uri );  // let FTP adapter to copy the additional parameters
                        oth_adapter.setMode( CommanderAdapter.MODE_CLONE, CommanderAdapter.CLONE_MODE );
                    }
                    oth_adapter.setUri( dest_uri );
                }
            }
            destAdapter = oth_adapter;
            if( destAdapter == null || !destAdapter.hasFeature( Feature.REAL ) ) {
                c.showError( c.getString( R.string.canceled ) );
                return;
            }
            cur_adapter.copyItems( items, destAdapter, move );
            // TODO: getCheckedItemPositions() returns an empty array after a
            // failed operation. why?
            list[current].flv.clearChoices();
        } catch( Exception e ) {
            Log.e( TAG, "copyFiles()", e );
            c.showError( e.getLocalizedMessage() );
        }
    }

    public final void renameItem( String new_name, boolean touched ) {
        CommanderAdapter adapter = getListAdapter( true );
        int pos = getSingle( touched );
        if( pos >= 0 ) {
            adapter.renameItem( pos, new_name, false );
            list[current].setSelection( new_name );
        }
    }

    public void createNewFile( String fileName ) {
        String local_name = fileName;
        CommanderAdapter ca = getListAdapter( true );
        if( fileName.charAt( 0 ) != '/' ) {
            String dirName = ca.toString();
            fileName = dirName + ( dirName.charAt( dirName.length() - 1 ) == '/' ? "" : "/" ) + fileName;
        }
        if( ca.createFile( fileName ) ) {
            refreshLists( fileName );
            setSelection( current, local_name );
            openForEdit( fileName, false );
        }
    }

    public final void createFolder( String new_name ) {
        getListAdapter( true ).createFolder( new_name );
        list[current].setSelection( new_name );
    }

    public final void createZip( String new_zip_name, boolean touch ) {
        if( new_zip_name == null || new_zip_name.length() == 0 ) return;
        CommanderAdapter ca = getListAdapter( true );
        if( ca instanceof FSAdapter ) {
            SparseBooleanArray cis = getMultiple( touch );
            if( cis == null || cis.size() == 0 ) {
                c.showError( c.getString( R.string.op_not_alwd ) );
                return;
            }
            FSAdapter fsa = (FSAdapter)ca;
            ZipAdapter z = new ZipAdapter( c );
            z.Init( c );
            destAdapter = z;
            File[] files = fsa.bitsToFiles( cis );
            String full_name = new_zip_name.charAt( 0 ) == '/' ? new_zip_name : 
                                Utils.mbAddSl( ca.toString() ) + new_zip_name; 
            z.createZip( files, full_name );
        } else
            c.showError( c.getString( R.string.not_supported ) );
    }

    public final void unpackZip() {
        CommanderAdapter ca = getListAdapter( true );
        if( ca instanceof FSAdapter ) {
            FSAdapter fsa = (FSAdapter)ca;
            SparseBooleanArray cis = getMultiple( true );
            if( cis == null || cis.size() == 0 ) return;
            File[] files = fsa.bitsToFiles( cis );
            if( files == null || files.length == 0 ) return;
            if( !".zip".equalsIgnoreCase( Utils.getFileExt( files[0].getName() ) ) ) return;
            ZipAdapter z = new ZipAdapter( c );
            z.Init( c );
            z.unpackZip( files[0] );
        }
    }
    
    public final void deleteItems( boolean touch ) {
        // c.showDialog( Dialogs.PROGRESS_DIALOG );
        if( getListAdapter( true ).deleteItems( getMultiple( touch ) ) )
            list[current].flv.clearChoices();
    }

    // /////////////////////////////////////////////////////////////////////////////////

    /**
     * An AdapterView.OnItemSelectedListener implementation
     */
    @Override
    public void onItemSelected( AdapterView<?> listView, View itemView, int pos, long id ) {
        // Log.v( TAG, "Selected item " + pos );
        locationBar.closeGoPanel();
        int which = list[current].id == listView.getId() ? current : opposite();
        list[which].setCurPos( pos );
        list[which].updateStatus();
    }

    @Override
    public void onNothingSelected( AdapterView<?> listView ) {
        // Log.v( TAG, "NothingSelected" );
        resetQuickSearch();
        int which = list[current].id == listView.getId() ? current : opposite();
        list[which].updateStatus();
    }

    /**
     * An AdapterView.OnItemClickListener implementation
     */
    @Override
    public void onItemClick( AdapterView<?> parent, View view, int position, long id ) {
        // Log.v( TAG, "onItemClick" );

        locationBar.closeGoPanel();
        resetQuickSearch();
        ListView flv = list[current].flv;
        if( flv != parent ) {
            togglePanels( false );
            Log.e( TAG, "onItemClick. current=" + current + ", parent=" + parent.getId() );
        }
        if( position == 0 )
            flv.setItemChecked( 0, false ); // parent item never selected
        list[current].setCurPos( position );
        CommanderAdapter ca = (CommanderAdapter)flv.getAdapter();
        if( disableOpenSelectOnly && ca.hasFeature( Feature.CHKBL ) ) {
            disableOpenSelectOnly = false;
            BaseAdapter ba = (BaseAdapter)ca;
            ba.notifyDataSetChanged();
        } else {
            openItem( position );
            flv.setItemChecked( position, false );
        }
        list[current].updateStatus();
    }

    public void openItem( int position ) {
        ListHelper l = list[current];
        l.setCurPos( position );
        CommanderAdapter ca = (CommanderAdapter)l.flv.getAdapter();
        // hack to let the PictureViewer (if being chosen to handle the intent) be able to traverse other pictures in the dir
        if( ca instanceof FSAdapter ) {    
            Uri uri = ca.getItemUri( position );
            if( uri != null ) {
                String mime = Utils.getMimeByExt( Utils.getFileExt( uri.getPath() ) );
                if( mime != null && mime.startsWith( "image" ) ) {
                    if( !Utils.str( uri.getScheme() ) )
                        uri = uri.buildUpon().scheme( "file" ).authority( "" ).build();
                    Intent i = createImageViewIntent( uri, mime, ca, position );
                    c.startActivity( i );
                    return;
                }
            }
        }
        ca.openItem( position );
    }

    public void goUp() {
        getListAdapter( true ).openItem( 0 );
    }

    public void goTop() {
        if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO )
            ForwardCompat.smoothScrollToPosition( list[current].flv, 0 );
        else
            list[current].flv.setSelectionAfterHeaderView();
    }

    public void goBot() {
        ListView flv = list[current].flv;
        int pos = flv.getCount() - 1;
        if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO )
            ForwardCompat.smoothScrollToPosition( flv, pos );
        else
            flv.setSelection( pos );
    }

    /**
     * View.OnTouchListener implementation
     */
    @Override
    public boolean onTouch( View v, MotionEvent event ) {
        resetQuickSearch();
        if( panels_sliding && v == hsv ) {
            if( x_start < 0. && event.getAction() == MotionEvent.ACTION_MOVE )
                x_start = event.getX();
            else if( x_start >= 0. && event.getAction() == MotionEvent.ACTION_UP ) {
                float d = event.getX() - x_start;
                x_start = -1;
                final int to_which;
                if( Math.abs( d ) > scroll_back )
                    to_which = d > 0 ? LEFT : RIGHT;
                else
                    to_which = current == LEFT ? LEFT : RIGHT;
                setPanelCurrent( to_which );
                return true;
            }
        } else if( v instanceof ListView ) {
            if( v == list[opposite()].flv )
                togglePanels( false );

            locationBar.closeGoPanel();
            switch( event.getAction() ) {
            case MotionEvent.ACTION_DOWN: {
                downX = event.getX();
                downY = event.getY();
                disableOpenSelectOnly = event.getX() > v.getWidth() * selWidth;
                if( !selAtRight )
                    disableOpenSelectOnly = !disableOpenSelectOnly;
                break;
            }
            case MotionEvent.ACTION_UP: {
                int deltaX = (int)( event.getX() - downX );
                int deltaY = (int)( event.getY() - downY );
                int absDeltaX = Math.abs( deltaX );
                int absDeltaY = Math.abs( deltaY );
                int thldX = v.getWidth() / 50;
                int thldY = v.getHeight() / 50;
                if( thldX < 10 ) thldX = 10;
                if( thldY < 10 ) thldY = 10;
                if( absDeltaY > thldY || absDeltaX > thldX )
                    disableOpenSelectOnly = false;
                list[current].focus();
                break;
            }
            }
        }
        return false;
    }

    /*
     * View.OnKeyListener implementation
     */
    @Override
    public boolean onKey( View v, int keyCode, KeyEvent event ) {
        // Log.v( TAG, "panel key:" + keyCode + ", uchar:" +
        // event.getUnicodeChar() + ", shift: " + event.isShiftPressed() );

        if( v instanceof ListView ) {
            locationBar.closeGoPanel();
            if( event.getAction() == KeyEvent.ACTION_DOWN ) {
                char ch = (char)event.getUnicodeChar();
                if( ch >= 'A' && ch <= 'z' || ch == '.' ) {
                    quickSearch( ch );
                    return true;
                }
                resetQuickSearch();
                switch( ch ) {
                case '(':
                case ')': {
                    int which = ch == '(' ? LEFT : RIGHT;
                    locationBar.openGoPanel( which, getFolderUriWithAuth( isCurrent( which ) ) );
                }
                    return true;
                case '*':
                    addCurrentToFavorites();
                    return true;
                case '{':
                case '}':
                    setPanelCurrent( ch == '{' ? Panels.LEFT : Panels.RIGHT );
                    return true;
                case '#':
                    setLayoutMode( !sxs );
                    return true;
                case '~':
                    swapPanels();
                    return true;
                case '+':
                case '-':
                    c.showDialog( ch == '+' ? Dialogs.SELECT_DIALOG : Dialogs.UNSELECT_DIALOG );
                    return true;
                case '"':
                    c.dispatchCommand( R.id.sz );
                    return true;
                case '2':
                    c.dispatchCommand( R.id.F2 );
                    return true;
                case '3':
                    c.dispatchCommand( R.id.F3 );
                    return true;
                case '4':
                    c.dispatchCommand( R.id.F4 );
                    return true;
                case '5':
                    c.dispatchCommand( R.id.F5 );
                    return true;
                case '6':
                    c.dispatchCommand( R.id.F6 );
                    return true;
                case '7':
                    c.dispatchCommand( R.id.F7 );
                    return true;
                case '8':
                    c.dispatchCommand( R.id.F8 );
                    return true;
                case ' ':
                    list[current].checkItem( true );
                    return true;
                }
                switch( keyCode ) {
                case KeyEvent.KEYCODE_DEL:
                    if( !c.backExit() )
                        goUp();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    resetQuickSearch();
                    if( event.isShiftPressed() ) {
                        list[current].checkItem( false );
                        // ListView will not move to next item on Shift+DPAD, so
                        // let's remove the Shift
                        // bit from meta state and re-dispatch the event.
                        KeyEvent shiftStrippedEvent = new KeyEvent( event.getDownTime(), event.getEventTime(), KeyEvent.ACTION_DOWN,
                                keyCode, event.getRepeatCount(), event.getMetaState()
                                        & ~( KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_RIGHT_ON ) );
                        return v.onKeyDown( keyCode, shiftStrippedEvent );
                    }
                    return false;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if( arrowsLegacy ) {
                        list[current].checkItem( true );
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_VOLUME_UP:
                    if( volumeLegacy ) {
                        list[current].checkItem( true );
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if( arrowsLegacy ) {
                        togglePanels( false );
                        return true;
                    }
                default:
                    return false;
                }
            }
            if( event.getAction() == KeyEvent.ACTION_UP ) {
                if( keyCode == KeyEvent.KEYCODE_BACK ) {
                    if( !c.backExit() )
                        goUp();
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * View.OnClickListener and OnLongClickListener implementation for the
     * titles and history Go
     */
    @Override
    public void onClick( View v ) {
        resetQuickSearch();
        int view_id = v.getId();
        switch( view_id ) {
        case R.id.left_dir:
        case R.id.right_dir:
            locationBar.closeGoPanel();
            int which = view_id == titlesIds[LEFT] ? LEFT : RIGHT;
            if( which == current ) {
                focus();
                refreshList( current, true, null );
            } else
                togglePanels( true );
        }
    }

    @Override
    public boolean onLongClick( View v ) {
        int which = v.getId() == titlesIds[LEFT] ? LEFT : RIGHT;
        locationBar.openGoPanel( which, getFolderUriWithAuth( isCurrent( which ) ) );
        return true;
    }

    /*
     * ListView.OnScrollListener implementation
     */
    public void onScroll( AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount ) {
    }

    @Override
    public void onScrollStateChanged( AbsListView view, int scrollState ) {
        CommanderAdapter ca;
        try {
            ca = (CommanderAdapter)view.getAdapter();
        } catch( ClassCastException e ) {
            Log.e( TAG, "onScrollStateChanged()", e );
            return;
        }
        if( ca != null ) {
            switch( scrollState ) {
            case OnScrollListener.SCROLL_STATE_IDLE:
                ca.setMode( CommanderAdapter.LIST_STATE, CommanderAdapter.STATE_IDLE );
                view.invalidateViews();
                break;
            case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
            case OnScrollListener.SCROLL_STATE_FLING:
                ca.setMode( CommanderAdapter.LIST_STATE, CommanderAdapter.STATE_BUSY );
                break;
            }
        }
    }

    /*
     * Persistent state
     */

    public void storeChoosedItems() {
        list[current].storeChoosedItems();
    }

    public void reStoreChoosedItems() {
        list[current].reStoreChoosedItems();
    }

    final static class State {
        private final static String LU = "LEFT_URI",  RU = "RIGHT_URI";
        private final static String LC = "LEFT_CRD",  RC = "RIGHT_CRD";
        private final static String LI = "LEFT_ITEM", RI = "RIGHT_ITEM";
        private final static String LM = "LEFT_MODE", RM = "RIGHT_MODE";
        private final static String CP = "LAST_PANEL";
        private final static String FU = "FAV_URIS";
        private final static String FV = "FAVS";
        private int current = -1;
        private Credentials leftCrd, rightCrd;
        private Uri         leftUri, rightUri;
        private String      leftItem,rightItem;
        private int         leftMode,rightMode;
        private String favs, fav_uris;
        
        public final int getCurrent() {
            return current;
        }

        public final void store( Bundle b ) {
            b.putInt( CP, current );
            b.putParcelable( LC, leftCrd );
            b.putParcelable( RC, rightCrd );
            b.putParcelable( LU, leftUri );
            b.putParcelable( RU, rightUri );
            b.putString( LI, leftItem );
            b.putString( RI, rightItem );
            b.putInt( LM, leftMode );
            b.putInt( RM, rightMode );
            b.putString( FV, favs );
        }

        public final void restore( Bundle b ) {
            current   = b.getInt( CP );
            leftCrd   = b.getParcelable( LC );
            rightCrd  = b.getParcelable( RC );
            leftUri   = b.getParcelable( LU );
            rightUri  = b.getParcelable( RU );
            leftItem  = b.getString( LI );
            rightItem = b.getString( RI );
            leftMode  = b.getInt( LM );
            rightMode = b.getInt( RM );
            favs = b.getString( FV );
            if( favs == null || favs.length() == 0 )
                fav_uris = b.getString( FU );
        }

        public final void store( SharedPreferences.Editor e ) {
            e.putInt( CP, current );
            e.putString( LU,  leftUri != null ?  leftUri.toString() : "" );
            e.putString( RU, rightUri != null ? rightUri.toString() : "" );
            e.putString( LC,  leftCrd != null ?  leftCrd.exportToEncriptedString() : "" );
            e.putString( RC, rightCrd != null ? rightCrd.exportToEncriptedString() : "" );
            e.putString( LI,  leftItem );
            e.putString( RI, rightItem );
            e.putInt( LM,     leftMode );
            e.putInt( RM,    rightMode );
            e.putString( FV, favs );
        }

        public final void restore( SharedPreferences p ) {
            String left_uri_s = p.getString( LU, null );
            if( Utils.str( left_uri_s ) )
                leftUri = Uri.parse( left_uri_s );
            String right_uri_s = p.getString( RU, null );
            if( Utils.str( right_uri_s ) )
                rightUri = Uri.parse( right_uri_s );

            String left_crd_s = p.getString( LC, null );
            if( Utils.str( left_crd_s ) )
                leftCrd = Credentials.createFromEncriptedString( left_crd_s );
            String right_crd_s = p.getString( RC, null );
            if( Utils.str( right_crd_s ) )
                rightCrd = Credentials.createFromEncriptedString( right_crd_s );
            leftItem  = p.getString( LI, null );
            rightItem = p.getString( RI, null );
            leftMode  = p.getInt( LM, 0 );
            rightMode = p.getInt( RM, 0 );
            current = p.getInt( CP, LEFT );
            favs = p.getString( FV, "" );
            if( favs == null || favs.length() == 0 )
                fav_uris = p.getString( FU, "" );
        }
        public final static void storeFaves( SharedPreferences.Editor e, String fas ) {
            e.putString( FV, fas );        
        }
        public final static String restoreFaves( SharedPreferences p ) {
            return p.getString( FV, "" );        
        }
    }

    public final State createEmptyStateObject() {
        return new State();
    }

    public final State getState() {
        //Log.v( TAG, "getState()" );
        State s = createEmptyStateObject();
        s.current = current;
        try {
            CommanderAdapter left_adapter = (CommanderAdapter)list[LEFT].getListAdapter();
            s.leftUri  = left_adapter.getUri();
            s.leftCrd  = left_adapter.getCredentials();
            s.leftMode = left_adapter.getMode() & ( CommanderAdapter.MODE_SORTING | CommanderAdapter.MODE_SORT_DIR );
            int pos = list[LEFT].getCurPos();
            s.leftItem = pos >= 0 ? left_adapter.getItemName( pos, false ) : "";

            CommanderAdapter right_adapter = (CommanderAdapter)list[RIGHT].getListAdapter();
            s.rightUri  = right_adapter.getUri();
            s.rightCrd  = right_adapter.getCredentials();
            s.rightMode = right_adapter.getMode() & ( CommanderAdapter.MODE_SORTING | CommanderAdapter.MODE_SORT_DIR );
            pos = list[RIGHT].getCurPos();
            s.rightItem = pos >= 0 ? right_adapter.getItemName( pos, false ) : "";

            s.favs = favorites.getAsString();
        } catch( Exception e ) {
            Log.e( TAG, "getState()", e );
        }
        return s;
    }

    public final void setState( State s, int dont_restore ) {
        //Log.v( TAG, "setState()" );
        if( s == null )
            return;
        resetQuickSearch();
        if( s.favs != null && s.favs.length() > 0 )
            favorites.setFromString( s.favs );
        else if( s.fav_uris != null )
            favorites.setFromOldString( s.fav_uris );
        current = s.current;
        if( dont_restore != LEFT ) {
            ListHelper list_h = list[LEFT];
            CommanderAdapter ca = list_h.getListAdapter(); 
            if( ca == null ) {
                Uri lu = s.leftUri != null ? s.leftUri : Uri.parse( "home:" );
                list_h.adapterMode = s.leftMode;
                list_h.mbNavigate( lu, s.leftCrd, s.leftItem, s.current == LEFT );
            } else {
                if( !"find".equals( ca.getScheme() ) )
                    list_h.refreshList( s.current == LEFT, s.leftItem );
            }
        }
        if( dont_restore != RIGHT ) {
            ListHelper list_h = list[RIGHT];
            CommanderAdapter ca = list_h.getListAdapter(); 
            if( ca == null ) {
                Uri ru = s.rightUri != null ? s.rightUri : Uri.parse( "home:" );
                list_h.adapterMode = s.rightMode;
                list_h.mbNavigate( ru, s.rightCrd, s.rightItem, s.current == RIGHT );
            } else
                if( !"find".equals( ca.getScheme() ) )
                    list_h.refreshList( s.current == RIGHT, s.rightItem );
        }
        applyColors();
    }
    
    final void restoreFaves() {
        SharedPreferences prefs = c.getPreferences( Context.MODE_MULTI_PROCESS );
        favorites.setFromString( State.restoreFaves( prefs ) );
    }
}
