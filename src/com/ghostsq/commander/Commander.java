/**
 *	This interface to abstract the commander's main executable from its utilities such as adapters 
 */
package com.ghostsq.commander;

import com.ghostsq.commander.adapters.Engine;
import com.ghostsq.commander.utils.Credentials;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Message;

/**
 * @author Ghost Squared
 *
 */
public interface Commander {
    public final static int REQUEST_CODE_PREFERENCES = 1, REQUEST_CODE_SRV_FORM = 2;    
    
	final static int UNKNOWN = 0, 
	                 ABORT   = 1,
	                 REPLACE = 2, 
	                 SKIP    = 4,
	                 DECIDED = 6,
                     APPLY_ALL   = 8,
	                 REPLACE_ALL = 8|2,
	                 SKIP_ALL    = 8|4;
	
    /**
     *   notifyMe() "what" constants:
     *   OPERATION_STARTED                     is sent when an operation starts
     *   OPERATION_FAILED                      always show message (default if not provided)  
     *   OPERATION_COMPLETED                   show message if provided)
     *   OPERATION_COMPLETED_REFRESH_REQUIRED  also make the adapter reread
     *   OPERATION_FAILED_LOGIN_REQUIRED       show user/pass dialog and pass the identity to the adapter with 
     *                                         the string as passed in the first parameter
     */
	public final static int  OPERATION_IN_PROGRESS = 0,
	                         OPERATION_STARTED = -1, 
	                         OPERATION_FAILED = -2, 
	                         OPERATION_COMPLETED = -3, 
	                         OPERATION_COMPLETED_REFRESH_REQUIRED = -4,
                             OPERATION_FAILED_LOGIN_REQUIRED = -5,
                             OPERATION_SUSPENDED_FILE_EXIST = -6,
                             OPERATION_FAILED_REFRESH_REQUIRED = -7;
	
	public final static int  OPERATION_REPORT_IMPORTANT = 870;

    public final static int  OPEN = 903, OPEN_WITH = 902, SEND_TO = 236, COPY_NAME = 390, FAV_FLD = 414, SHRCT_CMD = 269;
    
    public final static String NOTIFY_COOKIE = "cookie", NOTIFY_SPEED = "speed", NOTIFY_TASK = "task", NOTIFY_CRD = "crd", 
            NOTIFY_POSTO = "posto", NOTIFY_URI = "uri";

    public final static String MESSAGE_STRING  = "STRING";
    
    public final static String NAVIGATE_ACTION = "com.ghostsq.commander.NAVIGATE";
    public final static String NOTIFY_ACTION   = "com.ghostsq.commander.NOTIFY";
    public final static String MESSAGE_EXTRA   = "com.ghostsq.commander.MESSAGE";

    public final static int  ACTIVITY_REQUEST_FOR_NOTIFY_RESULT = 695; 
    public final static int  REQUEST_OPEN_DOCUMENT_TREE  = 935; 
    
    /**
     * @return current UI context
     */
    public Context getContext();

    /**
     * @param in  - an intent to launch
     * @param ret - if not zero,  startActivityForResult( in, ret ) will be called
     */
    public void issue( Intent in, int ret );

	/**
	 * @param msg - message to show in an alert dialog
	 */
	public void    showError( String msg );

	/**
	 * @param msg - message to show in an info dialog
	 */
	public void    showInfo( String msg );

    /**
     * @param id - the dialog id 
     */
    public void    showDialog( int dialog_id );
	
	/**
     * Navigate the current panel to the specified URI. 
     * @param uri         - URI to navigate to
     * @param crd         - The credentials  
     * @param positionTo  - Select an item with the given name
     */
	public void    Navigate( Uri uri, Credentials crd, String positionTo );
	
	/**
	 * Try to execute a command as if it came from the UI
	 * @param id - command id to execute
	 */
	public void dispatchCommand( int id );	
	
	/**
	 * Execute (launch) the specified item.  
	 * @param uri to open by sending an Intent
	 * @param crd user credentials
	 */
	public void Open( Uri uri, Credentials crd );

    /**
     * The waiting thread call after it sent the OPERATION_SUSPENDED_FILE_EXIST notification
     * @return one of ABORT, REPLACE, REPLACE_ALL, SKIP, SKIP_ALL
     */
    public int getResolution();

    /**
     * Procedure completion notification. 
     * @param Message object with the following fields:
     *          .obj  - the message string in a bundle by the MESSAGE_STRING key
     *          .what - the event type (see above the OPERATION_... constants)
     *          .arg1 - main progress value (0-100)
     *          .arg2 - secondary progress value (0-100)
     *          .getData() - a bundle with a string NOTIFY_COOKIE 
     * @return true if it's fine to destroy the working thread 
     */
    public boolean notifyMe( Message m );
    
    public boolean startEngine( Engine e );
}
