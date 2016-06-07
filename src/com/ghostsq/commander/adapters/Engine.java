package com.ghostsq.commander.adapters;

import java.io.File;

import com.ghostsq.commander.Commander;
import com.ghostsq.commander.R;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.Utils;

import android.location.Address;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class Engine extends Thread {
    protected final String TAG = getClass().getSimpleName();
    protected final static int CUT_LEN = 36, DELAY = 1000, MILLI = 1000;
    protected final static double PERC = 100.;
    protected Handler thread_handler = null;
	protected boolean stop = false;
	protected String  errMsg = null;
	protected long    threadStartedAt = 0;
    protected int     file_exist_behaviour = Commander.UNKNOWN;
    protected Engines.IReciever recipient = null; 

    protected Engine() {
    }
    protected Engine( Engines.IReciever recipient_ ) {
        recipient = recipient_;
    }
/*
    protected Engine( Handler h ) {
        thread_handler = h; // TODO - distinct the member from the parent class
    }
    protected Engine( Handler h, Runnable r ) {
        super( r );
        thread_handler = h; 
    }
*/    
    protected void setEngineName( String name ) {
        setName( name == null ? getClass().getName() : name );
    }
    public void setHandler( Handler h ) {
        thread_handler = h;
    }
	
    public boolean reqStop() {
        if( isAlive() ) {
            Log.i( getClass().getName(), "reqStop()" );
            if( stop )
                interrupt();
            else
                stop = true;
            return true;
        }
        else {
            Log.i( getClass().getName(), "Engine thread is not alive" );
            return false;
        }
    }
    protected boolean isStopReq() {
        return stop || isInterrupted();
    }
    protected Bundle wrap( String str ) {
        Bundle b = new Bundle( 1 );
        b.putString( Commander.MESSAGE_STRING, str );
        return b;
    }
    

    
    protected final void sendProgress( String s, int p ) {
        sendProgress( s, p, -1, -1 );
    }
    protected final void sendProgress( String s, int p1, int p2 ) {
        sendProgress( s, p1, p2, -1 );
    }
    protected final void sendProgress() {   // launch the spinner 
        if( thread_handler == null ) return;
        Message msg = thread_handler.obtainMessage( Commander.OPERATION_IN_PROGRESS, -1, -1, null );
        Bundle b = msg.getData();
        b.putLong( Commander.NOTIFY_TASK, getId() );
        thread_handler.sendMessage( msg );
    }
    protected final void sendProgress( String s, int p1, int p2, int speed ) {
        //Log.v( TAG, "sendProgress: " + speed );
        if( thread_handler == null ) return;
        Message msg = null;
        
        if( p1 < 0 )
            msg = thread_handler.obtainMessage( p1, -1, -1, wrap( s ) );
        else
            msg = thread_handler.obtainMessage( Commander.OPERATION_IN_PROGRESS, p1, p2, wrap( s ) );
        
        Bundle b = msg.getData();
        b.putLong( Commander.NOTIFY_TASK, getId() );
        if( speed >= 0 )
            b.putInt( Commander.NOTIFY_SPEED, speed );
        thread_handler.sendMessage( msg );
    }
    protected final void sendProgress( String s, int p, String cookie ) {
        //Log.v( TAG, "sendProgress: " + s + ", cookie: " + cookie );
        if( thread_handler == null ) return;
        
        Message msg = null;
        if( p < 0 )
            msg = thread_handler.obtainMessage( p, -1, -1, wrap( s ) );
        else
            msg = thread_handler.obtainMessage( Commander.OPERATION_IN_PROGRESS, p, -1, wrap( s ) );
        Bundle b = msg.getData();
        b.putLong( Commander.NOTIFY_TASK, getId() );
        b.putString( Commander.NOTIFY_COOKIE, cookie );
        thread_handler.sendMessage( msg );
    }

    protected final void sendLoginReq( String s, Credentials crd ) {
        sendLoginReq( s, crd, null );
    }
    protected final void sendLoginReq( String s, Credentials crd, String cookie ) {
        if( thread_handler == null ) return;
        
        Message msg = thread_handler.obtainMessage( Commander.OPERATION_FAILED_LOGIN_REQUIRED, -1, -1, wrap( s ) );
        Bundle b = msg.getData();
        b.putLong( Commander.NOTIFY_TASK, getId() );
        b.putParcelable( Commander.NOTIFY_CRD, crd );
        if( cookie != null )
            b.putString( Commander.NOTIFY_COOKIE, cookie );
        thread_handler.sendMessage( msg );
    }
    
    protected final void sendReceiveReq( String[] items ) {
        if( thread_handler == null ) return;
        if( items == null || items.length == 0 ) {
            sendProgress( "???", Commander.OPERATION_FAILED );
            return;
        }
        Message msg = thread_handler.obtainMessage( Commander.OPERATION_COMPLETED );
        Bundle b = msg.getData();
        b.putLong( Commander.NOTIFY_TASK, getId() );
        b.putStringArray( Engines.IReciever.NOTIFY_ITEMS_TO_RECEIVE, items );
        thread_handler.sendMessage( msg );
    }
    protected final void sendReceiveReq( File dest_folder ) {
        File[] temp_content = dest_folder.listFiles();
        String[] paths = new String[temp_content.length];
        for( int i = 0; i < temp_content.length; i++ )
            paths[i] = temp_content[i].getAbsolutePath();
        sendReceiveReq( paths );
    }    
    protected final void error( String err ) {
        Log.e( getClass().getSimpleName(), err == null ? "Unknown error" : err );
    	if( errMsg == null )
    		errMsg = err;
    	else
    		errMsg += "\n" + err;
    }
    protected final boolean noErrors() {
        return errMsg == null;
    }
    protected final void sendResult( String report ) {
        if( errMsg != null )
            sendProgress( report + "\n - " + errMsg, Commander.OPERATION_FAILED_REFRESH_REQUIRED );
        else {
            sendProgress( report, Commander.OPERATION_COMPLETED_REFRESH_REQUIRED );
        }
    }
    protected final void sendRefrReq( String posto ) {
        if( thread_handler == null ) return;
        Message msg = thread_handler.obtainMessage( Commander.OPERATION_COMPLETED_REFRESH_REQUIRED );
        if( posto != null ) {
            Bundle b = msg.getData();
            b.putLong( Commander.NOTIFY_TASK, getId() );
            b.putString( Commander.NOTIFY_POSTO, posto );
        }
        thread_handler.sendMessage( msg );
    }
    protected final void sendReport( String s ) {
        if( thread_handler == null ) return;
        Address a;
        Message msg = thread_handler.obtainMessage( Commander.OPERATION_COMPLETED, Commander.OPERATION_REPORT_IMPORTANT, -1, wrap( s ) );
        Bundle b = msg.getData();
        b.putLong( Commander.NOTIFY_TASK, getId() );
        thread_handler.sendMessage( msg );
    }
    protected final void doneReading( String msg, String cookie ) {
        if( errMsg != null )
            sendProgress( errMsg, Commander.OPERATION_FAILED, cookie );
        else {
            sendProgress( msg, Commander.OPERATION_COMPLETED, cookie );
        }
    }
    protected final boolean tooLong( int sec ) {
        if( threadStartedAt == 0 ) return false;
        boolean yes = System.currentTimeMillis() - threadStartedAt > sec * 1000;
        threadStartedAt = 0;
        return yes;
    }
    protected String sizeOfsize( long n, String sz_s ) {
        return "\n" + Utils.getHumanSize( n ) + "/" + sz_s;
    }
    protected final int askOnFileExist( String msg, Commander commander ) throws InterruptedException {
        if( ( file_exist_behaviour & Commander.APPLY_ALL ) != 0 )
            return file_exist_behaviour & ~Commander.APPLY_ALL;
        int res;
        synchronized( commander ) {
            sendProgress( msg, Commander.OPERATION_SUSPENDED_FILE_EXIST );
            while( ( res = commander.getResolution() ) == Commander.UNKNOWN )
                commander.wait();
        }
        if( res == Commander.ABORT ) {
            error( commander.getContext().getString( R.string.interrupted ) );
            return res;
        }
        if( ( res & Commander.APPLY_ALL ) != 0 )
            file_exist_behaviour = res;
        return res & ~Commander.APPLY_ALL;
    }
    
    public final Engines.IReciever getReciever() {
        return recipient; 
    }
}
