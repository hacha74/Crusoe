package com.muke.crusoe;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import com.muke.crusoe.gpsfile.*;
import com.muke.crusoe.gpsfile.TrackPoint.TrackSegment;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.text.format.Time;
import android.util.Log;
import android.widget.Toast;
//esta clase de llama solo una vez, a diferencia de Activity que se reinicia cuando roto el telefono
public final class CrusoeApplication extends Application implements Runnable{

	public static final String CRUSOE_LOCATION_INTENT = "com.muke.crusoe.Crusoe";
	public static enum thread_status{
		thStart,
		thRun,
		thStop				
	};
	public ArrayList<RoutePoint>routes = new ArrayList<RoutePoint>();
	//lista de waypoints
	//public ArrayList<WayPoint> waypoints = new ArrayList<WayPoint>();
	boolean bcerca=false;//indica si se encuentra cerca del waypoint
	public WayPoint gotoWpt = null;
	public RoutePoint ruta_seguir= null;//ruta a seguir
	public Location lastWpt = null;
	public TrackPoint track =  new TrackPoint();
	double recorrido=0;//distancia recorrida en mts
	public void SaveTrack()
	{
		try {
			File TracksDir =  new File(Environment.getExternalStorageDirectory() + "/Crusoe/Tracks");
	        if(!TracksDir.isDirectory())
	        	TracksDir.mkdirs();
			
	        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	        Calendar c = Calendar.getInstance();
			String dateString =  sdf.format(c.getTime());//sdf.format(now);
			
			File gpxfile = new File(TracksDir, dateString + ".gpx");
			GpxTrackWriter gpx = new GpxTrackWriter(gpxfile);
			gpx.writeHeader();
			if(gotoWpt!=null)
				gpx.writeBeginTrack(gotoWpt.getName());
			else
				gpx.writeBeginTrack("TRACK");
			for(TrackSegment s:track.Segments())
			{
				gpx.writeSegment(s);
			}
			gpx.writeEndTrack();
			gpx.writeFooter();
			gpx.close();
		}
		catch(FileNotFoundException e)
		{
			Log.i("ERROR", e.getMessage());
		}
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

	        File CrusoeDir =  new File(Environment.getExternalStorageDirectory() + "/Crusoe/Waypoints");
	        if(!CrusoeDir.isDirectory())
	        	CrusoeDir.mkdirs();
	        File []archivos = CrusoeDir.listFiles(new FileExtFilter("gpx"));
	        int f=0;
	        for(File a : archivos)
	        {
		        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
		        GpxFileContentHandler gpxFileContentHandler = new GpxFileContentHandler();
		        xmlReader.setContentHandler(gpxFileContentHandler);
	        	FileReader fileReader = new FileReader(a);
	        	InputSource inputSource = new InputSource(fileReader);          
	        	xmlReader.parse(inputSource);
	        	RoutePoint RP = new RoutePoint(a.getName().replaceAll(".gpx", ""));
	        	routes.add(RP);
	        	RP.addAll(gpxFileContentHandler.getLocationList());
	        	f +=RP.Locations().size();
	        	fileReader.close();
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

	private class CrusoeLocationListener implements LocationListener
	{
	  	boolean first_loc=true;
	  	long tinicio = 0;//tiempo inicial
	    
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
            if (first_loc==true) {
            	tinicio = System.currentTimeMillis();
                Toast.makeText(getBaseContext(), 
                    getResources().getString(R.string.gps_signal_found), 
                    Toast.LENGTH_SHORT).show();
                
            }
 
                try { 
                	if(!(lastWpt!=null))
                	{                	
                		lastWpt = loc;
                		track.AddLocation(loc);
                	}
                	else
                	{
                		double d = lastWpt.distanceTo(loc); 
                		if(d>=25.0)
                		{
                			track.AddLocation(loc);
                			recorrido +=d;
                		}
                	}
                	if(gotoWpt!=null)
                	{
                		//si se acerca a mas de 50 mts y luego se aleja 100 asumo que lleg� al Waypoint
                		float dist = gotoWpt.distanceTo(loc);
                		if(dist<100)
                			bcerca = true;
                		if(dist==0 || (dist>200 && bcerca==true))
                		{
                			gotoWpt = null;
                			bcerca = false;//lleg� y se est� alejando
                			//debo avisar que reinicialice la actividad!!
                			track.StopSegment();
                			if(ruta_seguir!=null)
                			{
                				gotoWpt = (WayPoint)ruta_seguir.Locations().toArray()[0];
                				ruta_seguir.Locations().remove(gotoWpt);
                				if(ruta_seguir.Locations().isEmpty())
                					ruta_seguir = null;//destruyo
                				track.StartSegment();
                			}
                		}
                	}
                		
                		
                	Intent t = new Intent();
                	t.setAction("com.muke.crusoe.Crusoe");
                	
                	//debo agregar a intent los datos de Location
                	if(gotoWpt!=null)
                	{
                		float distto=loc.distanceTo(gotoWpt);
                		t.putExtra("NAME", gotoWpt.getName());
                		if(distto>1000)
                		{
                			t.putExtra("DISTTO", String.format("%.2f KM", distto/1000));
                		}
                		else
                		{
                			t.putExtra("DISTTO", String.format("%d mts", (int)distto));
                		}
                	}
            		if(recorrido>1000.0)
            		{
            			//t.putExtra("DISTTO", String.format("%.2f KM", distto/1000));
                    	t.putExtra("TRAVELLED", String.format("%.2f KM", recorrido/1000));//recorrido
            		}
            		else
            		{
            			//t.putExtra("DISTTO", String.format("%d mts", (int)distto));
                    	t.putExtra("TRAVELLED", String.format("%d mts", (int)recorrido));//recorrido
            		}
                	t.putExtra("LATITUD", loc.convert(loc.getLatitude(), loc.FORMAT_SECONDS));
                	t.putExtra("LONGITUD", loc.convert(loc.getLongitude(), loc.FORMAT_SECONDS));
                	if(System.currentTimeMillis()>=tinicio)
                	{
                		long tiempo = System.currentTimeMillis() - tinicio;
                    	SimpleDateFormat sdf=null;
                    	if(tiempo>=3600)
                    		sdf = new SimpleDateFormat("HH:mm:ss");
                    	else
                    	{
                    		if(tiempo>60)
                    			sdf = new SimpleDateFormat("mm:ss");
                    		else
                    			sdf = new SimpleDateFormat("ss");
                    	}
                		t.putExtra("TRANSCURRIDO", sdf.format(new Date((long)tiempo)));
                	}
                	if(loc.getSpeed()>0)
                	{
                    	double tiempo = (double)recorrido/loc.getSpeed();
                    	SimpleDateFormat sdf=null;
                    	if(tiempo>=3600)
                    		sdf = new SimpleDateFormat("HH:mm:ss");
                    	else
                    	{
                    		if(tiempo>60)
                    			sdf = new SimpleDateFormat("mm:ss");
                    		else
                    			sdf = new SimpleDateFormat("ss");
                    	}
                		t.putExtra("ETA", sdf.format(new Date((long)tiempo*1000)));//tiempo estimado de arrivo
                	}
                	else
                		t.putExtra("ETA", "-");//tiempo estimado de arrivo
                	if(loc.hasAltitude())
                		t.putExtra("ELEVACION", loc.getAltitude());//mts sobre el nivel del mar
                	if(loc.hasAccuracy())
                		t.putExtra("ACCURACY", loc.getAccuracy());
                	if(loc.hasBearing())
                	{
                		t.putExtra("COURSE", loc.convert(loc.getBearing(), loc.FORMAT_SECONDS));//0-360. direccion actual
                		if(gotoWpt!=null)
                			t.putExtra("BEARING", loc.bearingTo(gotoWpt));//direccion al punto
                	}
                    if(loc.hasSpeed())
                    {//esta en mts/seg.
                    	double speed = loc.getSpeed()*3.6;
                    	t.putExtra("SPEED", String.format("%.2f KMh", speed));//*3600/1000 -> KM/h
                    }
                    else
                    {
                    	double speed = (loc.distanceTo(lastWpt)*3600)/(loc.getTime() - lastWpt.getTime());                    	
                    	t.putExtra("SPEED", String.format("%.2f KMh", speed));
                    }
                    t.putExtra("PROVIDER", loc.getProvider());
    				sendBroadcast(t);
                	//File root = Environment.getExternalStorageDirectory();    
                    first_loc=false;
                    lastWpt = loc;
                }
                catch(Exception e)
                {
                    Toast.makeText(getBaseContext(), 
                    		"onLocationChanged: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                }			
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
			mLocationManager.removeUpdates(mLocationListener);//corta el GPS		
	}
	@Override
	public void run() {//implements Runnable
		// TODO Auto-generated method stub
		thStatus = thread_status.thRun;
		mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		//currentLocation = mLocationManager.getCurrentLocation("gps");
		if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) //NETWORK_PROVIDER
		{
		
			
			Looper.prepare();
			thLooper = Looper.myLooper();
			
			mLocationListener = new CrusoeLocationListener();
			
			mLocationManager.requestLocationUpdates(
	                LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
			//mLocationManager.requestLocationUpdates(
	         //       LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
			// Si quiero obtener la posicion de la NET y el GPS a la vez uso las 2 llamadas a funcion.
			Looper.loop(); 
			//Looper.myLooper().quit(); 
			
		} 
		else {
			
            Toast.makeText(getBaseContext(), 
                    getResources().getString(R.string.gps_signal_not_found), 
                    Toast.LENGTH_SHORT).show();
            
		}
		
		thStatus = thread_status.thStop;
	}
}
