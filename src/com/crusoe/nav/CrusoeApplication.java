package com.crusoe.nav;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import com.crusoe.gpsfile.*;
import com.crusoe.gpsfile.TrackPoint.TrackSegment;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.support.v4.app.*;
import android.text.format.Time;
import android.util.Log;
import android.widget.Chronometer;
import android.widget.Toast;
//esta clase de llama solo una vez, a diferencia de Activity que se reinicia cuando roto el telefono
public final class CrusoeApplication extends Application implements Runnable{

	public static final String CRUSOE_LOCATION_INTENT = "com.crusoe.nav.Location";
	public static final String CRUSOE_GPS_STATUS_INTENT = "com.crusoe.nav.GpsStatus";
	public static final int CRUSOE_LOCATION_NOTIFICATION = 1;
	public static enum thread_status{
		thStart,
		thRun,
		thStop				
	};
	public ArrayList<RoutePoint>routes = new ArrayList<RoutePoint>();
	//lista de waypoints
	//public ArrayList<WayPoint> waypoints = new ArrayList<WayPoint>();
  	boolean first_loc=true;
  	long tinicio = 0;//tiempo inicial
	boolean bcerca=false;//indica si se encuentra cerca del waypoint
	public StatWpt gotoWpt = null;//wpt a donde me dirijo
	public WayPoint closeWpt = null;//waypoint cercano
	//public RoutePoint ruta_seguir= null;//ruta a seguir
	public ArrayList<StatWpt>ruta_seguir = new ArrayList<StatWpt>();
	public String active_route="";
	public int ruta_offset=0;//indice del waypoint actual de la ruta
	public Location lastWpt = null;
	public TrackPoint track =  new TrackPoint();
	double recorrido=0;//distancia recorrida en mts

	public String cfg_metric = "";//KM, Nautic
	public String cfg_maps = "";//Offline, Online
	public int map_src = CrusoeNavActivity.MAPQUESTOSM;
	public int map_zoom = 15;
	  
	public int SaveTrack()
	{
		int locs=0;
		try {
			File TracksDir =  new File(Environment.getExternalStorageDirectory() + getBaseContext().getString(R.string.track_dir));
	        if(!TracksDir.isDirectory())
	        	TracksDir.mkdirs();
			
	        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	        Calendar c = Calendar.getInstance();
			String dateString =  sdf.format(c.getTime());//sdf.format(now);
			
			File gpxfile = new File(TracksDir, dateString + ".gpx");
			GpxTrackWriter gpx = new GpxTrackWriter(gpxfile);
			gpx.writeHeader();
			if(track.WayPoints().size()!=0)
			{
				for(WayPoint w:track.WayPoints())
				{
					gpx.writeWaypoint(w);
				}
			}
			if(gotoWpt!=null)
				gpx.writeBeginTrack(gotoWpt.getName());
			else
				gpx.writeBeginTrack("TRACK");
			for(TrackSegment s:track.Segments())
			{
				locs +=gpx.writeSegment(s);
			}
			gpx.writeEndTrack();
			gpx.writeFooter();
			gpx.close();
		}
		catch(FileNotFoundException e)
		{
			Log.i("ERROR", e.getMessage());
		}
		return locs;
	}
	public void CargoWayPoints()
	{
	    try {
	    	if(routes.size()!=0)
	    		return;//ya los cargo
	    	
	        System.setProperty("org.xml.sax.driver","org.xmlpull.v1.sax2.Driver");
	        /*
	        SAXParserFactory spf = SAXParserFactory.newInstance();
	        spf.setNamespaceAware(true);
	        spf.setValidating(false);
	        SAXParser saxParser = spf.newSAXParser();
	        XMLReader xmlReader = saxParser.getXMLReader();
	        */      

	        File CrusoeDir =  new File(Environment.getExternalStorageDirectory() + getBaseContext().getString(R.string.wpt_dir));
	        if(!CrusoeDir.isDirectory())
	        {
	        	//es la primera vez. Copio riodelaplata.gpx
	        	CrusoeDir.mkdirs();
	        	InputStream rawInputStream = getResources().openRawResource(R.raw.riodelaplata);
	            File file = new File(CrusoeDir.getAbsolutePath(), "riodelaplata.gpx");
	            OutputStream output = new FileOutputStream(file);
                byte[] buffer = new byte[4 * 1024]; // or other buffer size
                int read;

                while ((read = rawInputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.flush();
                output.close();
	        	rawInputStream.close();
	        }
	        File []archivos = CrusoeDir.listFiles(new FileExtFilter("gpx"));
	        int f=0;
	        boolean wptfile=false;
	        RoutePoint waypoints = new RoutePoint("WAYPOINTS");//lista de waypoints no pertenecientes a una ruta
	        routes.add(waypoints);
	        for(File a : archivos)
	        {
		        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
		        GpxFileContentHandler gpxFileContentHandler = new GpxFileContentHandler();
		        xmlReader.setContentHandler(gpxFileContentHandler);
	        	FileReader fileReader = new FileReader(a);
	        	InputSource inputSource = new InputSource(fileReader);          
	        	xmlReader.parse(inputSource);
        		
	        	if(a.getName().toUpperCase().contains("WAYPOINTS.GPX"))
        			wptfile = true;

        		if(gpxFileContentHandler.getLocationList().size()>0)
	        	{
	        		//RoutePoint RP = new RoutePoint(a.getName().replaceAll(".gpx", ""));
	        		//routes.add(RP);
	        		waypoints.addAll(gpxFileContentHandler.getLocationList());
	        		f +=gpxFileContentHandler.getLocationList().size();
	        	}
	        	if(gpxFileContentHandler.getRouteList().size()>0)
	        	{
	        		routes.addAll(gpxFileContentHandler.getRouteList());
	        	}
	        	fileReader.close();
	        }
	        if(wptfile==false)
	        {
	        	//creo archivo
		        File W =  new File(Environment.getExternalStorageDirectory() + getBaseContext().getString(R.string.wpt_dir) + "waypoints.gpx");
		        W.createNewFile();
	        }
        	Log.i("TAG", String.format("%d waypoints leidos", f));
	    }catch (SAXException e) {
	    	// TODO Auto-generated catch block
	    	Log.i("ERROR", e.getMessage());
	    } catch (IOException e) {
	    	// TODO Auto-generated catch block
	    	Log.i("ERROR", e.getMessage());
	    }
	}
	public String Distance(float d)
	{
		String dst = "";
		if(cfg_metric.equalsIgnoreCase(getBaseContext().getString(R.string.cfg_km)))
		{
			if(d>1000)
			{	
				dst = String.format(Locale.US, "%.2f KM", d/1000);
			}
			else
			{
				dst = String.format("%d mts", (int)d);
			}
		}
		else
		{
			dst = String.format(Locale.US, "%.2f NM", d/1852);//1NM=1.852m
		}
		return dst;
	}
	public static ArrayList<RoutePoint> CargoWayPoints(String archivo)
	{
	    try {
	    	
	        System.setProperty("org.xml.sax.driver","org.xmlpull.v1.sax2.Driver");
	        /*
	        SAXParserFactory spf = SAXParserFactory.newInstance();
	        spf.setNamespaceAware(true);
	        spf.setValidating(false);
	        SAXParser saxParser = spf.newSAXParser();
	        XMLReader xmlReader = saxParser.getXMLReader();
	        */      
	        	ArrayList<RoutePoint> R = new ArrayList<RoutePoint>();
	        	File a = new File(archivo);
	        	int f=0;
		        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
		        GpxFileContentHandler gpxFileContentHandler = new GpxFileContentHandler();
		        xmlReader.setContentHandler(gpxFileContentHandler);
	        	FileReader fileReader = new FileReader(a);
	        	InputSource inputSource = new InputSource(fileReader);          
	        	xmlReader.parse(inputSource);
	        	if(gpxFileContentHandler.getLocationList().size()>0)
	        	{
	        		RoutePoint RP = new RoutePoint(a.getName().replaceAll(".gpx", ""));
	        		R.add(RP);
	        		RP.addAll(gpxFileContentHandler.getLocationList());
	        		f +=RP.Locations().size();
	        	}
	        	if(gpxFileContentHandler.getRouteList().size()>0)
	        	{
	        		R.addAll(gpxFileContentHandler.getRouteList());
	        	}
	        	fileReader.close();
        	Log.i("TAG", String.format("%d waypoints leidos", f));
        	return R;
	    }catch (SAXException e) {
	    	// TODO Auto-generated catch block
	    	Log.i("ERROR", e.getMessage());
	    } catch (IOException e) {
	    	// TODO Auto-generated catch block
	    	Log.i("ERROR", e.getMessage());
	    }
	    return null;
	}
	public WayPoint CloseWpt(float dist)
	{
		WayPoint wpt = null;
		for(RoutePoint r: routes)
		{
			for(WayPoint w : r.Locations())
			{
				float d = lastWpt.distanceTo(w);
				if(d<dist)
				{
					dist = d;
					wpt = w;
				}
			}
		}
		return wpt;
	}
	public int Closest(RoutePoint ruta, WayPoint wpt)
	{
		int i=0, pos=0;
		WayPoint w = (WayPoint)ruta.Locations().toArray()[i++];
		float dist = wpt.distanceTo(w);
		while(i<ruta.Locations().size())
		{
			w = (WayPoint)ruta.Locations().toArray()[i++];
			float d = wpt.distanceTo(w);
			if(d<dist)
				pos=i-1;		
		}
		return pos;
	}
	
	public WayPoint getWayPoint(String n)
	{
		for(RoutePoint r: routes)
		{
			for(WayPoint w: r.Locations())
			{
				if(n.compareTo(w.getName())==0)
				{
					return w;
				}
			}
		}
		return null;
	}
	public String getLocationNames()
	{
		String res=";";

		for(RoutePoint r: routes)
		{
			for(WayPoint w: r.Locations())
			{
				if(!res.contains(";" + w.getName() + ";"))
					res = res + w.getName() + ";";
			}
		}
		res = res.substring(1, res.length());
		return res;
	}
	public RoutePoint getRoute(String n)
	{
		for(RoutePoint r: routes)
		{
			if(r.getName().equalsIgnoreCase(n))
				return r;
		}
		return null;
	}
	thread_status thStatus = thread_status.thStart;
	Thread thread = null;
	Looper thLooper = null;
	@Override
	public void onCreate()
	{
		super.onCreate();
	}
	@Override
	public void onTerminate()
	{//no es el fin de la aplicacion. nose para que mierda es
		super.onTerminate();
		RemoveLocationListener();
	}
	public thread_status getThreadStatus()
	{
		return thStatus;
	}
	public Thread getThread()
	{
		return thread;
	}
	public Looper getLooper()
	{
		return thLooper;
	}
	public void StartThread()
	{
		thread = new Thread(this);
		thread.start();
	}
	void sendNotification(WayPoint wpt)
	{
		//http://developer.android.com/training/notify-user/build-notification.html
		Intent resultIntent = new Intent(getApplicationContext(), CrusoeNavActivity.class);
		// Because clicking the notification opens a new ("special") activity, there's
		// no need to create an artificial back stack.
		PendingIntent resultPendingIntent =
		    PendingIntent.getActivity(
		    getApplicationContext(),
		    0,
		    resultIntent,
		    PendingIntent.FLAG_UPDATE_CURRENT
		);                				
		long[] mVibratePattern = { 0, 200, 200, 300 };
		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
				getApplicationContext())
		.setContentTitle(getBaseContext().getString(R.string.app_name))
		.setContentText(getResources().getString(R.string.msg_approach) + wpt.getName())
		.setSmallIcon(R.drawable.crusoesail)
		.setAutoCancel(true)
		.setContentIntent(resultPendingIntent)
		.setVibrate(mVibratePattern);

		// Pass the Notification to the NotificationManager:
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.notify(CRUSOE_LOCATION_NOTIFICATION,
				notificationBuilder.build());
	}
	private class CrusoeLocationListener implements LocationListener, GpsStatus.Listener
	{
	    
		@Override
		public void onLocationChanged(Location loc) {
        	/*
        	 * TODO
        	 * Calcular:
        	 * Nombre del Waypoint 
        	 * Latitud, Longitud, Elevacion
        	 * Velocidad
        	 * Direccion actual, Direccion al Waypoint
        	 * Distancia recorrida, Distancia al Waypoint
        	 * Provider
        	 * Hora de Inicio, Hora actual
        	 * Tiempo de tracking, tiempo estimado de llegada
        	 */
			if(loc==null)
				return;
 
                try { 
                    if (first_loc==true) {
                    	tinicio = System.currentTimeMillis();
                        Toast.makeText(getBaseContext(), 
                            getResources().getString(R.string.gps_signal_found), 
                            Toast.LENGTH_SHORT).show();
                    }
                    double rec = 0;
                	if(!(lastWpt!=null))
                	{    //guardo primer wpt            	
                		lastWpt = loc;
                		track.LastSegment().AddLocation(loc);
                	}
                	else
                	{
                		rec = lastWpt.distanceTo(loc); 
                		//if(rec>=25.0)
                		if((loc.getAccuracy()<lastWpt.getAccuracy()) ||(rec>=lastWpt.getAccuracy()))
                		{	// si veo que los wpt no estan dentro de su Accuracy los guardo
                			track.LastSegment().AddLocation(loc);
                			recorrido +=rec;
                			if(recorrido<0)recorrido=0;
                            lastWpt = loc;
                            if(closeWpt==null)
                            {	//busco un wpt cercano
                            	closeWpt = CloseWpt(100);
                            	if(closeWpt!=null)
                            		sendNotification(closeWpt);
                            }
                            else
                            	closeWpt = CloseWpt(200);
                            	
                		}
                		else
                			rec=0;
                	}
                	if(gotoWpt!=null)
                	{
                		//si se acerca a mas de 50 mts y luego se aleja 100 asumo que lleg� al Waypoint
                		float dist = gotoWpt.getWpt().distanceTo(loc);
                		gotoWpt.addDist((float)rec);
                		if(dist<100)
                		{
                			if(!bcerca)
                			{
        			            //Toast.makeText(getBaseContext(), 
        			            //        R.string.msg_approach + gotoWpt.getName(), 
        			            //        Toast.LENGTH_SHORT).show();
                				sendNotification(gotoWpt.getWpt());
                				
                			}
                			bcerca = true;
                		}
                		if((dist<loc.getAccuracy() && dist<20) || (dist>200 && bcerca==true))
                		{
                			gotoWpt = null;
                			bcerca = false;//lleg� y se est� alejando
                			//debo avisar que reinicialice la actividad!!
                			track.StopSegment();
                			if(ruta_seguir!=null)
                			{
                				if(ruta_offset<ruta_seguir.size())
                				{
                					 ((StatWpt)ruta_seguir.get(ruta_offset++)).Terminado(true);
                					gotoWpt = (StatWpt)ruta_seguir.toArray()[ruta_offset];
                				}
                				//ruta_seguir.Locations().remove(gotoWpt);
                				//if(ruta_seguir.Locations().isEmpty())
                				if(ruta_offset>=ruta_seguir.size())
                				{//termino la ruta
                					ruta_seguir = null;//destruyo
                					ruta_offset=0;
                					active_route="";
                					gotoWpt=null;
                				}
                				track.StartSegment();
                			}
                		}
                	}
                		
                	sendMessage(loc);
                	//File root = Environment.getExternalStorageDirectory();    
                    first_loc=false;
                }
                catch(IllegalArgumentException a)
                {
                	Toast.makeText(getBaseContext(), 
                			"Illegal Argument " + 
                					String.format("%.2f %.2f %d", loc.getLatitude(),loc.getLongitude(), recorrido), 
                			Toast.LENGTH_LONG).show();
                }
                catch(Exception e)
                {
                    Toast.makeText(getBaseContext(), 
                    		"onLocationChanged: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                }			
		}
		void sendMessage(Location loc)
		{
        	Intent t = new Intent();
        	t.setAction(CrusoeApplication.CRUSOE_LOCATION_INTENT);
        	
        	//debo agregar a intent los datos de Location
        	if(gotoWpt!=null)
        	{
        		float distto=loc.distanceTo(gotoWpt.getWpt());
        		t.putExtra("NAME", gotoWpt.getName());
				t.putExtra("DISTTO", Distance(distto));//1NM=1.852m
        	}
			t.putExtra("TRAVELLED", Distance((float)recorrido));//recorrido
        	t.putExtra("LATITUD", loc.getLatitude());//loc.convert(loc.getLatitude(), loc.FORMAT_MINUTES));
        	t.putExtra("LONGITUD", loc.getLongitude());//loc.convert(loc.getLongitude(), loc.FORMAT_MINUTES));
        	if(System.currentTimeMillis()>=tinicio)
        	{
        		long tiempo = System.currentTimeMillis() - tinicio;
        		if(tiempo<0)
        		{
        			tinicio = System.currentTimeMillis();
        			tiempo=0;
        		}
        		Chronometer sdf = new Chronometer(getApplicationContext());
        		sdf.setFormat("HH:mm:ss");
        		sdf.setBase(tiempo);
            	/*SimpleDateFormat sdf=null;
        		sdf = new SimpleDateFormat("HH:mm:ss 'GMT'", Locale.US);
            	if(tiempo>=3600000L)
            		sdf = new SimpleDateFormat("HH:mm:ss");
            	else
            	{
            		if(tiempo>60)
            			sdf = new SimpleDateFormat("mm:ss");
            		else
            			sdf = new SimpleDateFormat("ss");
            	}
        		t.putExtra("TRANSCURRIDO", sdf.format(new Date((long)tiempo)));*/
        		t.putExtra("TRANSCURRIDO", tiempo);
        	}
        	if(loc.getSpeed()>0)
        	{
            	double eta = (double)recorrido/loc.getSpeed();
            	SimpleDateFormat sdf=null;
            	if(eta>=3600)
            		sdf = new SimpleDateFormat("HH:mm:ss");
            	else
            	{
            		if(eta>60)
            			sdf = new SimpleDateFormat("mm:ss");
            		else
            			sdf = new SimpleDateFormat("ss");
            	}
        		t.putExtra("ETA", sdf.format(new Date((long)eta*1000)));//tiempo estimado de arrivo
        	}
        	else
        		t.putExtra("ETA", "-");//tiempo estimado de arrivo
        	if(loc.hasAltitude())
        		t.putExtra("ELEVACION", loc.getAltitude());//mts sobre el nivel del mar
        	if(loc.hasAccuracy())
        		t.putExtra("ACCURACY", loc.getAccuracy());
        	if(loc.hasBearing())
        	{
        		t.putExtra("COURSE", loc.getBearing());//0-360. direccion actual
        		if(gotoWpt!=null)
        			t.putExtra("BEARING", loc.bearingTo(gotoWpt.getWpt()));//direccion al punto
        	}
        	double speed=0;
            if(loc.hasSpeed())
            {//esta en mts/seg.            	
            	speed = loc.getSpeed()*3.6;
            }
            else
            {
            	speed = ((lastWpt.distanceTo(loc))*3.6)/(loc.getTime() - lastWpt.getTime());                    	
            }
        	if(cfg_metric.equalsIgnoreCase(getBaseContext().getString(R.string.cfg_km)))
        	{
        		t.putExtra("SPEED", String.format(Locale.US, "%.2f", speed));//*3600/1000 -> KM/h
        		t.putExtra("SPD_UNIT", getBaseContext().getString(R.string.cfg_kmh));//*3600/1000 -> KM/h
        	}
        	else
        	{
        		t.putExtra("SPEED", String.format(Locale.US, "%.2f", speed/1.852));//*3600/1000 -> KM/h
        		t.putExtra("SPD_UNIT", getBaseContext().getString(R.string.cfg_knots));//*3600/1000 -> KM/h
        	}
            t.putExtra("PROVIDER", loc.getProvider());
			sendBroadcast(t);
		}
		@Override
		public void onProviderDisabled(String provider) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onProviderEnabled(String provider) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			// TODO Auto-generated method stub
			
		}
		@Override
		public void onGpsStatusChanged(int event) {
			// TODO Auto-generated method stub
			GpsStatus gpsStatus = mLocationManager.getGpsStatus(null);
	        if(gpsStatus != null) {
	            Iterable<GpsSatellite>satellites = gpsStatus.getSatellites();
	            Iterator<GpsSatellite>sat = satellites.iterator();
	            int i=0;
	            if(sat.hasNext())
	            {
	            	String strGpsStats="";//lo envio como mensaje a gpsfragment
	            	while (sat.hasNext()) {
	            		GpsSatellite satellite = sat.next();
	            		//sgptrGpsStats+= (i++) + ": " + satellite.getPrn() + "," + satellite.usedInFix() + "," + satellite.getSnr() + "," + satellite.getAzimuth() + "," + satellite.getElevation()+ "\n\n";
	            		strGpsStats +=satellite.getSnr() + ";";
	            	}
	            	Intent t = new Intent();
	            	t.setAction(CrusoeApplication.CRUSOE_GPS_STATUS_INTENT);
	            	t.putExtra("STATUS", strGpsStats);
	            	sendBroadcast(t);
	            }
	        }         			
		}
	
	};
	LocationManager mLocationManager = null;//maneja el gps
	//Location mLocation = null;//representacion geografica del gps. Latitud, longitud, altura
	CrusoeLocationListener mLocationListener;//evento de localizacion
	public LocationManager getLocationManager()
	{
		return mLocationManager;
	}
	public void RemoveLocationListener()
	{
		if(mLocationManager!=null)
		{
			mLocationManager.removeUpdates(mLocationListener);//corta el GPS
			mLocationManager.removeGpsStatusListener(mLocationListener);
		}
	}
	@Override
	public void run() {//implements Runnable
		// TODO Auto-generated method stub
		thStatus = thread_status.thRun;
		mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		//currentLocation = mLocationManager.getCurrentLocation("gps");
		Looper.prepare();
		if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) //NETWORK_PROVIDER
		{		
			
			thLooper = Looper.myLooper();
			
			mLocationListener = new CrusoeLocationListener();
			
			mLocationManager.requestLocationUpdates(
	                LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
			mLocationManager.addGpsStatusListener(mLocationListener);
			//mLocationManager.requestLocationUpdates(
	         //       LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
			// Si quiero obtener la posicion de la NET y el GPS a la vez uso las 2 llamadas a funcion.
			Looper.loop(); 
			//Looper.myLooper().quit(); 
			
		} 
		else {
			
            Toast.makeText(getBaseContext(), 
                    getResources().getString(R.string.gps_disable), 
                    Toast.LENGTH_SHORT).show();
            
		}
		
		thStatus = thread_status.thStop;
	}
}
