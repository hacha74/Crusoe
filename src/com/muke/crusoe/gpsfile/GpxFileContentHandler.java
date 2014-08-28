package com.muke.crusoe.gpsfile;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import android.location.Location;

public class GpxFileContentHandler implements ContentHandler {
    private String currentValue;
    private WayPoint location;
    private List<WayPoint> locationList;
    private final SimpleDateFormat GPXTIME_SIMPLEDATEFORMAT = new SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss'Z'");

    public GpxFileContentHandler() {
        locationList = new ArrayList<WayPoint>();
    }

    public List<WayPoint> getLocationList() {
        return locationList;
    }

    @Override
    public void startElement(String uri, String localName, String qName,
        Attributes atts) throws SAXException {

        if (localName.equalsIgnoreCase("trkpt")) {
            //location = new WayPoint("", "gpxImport");
            //location.setLatitude(Double.parseDouble(atts.getValue("lat").trim()));
            //location.setLongitude(Double.parseDouble(atts.getValue("lon").trim()));
        }
        if (localName.equalsIgnoreCase("wpt")) {
            location = new WayPoint("", "gpxImport");//name, provider
            location.setLatitude(Double.parseDouble(atts.getValue("lat").trim()));         
            location.setLongitude(Double.parseDouble(atts.getValue("lon").trim()));
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
        throws SAXException {
        if (localName.equalsIgnoreCase("ele")) {
            location.setAltitude(Double.parseDouble(currentValue.trim()));
        }

        if (localName.equalsIgnoreCase("time")) {
            try {
            Date date = GPXTIME_SIMPLEDATEFORMAT.parse(currentValue.trim());
            Long time = date.getTime();
            location.setTime(time);
            } catch (ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        if(localName.equalsIgnoreCase("name"))
        {
        	location.setName(currentValue.trim());
        }

        //if (localName.equalsIgnoreCase("trkpt")) {
        //    locationList.add(location);
        //}
        if (localName.equalsIgnoreCase("wpt")) {
        	locationList.add(location);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length)
        throws SAXException {
        currentValue = new String(ch, start, length);
    }

    @Override
    public void startDocument() throws SAXException {
        // TODO Auto-generated method stub
    }

    @Override
    public void endDocument() throws SAXException {
        // TODO Auto-generated method stub
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        // TODO Auto-generated method stub
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length)
        throws SAXException {
        // TODO Auto-generated method stub
    }

    @Override
    public void processingInstruction(String target, String data)
        throws SAXException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        // TODO Auto-generated method stub
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
        // TODO Auto-generated method stub
    }

    @Override
        public void startPrefixMapping(String prefix, String uri)
            throws SAXException {
            // TODO Auto-generated method stub
    }

}