package io.datareaders.georeader.geodat;

import java.util.Arrays;
import java.util.Collection;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

public class GSPixel implements IGeoGSAttribute<Number, Double> {
	
	int x, y;
	
	Number[] bandsData;

	private CoordinateReferenceSystem crs;
	
	public GSPixel(int x, int y, Number[] bandsData, CoordinateReferenceSystem crs) {
		this.x = x;
		this.y = y;
		this.bandsData = bandsData;
		this.crs = crs;
	}
	
	public GSPixel(int x, int y, Number[] bandsData) {
		this(x, y, bandsData, DefaultGeographicCRS.WGS84);
	}

	/**
	 * Coordinate in [x;y] form
	 * 
	 * @return int[]
	 */
	public int[] getCoordinate(){
		return new int[]{x, y};
	}

	@Override
	public Geometry getPosition() {
		return new GeometryFactory().createPoint(new Coordinate(x, y));
	}
	
	@Override
	public GSFeature transposeToGenstarFeature() {
		return transposeToGenstarFeature(this.crs);
	}
	
	@Override
	public GSFeature transposeToGenstarFeature(CoordinateReferenceSystem crs) {
		SimpleFeatureTypeBuilder b = new SimpleFeatureTypeBuilder();
		b.setName(this.getGenstarName());		
		b.setCRS(crs); // set crs first
		b.add("location", Point.class); // then add geometry
		SimpleFeatureType TYPE = b.buildFeatureType();
		
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		Point point = geometryFactory.createPoint(new Coordinate(this.x, this.y));
		featureBuilder.add(point);
		SimpleFeature feature = featureBuilder.buildFeature(null);

		return new GSFeature(feature);
	}
	
	@Override
	public String getGenstarName(){
		return "px ["+x+";"+y+"]";
	}
	
	@Override
	public Collection<Number> getProperties(){
		return Arrays.asList(bandsData);
	}

	@Override
	public Double getValue(Number attribute) {
		return attribute.doubleValue();
	}
	
}
