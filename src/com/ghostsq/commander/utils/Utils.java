package com.ghostsq.commander.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.io.OutputStream;

import com.ghostsq.commander.R;

import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.UrlQuerySanitizer;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.webkit.MimeTypeMap;
import android.text.format.DateFormat;

public final class Utils {
    public final static String C_AUDIO = "a", C_VIDEO = "v", C_TEXT = "t", C_ZIP = "z", C_OFFICE = "o",
             C_DROID = "d", C_BOOK = "b", C_IMAGE = "i", C_MARKUP = "m", C_APP = "x", C_PDF = "p", C_UNKNOWN = "u";
    
    private final static String[][] mimes = { // should be sorted!
            { ".3gpp", "audio/3gpp", C_AUDIO }, 
            { ".7z",   "application/x-7z-compressed", C_ZIP }, 
            { ".aif",  "audio/x-aiff", C_AUDIO }, 
            { ".apk",  "application/vnd.android.package-archive", C_DROID },
            { ".arj",  "application/x-arj", C_ZIP }, 
            { ".au",   "audio/basic", C_AUDIO }, 
            { ".avi",  "video/x-msvideo", C_VIDEO }, 
            { ".b1",   "application/x-b1", C_APP },
            { ".bmp",  "image/bmp", C_IMAGE },
            { ".bz",   "application/x-bzip2", C_ZIP },
            { ".bz2",  "application/x-bzip2", C_ZIP },
            { ".cab",  "application/x-compressed", C_ZIP },
            { ".chm",  "application/vnd.ms-htmlhelp", C_OFFICE },
            { ".conf", "application/x-conf", C_APP },
            { ".csv",  "text/csv", C_TEXT }, 
            { ".db",   "application/x-sqlite3", C_APP },
            { ".dex",  "application/octet-stream", C_DROID },
            { ".djvu", "image/vnd.djvu", C_IMAGE },
            { ".doc",  "application/msword", C_OFFICE },
            { ".docx", "application/msword", C_OFFICE },
            { ".epub", "application/epub", C_BOOK }, 
            { ".fb2",  "application/fb2", C_BOOK },
            { ".flac", "audio/flac", C_AUDIO },            
            { ".flv",  "video/x-flv", C_VIDEO },            
            { ".gif",  "image/gif", C_IMAGE }, 
            { ".gtar", "application/x-gtar", C_ZIP },
            { ".gz",   "application/x-gzip", C_ZIP },
            { ".htm",  "text/html", C_MARKUP }, 
            { ".html", "text/html", C_MARKUP }, 
            { ".img",  "application/x-compressed", C_ZIP },
            { ".jar",  "application/java-archive", C_ZIP },
            { ".java", "text/java", C_TEXT }, 
            { ".jpeg", "image/jpeg", C_IMAGE }, 
            { ".jpg",  "image/jpeg", C_IMAGE },
            { ".js",   "text/javascript", C_TEXT },
            { ".lzh",  "application/x-lzh", C_ZIP },
            { ".m3u",  "audio/x-mpegurl", C_AUDIO }, 
            { ".md5",  "application/x-md5", C_APP },
            { ".mid",  "audio/midi", C_AUDIO }, 
            { ".midi", "audio/midi", C_AUDIO }, 
            { ".mkv",  "video/x-matroska", C_VIDEO },
            { ".mobi", "application/x-mobipocket", C_BOOK },
            { ".mov",  "video/quicktime", C_VIDEO },
            { ".mp2",  "video/mpeg", C_VIDEO },
            { ".mp3",  "audio/mp3", C_AUDIO },
            { ".mp4",  "video/mp4", C_VIDEO },
            { ".mpeg", "video/mpeg", C_VIDEO }, 
            { ".mpg",  "video/mpeg", C_VIDEO }, 
            { ".odex", "application/octet-stream", C_DROID },
            { ".ods",  "application/vnd.oasis.opendocument.spreadsheet", C_OFFICE }, 
            { ".odt",  "application/vnd.oasis.opendocument.text", C_OFFICE }, 
            { ".oga",  "audio/ogg", C_AUDIO }, 
            { ".ogg",  "audio/ogg", C_AUDIO },    // RFC 5334 
            { ".ogv",  "video/ogg", C_VIDEO },    // RFC 5334 
            { ".opml", "text/xml", C_MARKUP }, 
            { ".pdf",  "application/pdf", C_PDF }, 
            { ".php",  "text/php", C_MARKUP },
            { ".pmd",  "application/x-pmd", C_OFFICE },   //      PlanMaker Spreadsheet
            { ".png",  "image/png", C_IMAGE }, 
            { ".ppt",  "application/vnd.ms-powerpoint", C_OFFICE }, 
            { ".pptx", "application/vnd.ms-powerpoint", C_OFFICE }, 
            { ".prd",  "application/x-prd", C_OFFICE },   //      SoftMaker Presentations Document
            { ".ra",   "audio/x-pn-realaudio", C_AUDIO }, 
            { ".ram",  "audio/x-pn-realaudio", C_AUDIO },
            { ".rar",  "application/x-rar-compressed", C_ZIP }, 
            { ".rtf",  "application/rtf", C_OFFICE }, 
            { ".sh",   "application/x-sh", C_APP },
            { ".so",   "application/octet-stream", C_APP },
            { ".sqlite","application/x-sqlite3", C_APP },
            { ".svg",  "image/svg+xml", C_IMAGE },
            { ".swf",  "application/x-shockwave-flash", C_VIDEO }, 
            { ".sxw",  "application/vnd.sun.xml.writer", C_OFFICE }, 
            { ".tar",  "application/x-tar", C_ZIP }, 
            { ".tcl",  "application/x-tcl", C_APP }, 
            { ".tgz",  "application/x-gzip", C_ZIP }, 
            { ".tif",  "image/tiff", C_IMAGE }, 
            { ".tiff", "image/tiff", C_IMAGE }, 
            { ".tmd",  "application/x-tmd", C_OFFICE },   //      TextMaker Document
            { ".txt",  "text/plain", C_TEXT },
            { ".vcf",  "text/x-vcard", C_OFFICE }, 
            { ".wav",  "audio/wav", C_AUDIO }, 
            { ".wma",  "audio/x-ms-wma", C_AUDIO }, 
            { ".wmv",  "video/x-ms-wmv", C_VIDEO }, 
            { ".xls",  "application/vnd.ms-excel", C_OFFICE }, 
            { ".xlsx", "application/vnd.ms-excel", C_OFFICE }, 
            { ".xml",  "text/xml", C_MARKUP }, 
            { ".xsl",  "text/xml", C_MARKUP }, 
            { ".zip",  "application/zip", C_ZIP } 
        };

    public final static String getMimeByExt( String ext ) {
        if( str( ext ) ) {
            String[] descr = getTypeDescrByExt( ext );
            if( descr != null ) return descr[1];
            // ask the system
            MimeTypeMap mime_map = MimeTypeMap.getSingleton();
            if( mime_map != null ) {
                String mime = mime_map.getMimeTypeFromExtension( ext.substring( 1 ) );
                if( str( mime ) ) return mime;
            }
        }
        return "*/*";
    }
    public final static String getCategoryByExt( String ext ) {
        if( str( ext ) ) {
            String[] descr = getTypeDescrByExt( ext );
            if( descr != null ) return descr[2];
            // ask the system
            MimeTypeMap mime_map = MimeTypeMap.getSingleton();
            if( mime_map != null ) {
                String mime = mime_map.getMimeTypeFromExtension( ext.substring( 1 ) );
                if( str( mime ) ) {
                    String type = mime.substring( 0, mime.indexOf( '/' ) );
                    if( type.compareTo( "text" ) == 0 )
                        return C_TEXT;
                    if( type.compareTo( "image" ) == 0 )
                        return C_IMAGE;
                    if( type.compareTo( "audio" ) == 0 )
                        return C_AUDIO;
                    if( type.compareTo( "video" ) == 0 )
                        return C_VIDEO;
                    if( type.compareTo( "application" ) == 0 )
                        return C_APP;
                }                
            }
        }
        return C_UNKNOWN;
    }

    public final static String[] getExtsByCategory( String c ) {
        if( c == null ) return null;
        ArrayList<String> exts = new ArrayList<String>();
        for( int l = 0; l < mimes.length; l++ ) {
            if( c.equals( mimes[l][2] ) )
                exts.add( mimes[l][0] );
        }
        String[] ret = new String[exts.size()];
        return exts.toArray( ret );
    }
    
    public final static String[] getTypeDescrByExt( String ext ) {
        ext = ext.toLowerCase();
        int from = 0, to = mimes.length;
        for( int l = 0; l < mimes.length; l++ ) {
            int idx = ( to - from ) / 2 + from;
            String tmp = mimes[idx][0];
            if( tmp.compareTo( ext ) == 0 )
                return mimes[idx];
            int cp;
            for( cp = 1;; cp++ ) {
                if( cp >= ext.length() ) {
                    to = idx;
                    break;
                }
                if( cp >= tmp.length() ) {
                    from = idx;
                    break;
                }
                char c0 = ext.charAt( cp );
                char ct = tmp.charAt( cp );
                if( c0 < ct ) {
                    to = idx;
                    break;
                }
                if( c0 > ct ) {
                    from = idx;
                    break;
                }
            }
        }
        return null;
    }

    public final static String getFileExt( String file_name ) {
        if( file_name == null )
            return "";
        int dot = file_name.lastIndexOf( "." );
        return dot >= 0 ? file_name.substring( dot ) : "";
    }

    public final static String[] prepareWildcard( String wc ) {
        return ( "\02" + wc.toLowerCase() + "\03" ).split( "\\*" );
    }

    public final static boolean match( String text, String[] cards ) {
        int pos = 0;
        String lc_text = "\02" + text.toLowerCase() + "\03";
        for( String card : cards ) {
            int idx = lc_text.indexOf( card, pos );
            if( idx < 0 )
                return false;
            pos = idx + card.length();
        }
        return true;
    }

    public final static int deleteDirContent( File d ) {
        int cnt = 0;
        File[] fl = d.listFiles();
        if( fl != null ) {
            for( File f : fl ) {
                if( f.isDirectory() )
                    cnt += deleteDirContent( f );
                if( f.delete() )
                    cnt++;
            }
        }
        return cnt;
    }

    public final static File[] getListOfFiles( String[] uris ) {
        File[] list = new File[uris.length];
        for( int i = 0; i < uris.length; i++ ) {
            if( uris[i] == null )
                return null;
            list[i] = new File( uris[i] );
        }
        return list;
    }

    public final static String getSecondaryStorage() {
        try {
            Map<String, String> env = System.getenv();
            String sec_storage = env.get( "SECONDARY_STORAGE" );
            if( !Utils.str( sec_storage ) ) return null;
            String[] ss = sec_storage.split( ":" );
            for( int i = 0; i < ss.length; i++ )
                if( ss[i].toLowerCase().indexOf( "sd" ) > 0 )
                    return ss[i];
            return "";
        } catch( Exception e ) {
        }
        return null;
    }
    
    public final static String getOpReport_( Context ctx, int total, int verb_id ) {
        String items = null;
        if( total > 1 ) {
            if( total < 5 )
                items = ctx.getString( R.string.items24 );
            if( items == null || items.length() == 0 )
                items = ctx.getString( R.string.items );
            if( items == null || items.length() == 0 )
                items = ctx.getString( R.string.item );
        }
        String verb = ctx.getString( verb_id );
        String report = ( total > 0 ? "" + total + " " + ( total > 1 ? items : ctx.getString( R.string.item ) ) : ctx
                .getString( R.string.nothing ) )
                + " "
                + ( total > 1 ? ctx.getString( R.string.were ) : ctx.getString( R.string.was ) )
                + " "
                + verb
                + ( total > 1 ? ctx.getString( R.string.verb_plural_sfx ) : "" ) + ".";
        return report;
    }

    public final static String getOpReport( Context ctx, int total, int verb_id ) {
        String verb = " " + ctx.getString( verb_id );
        if( total == 0 )
            return ctx.getString( R.string.report_0 ) + verb;
        if( total == 1 )
            return ctx.getString( R.string.report_1 ) + verb;
        if( total > 1 ) {
            verb += ctx.getString( R.string.verb_plural_sfx );
            if( total < 5 ) {
                String rep24 = ctx.getString( R.string.report_24, total );
                if( str( rep24 ) && !"\u00A0".equals( rep24 ) ) 
                    return rep24 + verb;
            }
            return ctx.getString( R.string.report_m, total ) + verb;
        }
        return null;
    }

    static final char[] spaces = { '\u00A0', '\u00A0', '\u00A0', '\u00A0', '\u00A0', '\u00A0', '\u00A0', '\u00A0' }; 
    
    public final static String getHumanSize( long sz ) {
        return getHumanSize( sz, true );
    }

    public final static String getHumanSize( long sz, boolean prepend_nbsp ) {
        try {
            String s;
            if( sz > 1073741824 )
                s = Math.round( sz * 10 / 1073741824. ) / 10. + "G";
            else if( sz > 1048576 )
                s = Math.round( sz * 10 / 1048576. ) / 10. + "M";
            else if( sz > 1024 )
                s = Math.round( sz * 10 / 1024. ) / 10. + "K";
            else
                s = "" + sz + " ";
            if( prepend_nbsp )
                return new String( spaces, 0, 8 - s.length() ) + s;
            else
                return s;
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return "" + sz + " ";
    }

    public final static long parseHumanSize( String s ) {
        if( s == null )
            return 0;
        final char[] sxs = { 'K', 'M', 'G', 'T' };
        long m = 1024;
        int s_pos;
        s = s.toUpperCase();
        try {
            for( int i = 0; i < 4; i++ ) {
                s_pos = s.indexOf( sxs[i] );
                if( s_pos > 0 ) {
                    float v = Float.parseFloat( s.substring( 0, s_pos ) );
                    return (long)( v * m );
                }
                m *= 1024;
            }
            s_pos = s.indexOf( 'B' );
            return Long.parseLong( s_pos > 0 ? s.substring( 0, s_pos ) : s );
        } catch( NumberFormatException e ) {
            e.printStackTrace();
        }
        return 0;
    }

    public final static String encodeToAuthority( String serv ) {
        String auth = null;
        int cp = serv.lastIndexOf( ':' );
        if( cp > 0 ) {
            String ps = serv.substring( cp + 1 );
            try {
                int port = Integer.parseInt( ps );
                if( port > 0 )
                    auth = Uri.encode( serv.substring( 0, cp ) ) + ":" + port;
            } catch( NumberFormatException e ) {
            }
        }
        if( auth == null )
            auth = Uri.encode( serv );
        return auth;
    }

    public static Uri getUriWithAuth( Uri u, Credentials crd ) {
        return crd != null ? getUriWithAuth( u, crd.getUserName(), crd.getPassword() ) : u;
    }

    public static Uri getUriWithAuth( Uri u, String un, String pw ) {
        if( un == null ) return u;
        String ui = Utils.escapeName( un );
        if( pw != null )
            ui += ":" + Utils.escapeName( pw );
        return updateUserInfo( u, ui );
    }
    
    public final static Uri updateUserInfo( Uri u, String encoded_ui ) {
        if( u == null ) return null;
        String ea = u.getEncodedAuthority();
        if( ea == null ) return u;
        int at_pos = ea.lastIndexOf( '@' );
        if( encoded_ui == null ) {
            if( at_pos < 0 ) return u;
            ea = ea.substring( at_pos + 1 );
        } else
            ea = encoded_ui + ( at_pos < 0 ? "@" + ea : ea.substring( at_pos ) );
        return u.buildUpon().encodedAuthority( ea ).build();
    }

    public final static String mbAddSl( String path ) {
        if( !str( path ) )
            return "/"; // XXX returning a slash here seems more logical, but are there any pitfalls?
        return path.charAt( path.length() - 1 ) == '/' ? path : path + "/";
    }

    public final static Uri addTrailngSlash( Uri u ) {
        String alt_path, path = u.getEncodedPath();
        if( path == null )
            alt_path = "/";
        else {
            alt_path = Utils.mbAddSl( path );
            if( alt_path == null || path.equals( alt_path ) ) return u;
        }
        return u.buildUpon().encodedPath( alt_path ).build(); 
    }
    
    public final static boolean str( String s ) {
        return s != null && s.length() > 0;
    }
    
    public final static boolean equals( String s1, String s2 ) {
        if( s1 == null ) return s2 == null ? true : false;
        return s1.equals( s2 );
    }
    
    public final static String join( String[] a, String sep ) {
        if( a == null )
            return "";
        StringBuffer buf = new StringBuffer( 256 );
        boolean first = true;
        for( int i = 0; i < a.length; i++ ) {
            if( first )
                first = false;
            else if( sep != null )
                buf.append( sep );
            buf.append( a[i] );
        }
        return buf.toString();
    }

    public final static void changeLanguage( Context c ) {
        changeLanguage( c, c.getResources() );
    }

    public final static void changeLanguage( Context c, Resources r ) {
        try {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences( c );
            String lang = sharedPref.getString( "language", "" );

            Locale locale;
            if( lang == null || lang.length() == 0 ) {
                locale = Locale.getDefault();
            } else {
                String country = lang.length() > 3 ? lang.substring( 3 ) : null;
                if( country != null )
                    locale = new Locale( lang.substring( 0, 2 ), country );
                else
                    locale = new Locale( lang );
            }
            Locale.setDefault( locale );
            Configuration config = new Configuration();
            config.locale = locale;
            r.updateConfiguration( config, null );
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    public final static CharSequence readStreamToBuffer( InputStream is, String encoding ) {
        if( is != null ) {
            try {
                int bytes = is.available();
                if( bytes < 1024 || bytes > 1048576 )
                    bytes = 10240;
                char[] chars = new char[bytes];
                InputStreamReader isr = null;
                if( str( encoding ) ) {
                    try {
                        isr = new InputStreamReader( is, encoding );
                    } catch( UnsupportedEncodingException e ) {
                        Log.w( "GC", encoding, e );
                        isr = new InputStreamReader( is );
                    }
                } else
                    isr = new InputStreamReader( is );
                StringBuffer sb = new StringBuffer( bytes );
                int n = -1;
                boolean available_supported = is.available() > 0;
                while( true ) {
                    n = isr.read( chars, 0, bytes );
                    // Log.v( "readStreamToBuffer", "Have read " + n + " chars" );
                    if( n < 0 )
                        break;
                    for( int i = 0; i < n; i++ ) {
                        if( chars[i] == 0x0D )
                            chars[i] = ' ';
                    }
                    sb.append( chars, 0, n );
                    if( available_supported ) {
                        for( int i = 0; i < 10; i++ ) {
                            if( is.available() > 0 )
                                break;
                            // Log.v( "readStreamToBuffer", "Waiting the rest " + i );
                            Thread.sleep( 20 );
                        }
                        if( is.available() == 0 ) {
                            // Log.v( "readStreamToBuffer", "No more data!" );
                            break;
                        }
                    }
                }
                return sb;
            } catch( Throwable e ) {
                Log.e( "Utils.readStreamToBuffer()", "Error on reading a stream", e );
            }
        }
        return null;
    }

    public final static boolean copyBytes( InputStream is, OutputStream os ) {
        try {
            byte[] buf = new byte[65536];
            int n;
            while( ( n = is.read( buf ) ) != -1 ) {
                os.write( buf, 0, n );
                Thread.sleep( 1 );
            }
            return true;
        } catch( Exception e ) {
            Log.e( "Utils.copyBytes", "Exception: " + e );
        }
        return false;
    }

    public final static File getTempDir( Context ctx ) {
        File parent_dir = null;
        if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO )
             parent_dir = ForwardCompat.getExternalFilesDir( ctx );
        else
             parent_dir = new File( Environment.getExternalStorageDirectory().getAbsolutePath() );
        File temp_dir = new File( parent_dir, "/temp/" );
        temp_dir.mkdirs();
        return temp_dir;
    }
    
    public final static File createTempDir( Context ctx ) {
        Date d = new Date();
        File parent_dir = null;
        if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO )
             parent_dir = ForwardCompat.getExternalFilesDir( ctx );
        else
             parent_dir = new File( Environment.getExternalStorageDirectory().getAbsolutePath() );
        File temp_dir = new File( parent_dir, "/temp/gc_" + d.getHours() + d.getMinutes() + d.getSeconds() + "/" );
        temp_dir.mkdirs();
        return temp_dir;
    }
    
    public final static int ENC_DESC_MODE_NUMB  = 0;
    public final static int ENC_DESC_MODE_BRIEF = 1;
    public final static int ENC_DESC_MODE_FULL  = 2;

    public final static String getEncodingDescr( Context ctx, String enc_name, int mode ) {
        if( enc_name == null )
            enc_name = "";
        Resources res = ctx.getResources();
        if( res == null )
            return null;
        String[] enc_dsc_arr = res.getStringArray( R.array.encoding );
        String[] enc_nms_arr = res.getStringArray( R.array.encoding_vals );
        try {
            for( int i = 0; i < enc_nms_arr.length; i++ ) {
                if( enc_name.equals( enc_nms_arr[i] ) ) {
                    if( mode == ENC_DESC_MODE_NUMB )
                        return "" + i;
                    String enc_desc = enc_dsc_arr[i];
                    if( mode == ENC_DESC_MODE_FULL )
                        return enc_desc;
                    else {
                        int nlp = enc_desc.indexOf( '\n' );
                        if( nlp < 0 )
                            return enc_desc;
                        return enc_desc.substring( 0, nlp );
                    }

                }
            }
        } catch( Exception e ) {
        }
        return null;
    }

    public final static String escapeRest( String s ) {
        if( !str( s ) )
            return s;
        return s.replaceAll( "%", "%25" )
                .replaceAll( "#", "%23" )
                .replaceAll( ":", "%3A" );
    }

    public final static String escapePath( String s ) {
        if( !str( s ) )
            return s;
        return escapeRest( s ).replaceAll( "@", "%40" );
    }

    public final static String escapeName( String s ) {
        if( !str( s ) )
            return s;
        return escapePath( s ).replaceAll( "/", "%2F" );
    }

    public final static String unEscape( String s ) {
        UrlQuerySanitizer urlqs = new UrlQuerySanitizer();
        return urlqs.unescape( s.replaceAll( "\\+", "_pLuS_" ) ).replaceAll( "_pLuS_", "+" );
    }

    public final static boolean isHTML( String s ) {
        return s.indexOf( "</", 3 ) > 0 ||
               s.indexOf( "/>", 3 ) > 0 ||
               s.indexOf( "&#x" ) >= 0;
    }
    
    public static byte[] hexStringToBytes( String hexString ) {
        int len = hexString.length() / 2;
        byte[] result = new byte[len];
        for( int i = 0; i < len; i++ )
            result[i] = Integer.valueOf( hexString.substring( 2 * i, 2 * i + 2 ), 16 ).byteValue();
        return result;
    }

    private final static String HEX = "0123456789abcdef";

    public final static String toHexString( byte[] buf, String delim ) {
        if( buf == null )
            return "";
        StringBuffer result = new StringBuffer( 2 * buf.length );
        for( int i = 0; i < buf.length; i++ ) {
            if( i > 0 && str( delim ) )
                result.append( delim );
            result.append( HEX.charAt( ( buf[i] >> 4 ) & 0x0f ) ).append( HEX.charAt( buf[i] & 0x0f ) );
        }
        return result.toString();
    }

    public final static String getHash( File f, String algorithm ) {
        String[] hashes = getHash( f, new String[] { algorithm } );
        return hashes != null ? hashes[0] : null;
    }

    public final static String[] getHash( File f, String[] algorithms ) {
        try {
            FileInputStream in  = new FileInputStream( f );
            MessageDigest[] digesters = new MessageDigest[algorithms.length];
            for( int i = 0; i < algorithms.length; i++ )
                digesters[i] = MessageDigest.getInstance( algorithms[i] );
            byte[] bytes = new byte[8192];
            int byteCount;
            while((byteCount = in.read(bytes)) > 0) {
                for( int i = 0; i < digesters.length; i++ )
                    digesters[i].update( bytes, 0, byteCount );
            }
            in.close();
            String[] hashes = new String[digesters.length];
            for( int i = 0; i < digesters.length; i++ ) {
                byte[] digest = digesters[i].digest();
                hashes[i] = toHexString( digest, null ); 
            }
            return hashes;
        } catch( Exception e ) {
            Log.e( "getHash", "", e );
            return null;
        }
    }    
    
    public final static float getBrightness( int color ) {
        float[] hsv = new float[3];
        Color.colorToHSV( color, hsv );
        return hsv[2];
    }

    public final static int setBrightness( int color, float br ) {
        float[] hsv = new float[3];
        Color.colorToHSV( color, hsv );
        hsv[2] = br;
        return Color.HSVToColor( hsv );
    }

    public final static int shiftBrightness( int color, float drop ) {
        float[] hsv = new float[3];
        Color.colorToHSV( color, hsv );
        hsv[2] *= drop;
        return Color.HSVToColor( hsv );
    }

    public final static GradientDrawable getShadingEx( int color, float drop ) {
        try {
            int[] cc = new int[2];
            cc[0] = color;
            cc[1] = shiftBrightness( color, drop );
            return new GradientDrawable( GradientDrawable.Orientation.TOP_BOTTOM, cc );
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }

    public final static int getCount( SparseBooleanArray cis ) {
        int counter = 0;
        for( int i = 0; i < cis.size(); i++ )
            if( cis.valueAt( i ) ) {
                counter++;
            }
        return counter;
    }

    public final static int getPosition( SparseBooleanArray cis, int of_item ) {
        int counter = 0;
        for( int i = 0; i < cis.size(); i++ )
            if( cis.valueAt( i ) ) {
                if( counter++ == of_item )
                    return cis.keyAt( i );
            }
        return -1;
    }
   
    public final static String getCause( Throwable e ) {
        Throwable c = e.getCause();
        if( c != null ) {
            String s = c.getLocalizedMessage();
            String cs = getCause( c );
            if( cs != null )
                return s + "\n" + cs;
            else
                return s;
        }
        return null;        
    }
 
    public final static String formatDate( Date date, Context ctx ) {
        boolean no_time = date.getHours() == 0 && date.getMinutes() == 0 && date.getSeconds() == 0;
        if( Locale.getDefault().getLanguage().compareTo( "en" ) != 0 ) {
            java.text.DateFormat locale_date_format = DateFormat.getDateFormat( ctx );
            String ret = locale_date_format.format( date );
            if( !no_time ) {
                java.text.DateFormat locale_time_format = DateFormat.getTimeFormat( ctx );
                ret += " " + locale_time_format.format( date ); 
            }
            return ret;
        } else {
            String fmt = "MMM dd yyyy";
            if( !no_time ) fmt += " hh:mm:ss";
            return (String)DateFormat.format( fmt, date );
        }
    }
    
    public enum RR {
        busy(R.string.busy), 
        copy_err(R.string.copy_err), 
        copied(R.string.copied), 
        moved(R.string.moved), 
        interrupted(R.string.interrupted), 
        uploading(R.string.uploading), 
        fail_del(R.string.fail_del), 
        cant_del(R.string.cant_del), 
        retrieving(R.string.retrieving), 
        deleting(R.string.deleting), 
        not_supported(R.string.not_supported), 
        file_exist(R.string.file_exist), 
        cant_md(R.string.cant_md),

        sz_folder( R.string.sz_folder),
        sz_dirnum( R.string.sz_dirnum),
        sz_dirsfx_p( R.string.sz_dirsfx_p),
        sz_dirsfx_s( R.string.sz_dirsfx_s),
        sz_file( R.string.sz_file),
        sz_files( R.string.sz_files),
        sz_Nbytes( R.string.sz_Nbytes),
        sz_bytes( R.string.sz_bytes),
        sz_lastmod( R.string.sz_lastmod),
        sz_total( R.string.sz_total),
        too_deep_hierarchy(R.string.too_deep_hierarchy),
        
        failed(R.string.failed),
        ftp_connected(R.string.ftp_connected),
        rtexcept(R.string.rtexcept)
        ;
        private int r;

        private RR(int r_) {
            r = r_;
        }

        public int r() {
            return r;
        }
    };
}