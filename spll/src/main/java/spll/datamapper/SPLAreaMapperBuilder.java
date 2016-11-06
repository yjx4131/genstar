package spll.datamapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.opengis.feature.type.Name;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.operation.buffer.BufferParameters;
import com.vividsolutions.jts.precision.GeometryPrecisionReducer;

import core.io.geo.GeotiffFile;
import core.io.geo.IGSGeofile;
import core.io.geo.ShapeFile;
import core.io.geo.entity.AGeoEntity;
import core.io.geo.entity.GSFeature;
import core.io.geo.entity.GSPixel;
import core.io.geo.entity.attribute.value.AGeoValue;
import core.util.GSBasicStats;
import core.util.GSPerformanceUtil;
import spll.algo.ISPLRegressionAlgorithm;
import spll.algo.LMRegressionOLSAlgorithm;
import spll.algo.exception.IllegalRegressionException;
import spll.datamapper.matcher.SPLAreaMatcherFactory;
import spll.datamapper.variable.SPLVariable;

public class SPLAreaMapperBuilder extends ASPLMapperBuilder<SPLVariable, Double> {

	private static final boolean LOGSYSO = true;
	private SPLMapper<SPLVariable, Double> mapper;

	private static int pixelRendered = 0;

	public SPLAreaMapperBuilder(ShapeFile mainFile, Name propertyName,
			List<IGSGeofile> ancillaryFiles, Collection<AGeoValue> variables) {
		this(mainFile, propertyName, ancillaryFiles, variables, new LMRegressionOLSAlgorithm());
	}
	
	public SPLAreaMapperBuilder(ShapeFile mainFile, Name propertyName,
			List<IGSGeofile> ancillaryFiles, Collection<AGeoValue> variables, 
			ISPLRegressionAlgorithm<SPLVariable, Double> regAlgo) {
		super(mainFile, propertyName, ancillaryFiles);
		super.setRegressionAlgorithm(regAlgo);
		super.setMatcherFactory(new SPLAreaMatcherFactory(variables));
	}

	@Override
	public SPLMapper<SPLVariable, Double> buildMapper() throws IOException, TransformException, InterruptedException, ExecutionException {
		if(mapper == null){
			mapper = new SPLMapper<>();
			mapper.setMainSPLFile(mainFile);
			mapper.setMainProperty(propertyName);
			mapper.setRegAlgo(regressionAlgorithm);
			mapper.setMatcherFactory(matcherFactory);
			for(IGSGeofile file : ancillaryFiles)
				mapper.insertMatchedVariable(file);
		}
		return mapper;
	}

	@Override
	public float[][] buildOutput(GeotiffFile outputFormat, boolean intersect) throws IllegalRegressionException, TransformException, IndexOutOfBoundsException, IOException {
		if(mapper == null)
			throw new IllegalAccessError("Cannot create output before a SPLMapper has been built and regression done");
		if(!ancillaryFiles.contains(outputFormat))
			throw new IllegalArgumentException("output format file must be one of ancillary files use to proceed regression");

		GSPerformanceUtil gspu = new GSPerformanceUtil("Start processing regression data to output raster", LOGSYSO);

		// Define output format
		int rows = outputFormat.getRowNumber();
		int columns = outputFormat.getColumnNumber();
		float[][] pixels = new float[columns][rows];

		// Store regression result
		Map<SPLVariable, Double> regCoef = mapper.getRegression();
		Map<GSFeature, Double> corCoef = mapper.getCorrectionCoefficient();

		// Define utilities
		Collection<IGSGeofile> ancillaries = new ArrayList<>(super.ancillaryFiles);
		Collection<GSFeature> mainGeoData = super.mainFile.getGeoData();
		ancillaries.remove(outputFormat);

		// Iterate over pixels
		IntStream.range(0, columns).parallel().forEach(
				x -> IntStream.range(0, rows).forEach(
						y -> pixels[x][y] = (float) this.computePixelWithinOutput(x, y, outputFormat,  ancillaries,
								mainGeoData, regCoef, corCoef, gspu, intersect)
						)
				);
		pixelRendered = 0;
		
		gspu.sysoStempMessage("End processing pixels - start processing some statistics:");
		
		GSBasicStats<Double> bs = new GSBasicStats<>(GSBasicStats.transpose(pixels));
		
		gspu.sysoStempMessage(bs.getStatReport());
		
		return pixels;
	}

	@Override
	public Map<GSFeature, Double> buildOutput(File outputFile, ShapeFile formatFile) {
		// TODO Auto-generated method stub
		return null;
	}

	/////////////////////////////////////////////////////////////////////////////
	// --------------------------- INNER UTILITIES --------------------------- //
	/////////////////////////////////////////////////////////////////////////////
	
	private double computePixelWithinOutput(int x, int y, GeotiffFile geotiff, Collection<IGSGeofile> ancillaries,
			Collection<GSFeature> mainFeatures, Map<SPLVariable, Double> regCoef, Map<GSFeature, Double> corCoef,
			GSPerformanceUtil gspu, boolean intersect) {
		// Output progression
		int prop10for100 = Math.round(Math.round(geotiff.getRowNumber() * geotiff.getColumnNumber() * 0.1d));
		if((++pixelRendered+1) % prop10for100 == 0)
			gspu.sysoStempPerformance((pixelRendered+1) / (prop10for100 * 10.0), this);

		// Get the current pixel value
		GSPixel refPixel = null;
		try {
			refPixel = geotiff.getPixel(x, y);
		} catch (TransformException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		// Get the related feature in main space features
		Point pixelLocation = refPixel.getPosition();
		Optional<GSFeature> opFeature = mainFeatures.stream().filter(ft -> pixelLocation.within(ft.getGeometry())).findFirst();
		
		if(opFeature.isPresent()){
			if(intersect)
				return computePixelIntersectOutput(refPixel, geotiff, ancillaries, mainFeatures, regCoef, corCoef);
			return computePixelWithin(refPixel, geotiff, ancillaries, opFeature.get(), regCoef, corCoef);
		}
		return geotiff.getNoDataValue();
	}
	
	// --------------------------- INNER ALGORITHM --------------------------- //
	
	/*
	 * WARNING: the within function used define inclusion as: 
	 * centroide of {@code refPixel} geometry is within the referent geometry
	 */
	private double computePixelWithin(GSPixel refPixel, GeotiffFile geotiff, Collection<IGSGeofile> ancillaries,
			GSFeature feat, Map<SPLVariable, Double> regCoef, Map<GSFeature, Double> corCoef){

		// Retain info about pixel and his context
		Point pixPosition = refPixel.getPosition();
		Collection<AGeoValue> pixData = refPixel.getValues();
		double pixArea = refPixel.getArea();
		double outputCoeff = corCoef.get(feat);

		// Setup output value for the pixel based on pixels' band values
		double output = regCoef.entrySet().stream().filter(var -> pixData.contains(var.getKey().getValue()))
				.mapToDouble(var -> var.getValue() * pixArea).sum();

		// Iterate over other explanatory variables to update pixels value
		for(IGSGeofile otherExplanVarFile : ancillaries){
			Iterator<? extends AGeoEntity> otherItt = otherExplanVarFile
					.getGeoAttributeIteratorWithin(refPixel.getGeometry());
			while(otherItt.hasNext()){
				AGeoEntity other = otherItt.next();
				Set<SPLVariable> otherValues = regCoef.keySet()
						.stream().filter(var -> other.getValues().contains(var.getValue()))
						.collect(Collectors.toSet());
				output += otherValues.stream().mapToDouble(val -> regCoef.get(val) * pixPosition.getArea()).sum();
			}
		}

		return output * outputCoeff;
	}

	/*
	 * WARNING: intersection area calculation is very computation demanding, so this method is pretty slow 
	 */
	private double computePixelIntersectOutput(GSPixel refPixel, GeotiffFile geotiff, Collection<IGSGeofile> ancillaries,
			Collection<GSFeature> mainFeatures, Map<SPLVariable, Double> regCoef, Map<GSFeature, Double> corCoef) {

		// Retain main feature the pixel is within
		Geometry pixGeom = refPixel.getGeometry(); 
		List<GSFeature> feats = mainFeatures.stream()
				.filter(ft -> ft.getGeometry().intersects(pixGeom))
				.collect(Collectors.toList());
		if(feats.isEmpty())
			return 0f;

		// Get the values contain in the pixel bands
		Collection<AGeoValue> pixData = refPixel.getValues();
		double pixArea = refPixel.getArea();

		// Setup output value for the pixel based on pixels' band values
		double output = regCoef.entrySet().stream().filter(var -> pixData.contains(var.getKey().getValue()))
				.mapToDouble(var -> var.getValue() * pixArea).sum();

		// Iterate over other explanatory variables to update pixels value
		for(IGSGeofile otherExplanVarFile : ancillaries){
			Iterator<? extends AGeoEntity> otherItt = otherExplanVarFile
					.getGeoAttributeIteratorIntersect(pixGeom);
			while(otherItt.hasNext()){
				AGeoEntity other = otherItt.next();
				Set<SPLVariable> otherValues = regCoef.keySet()
						.stream().filter(var -> other.getValues().contains(var.getValue()))
						.collect(Collectors.toSet());
				output += otherValues.stream().mapToDouble(val -> 
				regCoef.get(val) * other.getGeometry().intersection(pixGeom).getArea()).sum();
			}
		}

		// Compute corrected value based on output data (to balanced for unknown determinant information)
		// Intersection correction try /catch clause come from GAMA
		float correctedOutput = 0f; 
		for(GSFeature f : feats){
			Geometry fGeom = f.getGeometry();
			Geometry intersectGeom = null;
			try {
				intersectGeom = fGeom.intersection(pixGeom);
			} catch (final Exception ex) {
				try {
					final PrecisionModel pm = new PrecisionModel(PrecisionModel.FLOATING_SINGLE);
					intersectGeom = GeometryPrecisionReducer.reducePointwise(fGeom, pm)
							.intersection(GeometryPrecisionReducer.reducePointwise(pixGeom, pm));
				} catch (final Exception e) {
					// AD 12/04/13 : Addition of a third method in case of
					// exception
					try {
						intersectGeom = fGeom.buffer(0.01, BufferParameters.DEFAULT_QUADRANT_SEGMENTS, BufferParameters.CAP_FLAT)
								.intersection(pixGeom.buffer(0.01, BufferParameters.DEFAULT_QUADRANT_SEGMENTS,
										BufferParameters.CAP_FLAT));
					} catch (final Exception e2) {
						intersectGeom = null;
					}
				}
			}
			correctedOutput += Math.round(output * intersectGeom.getArea() / pixGeom.getArea() * corCoef.get(f));
		}
		return correctedOutput;
	}

}
