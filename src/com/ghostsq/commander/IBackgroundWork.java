package com.ghostsq.commander;

import com.ghostsq.commander.adapters.Engine;

public interface IBackgroundWork {
    public void    start( Engine engine );
    public boolean stopEngine( long task_id );
    
    public interface IBackgroundWorkBinder {
        public IBackgroundWork init( Commander c );
    }
}
