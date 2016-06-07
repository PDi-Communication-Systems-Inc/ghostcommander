package com.ghostsq.commander.utils;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;

public class Credentials implements Parcelable {
    private static String  TAG  = "GC.Credentials";
    private static String  seed = "5hO@%#O7&!H3#R";
    private static byte[] rawKey = null;
    public  static String  pwScreen = "***";
    public  static String  KEY  = "CRD";
    private String username, password;

    public Credentials( String usernamePassword ) { // ':' - separated
        int cp = usernamePassword.indexOf( ':' );
        if( cp < 0 ) {
            this.username = usernamePassword;
            return;
        }
        this.username = usernamePassword.substring( 0, cp );
        this.password = usernamePassword.substring( cp + 1 );
    }
    public Credentials( String userName, String password ) {
        this.username = userName;
        this.password = password;
    }
    public Credentials( Credentials c ) {
        this( c.getUserName(), c.getPassword() );
    }
    public String getUserName() {
        return this.username;
    }
    public String getPassword() {
        return this.password;
    }

     public static final Parcelable.Creator<Credentials> CREATOR = new Parcelable.Creator<Credentials>() {
         public Credentials createFromParcel( Parcel in ) {
             String un = in.readString();
             String pw = "";
             try {
                 pw = new String( decrypt( getRawKey( seed ), in.createByteArray() ) );
             } catch( Exception e ) {
                 Log.e( TAG, "on password decryption", e );
             }
             return new Credentials( un, pw );
         }

         public Credentials[] newArray( int size ) {
             return new Credentials[size];
         }
     };    
    
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel( Parcel dest, int f ) {
        byte[] enc_pw = null;
        try {
            enc_pw = encrypt( getRawKey( seed ), getPassword().getBytes() );
        } catch( Exception e ) {
            Log.e( TAG, "on password encryption", e );
        }
        dest.writeString( getUserName() );
        dest.writeByteArray( enc_pw );
    }

    private static byte[] getRawKey( String seed ) throws Exception {
        boolean primary = Credentials.seed.equals( seed );
        if( primary && Credentials.rawKey != null ) return Credentials.rawKey;
        KeyGenerator kgen = KeyGenerator.getInstance( "AES" );
        SecureRandom sr = SecureRandom.getInstance( "SHA1PRNG", "Crypto" );
        sr.setSeed( seed.getBytes() );
        kgen.init( 128, sr ); // 192 and 256 bits may not be available
        SecretKey skey = kgen.generateKey();
        byte[] raw = skey.getEncoded();
        if( primary )
            Credentials.rawKey = raw;
        return raw;
    }

    public static Credentials createFromEncriptedString( String s ) {
        return createFromEncriptedString( s, null );
    }

    public static Credentials createFromEncriptedString( String s, String seed_ ) {
        try {
            boolean base64 = true;
            if( seed_ == null ) {
                seed_ = Credentials.seed;
                base64 = false;
            }
            return new Credentials( decrypt( seed_, s, base64 ) );
        } catch( Exception e ) {
            Log.e( TAG, "on creating from an encrypted string", e );
        }
        return null;
    }
    public String exportToEncriptedString() {
        return exportToEncriptedString( null );
    }
    public String exportToEncriptedString( String seed_ ) {
        try {
            boolean base64 = true;
            if( seed_ == null ) {
                seed_ = this.seed;
                base64 = false;
            }
            return encrypt( seed_, getUserName() + ":" + getPassword(), base64 );
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static String decrypt( String encrypted ) throws Exception {
        return decrypt( seed, encrypted, false );
    }
    
    public static String encrypt( String seed, String cleartext, boolean base64out ) throws Exception {
        byte[] rawKey = getRawKey( seed );
        byte[] result = encrypt( rawKey, cleartext.getBytes() );
        if( base64out )
            return ForwardCompat.toBase64( result );
        else
            return Utils.toHexString( result, null );
    }

    public static String decrypt( String seed, String encrypted, boolean base64in ) throws Exception {
        byte[] rawKey  = getRawKey( seed );
        byte[] enc = base64in ? ForwardCompat.fromBase64( encrypted ) : Utils.hexStringToBytes( encrypted );
        byte[] result = decrypt( rawKey, enc );
        return new String( result );
    }

    private static byte[] encrypt( byte[] raw, byte[] clear ) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec( raw, "AES" );
        Cipher cipher = Cipher.getInstance( "AES" );
        cipher.init( Cipher.ENCRYPT_MODE, skeySpec );
        byte[] encrypted = cipher.doFinal( clear );
        return encrypted;
    }

    private static byte[] decrypt( byte[] raw, byte[] encrypted ) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec( raw, "AES" );
        Cipher cipher = Cipher.getInstance( "AES" );
        cipher.init( Cipher.DECRYPT_MODE, skeySpec );
        byte[] decrypted = cipher.doFinal( encrypted );
        return decrypted;
    }
}
