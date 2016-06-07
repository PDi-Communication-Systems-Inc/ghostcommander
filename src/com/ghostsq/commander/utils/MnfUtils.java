package com.ghostsq.commander.utils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.PatternMatcher;
import android.util.Log;

public final class MnfUtils {
    private static final String TAG = "MnfUtils";
    private ApplicationInfo ai;
    private Resources       rr;
    private String          apk_path;
    private String          mans;
    
    public MnfUtils( PackageManager pm, String app_name ) {
        try {
            ai = pm.getApplicationInfo( app_name, 0 );
            rr = pm.getResourcesForApplication( ai );
        }
        catch( NameNotFoundException e ) {
            e.printStackTrace();
        }
    }
    
    public MnfUtils( String apk_path_ ) {
        apk_path = apk_path_;
    }
    
    public final String extractManifest() {
        try {
            if( mans != null ) return mans;
            if( ai != null )
                apk_path = ai.publicSourceDir;
            if( apk_path == null ) return null;
            ZipFile  zip = new ZipFile( apk_path );
            ZipEntry entry = zip.getEntry( "AndroidManifest.xml" );
            if( entry != null ) {
                InputStream is = zip.getInputStream( entry );
                if( is != null ) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream( (int)entry.getSize() );
                    byte[] buf = new byte[4096];
                    int n;
                    while( ( n = is.read( buf ) ) != -1 )
                        baos.write( buf, 0, n );
                    is.close();
                    mans = decompressXML( baos.toByteArray() );
                    return mans;
                }
            }
        } catch( Throwable e ) {
            e.printStackTrace();
        }
        return null;
    }
    
    public final Drawable extractIcon() {
        try {
            if( apk_path == null ) return null;
            ZipFile  zip = new ZipFile( apk_path );
            ZipEntry entry = zip.getEntry( "res/drawable/icon.png" );   
            if( entry != null ) {
                InputStream is = zip.getInputStream( entry );
                return is != null ? new BitmapDrawable( is ) : null;
            }
            /*
            Enumeration<? extends ZipEntry> entries = zip.entries();
            if( entries != null ) {
                while( entries.hasMoreElements() ) {
                    entry = entries.nextElement();
                    if( entry == null ) continue;
                    String efn = entry.getName();
                    if( efn == null || !efn.startsWith( "res/drawable" ) ) continue;
                    if( efn.contains( "icon" ) ) {
                        InputStream is = zip.getInputStream( entry );
                        return is != null ? new BitmapDrawable( is ) : null;
                    }
                }
            }
            */
            // TODO: find icon from the manifest
        } catch( Throwable e ) {
            Log.e( TAG, "Can't get icon for " + apk_path, e );
        }
        return null;
    }

    // http://stackoverflow.com/questions/2097813/how-to-parse-the-androidmanifest-xml-file-inside-an-apk-package
    // decompressXML -- Parse the 'compressed' binary form of Android XML docs 
    // such as for AndroidManifest.xml in .apk files
    private final static int endDocTag = 0x00100101;
    private final static int startTag =  0x00100102;
    private final static int endTag =    0x00100103;
    
    private final String decompressXML( byte[] xml ) {
        StringBuffer xml_sb = new StringBuffer( 8192 ); 
        // Compressed XML file/bytes starts with 24x bytes of data,
        // 9 32 bit words in little endian order (LSB first):
        //   0th word is 03 00 08 00
        //   3rd word SEEMS TO BE:  Offset at then of StringTable
        //   4th word is: Number of strings in string table
        // WARNING: Sometime I indiscriminently display or refer to word in 
        //   little endian storage format, or in integer format (ie MSB first).
        int numbStrings = LEW(xml, 4*4);
        
        // StringIndexTable starts at offset 24x, an array of 32 bit LE offsets
        // of the length/string data in the StringTable.
        int sitOff = 0x24;  // Offset of start of StringIndexTable
        
        // StringTable, each string is represented with a 16 bit little endian 
        // character count, followed by that number of 16 bit (LE) (Unicode) chars.
        int stOff = sitOff + numbStrings*4;  // StringTable follows StrIndexTable
        
        // XMLTags, The XML tag tree starts after some unknown content after the
        // StringTable.  There is some unknown data after the StringTable, scan
        // forward from this point to the flag for the start of an XML start tag.
        int xmlTagOff = LEW(xml, 3*4);  // Start from the offset in the 3rd word.
        // Scan forward until we find the bytes: 0x02011000(x00100102 in normal int)
        for (int ii=xmlTagOff; ii<xml.length-4; ii+=4) {
          if (LEW(xml, ii) == startTag) { 
            xmlTagOff = ii;  break;
          }
        } // end of hack, scanning for start of first start tag
        
        // XML tags and attributes:
        // Every XML start and end tag consists of 6 32 bit words:
        //   0th word: 02011000 for startTag and 03011000 for endTag 
        //   1st word: a flag?, like 38000000
        //   2nd word: Line of where this tag appeared in the original source file
        //   3rd word: FFFFFFFF ??
        //   4th word: StringIndex of NameSpace name, or FFFFFFFF for default NS
        //   5th word: StringIndex of Element Name
        //   (Note: 01011000 in 0th word means end of XML document, endDocTag)
        
        // Start tags (not end tags) contain 3 more words:
        //   6th word: 14001400 meaning?? 
        //   7th word: Number of Attributes that follow this tag(follow word 8th)
        //   8th word: 00000000 meaning??
        
        // Attributes consist of 5 words: 
        //   0th word: StringIndex of Attribute Name's Namespace, or FFFFFFFF
        //   1st word: StringIndex of Attribute Name
        //   2nd word: StringIndex of Attribute Value, or FFFFFFF if ResourceId used
        //   3rd word: Flags?
        //   4th word: str ind of attr value again, or ResourceId of value
        
        // TMP, dump string table to tr for debugging
        //tr.addSelect("strings", null);
        //for (int ii=0; ii<numbStrings; ii++) {
        //  // Length of string starts at StringTable plus offset in StrIndTable
        //  String str = compXmlString(xml, sitOff, stOff, ii);
        //  tr.add(String.valueOf(ii), str);
        //}
        //tr.parent();
        
        // Step through the XML tree element tags and attributes
        int off = xmlTagOff;
        int indent = 0;
        int startTagLineNo = -2;
        while( off < xml.length ) {
          int tag0 = LEW(xml, off);
          //int tag1 = LEW(xml, off+1*4);
          int lineNo = LEW(xml, off+2*4);
          //int tag3 = LEW(xml, off+3*4);
          int nameNsSi = LEW(xml, off+4*4);
          int nameSi = LEW(xml, off+5*4);
        
          if (tag0 == startTag) { // XML START TAG
            int tag6 = LEW(xml, off+6*4);  // Expected to be 14001400
            int numbAttrs = LEW(xml, off+7*4);  // Number of Attributes to follow
            //int tag8 = LEW(xml, off+8*4);  // Expected to be 00000000
            off += 9*4;  // Skip over 6+3 words of startTag data
            String name = compXmlString(xml, sitOff, stOff, nameSi);
            //tr.addSelect(name, null);
            startTagLineNo = lineNo;
        
            // Look for the Attributes
            StringBuffer sb = new StringBuffer();
            for (int ii=0; ii<numbAttrs; ii++) {
              int attrNameNsSi = LEW(xml, off);  // AttrName Namespace Str Ind, or FFFFFFFF
              int attrNameSi = LEW(xml, off+1*4);  // AttrName String Index
              int attrValueSi = LEW(xml, off+2*4); // AttrValue Str Ind, or FFFFFFFF
              int attrFlags = LEW(xml, off+3*4);  
              int attrResId = LEW(xml, off+4*4);  // AttrValue ResourceId or dup AttrValue StrInd
              off += 5*4;  // Skip over the 5 words of an attribute
        
              String attrName = compXmlString(xml, sitOff, stOff, attrNameSi);
              String attrValue= null;
              if( attrValueSi != -1 )
                  attrValue = compXmlString(xml, sitOff, stOff, attrValueSi);
              else {
                  if( rr != null )
                    try {
                        attrValue = rr.getString( attrResId );
                    } catch( NotFoundException e ) {}
                  if( attrValue == null )
                      attrValue = "0x"+Integer.toHexString( attrResId );
              }
              sb.append( "\n" ).append( spaces( indent+1 ) ).append( attrName ).append( "=\"" ).append( attrValue ).append( "\"" );
              //tr.add(attrName, attrValue);
            }
            xml_sb.append( "\n" ).append( spaces( indent ) ).append( "<" ).append( name );
            if( sb.length() > 0 )
                xml_sb.append( sb );
            xml_sb.append( ">" );
            indent++;
        
          } else if (tag0 == endTag) { // XML END TAG
            indent--;
            off += 6*4;  // Skip over 6 words of endTag data
            String name = compXmlString(xml, sitOff, stOff, nameSi);
            xml_sb.append( "\n" ).append( spaces( indent ) ).append( "</" ).append( name ).append( ">" );
//            prtIndent(indent, "</"+name+">  (line "+startTagLineNo+"-"+lineNo+")");
            //tr.parent();  // Step back up the NobTree
        
          } else if (tag0 == endDocTag) {  // END OF XML DOC TAG
            break;
        
          } else {
            Log.e( TAG, "  Unrecognized tag code '"+Integer.toHexString(tag0) +"' at offset "+off);
            break;
          }
        } // end of while loop scanning tags and attributes of XML tree
        Log.v( TAG, "    end at offset "+off );
        return xml_sb.toString();
    } // end of decompressXML
    
    
    private final String compXmlString(byte[] xml, int sitOff, int stOff, int strInd) {
      if (strInd < 0) return null;
      int strOff = stOff + LEW(xml, sitOff+strInd*4);
      return compXmlStringAt(xml, strOff);
    }
    
    private final String spaces( int i ) {
        char[] dummy = new char[i*2];
        Arrays.fill( dummy, ' ' );
        return new String( dummy );
    }
    
    // compXmlStringAt -- Return the string stored in StringTable format at
    // offset strOff.  This offset points to the 16 bit string length, which 
    // is followed by that number of 16 bit (Unicode) chars.
    private final String compXmlStringAt(byte[] arr, int strOff) {
      int strLen = arr[strOff+1]<<8&0xff00 | arr[strOff]&0xff;
      byte[] chars = new byte[strLen];
      for (int ii=0; ii<strLen; ii++) {
        chars[ii] = arr[strOff+2+ii*2];
      }
      return new String(chars);  // Hack, just use 8 byte chars
    } // end of compXmlStringAt
    
    
    // LEW -- Return value of a Little Endian 32 bit word from the byte array
    //   at offset off.
    private final int LEW(byte[] arr, int off) {
      return arr[off+3]<<24&0xff000000 | arr[off+2]<<16&0xff0000
        | arr[off+1]<<8&0xff00 | arr[off]&0xFF;
    } // end of LEW

    
    public final IntentFilter[] getIntentFilters( String act_name ) {
        try {
            if( mans == null )
                mans = extractManifest();
            if( mans != null && mans.length() > 0 ) {
                ArrayList<IntentFilter> list = new ArrayList<IntentFilter>(); 
                XmlPullParserFactory factory;
                factory = XmlPullParserFactory.newInstance();
                factory.setNamespaceAware(true);
                XmlPullParser xpp = factory.newPullParser();
                xpp.setInput( new StringReader( mans ) );
                int et;
                while( ( et = xpp.next() ) != XmlPullParser.END_DOCUMENT ) {
                    if( et == XmlPullParser.START_TAG && "activity".equals( xpp.getName() ) ) {
                        String can = xpp.getAttributeValue( null, "name" );
                        if( act_name.indexOf( can ) >= 0 ) { // ??? why not exact match?
                            int d = xpp.getDepth();
                            while( ( et = xpp.next() ) != XmlPullParser.END_DOCUMENT &&
                            ( d < xpp.getDepth() || et != XmlPullParser.END_TAG ) ) {
                                if( "intent-filter".equals( xpp.getName() ) ) {
                                    IntentFilter inf = new IntentFilter();
                                    initIntentFilterFromXml( inf, xpp );
                                    list.add( inf );
                                }
                            }
                            break;
                        }
                    }
                }
                if( list.size() > 0 ) {
                    IntentFilter[] ret = new IntentFilter[list.size()];
                    return list.toArray( ret );
                }
            }
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }

    private static final boolean initIntentFilterFromXml( IntentFilter inf, XmlPullParser xpp ) {
        try {
            int outerDepth = xpp.getDepth();
            int type;
            final String NAME = "name";
            while( (type = xpp.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || xpp.getDepth() > outerDepth) ) {
                if( type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT )
                    continue;
                String tag = xpp.getName();
                if( tag.equals( "action" ) ) {
                    String name = xpp.getAttributeValue( null, NAME );
                    if( name != null )
                        inf.addAction( name );
                }
                else if( tag.equals( "category" ) ) {
                    String name = xpp.getAttributeValue( null, NAME );
                    if( name != null )
                        inf.addCategory( name );
                }
                else if( tag.equals( "data" ) ) {
                    int na = xpp.getAttributeCount();
                    for( int i = 0; i < na; i++ ) {
                        String port = null;
                        String an = xpp.getAttributeName( i );
                        String av = xpp.getAttributeValue( i );
                        if( "mimeType".equals( an ) ) {
                            try {
                                inf.addDataType( av );
                            }
                            catch( MalformedMimeTypeException e ) {
                            }
                        } else
                        if( "scheme".equals( an ) ) {
                            inf.addDataScheme( av );
                        } else
                        if( "host".equals( an ) ) {
                            inf.addDataAuthority( av, port );
                        } else
                        if( "port".equals( an ) ) {
                            port = av;
                        } else
                        if( "path".equals( an ) ) {
                            inf.addDataPath( av, PatternMatcher.PATTERN_LITERAL );
                        } else
                        if( "pathPrefix".equals( an ) ) {
                            inf.addDataPath( av, PatternMatcher.PATTERN_PREFIX );
                        } else
                        if( "pathPattern".equals( an ) ) {
                            inf.addDataPath( av, PatternMatcher.PATTERN_SIMPLE_GLOB );
                        }
                    }
                }
            }
            return true;
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
        return false;
    }
 /*   
    public static boolean compareIntentFilters( IntentFilter if1, IntentFilter if2 ) {
        try {
            int ca1 = if1.countActions();
            int ca2 = if2.countActions();
            if( ca1 != ca2 ) return false;
            for( int i = 0; i< ca1; i++ )
                if( !if1.getAction( i ).equals( if2.getAction( i ) ) ) return false;
            
            int cc1 = if1.countCategories();
            int cc2 = if2.countCategories();
            if( cc1 != cc2 ) return false;
            for( int i = 0; i< cc1; i++ )
                if( !if1.getCategory( i ).equals( if2.getCategory( i ) ) ) return false;
            
            int cd1 = if1.countDataTypes();
            int cd2 = if2.countDataTypes();
            if( cd1 != cd2 ) return false;
            for( int i = 0; i< cd1; i++ )
                if( !if1.getDataType( i ).equals( if2.getDataType( i ) ) ) return false;
            
            int cs1 = if1.countDataSchemes();
            int cs2 = if2.countDataSchemes();
            if( cs1 != cs2 ) return false;
            for( int i = 0; i< cs1; i++ )
                if( !if1.getDataScheme( i ).equals( if2.getDataScheme( i ) ) ) return false;
            return true;
        }
        catch( Exception e ) {
        }
        return false;
    }
*/
}
