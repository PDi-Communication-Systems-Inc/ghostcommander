package com.ghostsq.commander;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.Utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;

public class TextViewer extends Activity {
    public  final static String TAG = "TextViewerActivity";
    private final static String SP_ENC = "encoding";
    public  final static String STRURI = "string:";
    public  final static String STRKEY = "string";
    private final static int VIEW_BOT = 595, VIEW_TOP = 590, VIEW_ENC = 363;
    private ScrollView scrollView;
    public  TextView    text_view;
    public  Uri uri;
    public  String encoding;
    
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        try {
            boolean ct_enabled = requestWindowFeature( Window.FEATURE_CUSTOM_TITLE );
            setContentView( R.layout.textvw );
            SharedPreferences shared_pref = PreferenceManager.getDefaultSharedPreferences( this );
            int fs = Integer.parseInt( shared_pref != null ? shared_pref.getString( "font_size", "12" ) : "12" );
            text_view = (TextView)findViewById( R.id.text_view );
            if( text_view == null ) {
                Log.e( TAG, "No text view to show the content!" );
                finish();
                return;
            }
            text_view.setTextSize( fs );
            text_view.setTypeface( Typeface.create( "monospace", Typeface.NORMAL ) );

            ColorsKeeper ck = new ColorsKeeper( this );
            ck.restore();
            text_view.setBackgroundColor( ck.bgrColor );
            text_view.setTextColor( ck.fgrColor );
            
            if( ct_enabled ) {
                getWindow().setFeatureInt( Window.FEATURE_CUSTOM_TITLE, R.layout.atitle );
                TextView act_name_tv = (TextView)findViewById( R.id.act_name );
                if( act_name_tv != null )
                    act_name_tv.setText( R.string.textvw_label );
            }
            scrollView = (ScrollView)findViewById( R.id.scroll_view );
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SharedPreferences prefs = getPreferences( MODE_PRIVATE );
        if( prefs != null )
            encoding = prefs.getString( SP_ENC, "" );
        uri = getIntent().getData();
        if( !loadData() )
            finish();
        TextView file_name_tv = (TextView)findViewById( R.id.file_name );
        if( uri != null ) {
            String path = uri.getPath();
            if( file_name_tv != null && path != null && path.length() > 0 ) {
                String label_text = " - " + uri.getPath();
                String frgm = uri.getFragment();
                if( frgm != null )
                    label_text += " (" + frgm + ")";
                file_name_tv.setText( label_text );
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences.Editor editor = getPreferences( MODE_PRIVATE ).edit();
        editor.putString( SP_ENC, encoding == null ? "" : encoding );
        editor.commit();
    }
    
    @Override
    protected void onSaveInstanceState( Bundle toSaveState ) {
        Log.i( TAG, "Saving State: " + encoding );
        toSaveState.putString( SP_ENC, encoding == null ? "" : encoding );
        super.onSaveInstanceState( toSaveState );
    }

    @Override
    protected void onRestoreInstanceState( Bundle savedInstanceState ) {
        if( savedInstanceState != null )
            encoding = savedInstanceState.getString( SP_ENC );
        Log.i( TAG, "Restored State " + encoding );
        super.onRestoreInstanceState( savedInstanceState );
    }

    @Override
    public boolean onKeyDown( int keyCode, KeyEvent event ) {
        char c = (char)event.getUnicodeChar();
        switch( c ) {
        case 'q':
            finish();
            return true;
        case 'g':
            return dispatchCommand( VIEW_TOP );
        case 'G':
            return dispatchCommand( VIEW_BOT );
        }
        return super.onKeyDown( keyCode, event );
    }
    
    @Override
    public boolean onPrepareOptionsMenu( Menu menu ) {
        menu.clear();
        menu.add( Menu.NONE, VIEW_TOP, Menu.NONE, getString( R.string.go_top   ) ).setIcon( android.R.drawable.ic_media_previous );
        menu.add( Menu.NONE, VIEW_BOT, Menu.NONE, getString( R.string.go_end   ) ).setIcon( android.R.drawable.ic_media_next );
        menu.add( Menu.NONE, VIEW_ENC, Menu.NONE, Utils.getEncodingDescr( this, encoding, 
                                                     Utils.ENC_DESC_MODE_BRIEF ) ).setIcon( android.R.drawable.ic_menu_sort_alphabetically );
        return true;
    }
    @Override
    public boolean onMenuItemSelected( int featureId, MenuItem item ) {
        if( dispatchCommand( item.getItemId() ) ) return true; 
        return super.onMenuItemSelected( featureId, item );
    }

    public boolean dispatchCommand( int id ) {
        switch( id ) {
        case VIEW_BOT:
            scrollView.fullScroll( View.FOCUS_DOWN );
            return true;
        case VIEW_TOP:
            scrollView.fullScroll( View.FOCUS_UP );
            return true;
        case VIEW_ENC: {
                int cen = Integer.parseInt( Utils.getEncodingDescr( this, encoding, Utils.ENC_DESC_MODE_NUMB ) );
                new AlertDialog.Builder( this )
                    .setTitle( R.string.encoding )
                    .setSingleChoiceItems( R.array.encoding, cen, new DialogInterface.OnClickListener() {
                        public void onClick( DialogInterface dialog, int i ) {
                            encoding = getResources().getStringArray( R.array.encoding_vals )[i];
                            Log.i( TAG, "Chosen encoding: " + encoding );
                            dialog.dismiss();
                            loadData();
                        }
                    }).show();
            }
            return true;
            /*
        case WRAP: 
            try {
                EditText te = (EditText)findViewById( R.id.editor );
                horScroll = horScroll ? false : true;
                te.setHorizontallyScrolling( horScroll ); 
            }
            catch( Exception e ) {
                System.err.println("Exception: " + e );
            } 
            */
        }
        return false;
    }    

     private class DataLoadTask extends AsyncTask<Void, String, CharSequence> {
        @Override
        protected CharSequence doInBackground( Void... v ) {
            Uri uri = TextViewer.this.uri;
            try {
                final String   scheme = uri.getScheme();
                CommanderAdapter ca = null;
                InputStream is = null;
                if( ContentResolver.SCHEME_CONTENT.equals( scheme ) ) {
                    is = getContentResolver().openInputStream( uri ); 
                } else {
                    ca = CA.CreateAdapterInstance( uri, TextViewer.this );
                    if( ca != null ) {
                        Credentials crd = null; 
                        try {
                            crd = (Credentials)TextViewer.this.getIntent().getParcelableExtra( Credentials.KEY );
                        } catch( Exception e ) {
                            Log.e( TAG, "on taking credentials from parcel", e );
                        }
                        ca.setCredentials( crd );
                        is = ca.getContent( uri );
                    }
                }
                if( is != null ) {
                    CharSequence cs = Utils.readStreamToBuffer( is, encoding );
                    if( ca != null ) { 
                        ca.closeStream( is );
                        ca.prepareToDestroy();
                    }
                    else
                        is.close();
                    return cs;
                }
            } catch( OutOfMemoryError e ) {
                Log.e( TAG, uri.toString(), e );
                publishProgress( getString( R.string.too_big_file, uri.getPath() ) );
            } catch( Throwable e ) {
                Log.e( TAG, uri.toString(), e );
                publishProgress( getString( R.string.failed ) + e.getLocalizedMessage() );
                
            }
            return null;
        }
        @Override
        protected void onProgressUpdate( String... err ) {
            Toast.makeText( TextViewer.this, err[0], Toast.LENGTH_LONG ).show();
        }
        @Override
        protected void onPostExecute( CharSequence cs ) {
            try {
                TextViewer.this.text_view.setText( cs );
            } catch( Throwable e ) {
                onProgressUpdate( getString( R.string.failed ) + e.getLocalizedMessage() );
                e.printStackTrace();
            }
        }
     }
     
     private final boolean loadData() {
        if( uri != null ) { 
            try {
                final String   scheme = uri.getScheme();
                if( STRKEY.equals( scheme ) ) {
                    Intent i = getIntent();
                    String str = i.getStringExtra( STRKEY );
                    if( str != null ) {
                        text_view.setText( str );
                        return true;
                    }
                    return false;
                }
                new DataLoadTask().execute();
                return true;
            } catch( OutOfMemoryError e ) {
                Log.e( TAG, uri.toString(), e );
                Toast.makeText(this, getString( R.string.too_big_file, uri.getPath() ), Toast.LENGTH_LONG).show();
            } catch( Throwable e ) {
                Log.e( TAG, uri.toString(), e );
                Toast.makeText(this, getString( R.string.failed ) + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        }
        return false;
    }
}
