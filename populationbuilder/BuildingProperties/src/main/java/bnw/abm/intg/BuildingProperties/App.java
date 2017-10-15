package bnw.abm.intg.BuildingProperties;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.SchemaException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

import bnw.abm.intg.filemanager.csv.CSVWriter;
import bnw.abm.intg.filemanager.csv.abs.ABSDwellingPropertyCsv;
import bnw.abm.intg.filemanager.csv.abs.ABSStatisticalAreaCodeConverter;
import bnw.abm.intg.filemanager.csv.abs.models.DwellingType;
import bnw.abm.intg.filemanager.json.JSONWriter;
import bnw.abm.intg.filemanager.zip.Zip;
import bnw.abm.intg.util.BNWLogger;
import bnw.abm.intg.util.BNWProperties;

/**
 * @author Bhagya N. Wickramasinghe
 *
 */
public class App {
	private static final Logger logger = BNWLogger.getLogger("bnw.phd.intg.buildingproperties");

	private static Path outputFile = null;
	private static Random random = null;

	public static void main(String[] args) {
		/* Read all the properties */
		Path sa1ShapefilePath = null, buildingShapeFilesHome = null, saCodeZipFile = null, dwellingPropertyFile = null;
		String sa1FilterByProperty = null, buildingsFilterByProperty = null, saConverterReferenceCol = null,
				saConverterTargetCol = null;
		String buildingsShapeFileName = null, saCodeCsvName = null, buildingUniqueKey = null, lgaNameKey = null;
		String[] sa1FilterByValues = null, buildingsFilterByValues = null;
		BNWProperties props = null;

		if (args.length > 0) {
			try {
				props = new BNWProperties(args[0]);
			} catch (IOException e) {
				logger.log(Level.SEVERE, e.toString(), e);
			}
			// SA1 shapefile properties
			sa1ShapefilePath = props.readFileOrDirectoryPath("SA1PolygonsShapefile");
			sa1FilterByProperty = props.getProperty("FilterSA1sByProperty").trim();
			sa1FilterByValues = props.readCommaSepProperties("FilterSA1sByValues");
			props.getProperty("RefereceKeyInSA1Shapefile").trim();

			// Buildings shapefile properties
			buildingShapeFilesHome = props.readFileOrDirectoryPath("BuildingsShapeFileHome");
			buildingsShapeFileName = props.getProperty("BuildingsShapeFileName");
			buildingsFilterByProperty = props.getProperty("FilterBuildingsByProperty");
			buildingsFilterByValues = props.readCommaSepProperties("FilterBuildingsByValues");
			buildingUniqueKey = props.getProperty("BuildingUniqueKey");
			lgaNameKey = props.getProperty("AreaNameKey");

			// SA code converter properties
			saCodeZipFile = props.readFileOrDirectoryPath("SACodesZip");
			saCodeCsvName = props.getProperty("SACodesCsvInSACodesZip");
			saConverterReferenceCol = props.getProperty("ReferenceColumnHeader");
			saConverterTargetCol = props.getProperty("TargetColumnHeader");

			// ABS Dwelling property file
			dwellingPropertyFile = props.readFileOrDirectoryPath("DwellingPropertyFile");

			props.getProperty("NewForeignKeyInAddressRecord").trim();
			// tempOutputDir =
			// Paths.get(props.getProperty("TemporaryOutputDirectory").trim().equals("system")
			// ? System
			// .getProperty("java.io.tmpdir") :
			// props.readFileOrDirectoryPath("TemporaryOutputDirectory") +
			// File.separator);
			outputFile = props.readFileOrDirectoryPath("OutputFile");

			random = new Random(Long.parseLong(props.getProperty("RandomSeed")));

		} else {
			System.err.println("Give path to config.properties as the first argument");
		}

		SimpleFeatureCollection buildings = null, targetSA1s = null;
		Map<String, List<Building>> allBuildingsBySA1 = new HashMap<>();
		Map<String, List<DwellingType>> dwellTypesDistribution = null;
		Map<String, String> sacodesMap = null;
		try {
			List<Path> buildingShapeFiles = getBuildingsShapeFiles(buildingShapeFilesHome, buildingsShapeFileName);
			targetSA1s = SA1Reader.getSA1Collection(sa1ShapefilePath, sa1FilterByProperty, sa1FilterByValues);
			CoordinateReferenceSystem targetCRS = targetSA1s.getSchema().getCoordinateReferenceSystem();

			// Find the SA1 of each building
			for (Path buildingShapeFile : buildingShapeFiles) {
				buildings = Buildings.getBuildings(buildingShapeFile, buildingsFilterByProperty,
						buildingsFilterByValues, targetCRS);
				assignBuildingsToSA1s(buildings, targetSA1s, allBuildingsBySA1, buildingUniqueKey, lgaNameKey);
			}

			// Reading dwelling types distribution from file
			dwellTypesDistribution = ABSDwellingPropertyCsv.read(dwellingPropertyFile);

			// For converting 7 digit SA1 code to main code
			sacodesMap = ABSStatisticalAreaCodeConverter.loadCsvAndCreateMapWithAreaCode(saCodeZipFile, saCodeCsvName,
					saConverterReferenceCol, saConverterTargetCol);

			// Marking residential buildings and assigning dwelling properties
			assignDwellingPropertiesToBuildings(allBuildingsBySA1, dwellTypesDistribution, sacodesMap);
			saveAsJSON(allBuildingsBySA1, targetCRS);
		} catch (IOException | SchemaException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Gets a list of building shapefiles
	 * 
	 * @param shapefilesHome
	 *            Location of the building shape files. This can be a directory
	 *            or a zip file.
	 * @param shapefileInZip
	 *            Name of the shape file inside the home location
	 * @return List of Paths containing ready to read shape files
	 * @throws IOException
	 */
	static List<Path> getBuildingsShapeFiles(Path shapefilesHome, String shapefileInZip) throws IOException {
		List<Path> shapefiles = null;
		if (Files.isDirectory(shapefilesHome)) {
			Stream<Path> files = Files.list(shapefilesHome);
			shapefiles = files.map(z -> {
				try {
					return Zip.findFiles(z, shapefileInZip);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return null;
			}).flatMap(List::stream).collect(Collectors.toList());
		} else if (Files.isRegularFile(shapefilesHome)) {
			shapefiles = Zip.findFiles(shapefilesHome, shapefileInZip);
			logger.log(Level.INFO, "Found " + shapefiles.size() + " buildings shapefiles in the zip");
			logger.log(Level.INFO, shapefiles.toString());
			logger.log(Level.INFO, "Processing all files");

		}else{
			throw new FileNotFoundException(shapefilesHome.toString());
		}
		return shapefiles;
	}

	/**
	 * Find to which SA1 each building belongs to and group them by their SA1.
	 * Convert the Buildings Shapefile features to JSON Objects
	 * 
	 * @param buildings
	 *            All the buildings in the area (Shapefile feature objects)
	 * @param targetSA1s
	 *            Polygons of all SA1s in the area
	 * @param buildingsBySA1
	 *            map to hold buildings after assigning to SA1
	 * @return Map of Buildings JSON objects grouped by SA1 code
	 * @throws SchemaException
	 * @throws IOException
	 */
	static Map<String, List<Building>> assignBuildingsToSA1s(SimpleFeatureCollection buildings,
			SimpleFeatureCollection targetSA1s, Map<String, List<Building>> buildingsBySA1, String buildingUniqueKey,
			String lgaNameKey) throws SchemaException, IOException {
		// Bookkeeping
		int outsideBuildings = 0;
		SimpleFeature tempOutsideBuilding = null;
		int duplicates = 0;

		// Duplicate removal
		Map<String, SimpleFeature> existingAddresses = new HashMap<>();
		try (SimpleFeatureIterator buildingsIterator = buildings.features()) {
			String sa1code = null;
			while (buildingsIterator.hasNext()) {
				SimpleFeature building = buildingsIterator.next();

				if (existingAddresses.containsKey((String) building.getAttribute(buildingUniqueKey))) {
					duplicates++;
					continue;
				} else {
					existingAddresses.put((String) building.getAttribute(buildingUniqueKey), building);
				}

				sa1code = SA1Locater.findSA1ofBuilding(building, targetSA1s);
				if (sa1code != null) {
					Building buildingInJson = Buildings.map2POJO(building);

					if (buildingsBySA1.containsKey(sa1code)) {
						buildingsBySA1.get(sa1code).add(buildingInJson);
					} else {
						buildingsBySA1.put(sa1code, new ArrayList<>(Arrays.asList(buildingInJson)));
					}
					tempOutsideBuilding = building; // Just for extracting info
													// for logging purposes. No
													// real effect on overall
													// logic
				} else {
					outsideBuildings++; // Counting number of buildings that are
										// outside the area - to log
				}
			}
		}
		logger.log(Level.INFO,
				"Assigning buildings in " + tempOutsideBuilding.getAttribute(lgaNameKey) + " to SA1s complete");
		if (outsideBuildings > 0) {
			logger.log(Level.WARNING, "Shapes not belonging to any SA1 in "
					+ tempOutsideBuilding.getAttribute(lgaNameKey) + ": " + outsideBuildings);
		} else {
			logger.log(Level.WARNING, "Shapes not belonging to any SA1 in "
					+ tempOutsideBuilding.getAttribute(lgaNameKey) + ": " + outsideBuildings);
		}

		logger.log(Level.WARNING, "Duplicates in " + tempOutsideBuilding.getAttribute(lgaNameKey) + ": " + duplicates);

		return buildingsBySA1;
	}

	/**
	 * Assigns dwelling properties to buildings according to the distribution
	 * specified in ABS census data. We also assigns SA1_MAINCODE_2011 to each
	 * dwelling
	 * 
	 * @param buildingsBySA1
	 *            all buildings grouped by corresponding SA1
	 * @param dwellTypesBySA1
	 *            number of dwellings in a given dwelling type configuration in
	 *            each SA1
	 * @param sacodesMap
	 *            SA1 7 digit code to main code map
	 */
	static void assignDwellingPropertiesToBuildings(Map<String, List<Building>> buildingsBySA1,
			Map<String, List<DwellingType>> dwellTypesBySA1, Map<String, String> sacodesMap) {
		Map<String, Integer> requiredDwellings = new HashMap<>();
		Map<String, Integer> noBuildigSA1s = new HashMap<>();
		Map<String, Map<String, Integer>> unallocDwellTypes = new LinkedHashMap<>();

		for (String sa1code : dwellTypesBySA1.keySet()) {
			if (!buildingsBySA1.containsKey(sa1code)) {
				noBuildigSA1s.put(sa1code, dwellTypesBySA1.get(sa1code).stream().mapToInt(e -> e.dwellings()).sum());
				continue;
			}
			List<Building> dwellingsInSA1 = new ArrayList<>(buildingsBySA1.get(sa1code));
			Collections.shuffle(dwellingsInSA1, random);
			for (DwellingType dwellType : dwellTypesBySA1.get(sa1code)) {

				// Bookkeeping - place holders for recording dwelling types that
				// did not have enough buildings
				if (unallocDwellTypes.containsKey(dwellType.type())) {
					Map<String, Integer> tempMap = unallocDwellTypes.get(dwellType.type());
					int total2Alloc = tempMap.get("Total");
					tempMap.put("Total", total2Alloc + dwellType.dwellings());
					unallocDwellTypes.put(dwellType.type(), tempMap);
				} else {
					Map<String, Integer> tempMap = new HashMap<>();
					tempMap.put("Total", dwellType.dwellings());
					tempMap.put("Unallocated", 0);
					unallocDwellTypes.put(dwellType.type(), tempMap);
				}

				String[] typeNames = dwellType.type().split(":");
				int nofDwellings = dwellType.dwellings();

				// If there are not enough buildings in this SA1
				if (dwellingsInSA1.size() < nofDwellings) {

					// Bookkeeping - actually recording dwelling types that did
					// not have enough buildings
					Map<String, Integer> tempMap = unallocDwellTypes.get(dwellType.type());
					int v = tempMap.get("Unallocated");
					tempMap.put("Unallocated", v + nofDwellings - dwellingsInSA1.size());
					unallocDwellTypes.put(dwellType.type(), tempMap);

					// Bookkeeping - number of dwelling that lacking by SA1
					if (!requiredDwellings.containsKey(sa1code)) {
						requiredDwellings.put(sa1code, nofDwellings - dwellingsInSA1.size());
						nofDwellings = dwellingsInSA1.size();
					} else {
						int currentCount = requiredDwellings.get(sa1code);
						requiredDwellings.put(sa1code, currentCount + nofDwellings);
						continue;
					}
				}

				// Assigning dwelling property to selected buildings
				for (int i = 0; i < nofDwellings; i++) {
					BuildingProperty properties = dwellingsInSA1.remove(0).getProperties();
					properties.setBEDD(typeNames[0]);
					properties.setSTRD(typeNames[1]);
					properties.setTENLLD(typeNames[2]);
					properties.setCENSUS_HHSIZE(typeNames[3]);
					properties.setBUILDING_TYPE("RESIDENTIAL");
					properties.setSA1_7DIG11(sa1code);
					properties.setSA1_MAINCODE_2011(sacodesMap.get(sa1code));
				}
			}
			// We have allocated dwelling properties to the required number of
			// buildings. Assign null values to the rest of the buildings and
			// mark them NON RESIDENTIAL
			for (int i = 0; i < dwellingsInSA1.size(); i++) {
				BuildingProperty properties = dwellingsInSA1.get(i).getProperties();
				properties.setBEDD(null);
				properties.setSTRD(null);
				properties.setTENLLD(null);
				properties.setCENSUS_HHSIZE(null);
				properties.setBUILDING_TYPE("NON RESIDENTIAL");
				properties.setSA1_7DIG11(sa1code);
				properties.setSA1_MAINCODE_2011(sacodesMap.get(sa1code));
			}
		}

		// Logging SA1s that did not have enough buildings
		if (!requiredDwellings.isEmpty()) {
			logger.log(Level.WARNING,
					"Total extra dwellings in ABS data: " + requiredDwellings.values().stream().mapToInt(v -> v).sum());
			for (Entry<String, Integer> ent : requiredDwellings.entrySet()) {
				logger.log(Level.WARNING, "SA1:" + ent.getKey() + " lack:" + ent.getValue());
			}
		}

		// Logging SA1s that did not have any buildings
		if (!noBuildigSA1s.isEmpty()) {
			logger.log(Level.WARNING, "SA1s with no buildings in them: " + noBuildigSA1s.size());
			for (Entry<String, Integer> ent : noBuildigSA1s.entrySet()) {
				logger.log(Level.WARNING, "SA1:" + ent.getKey() + " required:" + ent.getValue());
			}
			logger.log(Level.WARNING, "Total affected buildings because of empty SA1s: "
					+ noBuildigSA1s.values().stream().mapToInt(v -> v).sum());
		}

		// Converting unallocated dwelling type data to a list and saving to a
		// csv
		if ((!unallocDwellTypes.isEmpty())) {
			logger.log(Level.WARNING, "Unallocated dwelling types are written to ./Unallocated.csv");
			List<List<String>> unallocatedList = new ArrayList<>();
			unallocatedList.add(Arrays.asList("Dwelling Type", "Total", "Unallocated"));
			for (Entry<String, Map<String, Integer>> dwellingName : unallocDwellTypes.entrySet()) {
				List<String> unallocEntry = new ArrayList<>();
				unallocEntry.add(dwellingName.getKey());
				unallocEntry.add(String.valueOf(dwellingName.getValue().get("Total")));
				unallocEntry.add(String.valueOf(dwellingName.getValue().get("Unallocated")));
				unallocatedList.add(unallocEntry);
			}
			try {
				new CSVWriter().writeAsCsv(Files.newBufferedWriter(Paths.get("Unallocated.csv")), unallocatedList);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		if (requiredDwellings.isEmpty() && noBuildigSA1s.isEmpty()) {
			logger.log(Level.INFO, "All SA1s have required number of buildings");
		}
	}

	/**
	 * Saves building JSON object to a file
	 * 
	 * @param buildingsBySA1
	 *            Map of all building json objects by SA1
	 * @throws JsonGenerationException
	 * @throws JsonMappingException
	 * @throws IOException
	 */
	static void saveAsJSON(Map<String, List<Building>> buildingsBySA1, CoordinateReferenceSystem crs)
			throws JsonGenerationException, JsonMappingException, IOException {
		System.out.print("Saving builings as a JSON.... ");
		class JsonPOJO {
			List<Building> features;

			public List<Building> getFeatures() {
				return features;
			}

			public void setFeatures(List<Building> features) {
				this.features = features;
			}
		}
		Map<String, Object> jsonStructure = new HashMap<>();
		jsonStructure.put("features",
				buildingsBySA1.values().stream().flatMap(List::stream).collect(Collectors.toList()));
		jsonStructure.put("wkt", crs.toWKT());
		JsonPOJO jsonPOJO = new JsonPOJO();
		jsonPOJO.setFeatures(new ArrayList(buildingsBySA1.values()));
		JSONWriter jsonw = new JSONWriter();
		jsonw.writeToJsonGzFile(jsonStructure, outputFile);
		System.out.println("Complete!");
		logger.log(Level.INFO, "JSON file successfully saved to: " + outputFile);

	}
}

class Unallocated {
	String dwellingType;
	int total;
	int unallocated;

	public Unallocated(String dwellingType, int total, int unallocated) {
		this.dwellingType = dwellingType;
		this.total = total;
		this.unallocated = unallocated;
	}
}