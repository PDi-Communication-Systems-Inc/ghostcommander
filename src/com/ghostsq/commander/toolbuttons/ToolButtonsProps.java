package com.ghostsq.commander.toolbuttons;

import android.app.ListActivity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.commonsware.cwac.tlv.TouchListView;
import com.ghostsq.commander.R;

public class ToolButtonsProps extends ListActivity
{
    public final String TAG = getClass().getName();
    private ToolButtonsAdapter adapter = null;
    private ToolButtons array = new ToolButtons();
    private TouchListView tlv;
   
    class ToolButtonsAdapter extends ArrayAdapter<ToolButton> {
        ToolButtonsAdapter() {
            super( ToolButtonsProps.this, R.layout.butrow, array );
        }
        
        class ViewHolder {
            protected ToolButton tb;
            protected TextView label;
            protected CheckBox viscb;
            protected EditText caped;
        }
               
        public View getView( int position, View convertView, ViewGroup parent ) {
            try {
                ToolButton tb = array.get( position );
                if( tb != null ) {
                    View row = convertView;
                    ViewHolder holder = null;
                    if( row == null ) {                                                    
                        LayoutInflater inflater = getLayoutInflater();
                        row = inflater.inflate( R.layout.butrow, parent, false ); // null ?
                        final ViewHolder viewHolder$ = new ViewHolder();
                        viewHolder$.tb = tb;
                        viewHolder$.label = (TextView)row.findViewById( R.id.tb_label );
                        viewHolder$.caped = (EditText)row.findViewById( R.id.tb_caption );
                        viewHolder$.caped.addTextChangedListener( new TextWatcher() {
                                @Override
                                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                                }
                                @Override
                                public void onTextChanged(CharSequence s, int start, int before, int count) {
                                }
                                @Override
                                public void afterTextChanged( Editable e ) {
                                    viewHolder$.tb.setCaption( viewHolder$.caped.getText().toString() );
                                }
                            });
                        viewHolder$.viscb = (CheckBox)row.findViewById( R.id.tb_visible );
                        viewHolder$.viscb.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
                                @Override
                                public void onCheckedChanged( CompoundButton buttonView, boolean isChecked) {
                                    viewHolder$.tb.setVisible( buttonView.isChecked() );
                                }
                            });
                        row.setTag( viewHolder$ );
                        holder = viewHolder$;
                    }
                    else {
                        holder = (ViewHolder)row.getTag();
                        holder.tb = tb;
                    }
                    holder.label.setText( tb.getName( ToolButtonsProps.this ) );
                    holder.viscb.setChecked( tb.isVisible() );
                    holder.caped.setText( tb.getCaption() );
                    return row;
                }
            } catch( Exception e ) {
                Log.e( TAG, "position " + position, e );
            }
            return null;
        }
    }

	@Override
	public void onCreate( Bundle bundle ) {
		super.onCreate( bundle );
		setContentView( R.layout.tblist );

        SharedPreferences shared_pref = PreferenceManager.getDefaultSharedPreferences( this );
        array.restore( shared_pref, this );	
		
		tlv = (TouchListView)getListView();
		adapter = new ToolButtonsAdapter();
		setListAdapter(adapter);
		
		tlv.setDropListener(onDrop);
		tlv.setRemoveListener(onRemove);
	}

	@Override
    protected void onPause() {
        super.onPause();
        Log.v( TAG, "onPause()" );
        toLog();
        SharedPreferences.Editor sp_edit = PreferenceManager.getDefaultSharedPreferences( this ).edit();
        array.store( sp_edit );
        sp_edit.commit();
	}
	
    private TouchListView.DropListener onDrop = new TouchListView.DropListener() {
		@Override
		public void drop( int from, int to ) {
			ToolButton item = adapter.getItem( from );
			adapter.remove( item );
			adapter.insert( item, to );
		}
	};
	
	private TouchListView.RemoveListener onRemove = new TouchListView.RemoveListener() {
		@Override
		public void remove( int which ) {
			adapter.remove( adapter.getItem( which ) );
		}
	};
	
    public void toLog() {
        StringBuffer resb = new StringBuffer();
        for( int i = 0; i < array.size(); i++ ) {
            resb.append( array.get( i ).getCaption() );
            resb.append( "-" );
            resb.append( array.get( i ).isVisible() );
            resb.append( ", " );
        }
        Log.v( TAG, resb.toString() );        
    }
}

