import org.apache.commons.lang3.tuple.Pair;

public class GribRotation {
    //Rotation code converted to Java from: https://confluence.govcloud.dk/pages/viewpage.action?pageId=76153193
    static Pair<Double, Double> getLonLat(double rot_lon, double rot_lat, float southpole_lon, float southpole_lat) {
        double to_rad = Math.PI / 180.0;
        double to_deg = 1.0 / to_rad;
        double sin_y_cen = Math.sin(to_rad * (southpole_lat + 90.0));
        double cos_y_cen = Math.cos(to_rad * (southpole_lat + 90.0));

        double sin_x_rot = Math.sin(to_rad * rot_lon);
        double cos_x_rot = Math.cos(to_rad * rot_lon);
        double sin_y_rot = Math.sin(to_rad * rot_lat);
        double cos_y_rot = Math.cos(to_rad * rot_lat);
        double sin_y_reg = cos_y_cen * sin_y_rot + sin_y_cen * cos_y_rot * cos_x_rot;
        if (sin_y_reg < -1.0) sin_y_reg = -1.0;
        if (sin_y_reg > 1.0) sin_y_reg = 1.0;

        double reg_lat = (float) to_deg * Math.asin(sin_y_reg);

        double cos_y_reg = Math.cos(reg_lat * to_rad);
        double cos_lon_rad = (cos_y_cen * cos_y_rot * cos_x_rot - sin_y_cen * sin_y_rot) / cos_y_reg;
        if (cos_lon_rad < -1.0) cos_lon_rad = -1.0;
        if (cos_lon_rad > 1.0) cos_lon_rad = 1.0;
        double sin_lon_rad = cos_y_rot * sin_x_rot / cos_y_reg;
        double lon_rad = Math.acos(cos_lon_rad);
        if (sin_lon_rad < 0.0) lon_rad = -lon_rad;

        double reg_lon = to_deg * lon_rad + southpole_lon;
        Pair<Double, Double> regLonLat = Pair.of(reg_lon, reg_lat);
        return regLonLat;
    }
}
