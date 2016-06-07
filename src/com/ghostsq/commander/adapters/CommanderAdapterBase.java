package com.ghostsq.commander.adapters;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ghostsq.commander.ColorsKeeper;
import com.ghostsq.commander.R;
import com.ghostsq.commander.Commander;
import com.ghostsq.commander.root.RootAdapter;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.Utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public abstract class CommanderAdapterBase extends BaseAdapter implements CommanderAdapter {
    protected final static String DEFAULT_DIR = Environment.getExternalStorageDirectory().getAbsolutePath();
    protected final String TAG = getClass().getName();
    public Context ctx;
    public Commander commander = null;
    public static final String SLS = File.separator;
    public static final char   SLC = File.separatorChar;
    public static final String PLS = "..";
    private static final boolean long_date = Locale.getDefault().getLanguage().compareTo( "en" ) != 0;
    private java.text.DateFormat localeDateFormat;
    private java.text.DateFormat localeTimeFormat;

    protected static final int ICON_SIZE = 32;
    protected int icoWidth = ICON_SIZE, imgWidth = ICON_SIZE;
    protected float density = 1;
    protected LayoutInflater mInflater = null;
    private int parentWidth, nameWidth, sizeWidth, dateWidth, attrWidth;
    private boolean a3r = false;
    protected boolean dirty = true;
    protected int thumbnail_size_perc = 100, font_size = 18;
    protected int mode = 0;
    protected boolean ascending = true;
    protected String parentLink = SLS;
    protected int numItems = 0;
    public  int shownFrom = 0, shownNum = 3;
    
    private static ColorsKeeper ck;
    private static int[]        typeColors   = new int[0];
    private static Pattern[][]  filePatterns = new Pattern[0][];

    public static void setTypeMaskColors( ColorsKeeper ck_ ) {
        try {
            ck = ck_;
            int n = ck.ftColors.size();
            typeColors   = new int[n];
            filePatterns = new Pattern[n][];
            for( int i = 0; i < n; i++ ) {
                ColorsKeeper.FileTypeColor ftc = ck.ftColors.get( i );
                if( ftc == null ) break;
                int    color = ftc.color;
                String smask = ftc.masks;
                if( smask == null ) break;
                typeColors[i] = color;
                String[] masks = smask.split( ";" );
                int m = masks.length;
                filePatterns[i] = new Pattern[m];
                for( int j = 0; j < m; j++ ) {
                    String re = masks[j].replace( ".", "\\." ).replace( "*", ".*" );
                    filePatterns[i][j] = Pattern.compile( re, Pattern.CASE_INSENSITIVE );
                }
            }
        } catch( Exception e ) {
        }
    }

    // Virtual method - to override!
    // derived adapter classes need to override this to take the obtained items
    // array and notify the dataset change
    protected void onReadComplete() {
    }

    protected class ReaderHandler extends Handler {
        @Override
        public void handleMessage( Message msg ) {
            try {
                if( msg.what <= Commander.OPERATION_FAILED ) {
                    onReadComplete();
                    reader = null;
                }
                commander.notifyMe( msg );
            } catch( Exception e ) {
                e.printStackTrace();
            }
        }
    };

    protected class SimpleHandler extends Handler {
        @Override
        public void handleMessage( Message msg ) {
            commander.notifyMe( msg );
        }
    };
    
    
    protected Engine reader = null;
    protected ReaderHandler readerHandler = null;
    protected SimpleHandler simpleHandler = null;

    // the Init( c ) method to be called after the constructor
    protected CommanderAdapterBase() {
    }

    protected CommanderAdapterBase(Context ctx_) {
        ctx = ctx_;
    }

    protected CommanderAdapterBase(Context ctx_, int mode_) {
        ctx = ctx_;
        mode = mode_;
    }

    @Override
    public void Init( Commander c ) {
        if( c != null ) {
            commander = c;
            readerHandler = new ReaderHandler();
            simpleHandler = new SimpleHandler();
            if( ctx == null )
                ctx = c.getContext();
            mInflater = (LayoutInflater)ctx.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
            Utils.changeLanguage( ctx );
            localeDateFormat = DateFormat.getDateFormat( ctx );
            localeTimeFormat = DateFormat.getTimeFormat( ctx );
            density = ctx.getResources().getDisplayMetrics().density;
            //Log.i( TAG, "Density: " + density );
        }
        parentWidth = 0;
        nameWidth = 0;
        sizeWidth = 0;
        dateWidth = 0;
        attrWidth = 0;
    }
    
    public void setContext( Context ctx_ ) {
        ctx = ctx_;
    }

    private final void calcWidths() {
        try {
            if( ( mode & ICON_MODE ) == ICON_MODE ) {
                icoWidth = (int)( density * ICON_SIZE );
                if( ( ICON_TINY & mode ) != 0 )
                    icoWidth >>= 1;
            } else
                icoWidth = 0;
            imgWidth = thumbnail_size_perc > 0 && thumbnail_size_perc != 100 ? icoWidth * thumbnail_size_perc / 100 : icoWidth;
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    public int getImgWidth() {
        return imgWidth;
    }

    @Override
    public int getMode() {
        return mode;
    }

    @Override
    public int setMode( int mask, int val ) {
        /*
        if( ( mask & SET_MODE_COLORS ) != 0 ) {
            switch( mask & SET_MODE_COLORS ) {
            case SET_TXT_COLOR:
                fg_color = val;
                break;
            case SET_SEL_COLOR:
                sl_color = val;
                break;
            }
            return 0;
        }
        */
        if( ( mask & SET_FONT_SIZE ) != 0 ) {
            font_size = val;
            return 0;
        }
        if( ( mask & SET_TBN_SIZE ) != 0 ) {
            thumbnail_size_perc = val;
            calcWidths();
            return 0;
        }
        if( ( mask & ( MODE_FINGERF | MODE_ICONS ) ) != 0 )
            calcWidths();

        mode &= ~mask;
        mode |= val;
        if( mask == LIST_STATE ) {
            /*
             * Log.v( TAG, ( mode & LIST_STATE ) == STATE_IDLE ?
             * "list    I D L E  !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" :
             * "list    B U S Y  !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" ); //
             * Android v2.3.3 has a bug (again!)
             */
        } else
            dirty = true;
        if( ( mask & MODE_SORT_DIR ) != 0 || ( mask & MODE_SORTING ) != 0 ) {
            if( ( mask & MODE_SORT_DIR ) != 0 )
                ascending = ( val & MODE_SORT_DIR ) == SORT_ASC;
            reSort();
            notifyDataSetChanged();
        }
        return mode;
    }

    @Override
    public void terminateOperation() {
        Log.i( TAG, "terminateOperation()" );
        if( reader != null )
            reader.reqStop();
    }

    @Override
    public void prepareToDestroy() {
        Log.i( TAG, "prepareToDestroy()" );
        terminateOperation();
        reader = null;
    }

    public final boolean _isWorkerStillAlive() {
        /*
        if( worker == null )
            return false;
        return worker.reqStop();
        */
        return false;
    }

    protected void notify( String s, String cookie ) {
        if( readerHandler == null ) return;
        Message msg = readerHandler.obtainMessage( s != null ? Commander.OPERATION_FAILED :
                                                               Commander.OPERATION_COMPLETED, s );
        if( msg != null ) {
            Bundle b = new Bundle();
            b.putString( Commander.NOTIFY_COOKIE, cookie );
            msg.setData( b );
            msg.sendToTarget();
        }
    }
    protected void notify( String cookie ) {
        notify( null, cookie );
    }
    
    protected void notify( String s, int what, int arg1 ) {
        Message msg = Message.obtain( simpleHandler, what, arg1, -1, s );
        if( msg != null )
            msg.sendToTarget();
    }
    
    protected void notify( String s, int what ) {
        notify( s, what, -1 );
    }
    
    protected void notify( int what ) {
        notify( null, what, -1 );
    }

    protected void notifyRefr( String item_name ) {
        Message msg = readerHandler.obtainMessage( Commander.OPERATION_COMPLETED_REFRESH_REQUIRED, null );
        if( msg != null ) {
            Bundle b = new Bundle();
            b.putString( Commander.NOTIFY_POSTO, item_name );
            msg.setData( b );
            msg.sendToTarget();
        }
    }
    protected void notifyNav( Uri uri ) {
        Message msg = readerHandler.obtainMessage( Commander.OPERATION_COMPLETED_REFRESH_REQUIRED, null );
        if( msg != null ) {
            Bundle b = new Bundle();
            b.putParcelable( Commander.NOTIFY_URI, uri );
            msg.setData( b );
            msg.sendToTarget();
        }
    }
    protected boolean notErr() {
        notify( s( R.string.not_supported ), Commander.OPERATION_FAILED );
        return false;
    }

    protected final String createTempDir() {
        return Utils.createTempDir( ctx ).getAbsolutePath();
    }

    @Override
    public int getCount() {
        return numItems;
    }

    public void setCount( int n ) {
        numItems = n;
        notifyDataSetChanged();
    }
    
    @Override
    public long getItemId( int position ) {
        return position;
    }

    @Override
    public View getView( int position, View convertView, ViewGroup parent ) {
        Item item = (Item)getItem( position );
        if( item == null )
            return null;
        ListView flv = (ListView)parent;
        SparseBooleanArray cis = flv.getCheckedItemPositions();
        item.sel = cis != null ? cis.get( position ) : false;
        View v = getView( convertView, parent, item );
        if( v == null ) Log.e( TAG, "View for the position " + position + " is null!" );
        return v;
    }

    protected String getLocalDateTimeStr( Date date ) {
        try {
            return localeDateFormat.format( date ) + " " + localeTimeFormat.format( date );
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return "(ERR)";
    }

    //Virtual, override to show attributes
    protected int getPredictedAttributesLength() {
        return 0;
    }

    protected View getView( View convertView, ViewGroup parent, Item item ) {
        View row_view = null;
        try {
            int parent_width = parent.getWidth();
            boolean recalc = dirty || parentWidth != parent_width;
            parentWidth = parent_width;
            dirty = false;
            boolean wm = (mode & MODE_WIDTH) == WIDE_MODE;
            boolean dm = ( mode & MODE_DETAILS ) == DETAILED_MODE;
            boolean ao = ( ATTR_ONLY & mode ) != 0;
            boolean current_wide = convertView != null && convertView.getId() == R.id.row_layout;
            if( convertView == null || 
        		( (  wm && !current_wide ) || 
        		  ( !wm &&  current_wide ) ) ) {
                row_view = mInflater.inflate( wm ? R.layout.row : R.layout.narrow, parent, false );
            }
            else {
                row_view = convertView;
                row_view.setBackgroundColor( 0 ); // transparent
            }
            boolean fat = ( mode & MODE_FINGERF ) == FAT_MODE;
            final int  LEFT_P = 1;
            final int RIGHT_P = 2;
            
            ImageView imgView = (ImageView)row_view.findViewById( R.id.fld_icon );
            TextView nameView =  (TextView)row_view.findViewById( R.id.fld_name );
            TextView attrView =  (TextView)row_view.findViewById( R.id.fld_attr );
            TextView dateView =  (TextView)row_view.findViewById( R.id.fld_date );
            TextView sizeView =  (TextView)row_view.findViewById( R.id.fld_size );

            float fnt_sz_rdc = font_size * 0.75f;   // reduced font size
            String name = item.name, size = "", date = "";
            if( dm ) {
            	if( item.size >= 0 )
            		size = Utils.getHumanSize( item.size );
            	final String MDHM_date_frm = "MMM dd kk:mm";
                if( item.date != null ) {
                    if( long_date ) {
                        date = getLocalDateTimeStr( item.date );
                    } else {
        	            String dateFormat;
    	            	dateFormat = item.date.getYear() + 1900 == Calendar.getInstance().get( Calendar.YEAR ) ?
    	                        MDHM_date_frm : "MMM dd yyyy ";
        	            date = (String)DateFormat.format( dateFormat, item.date );
                    }
                }
                if( recalc ) {
                    //Log.v( TAG, "recalc" );
                    if( ao ) {
                        sizeWidth = 0;
                        dateWidth = 0;
                        attrWidth = wm ? ( parent_width - icoWidth ) / 2 : parent_width - LEFT_P - RIGHT_P - icoWidth;
                    }
                    else {
                        if( dateView != null ) {
                            dateView.setTextSize( fnt_sz_rdc );
                            // dateWidth is pixels, but what's the return of measureText() ???
                            String sample_date = long_date ? "M" + getLocalDateTimeStr( new Date( -1 ) ) : MDHM_date_frm;
                            if( wm ) sample_date += "M";
                            dateWidth = (int)dateView.getPaint().measureText( sample_date );
                        }
                        if( sizeView != null ) {
                            sizeView.setTextSize( fnt_sz_rdc );
                            // sizeWidth is pixels, but what's the return of measureText() ???
                            sizeWidth = (int)sizeView.getPaint().measureText( "99999.9M" );
                        }
                        if( attrView != null ) {
                            // sizeWidth is pixels, but in what units the return of measureText() ???
                            int al = getPredictedAttributesLength();
                            if( al > 0 ) {
                                char[] dummy = new char[al];
                                Arrays.fill( dummy, 'c');
                                if( this instanceof RootAdapter ) {  // hack, redesign
                                    attrView.setTypeface( Typeface.create( "monospace", Typeface.NORMAL ) );
                                    attrView.setTextSize( fnt_sz_rdc * 0.9f );
                                }
                                else
                                    attrView.setTextSize( fnt_sz_rdc );
                                attrWidth = (int)attrView.getPaint().measureText( new String( dummy ) );
                                if( !wm ) {
                                    int remain = parent_width - sizeWidth - dateWidth - icoWidth - LEFT_P - RIGHT_P;
                                    a3r = attrWidth > remain;
                                    //Log.v( TAG, "aw=" + attrWidth + ",sl=" + remain + ",a3r=" + a3r );
                                    attrWidth = remain;
                                    if( a3r ) {
                                        attrWidth += sizeWidth + dateWidth;
                                    }
                                }
                            }
                            else
                                attrWidth = 0;
                        }
                    }
                }
            }
            if( item.sel && ck != null )
                row_view.setBackgroundColor( ck.selColor & 0xCFFFFFFF );
            int img_width = icoWidth;
            if( imgView != null ) {
                if( icoWidth > 0 ) {
                    imgView.setVisibility( View.VISIBLE );
                    boolean th_ok = false;
                    if( item.isThumbNail() && thumbnail_size_perc > 0 ) {
                        Drawable th = item.getThumbNail();
                        if( th != null ) {
                            if( !item.thumb_is_icon )
                                img_width = imgWidth;
                            RelativeLayout.LayoutParams rllp = (RelativeLayout.LayoutParams)imgView.getLayoutParams();
                            rllp.width = img_width;
                            rllp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                            imgView.setImageDrawable( th );
                            imgView.requestLayout();
                            imgView.invalidate();
                            th_ok = true;
                        }
                    }
                    if( !th_ok ) {
                        // when list is on its end we don't receive the idle notification!
                        if( thumbnail_size_perc > 0 && !item.no_thumb && ( mode & LIST_STATE ) == STATE_IDLE ) {
                            synchronized( this ) {
                                item.need_thumb = true;
                                notifyAll();
                            }
                        }
                        try {
                            //imgView.setMaxWidth( img_width );
                            RelativeLayout.LayoutParams rllp = (RelativeLayout.LayoutParams)imgView.getLayoutParams();
                            rllp.width = img_width;
                            rllp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                            int ico_id;
                            if( item.icon_id != -1 )
                                ico_id = item.icon_id;
                            else {
                                if( SLS.equals( item.name ) || PLS.equals( item.name ) )
                                    ico_id = R.drawable.up;
                                else if( item.dir )
                                    ico_id = R.drawable.folder;
                                else
                                    ico_id = getIconId( name );
                            }
                            imgView.setImageResource( ico_id );
                        }
                        catch( OutOfMemoryError e ) {
                            Log.e( TAG, "", e );
                        }
                    }
                }
                else
                    imgView.setVisibility( View.GONE );
            }
            int fg_color = ck != null ? ( item.sel ? ck.sfgColor : ck.fgrColor ) : ctx.getResources().getColor( R.color.fgr_def );
            int fg_color_m = fg_color;
            if( name == null || item.colorCache != 0 )
                fg_color_m = item.colorCache; 
            else {
                try {
                    for( int i = 0; i < typeColors.length; i++ ) {
                        for( int j = 0; j < filePatterns[i].length; j++ ) {
                            Pattern fp = filePatterns[i][j];
                            Matcher m = fp.matcher( name );
                            if( m != null && m.matches() ) {
                                 fg_color_m = typeColors[i];
                                 item.colorCache = fg_color_m;
                                 break;
                            }
                        }
                        if( fg_color_m != fg_color )
                            break;
                    }
                } catch( Exception e ) {
                    Log.e( TAG, "file: " + name, e );
                }
            }
            if( nameView != null ) {
                nameView.setTextSize( font_size );
                if( wm ) {
                    nameWidth = parent_width - img_width - dateWidth - sizeWidth - attrWidth - LEFT_P - RIGHT_P;
                    if( nameWidth < 280 ) {
                        nameWidth += attrWidth; // sacrifice the attr. field 
                        attrWidth = 0;
                    }
                    int nw = nameWidth;
                    if( !Utils.str( item.attr ) ) nw += attrWidth; 
                    nameView.setWidth( nw );
                }
                nameView.setText( name != null ? name : "???" );
                nameView.setTextColor( fg_color_m );
//nameView.setBackgroundColor( 0xFFFF00FF );  // DEBUG!!!!!!
            }
            if( dateView != null ) {
                boolean vis = dm && !ao && ( dateWidth > 0 );
                dateView.setVisibility( vis ? View.VISIBLE : View.GONE );
                if( vis ) {
                    dateView.setTextSize( fnt_sz_rdc );
                    dateView.setWidth( dateWidth );
                    dateView.setText( date );
                    dateView.setTextColor( fg_color_m );
//dateView.setBackgroundColor( 0xFF00AA00 );  // DEBUG!!!!!!
                }
            }
            if( sizeView != null ) {
                boolean vis = dm && !ao && ( sizeWidth > 0 );
                sizeView.setVisibility( vis ? View.VISIBLE : View.GONE );
                if( vis ) {
                    sizeView.setTextSize( fnt_sz_rdc );
                    sizeView.setWidth( sizeWidth );
                    sizeView.setText( size );
                    sizeView.setTextColor( fg_color_m );
//sizeView.setBackgroundColor( 0xFF0000FF );  // DEBUG!!!!!!
                }
            }
            if( attrView != null ) {
                boolean vis = dm && attrWidth > 0;
                attrView.setVisibility( vis ? View.VISIBLE : View.GONE );
                if( vis ) {
                    String attr_text = item.attr != null ? item.attr.trim() : "";
                    if( !wm ) {
                        //attrView.setPadding( img_width + 2, 0, 4, 0 ); // not to overlap the icon
                         {
                            RelativeLayout.LayoutParams rllp = new RelativeLayout.LayoutParams( 
                                                                   RelativeLayout.LayoutParams.WRAP_CONTENT, 
                                                                   RelativeLayout.LayoutParams.WRAP_CONTENT );
                            if( a3r ) {
                                rllp.addRule( RelativeLayout.ALIGN_PARENT_RIGHT );
                                rllp.addRule( RelativeLayout.BELOW, R.id.fld_date );
                                attrView.setGravity( 0x05 ); // RIGHT
                            } else {
                                rllp.addRule( RelativeLayout.BELOW, R.id.fld_name );
                                rllp.addRule( RelativeLayout.ALIGN_LEFT, R.id.fld_name );
                                rllp.addRule( RelativeLayout.ALIGN_TOP, R.id.fld_size );
                                attrView.setGravity( 0x03 ); // LEFT
                            }
                            attrView.setLayoutParams( rllp );
                        }
                    }
                    if( Utils.str( item.attr ) ) {
                        attrView.setWidth( attrWidth );
                        attrView.setTextSize( fnt_sz_rdc );
                        attrView.setVisibility( View.VISIBLE );
                        attrView.setText( attr_text );
                        attrView.setTextColor( fg_color_m );
                        if( this instanceof RootAdapter ) {
                            attrView.setTypeface( Typeface.create( "monospace", Typeface.NORMAL ) );
                            attrView.setTextSize( fnt_sz_rdc * 0.9f );
                        }
                    }
                    else {
                        attrView.setWidth( 0 );
                        attrView.setText( attr_text );
                    }
//attrView.setBackgroundColor( 0xFFFF0000 );  // DEBUG!!!!!!
                }
            }

            if( fat ) {
                int vp = (int)( 5 * density );
                row_view.setPadding( LEFT_P, vp, RIGHT_P, vp );
            }
            else 
                row_view.setPadding( LEFT_P, 3, RIGHT_P, 3 );
            
            row_view.setTag( null );
//Log.v( TAG, "p:" + parent_width + ",i:" + img_width + ",n:" + nameWidth + ",d:" + dateWidth + ",s:" + sizeWidth + ",a:" + attrWidth );            
        }
        catch( Exception e ) {
            Log.e( TAG, null, e ); 
        }
        return row_view;
    }

    public final static int getIconId( String file ) {
        String cat = Utils.getCategoryByExt( Utils.getFileExt( file ) );
        if( Utils.C_UNKNOWN.equals( cat ) )return R.drawable.unkn;
        if( Utils.C_AUDIO.equals( cat ) )  return R.drawable.audio;
        if( Utils.C_VIDEO.equals( cat ) )  return R.drawable.video;
        if( Utils.C_IMAGE.equals( cat ) )  return R.drawable.image;
        if( Utils.C_TEXT.equals( cat ) )   return R.drawable.text;
        if( Utils.C_BOOK.equals( cat ) )   return R.drawable.book;
        if( Utils.C_OFFICE.equals( cat ) ) return R.drawable.book;
        if( Utils.C_PDF.equals( cat ) )    return R.drawable.pdf;
        if( Utils.C_ZIP.equals( cat ) )    return R.drawable.zip;
        if( Utils.C_MARKUP.equals( cat ) ) return R.drawable.xml;
        if( Utils.C_APP.equals( cat ) )    return R.drawable.application;
        if( Utils.C_DROID.equals( cat ) )  return R.drawable.and;
        return R.drawable.unkn;
    }

    protected final String[] bitsToNames( SparseBooleanArray cis ) {
        try {
            int counter = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    counter++;
            String[] paths = new String[counter];
            int j = 0;
            for( int i = 0; i < cis.size(); i++ )
                if( cis.valueAt( i ) )
                    paths[j++] = getItemName( cis.keyAt( i ), true );
            return paths;
        } catch( Exception e ) {
            Log.e( TAG, "bitsToNames()", e );
        }
        return null;
    }

    @Override
    public Uri getItemUri( int position ) {
        return null;
    }

    @Override
    public boolean hasFeature( Feature feature ) {
        switch( feature ) {
        case F1:
        case F9:
        case F10:
        case EQ:
        case TGL:
        case HOME:
        case ENTER:
        case ADD_FAV:
        case FAVS:
        case SDCARD:
        case ROOT:
        case SOFTKBD:
        case SORTING:
        case MENU:
        case SCROLL:
            return true;
        case BY_NAME:
        case BY_EXT:
        case BY_SIZE:
        case BY_DATE:
            return hasFeature( Feature.SORTING );
        case  F2:
        case  F3:
        case  F4:
        case  F5:
        case  F6:
        case  F7:
        case  F8:
        case  SZ:
        case  SEL_UNS:
        case  HIDDEN:
        case  REFRESH:
        case  CHKBL:
            return hasFeature( Feature.REAL );
        case REAL:
            return false;
        default: return false;
        }
    }
    
    @Override
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num ) {
        try {
            Item item = (Item)getItem( acmi.position );
            boolean file = !item.dir && acmi.position != 0;
            if( acmi.position == 0 ) {
                menu.add( 0, R.id.eq, 0, R.string.oth_sh_this );
                return;
            }
            if( hasFeature( Feature.SZ ) )
                menu.add( 0, R.id.szt, 0, R.string.show_size );
            if( num <= 1 ) {
                if( hasFeature( Feature.F2 ) )
                    menu.add( 0, R.id.F2t, 0, R.string.rename_title );
                if( file ) {
                    if( hasFeature( Feature.F3 ) )
                        menu.add( 0, R.id.F3t, 0, R.string.view_title );
                    if( hasFeature( Feature.F4 ) )
                        menu.add( 0, R.id.F4t, 0, R.string.edit_title );
                }
            }
            if( hasFeature( Feature.LOCAL ) && file )
                menu.add( 0, Commander.SEND_TO, 0, R.string.send_to );
            if( hasFeature( Feature.F5 ) )
                menu.add( 0, R.id.F5t, 0, R.string.copy_title );
            if( hasFeature( Feature.F6 ) )
                menu.add( 0, R.id.F6t, 0, R.string.move_title );
            if( hasFeature( Feature.F8 ) )
                menu.add( 0, R.id.F8t, 0, R.string.delete_title );
            if( hasFeature( Feature.LOCAL ) ) {
                if( file && num <= 1 )
                    menu.add( 0, Commander.OPEN_WITH, 0, R.string.open_with );
                menu.add( 0, R.id.new_zip, 0, R.string.new_zip );
            }
            if( num <= 1 ) {
                menu.add( 0, Commander.COPY_NAME, 0, R.string.copy_name );
                if( hasFeature( Feature.LOCAL ) )
                    menu.add( 0, Commander.SHRCT_CMD, 0, R.string.shortcut );
            }
            if( acmi.position != 0 && item.dir && num == 1 ) {
                menu.add( 0, Commander.FAV_FLD, 0, ctx.getString( R.string.fav_fld, item.name ) );
                menu.add( 0, R.id.eq_dir, 0, ctx.getString( R.string.oth_sh_dir, item.name ) );
            }

        } catch( Exception e ) {
            Log.e( TAG, "populateContextMenu() " + e.getMessage(), e );
        }
    }

    @Override
    public void setCredentials( Credentials crd ) {
    }
    @Override
    public Credentials getCredentials() {
        return null;
    }

    @Override
    public void doIt( int command_id, SparseBooleanArray cis ) {
        // to be implemented in derived classes
    }

    @Override
    public boolean handleActivityResult( int requestCode, int resultCode, Intent data ) {
        // to be implemented in derived classes
        return false;
    }

    @Override
    public Item getItem( Uri u ) {
        return null;
    }
    
    @Override
    public InputStream getContent( Uri u, long skip ) {
        return null;
    }

    @Override
    public InputStream getContent( Uri u ) {
        return getContent( u, 0 );
    }

    @Override
    public OutputStream saveContent( Uri u ) {
        return null;
    }

    @Override
    public void closeStream( Closeable is ) {
        try {
            if( is != null )
                is.close();
        } catch( IOException e ) {
            e.printStackTrace();
        }
    }

    @Override
    public Engines.IReciever getReceiver() {
        return null;
    }
    
    protected void reSort() {
        // to override by all the derives
    }

    /*
     * public final void showMessage( String s ) { Toast.makeText(
     * commander.getContext(), s, Toast.LENGTH_LONG ).show(); }
     */
    protected final String s( int r_id ) {
        return ctx.getString( r_id );
    }
}
