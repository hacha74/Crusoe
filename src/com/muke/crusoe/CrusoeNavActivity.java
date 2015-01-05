package com.muke.crusoe;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;

import com.muke.crusoe.CrusoeApplication.thread_status;
import com.muke.crusoe.gpsfile.GpxWriter;
import com.muke.crusoe.gpsfile.RoutePoint;
import com.muke.crusoe.gpsfile.WayPoint;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TabHost;
import android.widget.Toast;
import android.widget.TabHost.OnTabChangeListener;

public class CrusoeNavActivity extends FragmentActivity implements
		OnTabChangeListener, OnPageChangeListener {
	//clases para el manejo de los Fragments
	private CrusoeNavPagerAdapter mAdapter;
    private ViewPager mViewPager;
    private TabHost mTabHost;
    
	//WayPoint gotoWpt = null;
	WayPoint markWpt = null;
	boolean bQuit = false;
	public final static int MarkRC=1;//id para el mark
	public final static int WptRC=2;//id del goto
	public final static int RouteRC=3;//id de las rutas
	public final static int RouteEdit=4;//id de edicion de rutas
	public final static int WptEdit = 5;
	
	//boolean goback=false;
	public static final String CRUSOE_LOCATION_VIEW_INTENT = "com.muke.crusoe.LocationView";

	WayPoint mLocation = null;
	private final IntentFilter intentFilter = new IntentFilter(CrusoeApplication.CRUSOE_LOCATION_INTENT);
	private final CrusoeLocationReceiver mReceiver = new CrusoeLocationReceiver();
	private boolean registered=false;
	  
	private class CrusoeLocationReceiver extends BroadcastReceiver{

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

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
	  super.onConfigurationChanged(newConfig);
		if(registered==false)
			this.registerReceiver(mReceiver, intentFilter);
		registered = true;
		//compassView = (ImageView)findViewById(R.id.compassImg);

	}
    // Method to add a TabHost
    private static void AddTab(CrusoeNavActivity activity, TabHost tabHost, TabHost.TabSpec tabSpec) {
        tabSpec.setContent(new CrusoeTabFactory(activity));
        tabHost.addTab(tabSpec);
    }
    private void initialiseTabHost() {
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();

        // TODO Put here your Tabs
        CrusoeNavActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("DataTab").setIndicator("Data"));
        CrusoeNavActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("CompassTab").setIndicator("Compass"));
        CrusoeNavActivity.AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("StatTab").setIndicator("Stats"));

        mTabHost.setOnTabChangedListener(this);
    }
	@Override
	protected void onStop()
	{
		Log.i("TAG", "DataActivity.onStop");
		try{
			CrusoeApplication app = (CrusoeApplication)getApplication();
			if(registered)
				unregisterReceiver(mReceiver);
			registered = false;
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
			Log.i("TAG", "CrusoeNavActivity.onCreate");
			setContentView(R.layout.activity_main);
			CrusoeApplication app = ((CrusoeApplication)getApplication());
			app.CargoWayPoints();
			//inicializo framents
	        mViewPager = (ViewPager) findViewById(R.id.viewpager);
			initialiseTabHost();

	        // Tab Initialization
	        //initialiseTabHost();
	        mAdapter = new CrusoeNavPagerAdapter(getSupportFragmentManager());
	        // Fragments and ViewPager Initialization
	       
	        
	        mViewPager.setAdapter(mAdapter);
	        mViewPager.setOnPageChangeListener(CrusoeNavActivity.this);

	        if(!registered)
			{
				this.registerReceiver(mReceiver, intentFilter);
				registered = true;
			}
			
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
	@Override
	protected void onRestart()
	{
		super.onRestart();
		try {
			CrusoeApplication app = ((CrusoeApplication)getApplication());

			if(!registered)
			{
				this.registerReceiver(mReceiver, intentFilter);
				registered = true;
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
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode==RESULT_CANCELED)
		{
			return;
		}
			
		CrusoeApplication app = ((CrusoeApplication)getApplication());
    	if(requestCode==RouteRC)
    	{
    		if(resultCode==RESULT_OK)
    		{
    			String res = data.getStringExtra("RESULT");
    			int i=0;
    			if(res.compareTo("NEW")==0)
    			{
    				//agregar nueva ruta!!!
    				String n = data.getStringExtra("NAME");
    				if(n!=null && n!="")
    				{
    					app.routes.add(new RoutePoint(n));
    				}
    				return;
    			}
    			if(res.compareTo("ADD")==0)
    			{
    				//agregar waypoint a ruta
    				String r = data.getStringExtra("ROUTE");
    				RoutePoint rp = app.getRoute(r);
    				if(rp!=null)
    				{
    					String w = data.getStringExtra("NAME");
    					WayPoint wp = app.getWayPoint(w);
    					if(wp!=null)
    					{
    						rp.addWayPoint(wp);
    		    			SaveRoute(rp, rp.getName() + ".gpx");
    					}
    					else
    				        Toast.makeText(getBaseContext(), 
 				                   R.string.error_no_wpt, 
 				                    Toast.LENGTH_SHORT).show();
    				}
    				else
				        Toast.makeText(getBaseContext(), 
				                   R.string.RouteNotExist, 
				                    Toast.LENGTH_SHORT).show();
    				return;
    			}
    			app.ruta_seguir = null;
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
    				case CrusoeListActivity.Invert://invertir
    				{
    					if(mLocation!=null)
    					{
    						RoutePoint R = app.routes.get(i);
    						app.ruta_seguir = new RoutePoint(R.getName() + " INV");
    						//app.ruta_seguir.addAll(R.Locations());
    						for(WayPoint w : R.Locations())
    						{
    							app.ruta_seguir.insWayPoint(0, w);
    						}
    						int comienzo = app.Closest(app.ruta_seguir, mLocation);
    						while(comienzo>=0)
    						{
    							app.gotoWpt = (WayPoint)app.ruta_seguir.Locations().toArray()[0];
    							app.ruta_seguir.Locations().remove(app.gotoWpt);
    							comienzo--;
    						}
    						app.track.StartSegment();
    					}
    			    	else
    				        Toast.makeText(getBaseContext(), 
    				                   R.string.error_gps_pos, 
    				                    Toast.LENGTH_SHORT).show();
    			    		
    				}
    					return;
    				case CrusoeListActivity.Active://activar
    				{
    					if(mLocation!=null)
    					{
    						RoutePoint R = app.routes.get(i);
    						app.ruta_seguir = new RoutePoint(R.getName());
    						app.ruta_seguir.addAll(R.Locations());
    						int comienzo = app.Closest(app.ruta_seguir, mLocation);
    						while(comienzo>=0)
    						{
    							app.gotoWpt = (WayPoint)app.ruta_seguir.Locations().toArray()[0];
    							app.ruta_seguir.Locations().remove(app.gotoWpt);
    							comienzo--;
    						}
    						app.track.StartSegment();
    					}
    		    		else
    			            Toast.makeText(getBaseContext(), 
    			                    R.string.error_gps_pos, 
    			                    Toast.LENGTH_SHORT).show();
    		    		
    				}
    					return;
    				case CrusoeListActivity.Delete://borrar
    					app.routes.remove(i);
    					return;
    				case CrusoeListActivity.Edit://editar
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
    	if(requestCode==WptRC)
    	{
    		if(resultCode==RESULT_OK)
    		{
   				int action = data.getIntExtra("ACTION", -1);
   				switch(action)
   				{
   				case CrusoeListActivity.Add:
   				{
   	    			String wpt = data.getStringExtra("RESULT");
   	    			WayPoint p = new WayPoint(wpt);
   	    			RoutePoint ruta = app.getRoute("waypoints");
   	    			if(ruta==null)
   	    			{
   	    				Log.i("ERROR", this.getResources().getString(R.string.error_mark));
   	    	            Toast.makeText(getBaseContext(), 
   	    	                    R.string.error_mark, 
   	    	                    Toast.LENGTH_SHORT).show();
   	    				return;
   	    			}
   	    			p.setLatitude(data.getDoubleExtra("LATITUD", 0.0));
   	    			p.setLongitude(data.getDoubleExtra("LONGITUD", 0.0));
   	    			ruta.addWayPoint(p);
   	    			SaveRoute(ruta, "waypoints.gpx");
   				}
   				break;
   				case CrusoeListActivity.Active://activar
   				{
   	    			app.gotoWpt = null;
   	    			String res = data.getStringExtra("RESULT");
   	    			WayPoint p = app.getWayPoint(res);
   	    			if(p==null)
   	    			{
           	            Toast.makeText(getBaseContext(), 
           	                    R.string.error_no_wpt, 
           	                    Toast.LENGTH_SHORT).show();
           	            break;
   	    			}
   	    			if(app.ruta_seguir!=null)
   	    			{
   	    				//si tengo una ruta activa, busco si el wpt pertenece a la ruta.
   	    				//si pertence voy directamente a ese punto y continuo con los siguientes de la ruta
   	    				while(app.ruta_seguir.Locations().size()>0)
   	    				{
   							app.gotoWpt = (WayPoint)app.ruta_seguir.Locations().toArray()[0];
   							if(res.compareTo(app.gotoWpt.getName())==0)
   							{
   	   							app.ruta_seguir.Locations().remove(app.gotoWpt);
   								app.track.StopSegment();
   								app.track.StartSegment();
   								return;
   							}
   							//debo borrar todos los puntos anteriores.
   							app.ruta_seguir.Locations().remove(app.gotoWpt);
   	    				}
   	    			}
   					app.track.StartSegment();
   					if(mLocation!=null)
   						app.gotoWpt = p;
   					else
           	            Toast.makeText(getBaseContext(), 
           	                    R.string.gps_signal_not_found, 
           	                    Toast.LENGTH_SHORT).show();
   				}
   					break;
   				case CrusoeListActivity.Delete://borrar
   				{
   	    			String res = data.getStringExtra("RESULT");
   	    			WayPoint p = app.getWayPoint(res);
   	    			if(p!=null)
   	    			{
   	    				for(RoutePoint r: app.routes)
   	    				{
   	    					r.Locations().remove(p);
   	    				}
   	    			}
   				}
   					break;
   				case CrusoeListActivity.Edit://editar
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
    	if(requestCode==RouteEdit)
    	{//mandarlo a GotoActivity
    		if(resultCode==RESULT_OK)
    		{
    			String res = data.getStringExtra("RESULT");
    			WayPoint p = null;
    			for(RoutePoint r: app.routes)
    			{
    				for(WayPoint w: r.Locations())
    				{
    					if(res.compareTo(w.getName())==0)
    					{
    						p = w;
    						break;
    					}
    				}
    			}
    			if(p!=null)
    			{
    				int action = data.getIntExtra("ACTION", -1);
    				switch(action)
    				{
    				case CrusoeListActivity.Add:
    				{
    		    		if(app.routes.size()>0)
    		    		{
    		    			String param="";
    		    			for(RoutePoint r : app.routes)
    		    			{
    		    				for(WayPoint w: r.Locations())
    		    				{
    		    					param = param + w.getName() + ";";
    		    				}
    		    			}
    						Intent gotoIntent = new Intent(this, CrusoeListActivity.class);
    						gotoIntent.putExtra("NAMES", param);
    						gotoIntent.putExtra("TYPE", WptRC);
    						this.startActivityForResult(gotoIntent, RouteEdit);
    		    		}
    		    		else
    			            Toast.makeText(getBaseContext(), 
    			                    R.string.error_no_wpt, 
    			                    Toast.LENGTH_SHORT).show();
    				}
    					break;
    				case CrusoeListActivity.Delete://borrar
    	    			for(RoutePoint r: app.routes)
    	    			{
    	    				r.Locations().remove(p);
    	    			}
    					break;
    				case CrusoeListActivity.Edit://editar
    					
    					//llega aca para editar una Ruta
        	            Toast.makeText(getBaseContext(), 
        	                    R.string.NotImplemented, 
        	                    Toast.LENGTH_SHORT).show();
    					break;
    				}
    			}
    		}
    	}
    	/*
    	if(requestCode==MarkRC)
    	{
    		if(resultCode==RESULT_OK)
    		{
    			String wpt = data.getStringExtra("RESULT");
    			markWpt.setName(wpt);
    			RoutePoint ruta = app.getRoute("waypoints");
    			if(ruta==null)
    			{
    				Log.i("ERROR", this.getResources().getString(R.string.error_mark));
    	            Toast.makeText(getBaseContext(), 
    	                    R.string.error_mark, 
    	                    Toast.LENGTH_SHORT).show();
    				return;
    			}
    			ruta.addWayPoint(markWpt);
    			SaveRoute(ruta, "waypoints.gpx");
    		}    		
    	}*/
    	return;
    }
    void SaveRoute(RoutePoint ruta, String filename)
    {
		try {
			File WaypointsDir =  new File(Environment.getExternalStorageDirectory() + "/Crusoe/Waypoints");
			File gpxfile = new File(WaypointsDir, filename);
			GpxWriter csv = new GpxWriter(gpxfile);
			csv.writeHeader();
			int i=0;
			while(i<ruta.Locations().size())
			{
				csv.writeWaypoint(ruta.getWayPoint(i));
				i++;
			}
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
    			markIntent.putExtra("ACTION", WayPointsListActivity.Add);
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
					Intent routeIntent = new Intent(this, CrusoeListActivity.class);
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
    		Toast.makeText(this.getBaseContext(), 
	                "VERSION 0.1.0.1", 
	                Toast.LENGTH_LONG).show();
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

}