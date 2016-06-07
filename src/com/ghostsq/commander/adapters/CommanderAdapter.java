package com.ghostsq.commander.adapters;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.utils.Credentials;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.SparseBooleanArray;
import android.view.ContextMenu;
import android.widget.AdapterView;

/**
 * <code>CommanderAdapter</code> interface
 * @author Ghost Squared (ghost.sq2@gmail.com)
 * <p>
 * All the adapters should extend {@link CommanderAdapterBase}
 * which implements this interface.
 * <p>
 * Most of the methods are asynchronous.
 * They start a new thread which sends Message objects
 * which is routed to the {@link Commander#notifyMe Commander.notifyMe( Message m )}
 * 
 */
/**
 * @author zc2
 *
 */
public interface CommanderAdapter {

    /**
     *   An instance of the following Item class to be returned by ListAdapter's getItem() override 
     *   @see android.widget.ListAdapter#getItem
     */
    public class Item {
        public  String    name = "";
        public  Date      date = null;
        public  long      size = -1;
        public  boolean   dir = false, sel = false;
        public  String    attr = "";
        public  Object    origin = null;
        public  int       icon_id = -1;
        private Drawable  thumbnail;
        private long      thumbnailUsed;
        public  int       colorCache = 0;
        public  Item() {}
        public  Item( String name_ )    { name = name_; }
        public  final boolean  isThumbNail()              { return thumbnail != null; }
        public  final Drawable getThumbNail()             { thumbnailUsed = System.currentTimeMillis(); return thumbnail; }
        public  final void     setThumbNail( Drawable t ) { thumbnailUsed = System.currentTimeMillis(); thumbnail = t; thumb_pending = false; }
        public  final void     setIcon( Drawable t ) { setThumbNail( t ); thumb_is_icon = true; }
        public  final boolean  remThumbnailIfOld( int ttl ) { 
            if( thumbnail != null && !need_thumb && System.currentTimeMillis() - thumbnailUsed > ttl ) {
                thumbnail = null;
                return true;
            }
            return false;
        }
        public boolean  need_thumb = false, no_thumb = false, thumb_is_icon = false, thumb_pending = false;
    }
    
    /**
     * To initialize an adapter, the adapter creator calls the Init() method.
     * This needed because only the default constructor can be called
     * during a creation of a foreign packaged class  
     * 
	 * @param c - the reference to commander
	 * 
	 * @see CA#CreateAdapter
	 *    
	 */
	public void Init( Commander c );

	/**
	 * @return the scheme the adapter implements
	 */
	public String getScheme();
	
    /**
     * Just passive set the current URI without an attempt to obtain the list or the similar
     * 
     * @param uri - the URI of the resource to connect to or work with  
     */
    public void setUri( Uri uri );
    
    /**
     * Retrieve the current URI from the adapter
     * 
     * @return current adapter URI
     *      Note, that the URI returned always without the credentials. 
     *      Use the {@link #getCredentials getCredentials()} method separately  
     */
    public Uri getUri();
	
	/**
	 *  Output modes.  
	 */
	public final static int MODE_WIDTH = 0x0001, NARROW_MODE = 0x0000,     WIDE_MODE = 0x0001, 
	                      MODE_DETAILS = 0x0002, SIMPLE_MODE = 0x0000, DETAILED_MODE = 0x0002,
	                      MODE_FINGERF = 0x0004,   SLIM_MODE = 0x0000,      FAT_MODE = 0x0004,
                          MODE_HIDDEN  = 0x0008,   SHOW_MODE = 0x0000,     HIDE_MODE = 0x0008,
                          MODE_SORTING = 0x0030,   SORT_NAME = 0x0000,     SORT_SIZE = 0x0010, SORT_DATE = 0x0020, SORT_EXT = 0x0030,
                        MODE_SORT_DIR  = 0x0040,    SORT_ASC = 0x0000,      SORT_DSC = 0x0040,
                            MODE_CASE  = 0x0080,   CASE_SENS = 0x0000,   CASE_IGNORE = 0x0080,
                             MODE_ATTR = 0x0300,     NO_ATTR = 0x0000,     SHOW_ATTR = 0x0100, ATTR_ONLY = 0x0200,
                             MODE_ROOT = 0x0400,  BASIC_MODE = 0x0000,     ROOT_MODE = 0x0400,
                            MODE_ICONS = 0x3000,   TEXT_MODE = 0x0000,     ICON_MODE = 0x1000, ICON_TINY = 0x2000,
                            LIST_STATE = 0x10000, STATE_IDLE = 0x00000,   STATE_BUSY = 0x10000,
                            MODE_CLONE = 0x20000,NORMAL_MODE = 0x00000,   CLONE_MODE = 0x20000,
                          SET_TBN_SIZE = 0x01000000, 
                         SET_FONT_SIZE = 0x02000000;

	/**
     * To set the desired adapter mode or pass some extra data.
     * <p>The mode is about how the adapter outputs the items
     * @param mask - could be one of the following  
     *  <p>     {@code MODE_WIDTH}    wide when there are enough space, narrow to output the data in two line to save the space
     *  <p>     {@code MODE_DETAILS}  to output the item details besides just the name
     *  <p>     {@code MODE_FINGERF}  to enlarge the item view 
     *  <p>     {@code MODE_HIDDEN}   to show hidden files
     *  <p>     {@code MODE_SORTING}  rules how the items are sorted  
     *  <p>     {@code MODE_SORT_DIR} direction of the sorting  
     *  <p>     {@code MODE_CASE}     to honor the case in the sorting     
     *  <p>     {@code MODE_ATTR}     to show additional attributes  
     *  <p>     {@code MODE_ROOT}     to show the root and mount in the home adapter  
     *  <p>     {@code MODE_ICONS}    to show the file icons  
     *  <p>     {@code LIST_STATE}    set the current state taken from {@link AbsListView.OnScrollListener#onScrollStateChanged}  
     *  <p>     {@code SET_TBN_SIZE}  to pass the integer - the size of the thubnails  
     *  <p>     {@code SET_FONT_SIZE} to pass the font size     
     * @param mode - the real value. See the bits above 
     * @return the current mode 
     */
    public int setMode( int mask, int mode );
    
    /**
     * @return current adapter's mode bits
     */
    public int getMode();
    
    
    /**
     *   Called when the user taps and holds on an item
     *   
     *   @param menu - to call the method .add()
     *   @param acmi - to know which item is processed
     *   @param num  - current mode
     */
    public void populateContextMenu( ContextMenu menu, AdapterView.AdapterContextMenuInfo acmi, int num );

    
    /**
     *  features to be probed by calling the {@code hasFeature()} 
     */
    public enum Feature {
        FS,
        LOCAL,
        REAL,
        F1,
        F2,
        F3,
        F4,
        SF4,
        F5,
        F6,
        F7,
        F8,
        F9,
        F10,
        EQ,
        TGL,
        SZ,
        SORTING,
        BY_NAME,
        BY_EXT,
        BY_SIZE,
        BY_DATE,
        SEL_UNS,
        ENTER,
        ADD_FAV,
        REMOUNT,
        HOME,
        FAVS,
        SDCARD,
        ROOT,
        MOUNT,
        HIDDEN,
        REFRESH,
        SOFTKBD,
        SEARCH,
        MENU,
        SEND,
        CHKBL,
        SCROLL
    }
    
    /**
     * queries an implemented feature
     * @return true if the feature  
     */
    public boolean hasFeature( Feature feature );

    /**
     * Pass the user credentials to be used later
     * 
     * @param user credentials
     */
    public void setCredentials( Credentials crd );

    /**
     * Obtain the current used credentials
     * 
     * @return user credentials
     */
    public Credentials getCredentials();
    
    /**
     * The "main" method to obtain the current adapter's content
     * 
     * @param uri - a folder's URI to initialize. If null passed, just refresh
     * @param pass_back_on_done - the file name to select
     */
	public boolean readSource( Uri uri, String pass_back_on_done );

	/**
     * Tries to do something with the item 
     * <p>Outside of an adapter we don't know how to process it.
     * But an adapter knows, is it a folder and can be opened (it calls Commander.Navigate() in this case)
     * or processed as default action (then it calls Commander.Open() )
	 * 
	 * @param position index of the item to action
	 * 
	 */
	public void openItem( int position );
	
    /**
     * Return the name of an item at the specified position
     * 
     * @param position - numer in the list. 
     * Starts from 1, because 0 is the link to the parent!
     * @param full     - true - to return the absolute path, false - only the local name
     * @return string representation of the item
     */
    public String getItemName( int position, boolean full );    

    /**
     * Return the URI of an item at the specified position
     * 
     * @param position
     * @return full URI to access the item without the credentials!
     */
    public Uri getItemUri( int position );    

	/**
	 * Starts the occupied size calculation procedure, or just some info
	 * 
	 * @param  cis selected item (files or directories)
	 *         will call Commander.NotifyMe( "requested size info", Commander.OPERATION_COMPLETED ) when done  
	 */
	public void reqItemsSize( SparseBooleanArray cis );
	
	/**
	 * @param position in the list
     * @param newName for the item
     * @param copy file (preserve old name)
	 * @return true if success
	 */
	public boolean renameItem( int position, String newName, boolean copy );
	
	/**
	 * @param cis	booleans which internal items to copy
	 * @param to    an adapter, which method receiveItems() to be called
	 * @param move  move instead of copy
	 * @return      true if succeeded
	 * 
	 * @see #receiveItems
	 */
	public boolean copyItems( SparseBooleanArray cis, CommanderAdapter to, boolean move );
	
	/**
	 *  To be used in receiveItems()
	 *  @see #receiveItems
	 */
    public final static int MODE_COPY = 0;
    public final static int MODE_MOVE = 1;
    public final static int MODE_DEL_SRC_DIR = 2;
    public final static int MODE_MOVE_DEL_SRC_DIR = 3;
	/**
	 * This method receives the files from another adapter 
	 * 
	 * @param fileURIs  list of files as universal transport parcel. All kind of adapters (network, etc.)
	 * 					accepts data as files. It should be called from the current list's adapter
	 * @param move_mode move mode
	 * @return          true if succeeded
	 */
	public boolean receiveItems( String[] fileURIs, int move_mode );

	/**
	 * A cast method since at this moment an adapter is also the implementation of the IReciever
	 * @return interface with receiveItems()
	 */
	public Engines.IReciever getReceiver();
	
    /**
     * @param fileURI - the location of the file  
     * @return        - the Item with all the information in it
     * This method is not supposed to be called from the UI thread.
     */
    public Item getItem( Uri fileURI );

    /**
     * @param fileURI - the location of the file
     * @param skip    - tells the data provider to start from a middle point  
     * @return        - the content of the file
     * The caller has to call closeStream() after it's done working with the content
     */
    public InputStream getContent( Uri fileURI, long skip );

    /**
     *  same as getContent( fileURI, 0 );
     */
    public InputStream getContent( Uri fileURI );

    /**
     * @param fileURI - the location of the file
     * @return  stream to data be written 
     * The caller has to call closeStream() after it's done working with the content
     */
    public OutputStream saveContent( Uri fileURI );
    
    /**
     * @param s - the stream obtained by the getContent() or saveContent() methods to be closed by calling this  
     */
    public void closeStream( Closeable s );
    

    /**
     * @param path - the location of the file to create  
     */
    public boolean createFile( String name );

    /**
     * @param path - the location of the folder (directory) to create  
     */
	public void createFolder( String name );

    /**
     * @param  cis selected item (files or directories)
     *         will call Commander.NotifyMe( "requested size info", Commander.OPERATION_COMPLETED ) when done  
     */
	public boolean deleteItems( SparseBooleanArray cis );
    
    /**
     * @param command_id - command id to execute
     * @param items - selected or checked items to work with  
     */
    public void doIt( int command_id, SparseBooleanArray cis );

    /**
     * this method is called when the Commander can't find what to do with an activity result
     */
    public boolean handleActivityResult( int requestCode, int resultCode, Intent data );
    
    /**
     * to be called before the adapter is going to be destroyed
     */
	public void terminateOperation();
	public void prepareToDestroy();
}
