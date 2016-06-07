package com.ghostsq.commander.adapters;

import java.io.File;
import java.util.Date;

import com.ghostsq.commander.adapters.CommanderAdapter;

public class FileItem extends CommanderAdapter.Item implements FSEngines.IFileItem {
    public FileItem( String name ) {
        this( new File( name ) );
    }
    public FileItem( File f ) {
        origin = f;
        
        dir  = f.isDirectory();
        if( dir ) {
            /*
            if( ( mode & ICON_MODE ) == ICON_MODE )  
                item.name = f.f.getName() + SLS;
            else
            */
            name = File.separator + f.getName();
        } else {
            name = f.getName();
            size = f.length();
        }
        long msFileDate = f.lastModified();
        if( msFileDate != 0 )
            date = new Date( msFileDate );
    }
    @Override
    public File f() {
        return origin != null ? (File)origin : null;
    }
}
