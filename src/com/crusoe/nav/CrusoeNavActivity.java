package com.crusoe.nav;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Locale;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;

import com.crusoe.nav.ConfigDialog.ConfigDialogListener;
import com.crusoe.nav.StatWpt;
import com.crusoe.nav.RouteListActivity;
import com.crusoe.nav.FileChooser;
import com.crusoe.nav.CrusoeApplication.thread_status;
import com.crusoe.nav.small.CrusoeSmallActivity;
import com.crusoe.gpsfile.GpxWriter;
import com.crusoe.gpsfile.RoutePoint;
import com.crusoe.gpsfile.WayPoint;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TabHost;
import android.widget.Toast;
import android.widget.TabHost.OnTabChangeListener;

public abstract class CrusoeNavActivity extends FragmentActivity 
	implements ConfigDialogListener, OnTabChangeListener, OnPageChangeListener 
{
	/*
	 * Esta es la clase base principal.
	 * Desde aqui se manejan los eventos y
	 * los menu's: Goto, Routes, Mark, Track, Quit
	 * Los fragments se manejan en la clase heredera y depende del tama�o
	 */
	
	public static final String PREFS_CFG_MAPS = "cfg_maps";
	public static final String PREFS_MAP_SRC = "cfg_map_src";
	public static final int MAPQUESTOSM = 0;
	public static final int MAPNIK = 1;
	public static final String PREFS_CFG_METRIC = "cfg_metric";
	public static final String PREFS_NAME = "com.crusoe.nav.prefs";
	public static final String PREFS_MAP_ZOOM = "map_zoom";
	public static SharedPreferences mPrefs;//preferencia de configuracion
	//clases para el manejo de los Fragments para SMALL
	protected FragmentPagerAdapter mAdapter;
    protected ViewPager mViewPager;
    protected TabHost mTabHost;
    
	WayPoint markWpt = null;
	boolean bQuit = false;
	public final static int MarkRC=1;//id para el mark
	public final static int WptRC=2;//id del goto
	public final static int RouteRC=3;//id de las rutas
	public final static int RouteEdit=4;//id de edicion de rutas
	public final static int WptEdit = 5;
	public final static int FileCh = 6;
	public final static int StatRC = 7;
	public final static int TrackRC = 8;
	
	//Constantes utilizadas en las distintas actividades
	public static final int NotDefined=0;
	public static final int Active=1;
	public static final int Invert=2;
	public static final int Delete=3;
	public static final int Add=4;
	public static final int Insert = 5;
	public static final int Edit=6;
	public static final int MoveUp=7;
	public static final int MoveDown=8;

	//boolean goback=false;
	public static final String CRUSOE_LOCATION_VIEW_INTENT = "com.crusoe.nav.LocationView";
	public static final String CRUSOE_MESSAGE = "com.crusoe.nav.message";

	WayPoint mLocation = null;
	private final IntentFilter locFilter = new IntentFilter(CrusoeApplication.CRUSOE_LOCATION_INTENT);
	private final CrusoeLocationReceiver locReceiver = new CrusoeLocationReceiver();
	private boolean locRegistered=false;
	
    // Method to add a TabHost
    protected static void AddTab(CrusoeSmallActivity activity, TabHost tabHost, TabHost.TabSpec tabSpec) {
        tabSpec.setContent(new CrusoeTabFactory(activity));
        tabHost.addTab(tabSpec);
    }

    protected abstract void initialiseTabHost();

    @Override
	public void onPageScrollStateChanged(int arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onPageScrolled(int arg0, float arg1, int arg2) {
		// TODO Auto-generated method stub
	       int pos = this.mViewPager.getCurrentItem();
	       this.mTabHost.setCurrentTab(pos);
	}

	@Override
	public void onPageSelected(int arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onTabChanged(String arg0) {
		// TODO Auto-generated method stub
        int pos = this.mTabHost.getCurrentTab();
        this.mViewPager.setCurrentItem(pos);
	}

    private class CrusoeLocationReceiver extends BroadcastReceiver{
		//recibe los cambios de posicion del gps
		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			//extraigo de Intent los datos enviados
			
	        //handler.sendEmptyMessage(1000);//envia mensaje. Atiende handler
			try{
				mLocation = new WayPoint("", "", intent.getExtras().getString("PROVIDER"));
				mLocation.setAccuracy(intent.getFloatExtra("ACCURACY", (float)0.0));
				mLocation.setLatitude(intent.getDoubleExtra("LATITUD", 0.0));
				mLocation.setLongitude(intent.getDoubleExtra("LONGITUD", 0.0));
			
            	//Intent t = new Intent();
            	intent.setAction(CrusoeNavActivity.CRUSOE_LOCATION_VIEW_INTENT);
            	//t.putExtra("ACCURACY", mLocation.getAccuracy());
            	//t.putExtra("LATITUD", mLocation.getLatitude());//loc.convert(loc.getLatitude(), loc.FORMAT_SECONDS));
            	//t.putExtra("LONGITUD", mLocation.getLongitude());//loc.convert(loc.getLongitude(), loc.FORMAT_SECONDS));
                //t.putExtra("PROVIDER", mLocation.getProvider());
				sendBroadcast(intent);
			}
			catch(Exception e)
			{
                Toast.makeText(getBaseContext(), 
                		"CrusoeNavActivity.onReceived: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
			}
		}
	};

	private final IntentFilter msgFilter = new IntentFilter(CRUSOE_MESSAGE);
	private final CrusoeMessageReceiver msgReceiver = new CrusoeMessageReceiver();
	private boolean msgRegistered=false;
	//borra WayPoint de todas las rutas
	void DeleteWpt(String n)
	{
		CrusoeApplication app = ((CrusoeApplication)getApplication());
		WayPoint p = app.getWayPoint(n);
		if(p!=null)
		{
			for(RoutePoint r: app.routes)
			{
				r.Locations().remove(p);
			}
		}		
	}
	//coloca  un WayPoint en GOTO
	void GotoWpt(String n)
	{
		CrusoeApplication app = ((CrusoeApplication)getApplication());
		app.gotoWpt = null;
		WayPoint p = app.getWayPoint(n);
		if(p==null)
		{
            Toast.makeText(getBaseContext(), 
                    R.string.error_no_wpt, 
                    Toast.LENGTH_SHORT).show();
            return;
		}
		
		if(app.ruta_seguir!=null)
		{
			//si tengo una ruta activa, busco si el wpt pertenece a la ruta.
			//si pertence voy directamente a ese punto y continuo con los siguientes de la ruta
			int w=0;
			while(w<app.ruta_seguir.size())
			{
				app.gotoWpt = (StatWpt)app.ruta_seguir.get(w);
				if(n.compareTo(app.gotoWpt.getName())==0)
				{
					//app.ruta_seguir.remove(app.gotoWpt);
					int w2=0;
					while(w2<w)
					{
						((StatWpt)app.ruta_seguir.get(w2)).Terminado(true);
						w2++;
					}
					app.ruta_offset=w;
					app.track.StopSegment();
					app.track.StartSegment();
					return;
				}
				//debo borrar todos los puntos anteriores.
				//app.ruta_seguir.remove(app.gotoWpt);
				w++;
			}
		}
		//si habia una ruta activa termina y me dirijo al goto
		app.ruta_seguir=null;
		app.ruta_offset=0;
		
		app.track.StartSegment();
		if(mLocation!=null)
			app.gotoWpt = new StatWpt(p);
		else
            Toast.makeText(getBaseContext(), 
                    R.string.gps_signal_not_found, 
                    Toast.LENGTH_SHORT).show();
	
	}
	void UpdateStats()
	{
       	//actualizo StatFragment
		Log.i("TAG", "CrusoeNavActivity.UpdateStats");
    	Intent t = new Intent();
    	t.setAction(StatFragment.CRUSOE_STAT_MESSAGE);
		sendBroadcast(t);
	}
	//recibe mensajes de otras actividades y fragments
	private class CrusoeMessageReceiver extends BroadcastReceiver{
		//recibe mensajes como nuevo goto, ruta, ...
		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			//extraigo de Intent los datos enviados
			Log.i("TAG", "CrusoeNavActivity.MSG");
			try{
	           	int select = intent.getIntExtra("ACTION", NotDefined);
	    		CrusoeApplication app = ((CrusoeApplication)getApplication());
	    		String n = intent.getStringExtra("NAME");
	           	switch(select)
	           	{
	           	case Active:
	           		GotoWpt(n);
	           		break;
	           	case Delete:
	           		{
	        			//WayPoint p = app.getWayPoint(n);
	           			int w=0;
	           			StatWpt sw=null;
	           			while(w<app.ruta_seguir.size())
	           			{
	           				sw = app.ruta_seguir.get(w);
	           				if(n.compareTo(sw.getWpt().getName())==0)
	           					break;
	           				w++;
	           			}
	           			if(w<app.ruta_seguir.size())
	           				app.ruta_seguir.remove(sw);
	           		}
	           		break;
	           	}
	           	//actualizo StatFragment
	           	UpdateStats();
			}
			catch(Exception e)
			{
                Toast.makeText(getBaseContext(), 
                		"CrusoeNavActivity.onReceived: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
			}
		}
	};

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		//cambia configuracion: landscape o portrait
	  super.onConfigurationChanged(newConfig);
		if(locRegistered==false)
			this.registerReceiver(locReceiver, locFilter);
		locRegistered = true;
		if(msgRegistered==false)
			this.registerReceiver(msgReceiver, msgFilter);
		msgRegistered = true;
	}
	
    // Method to add a TabHost
    protected static void AddTab(CrusoeNavActivity activity, TabHost tabHost, TabHost.TabSpec tabSpec) {
        tabSpec.setContent(new CrusoeTabFactory(activity));
        tabHost.addTab(tabSpec);
    }
    /*
    private void initialiseTabHost() {
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();

        // TODO Put here your Tabs
        CrusoeNavActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("DataTab").setIndicator("Data"));
        CrusoeNavActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("CompassTab").setIndicator("Compass"));
        CrusoeNavActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("StatTab").setIndicator("Stats"));

        mTabHost.setOnTabChangedListener(this);
    }*/
	@Override
	protected void onStop()
	{
		Log.i("TAG", "DataActivity.onStop");
		try{
			CrusoeApplication app = (CrusoeApplication)getApplication();
			if(locRegistered)
				unregisterReceiver(locReceiver);
			locRegistered = false;
			if(msgRegistered)
				unregisterReceiver(msgReceiver);
			msgRegistered = false;
			if(app.getThreadStatus() == thread_status.thStop && app.getLooper()!=null)
			{
				//handler.sendEmptyMessage(1002);//envia mensaje. Atiende handler
				if(bQuit)
				{
					app.RemoveLocationListener();//apaga gps.
					Log.i("TAG", "DataActivity Looper.quit");
				}
			}
		}
		catch(Exception e)
		{
            Toast.makeText(getBaseContext(), 
            		"No se pudo parar thread " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show();
		}
		finally
		{
			super.onStop();
		}
	}
	
	@Override
	protected void onDestroy()
	{	//cuando termina o es destruida por el sistema
		//http://developer.android.com/reference/android/app/Activity.html
			super.onDestroy();
			//CrusoeApplication app = (CrusoeApplication)getApplication();
			//app.RemoveLocationListener();
			Log.i("TAG", "DataActivity.onDestroy");
			if(bQuit)
			{
				//no es muy elegante pero por ahora es lo unico que hay 
				Process.killProcess(Process.myPid());				
			}
			//CrusoeApplication app = (CrusoeApplication)getApplication();
			//app.RemoveLocationListener();
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try{
			mPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

			Log.i("TAG", "CrusoeNavActivity.onCreate");
			if(!locRegistered)
			{
				if(this.registerReceiver(locReceiver, locFilter)!=null)
					locRegistered = true;
				else
					Log.i("ERROR", "CrusoeNavActivity.CrusoeLocationReceiver");
					
			}
	        if(!msgRegistered)
			{
				if(this.registerReceiver(msgReceiver, msgFilter)!=null)
					msgRegistered = true;
				else
					Log.i("ERROR", "CrusoeNavActivity.CrusoeMessageReceiver");
			}
			
	        
			CrusoeApplication app = ((CrusoeApplication)getApplication());
	        app.cfg_metric = mPrefs.getString(PREFS_CFG_METRIC, getBaseContext().getString(R.string.cfg_km));
	        app.cfg_maps = mPrefs.getString(PREFS_CFG_MAPS, getBaseContext().getString(R.string.Online));
	        app.map_src = mPrefs.getInt(PREFS_MAP_SRC, MAPQUESTOSM);// o MAPNIK
	        app.map_zoom = mPrefs.getInt(PREFS_MAP_ZOOM, 15);
			if(app.getThreadStatus()==thread_status.thStart)
			{
				app.StartThread();
			}
		}
		catch(Exception e)
		{
			Log.i("ERROR CrusoeNavActivity ", e.getMessage());
		}
	}
	@Override
	protected void onRestart()
	{
		super.onRestart();
		try {
			CrusoeApplication app = ((CrusoeApplication)getApplication());

			if(!locRegistered)
			{
				this.registerReceiver(locReceiver, locFilter);
				locRegistered = true;
			}
			if(!msgRegistered)
			{
				this.registerReceiver(msgReceiver, msgFilter);
				msgRegistered = true;
			}
			//CrusoeApplication app = ((CrusoeApplication)getApplication());
			if(app.getThreadStatus()==thread_status.thStart)
			{
				app.StartThread();
			}
		}
		catch(Exception e)
		{
			Log.i("ERROR", e.getMessage());
		}
	}
	void onFileAction(int resultCode, Intent data)
	{
		CrusoeApplication app = ((CrusoeApplication)getApplication());
		if(resultCode==RESULT_OK && android.os.Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED))
		{
			String res = data.getStringExtra("RESULT");//nombre del archivo a cargar
			ArrayList<RoutePoint> Rt = CrusoeApplication.CargoWayPoints(res);
			if(Rt!=null)
			{
				for(RoutePoint RP:Rt)
				{
					SaveRoute(RP, Environment.getExternalStorageDirectory() + getBaseContext().getString(R.string.wpt_dir), RP.getName() + ".gpx");
				}
				app.routes.addAll(Rt);
			}
		}
	}
	/*
	 * onRouteAction: atiende los comandos: Activar, Invertir y Borra Ruta. El resto se atiende en su respectivo dialogo.
	 */
	void onRouteAction(int resultCode, Intent data)
	{
		Log.i("TAG", "CrusoeNavActivity.onActivityResult.onRouteAction");
		CrusoeApplication app = ((CrusoeApplication)getApplication());
		if(resultCode==RESULT_OK)
		{
			
			String res = data.getStringExtra("RESULT");
			int i=0;
			app.ruta_seguir = null;
			app.ruta_offset=0;
			while(i<app.routes.size())
			{
				String nombre = app.routes.get(i).getName();
				if(res.compareTo(nombre)==0)
					break;
				i++;
			}
			if(i<app.routes.size())
			{
				int action = data.getIntExtra("ACTION", -1);
				switch(action)
				{
				case CrusoeNavActivity.Invert://invertir
				{
					if(mLocation!=null)
					{
						RoutePoint R = app.routes.get(i);
						if(app.ruta_seguir==null)
							app.ruta_seguir = new ArrayList<StatWpt>();
						app.ruta_seguir.clear();
						app.active_route = R.getName();
						//app.ruta_seguir = new RoutePoint(R.getName() + " INV");
						for(WayPoint w:R.Locations())
						{
							app.ruta_seguir.add(0, (StatWpt)new StatWpt(w));
						}
						//int comienzo = app.Closest(app.ruta_seguir, mLocation);
						//while(comienzo>=0)
						//{
						//	app.gotoWpt = (WayPoint)app.ruta_seguir.Locations().toArray()[0];
						//	app.ruta_seguir.Locations().remove(app.gotoWpt);
						//	comienzo--;
						//}
						app.gotoWpt = (StatWpt)app.ruta_seguir.get(0);
						app.ruta_offset=0;
						if(app.ruta_seguir!=null)
						{
							for(StatWpt w:app.ruta_seguir)
							{
								app.track.AddWayPoint(w.getWpt());
							}
						}
						app.track.StartSegment();
			           	//actualizo StatFragment
						UpdateStats();
					}
			    	else
				        Toast.makeText(getBaseContext(), 
				                   R.string.error_gps_pos, 
				                    Toast.LENGTH_SHORT).show();
			    		
				}
					return;
				case CrusoeNavActivity.Active://activar
				{
					if(mLocation!=null)
					{
						RoutePoint R = app.routes.get(i);
						if(app.ruta_seguir==null)
							app.ruta_seguir = new ArrayList<StatWpt>();
						app.ruta_seguir.clear();
						app.active_route = R.getName();
						//app.ruta_seguir = new RoutePoint(R.getName());
						app.track.AddWayPoints(R.Locations());
						for(WayPoint w:R.Locations())
						{
							app.ruta_seguir.add(new StatWpt(w));
						}
						//int comienzo = app.Closest(app.ruta_seguir, mLocation);
						//while(comienzo>=0)
						//{
						//	app.gotoWpt = (WayPoint)app.ruta_seguir.Locations().toArray()[0];
						//	app.ruta_seguir.Locations().remove(app.gotoWpt);
						//	comienzo--;
						//}
						app.gotoWpt = (StatWpt)app.ruta_seguir.get(0);
						app.ruta_offset=0;
						app.track.StartSegment();
						UpdateStats();
					}
		    		else
			            Toast.makeText(getBaseContext(), 
			                    R.string.error_gps_pos, 
			                    Toast.LENGTH_SHORT).show();
		    		
				}
					return;
				case CrusoeNavActivity.Delete://borrar
					app.routes.remove(i);
					return;
				default://cualquier otro caso. No deberia llegar aca
					{
    	            Toast.makeText(getBaseContext(), 
    	                    R.string.NotImplemented, 
    	                    Toast.LENGTH_SHORT).show();
					}	
					break;
				}
			}
		}
	}
	/*
	 * onTrackAction: selecciona el track a ver en el mapa
	 */
	void onTrackAction(int resultCode, Intent data)
	{
		Log.i("TAG", "CrusoeNavActivity.onActivityResult.onTrackAction");
		CrusoeApplication app = ((CrusoeApplication)getApplication());
		if(resultCode==RESULT_OK)
		{
			
			String res = data.getStringExtra("RESULT");
			int i=0;
			app.ruta_seguir = null;
			app.ruta_offset=0;
			while(i<app.routes.size())
			{
				String nombre = app.routes.get(i).getName();
				if(res.compareTo(nombre)==0)
					break;
				i++;
			}
			if(i<app.routes.size())
			{
					if(mLocation!=null)
					{
						RoutePoint R = app.routes.get(i);
						if(app.ruta_seguir==null)
							app.ruta_seguir = new ArrayList<StatWpt>();
						app.ruta_seguir.clear();
						app.active_route = R.getName();
						//app.ruta_seguir = new RoutePoint(R.getName());
						app.track.AddWayPoints(R.Locations());
						for(WayPoint w:R.Locations())
						{
							app.ruta_seguir.add(new StatWpt(w));
						}
					}
		    		else
			            Toast.makeText(getBaseContext(), 
			                    R.string.error_gps_pos, 
			                    Toast.LENGTH_SHORT).show();
			}
		    		
		}
	}
	void onWayPointAction(int resultCode, Intent data)
	{
		CrusoeApplication app = ((CrusoeApplication)getApplication());
		if(resultCode==RESULT_OK)
		{
				int action = data.getIntExtra("ACTION", -1);
				switch(action)
				{
				case CrusoeNavActivity.Add:
				{//esto debe pasarse a RouteListActivity luego de un EditRouteActivity
	    			String wpt = data.getStringExtra("RESULT");
	    			WayPoint p = new WayPoint(wpt);
	    			RoutePoint ruta = app.getRoute("waypoints");
	    			if(ruta==null)
	    			{
	    				//Log.i("ERROR", this.getResources().getString(R.string.error_mark));
	    	            //Toast.makeText(getBaseContext(), 
	    	            //        R.string.error_mark, 
	    	            //        Toast.LENGTH_SHORT).show();
	    				//return;
	    				ruta = new RoutePoint("waypoints");
	    				app.routes.add(ruta);
	    			}
	    			p.setLatitude(data.getDoubleExtra("LATITUD", 0.0));
	    			p.setLongitude(data.getDoubleExtra("LONGITUD", 0.0));
	    			ruta.addWayPoint(p);
	    			SaveRoute(ruta, Environment.getExternalStorageDirectory() + getBaseContext().getString(R.string.wpt_dir), "waypoints.gpx");
				}
				break;
				case CrusoeNavActivity.Active://activar
				{
	    			String res = data.getStringExtra("RESULT");
	    			GotoWpt(res);
				}
					break;
				case CrusoeNavActivity.Delete://borrar
				{
	    			String res = data.getStringExtra("RESULT");
	    			DeleteWpt(res);
				}
					break;
				case CrusoeNavActivity.Edit://editar
				{
					//llega aca para editar un Wpt
	    			String new_name = data.getStringExtra("RESULT");
	    			String old_name = data.getStringExtra("NAME");
	    			WayPoint p = app.getWayPoint(old_name);
	    			if(p==null)
	    			{
      	            Toast.makeText(getBaseContext(), 
       	                    R.string.error_no_wpt, 
       	                    Toast.LENGTH_SHORT).show();
	    				break;
	    			}
	    			p.setName(new_name);
				}
					break;
				}
		}
	}

	@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	//resultado de llamados a otras actividades
		if(resultCode==RESULT_CANCELED)
		{
			return;
		}
			
    	if(requestCode==WptRC || requestCode == StatRC)//resultado de llamado a WAyPoint ListActivity
    	{
    		onWayPointAction(resultCode, data);
    	}
    	if(requestCode==RouteRC)//resultado de llamado a RouteListActivity
    	{
    		onRouteAction(resultCode, data);
    	}
    	if(requestCode==FileCh)//Import File
    	{
    		onFileAction(resultCode, data);
    	}
    	/*
    	if(requestCode==RouteEdit)
    	{
    		//fue llevado a EditRouteActivity
    		onRouteEdit(resultCode, data);
    	}*/
    	return;
    }
    public static void SaveRoute(RoutePoint ruta, String directory, String filename)
    {
		try {
			//File WaypointsDir =  new File(Environment.getExternalStorageDirectory() + getBaseContext().getString(R.string.wpt_dir));
			File gpxfile = new File(directory, filename);
			//File gpxfile = new File(filename);
			GpxWriter csv = new GpxWriter(gpxfile);
			csv.writeHeader();
			int i=0;
			csv.startRoute(filename.replace(".gpx", ""));
			while(i<ruta.Locations().size())
			{
				csv.writeRoutePoint(ruta.getWayPoint(i));
				i++;
			}
			csv.endRoute();
			csv.writeFooter();
			csv.close();
		}
		catch(FileNotFoundException e)
		{
			Log.i("ERROR", e.getMessage());
		}
    }
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		Log.i("TAG", "DataActivity.onCreateOptionsMenu");
		Configuration cfg = getResources().getConfiguration();
		int size = cfg.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
		if(size == Configuration.SCREENLAYOUT_SIZE_SMALL)
			getMenuInflater().inflate(R.menu.small, menu);
		else
			getMenuInflater().inflate(R.menu.data, menu);
		return true;
	}
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	// Handle item selection
    	switch (item.getItemId()) {
    	case R.id.action_track:
    	{
    		//grabo a disco el trayecto con nombre de la fecha actual.
    		//En ppio. guardo un trayecto por fecha.
    		//Cada archivo tiene varios trayectos con nombre del goto y la hora.
    		//los segmentos son por si apago el track o se pierde la se�al.
    		//Crear la carpeta Crusoe
    		//Crear los subdirs Tracks y Waypoints.
    		//El archivo Waypoints guarda los waypoints generales en la carpeta Waypoints.
    		//Las rutas van en la carpeta Waypoints.
    		//Los nombre de las rutas corresponden al nombre del archivo.
    		//Cada ruta se compone de los waypoints que contiene el archivo.
    		if(mLocation!=null)
    		{
    			CrusoeApplication app = ((CrusoeApplication)getApplication());
    			int locs = app.SaveTrack();
	            Toast.makeText(getBaseContext(), 
	                    String.format("%d %s", locs, R.string.gps_track_saved), 
	                    Toast.LENGTH_SHORT).show();
    		}
    		else
	            Toast.makeText(getBaseContext(), 
	                    R.string.error_gps_pos, 
	                    Toast.LENGTH_SHORT).show();
    	}
    		break;
    	case R.id.action_loc://seteo mi posicion en el centro de mapview
    	{
        	Intent t = new Intent();
        	t.setAction(MapViewFragment.CRUSOE_LOC_MESSAGE);
    		sendBroadcast(t);    		
    	}
    		break;
    	case R.id.action_mark:
    	{
    		if(!(mLocation == null))
    		{
    			//waypoints.add(mLocation);
    			markWpt = mLocation;
    			Intent markIntent = new Intent(this, WayPointActivity.class);
    			markIntent.putExtra("LATITUD", mLocation.getLatitude());
    			markIntent.putExtra("LONGITUD", mLocation.getLongitude());
    			markIntent.putExtra("ACCURACY", mLocation.getAccuracy());
    			markIntent.putExtra("ACTION", Add);
    			this.startActivityForResult(markIntent, WptRC);
    		}
    		else
	            Toast.makeText(getBaseContext(), 
	                    R.string.error_gps_pos, 
	                    Toast.LENGTH_SHORT).show();
    	}		
    		break;
    	case R.id.action_goto:
    	{
    		CrusoeApplication app = ((CrusoeApplication)getApplication());
    		if(app.routes.size()>0)
    		{
				String param=getBaseContext().getString(R.string.action_Agregar) + ";";
    			for(RoutePoint r : app.routes)
    			{
    				for(WayPoint w: r.Locations())
    				{
    					param = param + w.getName() + ";";
    				}
    			}
				Intent gotoIntent = new Intent(this, WayPointsListActivity.class);
				gotoIntent.putExtra("NAMES", param);
				//gotoIntent.putExtra("TYPE", WptRC);
				this.startActivityForResult(gotoIntent, WptRC);
    		}
    		else
	            Toast.makeText(getBaseContext(), 
	                    R.string.error_no_wpt, 
	                    Toast.LENGTH_SHORT).show();
    	}
    		break;
    	case R.id.action_route:
    	{
    			CrusoeApplication app = ((CrusoeApplication)getApplication());
    			if(app.routes.size()>0)
    			{
    				int i=0;
    				String param=getBaseContext().getString(R.string.action_Agregar) + ";";
    				while(i<app.routes.size())
    				{
    					if(app.routes.get(i).getName().toLowerCase(Locale.getDefault()).compareTo("waypoints")!=0)
    						param = param + app.routes.get(i).getName() + ";";
    					i++;
    				}
					Intent routeIntent = new Intent(this, RouteListActivity.class);
					routeIntent.putExtra("NAMES", param);
					routeIntent.putExtra("TYPE", RouteRC);
					this.startActivityForResult(routeIntent, RouteRC);
    			}
    			else
	            	Toast.makeText(getBaseContext(), 
	                    R.string.error_no_routes, 
	                    Toast.LENGTH_SHORT).show();
    	}
    		break;
    	case R.id.action_settings:
    	{
    		//Toast.makeText(this.getBaseContext(), "VERSION 0.1.0.1", Toast.LENGTH_LONG).show();
    		try {
        		FragmentManager fm = getSupportFragmentManager();
				String s = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
	    		ConfigDialog dlg = new ConfigDialog("CrusoeNav " + s);
	    		dlg.show(fm, getResources().getString(R.string.action_settings));
			} catch (NameNotFoundException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
	    		Toast.makeText(this.getBaseContext(), "ERROR " + e.getMessage(), Toast.LENGTH_LONG).show();
    			Log.i("ERROR", e.getMessage());
			}
    	}
    		break;
    	case R.id.action_import:
    	{
			Intent fileIntent = new Intent(this, FileChooser.class);
			this.startActivityForResult(fileIntent, FileCh);
    	}
    		break;
    	case R.id.statlist://para tablets con API>11
       	{
			CrusoeApplication app = ((CrusoeApplication)getApplication());
			if(app.active_route!="")
			{
				Intent routeIntent = new Intent(this, StatDialog.class);
				routeIntent.putExtra("NAMES", app.active_route);
				routeIntent.putExtra("TYPE", StatRC);
				this.startActivityForResult(routeIntent, StatRC);
			}
			else
            	Toast.makeText(getBaseContext(), 
                    R.string.error_no_routes, 
                    Toast.LENGTH_SHORT).show();
	}
    		break;
    	case R.id.tracklist:
    	{
			CrusoeApplication app = ((CrusoeApplication)getApplication());
				Intent routeIntent = new Intent(this, TrackListActivity.class);
				routeIntent.putExtra("TYPE", TrackRC);
				this.startActivityForResult(routeIntent, TrackRC);
			}
    		break;
    		
    	case R.id.action_quit:
    	{
    		try{
    			CrusoeApplication app = (CrusoeApplication)getApplication();
    			bQuit = true;
    			app.getLooper().quit();
    			app.getThread().join();
    		}catch(InterruptedException e)
    		{
    			Log.i("EXC", e.getMessage());
    		}
    		finally
    		{
    		this.finish();
    		}
    	}
    		break;
    	}
    	return true;
    }

	public static String convMilliSec(long milli)
	{//convierto latitud

		int ss = (int)milli/1000;
		int mm = ss/60;
		ss = ss % 60;
		int hh = mm/60;
		mm = mm % 60;
		String hms=String.format("%02d:%02d:%02d", hh, mm, ss);
		
		return hms;
	}
	@Override
	public void onFinishConfigDialog(String res) {
		// TODO Auto-generated method stub
		CrusoeApplication app = ((CrusoeApplication)getApplication());
		if(res.contentEquals("Offline") || res.contentEquals("Online"))
			app.cfg_maps = res;
		else
		{
			if(res.contentEquals("MAPQUEST"))
				app.map_src = CrusoeNavActivity.MAPQUESTOSM;
			else
			{
				if(res.contentEquals("MAPNIK"))
					app.map_src = CrusoeNavActivity.MAPNIK;	
				else
					app.cfg_metric = res;
			}
		}
        final SharedPreferences.Editor edit = mPrefs.edit();
        edit.putString(PREFS_CFG_MAPS, app.cfg_maps);
        edit.putInt(PREFS_MAP_SRC, app.map_src);
        edit.putString(PREFS_CFG_METRIC, app.cfg_metric);
        edit.putInt(PREFS_MAP_ZOOM, app.map_zoom);
        edit.commit();
		
	}

}
