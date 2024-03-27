package org.mapfish.print;

import java.awt.Rectangle;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.Position2D;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.mapfish.print.attribute.map.GenericMapAttribute;
import org.mapfish.print.attribute.map.MapBounds;
import org.geotools.api.referencing.operation.MathTransform;
import org.locationtech.jts.geom.Coordinate;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.GeodeticCalculator;
import org.mapfish.print.map.DistanceUnit;
import org.mapfish.print.map.Scale;

/**
 *
 * @author fdiaz
 */
public final class EPSG3857Utils {

    private EPSG3857Utils() {

    }

    public static boolean is3857(final CoordinateReferenceSystem crs) {
        String crsNameCode = crs.getName().getCode();
        String crsId = crs.getIdentifiers().iterator().next().toString();
        return ("WGS 84 / Pseudo-Mercator".equalsIgnoreCase(crsNameCode) || "EPSG:3857".equalsIgnoreCase(crsId));
    }

    public static double computeScalingFactor(final MapBounds bounds) {
        try {
            CoordinateReferenceSystem crs = bounds.getProjection();
            GeodeticCalculator calculator = new GeodeticCalculator(crs);
            calculator.setStartingGeographicPoint(0, 0);
            calculator.setDestinationGeographicPoint(1, 0);
            double equador1DegreeDistance = calculator.getOrthodromicDistance();

            double centerY = bounds.getCenter().getOrdinate(1); //.toReferencedEnvelope(paintArea).getCenterY();
            final MathTransform transform = CRS.findMathTransform(crs, GenericMapAttribute.parseProjection("EPSG:4326", true));
            final Coordinate start = JTS.transform(new Coordinate(0, centerY), null, transform);
            calculator.setStartingGeographicPoint(0, start.y);
            calculator.setDestinationGeographicPoint(1, start.y);
            double latitud1DegreeDistance = calculator.getOrthodromicDistance();

            double factor = equador1DegreeDistance / latitud1DegreeDistance;

            return factor;
        } catch (Exception e) {
            throw new PrintException("Can't compute resolution for EPSG:3857", e);
        }

    }

    public static double computeOrthodromicWidthInInches(final ReferencedEnvelope bbox) {
        try {
            CoordinateReferenceSystem crs = bbox.getCoordinateReferenceSystem();
            GeodeticCalculator calculator = new GeodeticCalculator(crs);
            final double centerY = bbox.centre().y;

            final MathTransform transform
                = CRS.findMathTransform(crs, GenericMapAttribute.parseProjection("EPSG:4326", true));
            final Coordinate start = JTS.transform(new Coordinate(bbox.getMinX(), centerY), null, transform);
            final Coordinate end = JTS.transform(new Coordinate(bbox.getMaxX(), centerY), null, transform);
            calculator.setStartingGeographicPoint(start.x, start.y);
            calculator.setDestinationGeographicPoint(end.x, end.y);
            final double orthodromicWidthInEllipsoidUnits = calculator.getOrthodromicDistance();

            DistanceUnit ellipsoidUnit
                = DistanceUnit.fromString(calculator.getEllipsoid().getAxisUnit().toString());

            double orthodromicWidthInInches = ellipsoidUnit.convertTo(orthodromicWidthInEllipsoidUnits, DistanceUnit.IN);
            return orthodromicWidthInInches;
        } catch (Exception e) {
            throw new PrintException("Can't compute orthodromic width in inches for EPSG:3857", e);
        }

    }

    public static ReferencedEnvelope computeReferencedEnvelope(final Rectangle paintArea, final Scale scale, final Coordinate center, final CoordinateReferenceSystem crs) {
        try {
            double geoWidthInM = scale.getResolution() * paintArea.width;
            double geoHeightInM = scale.getResolution() * paintArea.height;

            GeodeticCalculator calc = new GeodeticCalculator(crs);

            DistanceUnit ellipsoidUnit
                = DistanceUnit.fromString(calc.getEllipsoid().getAxisUnit().toString());
            double geoWidth = DistanceUnit.M.convertTo(geoWidthInM, ellipsoidUnit);
            double geoHeight = DistanceUnit.M.convertTo(geoHeightInM, ellipsoidUnit);

            Position2D directPosition2D = new Position2D(center.x, center.y);
            directPosition2D.setCoordinateReferenceSystem(crs);
            calc.setStartingPosition(directPosition2D);

            final int west = -90;
            calc.setDirection(west, geoWidth / 2.0);
            double minGeoX = calc.getDestinationPosition().getOrdinate(0);

            final int east = 90;
            calc.setDirection(east, geoWidth / 2.0);
            double maxGeoX = calc.getDestinationPosition().getOrdinate(0);

            final int south = 180;
            calc.setDirection(south, geoHeight / 2.0);
            double southHeight = calc.getOrthodromicDistance();

            final int north = 0;
            calc.setDirection(north, geoHeight / 2.0);
            double northHeight = calc.getOrthodromicDistance();

            double halfHeight = (southHeight + northHeight) / 2;
            double minGeoY = calc.getStartingPosition().getOrdinate(1) - halfHeight;
            double maxGeoY = calc.getStartingPosition().getOrdinate(1) + halfHeight;

            return new ReferencedEnvelope(
                minGeoX,
                maxGeoX,
                minGeoY,
                maxGeoY,
                crs);
        } catch (TransformException e) {
            throw new PrintException("Failed to compute referenced envelope", e);
        }

    }

}
