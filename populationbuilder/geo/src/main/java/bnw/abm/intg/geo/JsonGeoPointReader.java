package bnw.abm.intg.geo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.feature.FeatureCollection;
import org.geotools.util.UnsupportedImplementationException;

import bnw.abm.intg.filemanager.json.JSONReadable;

/**
 * @author Bhagya N. Wickramasinghe
 *
 */
public class JsonGeoPointReader extends GeoFeatureReader {
	private JSONReadable jsonReader;

	public JsonGeoPointReader(JSONReadable jsonReader) {
		this.jsonReader = jsonReader;

	}
	
	public void loadFeaturesByProperty(Path input, String property, String[] propertyValues){
		throw new UnsupportedImplementationException("Function not implemented");
	}

	public void loadFeatures(Path input) throws IOException {
		Map jsonMap = jsonReader.readJSON(input);
		List<Object> features = (List) jsonMap.get("features");;
		for (Object feature : features) {
			HashMap mFeature = (HashMap) feature;
			HashMap geom = (HashMap) mFeature.get("geometry");
			HashMap<String, Double> tmpCoords = new HashMap<String, Double>(2);
			tmpCoords.put("lat", (Double) ((ArrayList) geom.get("coordinates")).get(0));
			tmpCoords.put("lon", (Double) ((ArrayList) geom.get("coordinates")).get(1));

			mFeature.put("coordinates", tmpCoords);
			mFeature.remove("geometry");
		}
		featureCollection = (FeatureCollection) features; // This won't work
	}

}
