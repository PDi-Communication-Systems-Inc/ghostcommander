package com.ghostsq.commander.root;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.regex.Matcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.ghostsq.commander.adapters.Engine;;

public class ExecEngine extends Engine {
    protected String sh;
    protected Context context;
    private   String bb = "";
    private   String  where, command;
    private   boolean use_busybox = false;
    private   int wait_timeout = 500;
    private   StringBuilder result;
    private   boolean done = false;

    private   OutputStreamWriter os = null;
    private   BufferedReader is = null;
    private   BufferedReader es = null;
    
    protected ExecEngine( Context context_ ) {
        context = context_;
        where = null;
        command = null;
        result = null;
        sh = getSuPath();
    }
    public ExecEngine( Context context_, String where_, String command_, boolean use_bb, int timeout ) {
        context = context_;
        where = where_;
        command = command_;
        sh = getSuPath();
        use_busybox = use_bb; 
        wait_timeout = timeout;
        result = new StringBuilder( 1024 );
    }

    protected final String getSuPath() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( context );
        return sharedPref.getString( "su_path", "su" );
    }     
    
    @Override
    public void run() {
        try {
            if( command == null ) return;
            execute();
        } catch( Exception e ) {
            error( "Exception: " + e );
        }
        synchronized( this ) {
            done = true;
            notify();
        }
        if( thread_handler != null )
            sendResult( result != null && result.length() > 0 ? result.toString() : 
                   ( errMsg != null ? "\nFailed to execute \"" + command + "\"" : null ) );
    }
    
    protected boolean execute( String cmd, boolean use_bb ) {
        command = cmd;
        use_busybox = use_bb;
        return execute();
    }
    protected boolean execute( String cmd, boolean use_bb, int timeout ) {
        command = cmd;
        use_busybox = use_bb;
        wait_timeout = timeout;
        return execute();
    }
    
    protected boolean execute() {
        os = null;
        is = null;
        es = null;
        try {
            setEngineName( null );
            if( context != null ) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( context );
                bb = sharedPref.getString( "busybox_path", "busybox" ) + " ";
            }
            else
                bb = "busybox ";
            
            Process p = Runtime.getRuntime().exec( sh );
            
            os = new OutputStreamWriter( p.getOutputStream() );
            is = new BufferedReader( new InputStreamReader( p.getInputStream() ) );
            es = new BufferedReader( new InputStreamReader( p.getErrorStream() ) );
            if( where != null )
                outCmd( false, "cd '" + where + "'", os );
            boolean ok = cmdDialog( os, is, es );
            os.write( "exit\n" );
            os.flush();
            p.waitFor();
            int ev = p.exitValue();
            if( ev != 0 ) {
                Log.e( TAG, "Exit code " + ev );
                procError( es );
                if( errMsg == null || errMsg.length() == 0 )
                    error( "Exit code " + ev );
                return false;
            }
            return ok;
        }
        catch( Exception e ) {
            error( "Exception: " + e );
        }
        finally {
            try {
                if( os != null ) os.close();
                if( is != null ) is.close();
                if( es != null ) es.close();
            } catch( IOException e ) {
                e.printStackTrace();
            }
        }
        return false;
    }
 
    protected void outCmd( boolean use_bb, String cmd, OutputStreamWriter os ) 
              throws IOException, InterruptedException { 
        String to_exec = ( use_bb ? bb : "" ) + cmd + "\n";
        Log.v( TAG, "executing: " + to_exec );
        os.write( to_exec ); // execute the command
        os.flush();
        boolean ready = false;
        final int swait = 10;
        final int tries = wait_timeout / swait; 
        for( int i = 0; i < tries; i++ ) {
            if( is.ready() ) {
                ready = true;
                break;
            }
            Thread.sleep( swait );
        }
        if( ready )
            Log.d( TAG, "After cmd execution the input stream is ready" );
        else
            Log.w( TAG, "After " + wait_timeout + "ms the input stream is NOT yet ready!" );            
     }    

    // to override by a derived class which wants something more complex
    protected boolean cmdDialog( OutputStreamWriter os, BufferedReader is, BufferedReader es ) { 
        try {
            if( command != null )
                outCmd( use_busybox, command, os );
            boolean err = procError( es );
            if( !is.ready() ) // may be an error may be not
                Log.w( TAG, "No output from the executed command " + command );
            return procInput( is ) || !err;
        } catch( Exception e ) {
            error( e.getMessage() );
            if( command != null ) 
                Log.e( TAG, "Exception '" + e.getMessage() + "' nn execution '" + command + "'" );
        }
        return false;
    }    
    
    // to override by derived classes
    protected boolean procInput( BufferedReader br ) 
              throws IOException, Exception { 
        if( br != null && result != null )
            while( br.ready() ) {
                Thread.sleep( 10 );
                if( isStopReq() ) 
                    throw new Exception();
                String ln = br.readLine();
                if( ln == null ) break;
                result.append( ln ).append( "\n" );
            }
        return result != null && result.length() > 0;
    }

    protected boolean procError( BufferedReader es ) throws IOException {
        while( es.ready() ) {
            String err_str = es.readLine();
            if( err_str.trim().length() > 0 ) {
                error( err_str );
                return true;
            }
        }
        if( isStopReq() ) {
            error( "Canceled" );
            return true;
        }
        return false;
    }
    
    public synchronized StringBuilder getResult() {
        try {
            wait( 500 );
        } catch( InterruptedException e ) {}
        return done ? result : null;
    }
    
    static String prepFileName( String fn ) {
        return "'" + fn.replaceAll( "'", Matcher.quoteReplacement("'\\''") ) + "'";
    }
}
