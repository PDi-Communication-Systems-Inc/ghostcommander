package com.ghostsq.commander.adapters;

import java.util.ArrayList;
import java.util.Iterator;

public class Engines extends ArrayList<Engine> {
    
    public final void addAndStart( Engine engine ) {
        add( engine );
        engine.start();
    }

    public Engine get( long id ) {
        Iterator<Engine> it = iterator();
        while( it.hasNext() ) {
            Engine eng = it.next();
            if( eng.getId() == id )
                return eng;
        }
        return null;
    }
    
    public final void remove( long id ) {
        Iterator<Engine> it = iterator();
        while( it.hasNext() ) {
            Engine eng = it.next();
            if( eng.getId() == id ) {
                eng.reqStop();
                it.remove();
            }
        }        
    }
    
    public final void terminateAll() {
        Iterator<Engine> it = iterator();
        while( it.hasNext() ) {
            it.next().reqStop();
        }        
    }

    public final void deleteAll() {
        Iterator<Engine> it = iterator();
        while( it.hasNext() ) {
            it.next();
            it.remove();
        }        
    }

    public interface IReciever {
        public final static String NOTIFY_ITEMS_TO_RECEIVE = "itms";
        public boolean receiveItems( String[] fileURIs, int move_mode );
    }
}
