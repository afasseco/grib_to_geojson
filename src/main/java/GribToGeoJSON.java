import org.apache.commons.lang3.tuple.Pair;
import ucar.nc2.grib.GdsHorizCoordSys;
import ucar.nc2.grib.grib1.*;
import ucar.nc2.grib.grib1.tables.Grib1Customizer;
import ucar.unidata.geoloc.projection.RotatedLatLon;
import ucar.unidata.io.RandomAccessFile;

import java.io.*;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class GribToGeoJSON {
    static double bbox[] = new double[]{11.560181, 54.714023, 13.563198, 56.717037};

    static boolean isInbbox(double lon, double lat) {
        return (lon >= bbox[0] && lon <= bbox[2] && lat >= bbox[1] && lat <= bbox[3]);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Usage: java GribToGeoJSON <gribfile> <../grib1/2.94.253.table> <output.geojson>");
        }

        String filename = args[0];
        String localGribDefinitionFile = args[1];
        String geoJsonFile = args[2];

        convertLocalDefinitionsToEcmwfFormat(localGribDefinitionFile); //Writes to temporary file local_table_2_.temp

        List<HashMap<String, Object>> points = new ArrayList<>();
        RandomAccessFile raf = new RandomAccessFile(filename, "r");
        Formatter formatter = new Formatter(System.out);
        Grib1RecordScanner reader = new Grib1RecordScanner(raf);
        ucar.nc2.grib.grib1.tables.Grib1ParamTables.addParameterTable(94, 255, 253, "local_table_2_.temp");
        while (reader.hasNext()) {
            // Iterate over each parameter and figure out if this should be included
            ucar.nc2.grib.grib1.Grib1Record gr1 = reader.next();
            Grib1Gds gds = gr1.getGDS();
            Grib1Customizer grib1Customizer = Grib1Customizer.factory(gr1, null);
            Grib1SectionProductDefinition pdSsection = gr1.getPDSsection();

            Grib1Parameter parameter = grib1Customizer.getParameter(pdSsection.getCenter(), pdSsection.getSubCenter(), pdSsection.getTableVersion(), pdSsection.getParameterNumber());
            Grib1ParamLevel plevel = grib1Customizer.getParamLevel(pdSsection);

            boolean shouldIncludeParameter = parameter.getName().equals("T") && plevel.getValue1() == 2 && plevel.getNameShort().equals("height_above_ground");
            if (!shouldIncludeParameter) {
                continue;
            }
            pdSsection.showPds(grib1Customizer, formatter); //Show information about this layer

            GdsHorizCoordSys gdsHorizCoordSys = gds.makeHorizCoordSys();

            float[] data = gr1.readData(raf);

            int elementNumber = 0;
            //Find pole coordinates around which coordinates should be rotated
            float lonpole = (float) ((RotatedLatLon) gdsHorizCoordSys.proj).getLonpole();
            float latpole = (float) ((RotatedLatLon) gdsHorizCoordSys.proj).findProjectionParameter("grid_south_pole_latitude").getNumericValue();

            for (int y = 0; y < gdsHorizCoordSys.ny; y++) {
                double la = gdsHorizCoordSys.starty + y * gdsHorizCoordSys.dy;
                for (int x = 0; x < gdsHorizCoordSys.nx; x++) {
                    float parameterValue = data[elementNumber];
                    boolean hasElement = points.size() > elementNumber;
                    HashMap<String, Object> point; //Build a hashmap for each point in grid that stores coordinates and relevant data
                    if (hasElement) {
                        point = points.get(elementNumber);
                    } else { //Does not have element yet, create and save it
                        point = new HashMap<>();
                        double lo = gdsHorizCoordSys.startx + x * gdsHorizCoordSys.dx;
                        Pair<Double, Double> southPoleLat = GribRotation.getLonLat(lo, la, lonpole, latpole); //Rotate point to correct coordinates
                        point.put("lon", southPoleLat.getLeft()); //Store coordinates
                        point.put("lat", southPoleLat.getRight());
                        points.add(elementNumber, point);
                    }
                    point.put(parameter.getName(), parameterValue); //Store parameter data
                    elementNumber++;
                }
            }
        }
        raf.close();

        //Now build output from the list of points
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(geoJsonFile))) {
            bufferedWriter.write("{\n" +
                                     "  \"type\": \"FeatureCollection\",\n" +
                                     "  \"features\": [");
            boolean isFirst = true;
            for (HashMap<String, Object> point : points) {
                boolean shouldInclude = isInbbox((Double) point.get("lon"), (Double) point.get("lat"));
                if (shouldInclude) {
                    if (!isFirst) {
                        bufferedWriter.write(",");
                    }
                    isFirst = false;

                    bufferedWriter.write("{\n" +
                            "      \"geometry\": {\n" +
                            "        \"coordinates\": [\n" +
                            "          " + point.get("lon").toString() + ",\n" +
                            "          " + point.get("lat").toString() + "\n" +
                            "        ],\n" +
                            "        \"type\": \"Point\"\n" +
                            "      },\n" +
                            "      \"type\": \"Feature\",\n" +
                            "    \"properties\":{");

                    //No longer needed - otherwise these would become properties as well
                    point.remove("lon");
                    point.remove("lat");

                    //Write out rest of hashmap as properties
                    List<String> properties = point.entrySet().stream().map(property -> "\"" + property.getKey() + "\"" + ":" + "" + property.getValue()).collect(Collectors.toList());
                    bufferedWriter.write(String.join(",", properties));
                    bufferedWriter.write(
                            "    }\n" +
                                "  }\n");
                }
            }
            bufferedWriter.write("  ]\n" +
                                     "}");
        }

    }

    // Function to do a quick conversion from the format that the local ec codes are in to the format that ucar grib code understands
    private static void convertLocalDefinitionsToEcmwfFormat(String localGribDefinitionFile) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(localGribDefinitionFile));
             BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter("local_table_2_.temp"))
        ) {
            bufferedWriter.write("temp file for parameter names");
            bufferedWriter.write("\n");
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                try {
                    int i = line.indexOf(' ');
                    String num = line.substring(0, i);
                    line = line.substring(i + 1);

                    i = line.indexOf(' ');
                    String smallName = line.substring(0, i);
                    line = line.substring(i + 1);

                    i = line.indexOf(' ');
                    String name = line.substring(0, i);
                    line = line.substring(i + 1);

                    i = line.lastIndexOf(' ');
                    String description = line.substring(0, i);
                    String unit = line.substring(i + 1);

                    bufferedWriter.write("......................");
                    bufferedWriter.write("\n");
                    bufferedWriter.write(num);
                    bufferedWriter.write("\n");
                    bufferedWriter.write(name);
                    bufferedWriter.write("\n");
                    bufferedWriter.write(description);
                    bufferedWriter.write("\n");
                    bufferedWriter.write(unit);
                    bufferedWriter.write("\n");
                } catch (StringIndexOutOfBoundsException e) {
                    System.err.println("Could not parse " + line); //Last line does not conform to format, so just skip
                }
            }
        }
    }
}
