package com.ghostsq.commander;

import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.adapters.Engine;
import com.ghostsq.commander.adapters.Engines;

import android.content.Intent;
import android.app.Service;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

public class BackgroundWork extends Service implements IBackgroundWork {
    private final static String TAG = "BackgroundWork";
    private Engines engines = null;
    private Commander commander = null;
    private WorkerHandler workerHandler = null;

    private class BackgroundWorkBinder extends Binder implements IBackgroundWorkBinder {
        public IBackgroundWork init( Commander c ) {
            commander = c;
            return BackgroundWork.this;
        }
    }

    private final IBinder binder = new BackgroundWorkBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        engines = new Engines();
        workerHandler = new WorkerHandler();

    }

    @Override
    public void onStart( Intent intent, int start_id ) {
        super.onStart( intent, start_id );
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        engines.terminateAll();
        Log.d( TAG, "onDestroy" );
    }

    @Override
    public IBinder onBind( Intent intent ) {
        return binder;
    }

    protected class WorkerHandler extends Handler {
        @Override
        public void handleMessage( Message msg ) {
            Bundle b = msg.getData();
            long id = b.getLong( Commander.NOTIFY_TASK );
            try {
                String[] items = b.getStringArray( Engines.IReciever.NOTIFY_ITEMS_TO_RECEIVE );
                if( items != null ) {
                    Engine eng = engines.get( id ); 
                    if( eng != null ) {
                        Engines.IReciever recipient = eng.getReciever();
                        if( recipient != null )
                            recipient.receiveItems( items, CommanderAdapter.MODE_MOVE_DEL_SRC_DIR );
                    }
                }
            } catch( Exception e ) {
                e.printStackTrace();
            }
             
            if( commander == null || commander.notifyMe( msg ) ) {
                engines.remove( id );
            }
        }
    };

    // --- IBackgroundWork implementation
    
    public void start( Engine engine ) {
        engine.setHandler( workerHandler );
        engines.addAndStart( engine );
    }

    public boolean stopEngine( long task_id ) {
        engines.remove( task_id );
        return true;
    }

}
