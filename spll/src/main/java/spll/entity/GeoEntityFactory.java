package spll.entity;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.feature.type.BasicFeatureTypes;
import org.geotools.geometry.Envelope2D;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeatureType;

import com.vividsolutions.jts.geom.Geometry;

import core.metamodel.attribute.geographic.GeographicAttribute;
import core.metamodel.attribute.geographic.GeographicAttributeFactory;
import core.metamodel.value.IValue;
import core.metamodel.value.numeric.ContinuousValue;
import core.util.data.GSDataParser;
import spll.util.SpllGeotoolsAdapter;

/**
 * The factory to safely create Spll geographical entity
 * <p>
 *  TODO: no need now to store all possible attribute to avoid duplicate
 * 
 * @author kevinchapuis
 *
 */
public class GeoEntityFactory {

	public static String ATTRIBUTE_PIXEL_BAND = "Band";
	public static String ATTRIBUTE_FEATURE_POP = "Population";
	
	private final Map<String, GeographicAttribute<? extends IValue>> featureAttributes;
	
	private SimpleFeatureBuilder contingencyFeatureBuilder;
	
	private Logger log = LogManager.getLogger();

	public GeoEntityFactory() {
		this.featureAttributes = new HashMap<>();
	}
	
	/**
	 * Defines the set of attributes for entities to be created. This set will 
	 * be the support to add new value and recall them to avoid duplicates
	 * 
	 * WARNING: this constructor can only create {@link SpllPixel}
	 * TODO: make a unique constructor OR switch to a builder
	 * 
	 * @param attributes
	 */
	public GeoEntityFactory(Set<GeographicAttribute<? extends IValue>> attributes) {
		this.featureAttributes = attributes.stream().collect(Collectors
				.toMap(GeographicAttribute::getAttributeName, Function.identity()));
	}
	
	/**
	 * In addition to set of attribute, also defines a way to create Geotools
	 * SimpleFeature to facilitate the creation of vector style geo entity
	 * 
	 * @param attributes
	 * @param featureTypeName
	 * @param crs
	 * @param geomClazz
	 */
	public GeoEntityFactory(Set<GeographicAttribute<? extends IValue>> attributes, SimpleFeatureType schema){
		this(attributes);
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("SimpleFeatureTypeBuilder");
        builder.setCRS(schema.getCoordinateReferenceSystem()); // <- Coordinate reference system
        builder.add(BasicFeatureTypes.GEOMETRY_ATTRIBUTE_NAME, schema.getGeometryDescriptor().getType().getBinding());
        log.debug("Setup a builder ({}) with {} geometry and [{}] attribute list",
        		builder.getName(),
        		schema.getGeometryDescriptor().getType().getBinding().getSimpleName(), 
        		attributes.stream().map(a -> a.getAttributeName()).collect(Collectors.joining(", ")));
        for(GeographicAttribute<? extends IValue> attribute : attributes)
            builder.add(attribute.getAttributeName(), attribute.getValueSpace().getValues().stream()
        			.allMatch(value -> value.getType().isNumericValue()) ? Number.class : String.class);
        
        this.contingencyFeatureBuilder = new SimpleFeatureBuilder(builder.buildFeatureType());
	}
	
	// ---------------------------------------------------------- //

	/**
	 * Create a vector style entity
	 * 
	 * @param feature
	 * @return
	 */
	public SpllFeature createGeoEntity(Feature feature, List<String> attList) {
		Map<GeographicAttribute<? extends IValue>, IValue> values = new HashMap<>();
		
		Collection<Property> propertyList = feature.getProperties().stream()
				.filter(property -> !BasicFeatureTypes.GEOMETRY_ATTRIBUTE_NAME.equals(property.getName().getLocalPart()) 
						&& (attList.isEmpty() || attList.contains(property.getName().getLocalPart())))
				.collect(Collectors.toSet());
		for(Property property : propertyList){
			String name = property.getName().getLocalPart();
			GeographicAttribute<? extends IValue> attribute = null;
			attribute = featureAttributes.get(name);
			if (attribute == null) {
				// if the corresponding attribute does not yet exist, we create it on the fly
					attribute = SpllGeotoolsAdapter.getInstance().getGeographicAttribute(property);
					System.out.println("discovered attribute: "+attribute.getAttributeName());
					featureAttributes.put(name, attribute);
			}	
			Object v = property.getValue();
			if (v == null) {
				values.put(attribute, attribute.getValueSpace().getEmptyValue());
				
			} else {
				values.put(attribute, attribute.getValueSpace().addValue(v.toString()));
			}
		}
		return new SpllFeature(values, feature);
	}
	
	/**
	 * 
	 * 
	 * @param the_geom
	 * @param featureValues
	 * @return
	 */
	public SpllFeature createGeoEntity(Geometry the_geom, Map<GeographicAttribute<? extends IValue>, IValue> featureValues){
		GSDataParser gsdp = new GSDataParser();
		// Use factory defined feature constructor to build the inner feature
		contingencyFeatureBuilder.add(the_geom);
		featureValues.keySet().stream().forEach(att -> 
			contingencyFeatureBuilder.set(att.getAttributeName(), att.getValueSpace().getType().isNumericValue() ? 
					gsdp.getNumber(featureValues.get(att).getStringValue()) : featureValues.get(att).getStringValue()));
		Feature feat = contingencyFeatureBuilder.buildFeature(null);
		
		// Add non previously encountered attribute to attributes set
		for(GeographicAttribute<? extends IValue> att : featureValues.keySet())
			if(!featureAttributes.containsValue(att))
				featureAttributes.put(att.getAttributeName(), att);
		
		// Return created GSFeature
		return new SpllFeature(featureValues, feat);
	}

	/**
	 * Create a raster style entity
	 * 
	 * @param pixelBands
	 * @param pixel
	 * @param gridX
	 * @param gridY
	 * @return
	 */
	public SpllPixel createGeoEntity(Number[] pixelBands, Envelope2D pixel, int gridX, int gridY) {
		Map<GeographicAttribute<? extends ContinuousValue>, ContinuousValue> values = new HashMap<>();
		Set<GeographicAttribute<ContinuousValue>> pixelAttributes = new HashSet<>();
		for(int i = 0; i < pixelBands.length; i++){
			String bandsName = ATTRIBUTE_PIXEL_BAND+i;
			GeographicAttribute<ContinuousValue> attribute = null;
			Optional<GeographicAttribute<ContinuousValue>> opAtt = pixelAttributes.stream()
					.filter(att -> att.getAttributeName().equals(bandsName)).findAny();
			if(opAtt.isPresent())
				attribute = opAtt.get();
			else {
				attribute = GeographicAttributeFactory.getFactory().createContinueAttribute(bandsName);
				pixelAttributes.add(attribute);
			}
			values.put(attribute, attribute.getValueSpace().addValue(pixelBands[i].toString()));
		}
		return new SpllPixel(values, pixel, gridX, gridY);
	}

}
