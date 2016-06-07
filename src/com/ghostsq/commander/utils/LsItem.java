package com.ghostsq.commander.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ghostsq.commander.adapters.CommanderAdapter;

import android.util.Log;

public class LsItem {
    private static final String TAG = "LsItem";
 // Debian FTP site      
//  -rw-r--r--    1 1176     1176         1062 Sep 04 18:54 README      
//Android FTP server
//  -rw-rw-rw- 1 system system 93578 Sep 26 00:26 Quote Pro 1.2.4.apk
//Win2K3 IIS        
//  -rwxrwxrwx   1 owner    group          314800 Feb 10  2008 classic.jar
    private static Pattern unix = Pattern.compile( "^([\\-bcdlprwxsStT]{9,10}\\s+\\d+\\s+[^\\s]+\\s+[^\\s]+)\\s+(\\d+)\\s+((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+\\d{1,2}\\s+(?:\\d{4}|\\d{1,2}:\\d{2}))\\s(.+)" );
// inetutils-ftpd:
//  drwx------  3 user     80 2009-02-15 12:33 .adobe
// android native:
//  ----rwxr-x system   sdcard_rw      683 2013-05-25 19:52 1.zip

    private static Pattern inet = Pattern.compile( "^([\\-bcdlprwxsStT]{9,10}\\s+.+)\\s+(\\d*)\\s+(\\d{4}-\\d{2}-\\d{2}\\s\\d{1,2}:\\d{2})\\s(.+)" );
    // MSDOS style
//  02-10-08  02:08PM               314800 classic.jar
    private static Pattern msdos = Pattern.compile( "^(\\d{2,4}-\\d{2}-\\d{2,4}\\s+\\d{1,2}:\\d{2}[AP]M)\\s+(\\d+|<DIR>)\\s+(.+)" );
    private static SimpleDateFormat format_date_time  = new SimpleDateFormat( "MMM d HH:mm",        Locale.ENGLISH );
    private static SimpleDateFormat format_date_year  = new SimpleDateFormat( "MMM d yyyy",         Locale.ENGLISH );
    private static SimpleDateFormat format_full_date  = new SimpleDateFormat( "yyyy-MM-dd HH:mm",   Locale.ENGLISH );
    private static SimpleDateFormat format_msdos_date = new SimpleDateFormat( "MM-dd-yy  HH:mmaa",  Locale.ENGLISH );
    
    private String  name, link_target_name = null, attr = null;
    private boolean directory = false;
    private boolean link = false;
    private long    size = 0;
    private Date    date;
    public static final String LINK_PTR = " -> ";
    public LsItem( String ls_string ) {
        Matcher m = unix.matcher( ls_string );
        if( m.matches() ) {
            try {
                name = m.group( 4 );
                if( ls_string.charAt( 0 ) == 'd' )
                    directory = true;
                if( ls_string.charAt( 0 ) == 'l' ) {
                    link = true;
                    int arr_pos = name.indexOf( LINK_PTR );
                    if( arr_pos > 0 ) {
                        link_target_name = name.substring( arr_pos + 4 );
                        name = name.substring( 0, arr_pos );
                    }
                }
                size = Long.parseLong( m.group( 2 ) );
                String date_s = m.group( 3 ); 
                boolean in_year = date_s.indexOf( ':' ) > 0;
                SimpleDateFormat df = in_year ? format_date_time : format_date_year;
                date = df.parse( date_s );
                if( in_year ) {
                    Calendar cal = Calendar.getInstance();
                    int cur_year = cal.get( Calendar.YEAR ) - 1900;
                    int cur_month = cal.get( Calendar.MONTH );
                    int f_month = date.getMonth();
                    if( f_month > cur_month )
                        cur_year--;
                    date.setYear( cur_year );
                }
                attr = m.group( 1 );
                //Log.v( TAG, "Item " + name + ", " + attr );
            } catch( ParseException e ) {
                e.printStackTrace();
            }
            return;
        }
        m = inet.matcher( ls_string );
        if( m.matches() ) {
            try {
                if( ls_string.charAt( 0 ) == 'd' )
                    directory = true;
                name = m.group( 4 );
                if( ls_string.charAt( 0 ) == 'l' ) {    // link
                    link = true;
                    int arr_pos = name.indexOf( LINK_PTR );
                    if( arr_pos > 0 ) {
                        link_target_name = name.substring( arr_pos + 4 );
                        name = name.substring( 0, arr_pos );
                    }
                }
                String sz_str = m.group( 2 );
                size = sz_str != null && sz_str.length() > 0 ? Long.parseLong( sz_str ) : -1;
                String date_s = m.group( 3 ); 
                SimpleDateFormat df = format_full_date;
                date = df.parse( date_s );
                attr = m.group( 1 );
                if( attr != null ) attr = attr.trim();
            } catch( ParseException e ) {
                e.printStackTrace();
            }
            return;
        }
        m = msdos.matcher( ls_string );
        if( m.matches() ) {
            try {
                name = m.group( 3 );
                if( m.group( 2 ).equals( "<DIR>" ) )
                    directory = true;
                else
                    size = Long.parseLong( m.group( 2 ) );
                
                String date_s = m.group( 1 ); 
                SimpleDateFormat df = format_msdos_date;
                date = df.parse( date_s );
            } catch( ParseException e ) {
                e.printStackTrace();
            }
            return;
        }
        Log.e( TAG, "\nUnmatched string: " + ls_string + "\n" );
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append( name );
        if( link_target_name != null ) s.append( " " + LINK_PTR + " " + link_target_name );
        if( attr != null ) s.append( " (" + attr + ")" );
        if( directory ) s.append( " DIR" );
        if( link ) s.append( " LINK" );
        s.append( " " + size );
        s.append( " " + date );
        return s.toString();
    }
    
    public final String getName() {
        return name;
    }
    public final Date getDate() {
        return date;
    }
    public final long length() {
        return size;
    }
    public final boolean isValid() {
        return name != null;
    }
    public final boolean isDirectory() {
        return directory;
    }
    public final void setDirectory() {
        directory = true;
    }
    public final String getLinkTarget() {
        return link ? link_target_name : null;
    }
    public final String getAttr() {
        return attr;
    }
    public final int compareTo( LsItem o ) {
        return getName().compareTo( o.getName() );
    }
    public class LsItemPropComparator implements Comparator<LsItem> {
        int type;
        boolean case_ignore, ascending;
        public LsItemPropComparator( int type_, boolean case_ignore_, boolean ascending_ ) {
            type = type_;
            case_ignore = case_ignore_ && ( type_ == CommanderAdapter.SORT_EXT || 
                                            type_ == CommanderAdapter.SORT_NAME );
            ascending = ascending_;
        }
        @Override
        public int compare( LsItem f1, LsItem f2 ) {
            boolean f1IsDir = f1.isDirectory();
            boolean f2IsDir = f2.isDirectory();
            if( f1IsDir != f2IsDir )
                return f1IsDir ? -1 : 1;
            int ext_cmp = 0;
            switch( type ) {
            case CommanderAdapter.SORT_EXT:
                ext_cmp = case_ignore ? 
                        Utils.getFileExt( f1.getName() ).compareToIgnoreCase( Utils.getFileExt( f2.getName() ) ) :
                        Utils.getFileExt( f1.getName() ).compareTo( Utils.getFileExt( f2.getName() ) );
                break;
            case CommanderAdapter.SORT_SIZE:
                ext_cmp = f1.length() - f2.length() < 0 ? -1 : 1;
                break;
            case CommanderAdapter.SORT_DATE:
                ext_cmp = f1.getDate().compareTo( f2.getDate() );
                break;
            }
            if( ext_cmp == 0 )
                ext_cmp = case_ignore ? f1.getName().compareToIgnoreCase( f2.getName() ) : f1.compareTo( f2 );
            return ascending ? ext_cmp : -ext_cmp;
        }
    }
    public static LsItem[] createArray( int n ) {
        return new LsItem[n];
    }
}
