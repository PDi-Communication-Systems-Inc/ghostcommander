package com.ghostsq.commander;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import com.ghostsq.commander.adapters.CA;
import com.ghostsq.commander.adapters.CommanderAdapter;
import com.ghostsq.commander.favorites.Favorite;
import com.ghostsq.commander.utils.Credentials;
import com.ghostsq.commander.utils.ForwardCompat;
import com.ghostsq.commander.utils.Utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class Editor extends Activity implements TextWatcher {
    private final static String TAG = "EditorActivity";
    private final static String SP_ENC = "encoding";
	final static int MENU_SAVE = 214, MENU_SVAS = 212, MENU_RELD = 439, MENU_WRAP = 241, MENU_ENC = 363, MENU_EXIT = 323;
//	final static String URI = "URIfileForEdit";

	private EditText te;
	private boolean horScroll = true;
	public  Uri uri;
	public  CommanderAdapter ca;
	public  boolean dirty = false;
	public  String encoding;
	private DataLoadTask loader = null;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        try {
            SharedPreferences prefs = getPreferences( MODE_PRIVATE );
            if( prefs != null )
                encoding = prefs.getString( SP_ENC, "" );
            boolean ct_enabled = false, ab_enabled = false;
            //     !ForwardCompat.hasPermanentMenuKey( this )
            if( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
                final int size_class = ( getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK );
                if( size_class >= Configuration.SCREENLAYOUT_SIZE_LARGE ||
                    !ForwardCompat.hasPermanentMenuKey( this ) ) {
                    ab_enabled = getWindow().requestFeature( Window.FEATURE_ACTION_BAR );
                    if( ab_enabled && size_class <= Configuration.SCREENLAYOUT_SIZE_LARGE )
                        ForwardCompat.setupActionBar( this );
                }
            }
            if( !ab_enabled )
                ct_enabled = requestWindowFeature( Window.FEATURE_CUSTOM_TITLE );
            setContentView(R.layout.editor);
            te = (EditText)findViewById( R.id.editor );
            te.addTextChangedListener( this );
            
            // experimental!
            te.setFilters( new InputFilter[] { new InputFilter.LengthFilter(0x7FFFFFFF) } ); 
            
            SharedPreferences shared_pref = PreferenceManager.getDefaultSharedPreferences( this );
            int fs = Integer.parseInt( shared_pref != null ? shared_pref.getString( "font_size", "12" ) : "12" );
            te.setTextSize( fs );
            
            ColorsKeeper ck = new ColorsKeeper( this );
            ck.restore();
            te.setBackgroundColor( ck.bgrColor );
            te.setTextColor( ck.fgrColor );
            
            if( ct_enabled ) {
                getWindow().setFeatureInt( Window.FEATURE_CUSTOM_TITLE, R.layout.atitle );
                TextView act_name_tv = (TextView)findViewById( R.id.act_name );
                if( act_name_tv != null )
                    act_name_tv.setText( R.string.editor_label );
            }
            uri = getIntent().getData();
            loader = new DataLoadTask();
            loader.execute();
            TextView file_name_tv = (TextView)findViewById( R.id.file_name );
            if( file_name_tv != null ) {
                String label_text = " - " + uri.getPath();
                String frgm = uri.getFragment();
                if( frgm != null )
                    label_text += " (" + frgm + ")";
                file_name_tv.setText( label_text );
            }
            te.setHorizontallyScrolling( horScroll );
        }
        catch( Exception e ) {
            Log.e( TAG, "", e );
            finish();
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
    protected void onStop() {
        //Log.d( TAG, "onStop" );
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        //Log.d( TAG, "onDestroy" );
        super.onDestroy();
        if( loader != null )
            loader.cancel( true );
        if( ca != null ) {
            //Log.d( TAG, "Prep destroy the CA" );
            ca.prepareToDestroy();
            ca = null;
        }
    }
    
    @Override
    public boolean onKeyDown( int keyCode, KeyEvent event ) {
        switch( keyCode ) {
        case KeyEvent.KEYCODE_BACK:
            if( dirty ) {
                askToSave();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
        
    @Override
    public boolean onPrepareOptionsMenu( Menu menu ) {
        menu.clear();
        menu.add( Menu.NONE, MENU_SAVE, Menu.NONE, getString( R.string.save     ) ).setIcon( android.R.drawable.ic_menu_save );
        menu.add( Menu.NONE, MENU_SVAS, Menu.NONE, getString( R.string.save_as  ) ).setIcon( android.R.drawable.ic_menu_save );
        menu.add( Menu.NONE, MENU_RELD, Menu.NONE, getString( R.string.revert   ) ).setIcon( android.R.drawable.ic_menu_revert );
        menu.add( Menu.NONE, MENU_WRAP, Menu.NONE, getString( R.string.wrap     ) ).setIcon( R.drawable.wrap );
        menu.add( Menu.NONE, MENU_ENC,  Menu.NONE, Utils.getEncodingDescr( this, encoding, 
                                                      Utils.ENC_DESC_MODE_BRIEF ) ).setIcon(android.R.drawable.ic_menu_sort_alphabetically );
        menu.add( Menu.NONE, MENU_EXIT, Menu.NONE, getString( R.string.exit     ) ).setIcon( android.R.drawable.ic_notification_clear_all );
	    return true;
    }
    @Override
    public boolean onMenuItemSelected( int featureId, MenuItem item ) {
        switch( item.getItemId() ) {
        case MENU_SAVE:
            new DataSaveTask( false ).execute( uri );
            return true;
        case MENU_SVAS: 
            try {
                LayoutInflater factory = LayoutInflater.from( this );
                View iv = factory.inflate( R.layout.input, null );
                if( iv != null ) {
                    TextView prompt = (TextView)iv.findViewById( R.id.prompt );
                    final EditText edit   = (EditText)iv.findViewById( R.id.edit_field );
                    prompt.setText( R.string.newf_prompt );
                    String path = uri.getPath();
                    if( path == null ) return false;
                    final int _alsp = path.lastIndexOf( '/' ) + 1;
                    edit.setText( path.substring( _alsp ) );
                    new AlertDialog.Builder( this )
                        .setTitle( R.string.save_as )
                        .setView( iv )
                        .setPositiveButton( R.string.save, new DialogInterface.OnClickListener() {
                            public void onClick( DialogInterface dialog, int i ) {
                                String fn = edit.getText().toString();
                                if( !Utils.str( fn ) ) return;
                                uri = Editor.this.uri.buildUpon().path( uri.getPath().substring( 0, _alsp ) + fn ).build();
                                new DataSaveTask( false ).execute( uri );
                            }
                        } ).setNegativeButton( R.string.dialog_cancel, null ).show();
                }
            } catch( Throwable e ) {
                Log.e( TAG, "", e );
            }
            return true;
        case MENU_RELD:
            loader = new DataLoadTask();
            loader.execute();
            return true;
        case MENU_ENC: {
                int cen = Integer.parseInt( Utils.getEncodingDescr( this, encoding, Utils.ENC_DESC_MODE_NUMB ) );
                new AlertDialog.Builder( this )
                    .setTitle( R.string.encoding )
                    .setSingleChoiceItems( R.array.encoding, cen, new DialogInterface.OnClickListener() {
                        public void onClick( DialogInterface dialog, int i ) {
                            dialog.dismiss();
                            encoding = getResources().getStringArray( R.array.encoding_vals )[i];
                            Log.i( TAG, "Chosen encoding: " + encoding );
                            Editor.this.showMessage( getString( R.string.encoding_set, encoding ) );
                        }
                    }).show();
            }
            return true;
        case MENU_WRAP: 
            try {
                EditText te = (EditText)findViewById( R.id.editor );
                horScroll = horScroll ? false : true;
                te.setHorizontallyScrolling( horScroll ); 
            }
            catch( Exception e ) {
                Log.e( TAG, "", e );
            }
            return true;
        case MENU_EXIT:
            if( dirty )
                askToSave();
            else {
                //Log.d( TAG, "finishing" );
                finish();
            }
        }
        return super.onMenuItemSelected(featureId, item);
    }

    private final void askToSave() { 
        DialogInterface.OnClickListener ocl = new DialogInterface.OnClickListener() {
                public void onClick( DialogInterface dialog, int which_button ) {
                    if( which_button == DialogInterface.BUTTON_POSITIVE ) {
                        new DataSaveTask( true ).execute( uri );
                    }
                    else if( which_button == DialogInterface.BUTTON_NEGATIVE ) {
                        //Log.d( TAG, "finishing" );
                        Editor.this.finish();
                    }
                }
            };
        new AlertDialog.Builder( this )
                .setIcon( android.R.drawable.ic_dialog_alert )
                .setTitle( R.string.save )
                .setMessage( R.string.not_saved )
                .setPositiveButton( R.string.save, ocl )
                .setNegativeButton( R.string.dialog_cancel, ocl )
                .show();
    }
    
    
    public final void showMessage( String s ) {
    	Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
    
     private class DataLoadTask extends AsyncTask<Void, String, CharSequence> {
        private ProgressDialog pd; 
         
        @Override
        protected void onPreExecute() {
            pd = ProgressDialog.show( Editor.this, "", getString( R.string.loading ), true, true );
        }
         
        @Override
        protected CharSequence doInBackground( Void... v ) {
            Uri uri = Editor.this.uri;
            try {
                //Log.d( TAG, "loading file from " + uri.toString() );
                final String   scheme = uri.getScheme();
                InputStream is = null;
                if( Editor.this.ca == null ) {
                    Editor.this.ca = CA.CreateAdapterInstance( uri, Editor.this );
                }
                if( Editor.this.ca != null ) {
                    Credentials crd = null; 
                    try {
                        crd = (Credentials)Editor.this.getIntent().getParcelableExtra( Credentials.KEY );
                    } catch( Exception e ) {
                        Log.e( TAG, "on taking credentials from parcel", e );
                    }
                    Editor.this.ca.setCredentials( crd );
                    is = Editor.this.ca.getContent( uri );
                }
                if( is != null ) {
                    CharSequence cs = Utils.readStreamToBuffer( is, encoding );
                    if( Editor.this.ca != null ) { 
                        Editor.this.ca.closeStream( is );
                    }
                    else
                        is.close();
                    return cs;
                }
                publishProgress( getString( R.string.rtexcept, uri.toString() ) );
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
            if( err.length > 0 ) Editor.this.showMessage( err[0] );
        }
        @Override
        protected void onPostExecute( CharSequence cs ) {
            pd.cancel();
            Editor.this.te.setText( cs );
            Editor.this.dirty = false;
            Editor.this.loader = null; 
        }
     }

     private class DataSaveTask extends AsyncTask<Uri, String, Boolean> {
        private ProgressDialog pd;
        private boolean close_on_finish;
        
        DataSaveTask( boolean close_on_finish_ ) {
            close_on_finish = close_on_finish_;
        }
         
        @Override
        protected void onPreExecute() {
            pd = ProgressDialog.show( Editor.this, "", getString( R.string.wait ), true, true );
        }
         
        @Override
        protected Boolean doInBackground( Uri... save_uri_ ) {
            Uri save_uri = save_uri_.length > 0 ? save_uri_[0] : null;
            if( Editor.this.ca == null ) {
                Log.e( TAG, "Adapter is null " );
                return false;
            }
            if( save_uri == null ) {
                Log.e( TAG, "No URI to save" );
                return false;
            }
            //Log.d( TAG, "saving file to " + save_uri.toString() );
            Credentials crd = null; 
            try {
                crd = (Credentials)Editor.this.getIntent().getParcelableExtra( Credentials.KEY );
            } catch( Exception e ) {
                Log.e( TAG, "on taking credentials from parcel", e );
            }
            Editor.this.ca.setCredentials( crd );
            OutputStream os = Editor.this.ca.saveContent( save_uri );
            if( os == null ) {
                Log.e( TAG, "No output stream" );
                return false;
            }
            try {
                final int BUF_SIZE = 1024*16;
                OutputStreamWriter osw = Editor.this.encoding != null && Editor.this.encoding.length() != 0 ?
                        new OutputStreamWriter( os, Editor.this.encoding ) :
                        new OutputStreamWriter( os );
                        
                Editable e = Editor.this.te.getText();
                int len = e.length();
                //Log.d( TAG, "length (chars): " + len );
                if( len < BUF_SIZE ) {
                    osw.write( e.toString() );
                    osw.flush();
                } else {
                    char[] chars = new char[BUF_SIZE];
                    int start = 0, end = BUF_SIZE, cnt = 0;
                    while( start < len-1 ) {
                        e.getChars( start, end, chars, 0 );
                        osw.write( chars, 0, end - start );
                        //Log.d( TAG, "end - start=" + (end - start) + " chars.length=" + chars.length );
                        start = end;
                        end += BUF_SIZE;
                        if( end > len )
                            end = len;
                        cnt++;
                    }
                    osw.flush();
                    //Log.d( TAG, "IO iterations: " + cnt );
                }
                Editor.this.ca.closeStream( os );
                File f = new File( save_uri.getPath() );
                publishProgress( getString( R.string.saved, f != null ? f.getName() : "file" ) );
                if( f != null )
                    //Log.d( TAG, "Saved size (bytes): " + f.length() );
                return true;
            } catch( Throwable e ) {
                Log.e( TAG, Favorite.screenPwd( save_uri ), e );
            }
            return false;
        }
        @Override
        protected void onProgressUpdate( String... err ) {
            if( err.length > 0 ) Editor.this.showMessage( err[0] );
        }
        @Override
        protected void onPostExecute( Boolean succeeded ) {
            pd.cancel();
            if( succeeded ) 
                Editor.this.dirty = false;
            else
                Editor.this.showMessage( Editor.this.getString( R.string.cant_save ) );
            if( close_on_finish ) {
                //Log.d( TAG, "finishing" );
                Editor.this.finish();
            }
        }
     }
     
    @Override
    public void afterTextChanged( Editable s ) {
        dirty = true;
    }
    @Override
    public void beforeTextChanged( CharSequence s, int start, int count, int after ) {
    }
    @Override
    public void onTextChanged( CharSequence s, int start, int before, int count ) {
    }
}
