package spll.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.geotools.feature.SchemaException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengis.referencing.operation.TransformException;

import core.configuration.dictionary.DemographicDictionary;
import core.metamodel.IPopulation;
import core.metamodel.attribute.demographic.DemographicAttribute;
import core.metamodel.attribute.demographic.DemographicAttributeFactory;
import core.metamodel.attribute.geographic.GeographicAttribute;
import core.metamodel.attribute.geographic.GeographicAttributeFactory;
import core.metamodel.entity.ADemoEntity;
import core.metamodel.entity.AGeoEntity;
import core.metamodel.value.IValue;
import core.util.data.GSEnumDataType;
import core.util.excpetion.GSIllegalRangedData;
import gospl.generator.util.GSUtilGenerator;
import spll.SpllPopulation;
import spll.io.exception.InvalidGeoFormatException;
import spll.popmapper.SPUniformLocalizer;

public class IoTest {

	public static IPopulation<ADemoEntity, DemographicAttribute<? extends IValue>> pop;
	public static SPLRasterFile RasterF;
	public static SPLVectorFile vectorF;
	
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		setupRandom();
	}

	
	@Test
	public void localizePopAndSave() {
		try {
			vectorF = SPLGeofileBuilder.getShapeFile(new File("src/test/resources/buildings.shp"), null);
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidGeoFormatException e) {
			e.printStackTrace();
		}
		SPUniformLocalizer localizer = new SPUniformLocalizer(new SpllPopulation(pop, vectorF));
		SpllPopulation localizedPop = localizer.localisePopulation();
		File f = new File("src/test/resources/pop.shp");
		
		try {
			new SPLGeofileBuilder().setFile(f).setPopulation(localizedPop).buildShapeFile();
		} catch (IOException | SchemaException | InvalidGeoFormatException e) {
			e.printStackTrace();
		}
		assert (f.exists() && f.getTotalSpace() > 0);
	}
	
	@Test
	public void transferToRaster() {
		try {
			RasterF = (SPLRasterFile) new SPLGeofileBuilder().setFile(new File("src/test/resources/CLC12_D076_RGF_S.tif")).buildGeofile();
			Collection<? extends AGeoEntity<? extends IValue>> entities = RasterF.getGeoEntity();
			Map<? extends AGeoEntity<? extends IValue>, Number> transfer = entities.stream().collect(Collectors.toMap(e -> e, 
					e -> Double.valueOf(1)));
			
			final GeographicAttribute<? extends IValue> transferAttribute = GeographicAttributeFactory.getFactory()
					.createIntegerAttribute("count");
			File f = new File("src/test/resources/raster.tif");
			RasterF.transferTo(f, transfer, transferAttribute);
		} catch (IllegalArgumentException | TransformException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidGeoFormatException e) {
			e.printStackTrace();
		}
	}
	
	
	@SuppressWarnings("unchecked")
	private static void setupRandom(){
		DemographicDictionary<DemographicAttribute<? extends IValue>> atts = new DemographicDictionary<>();
		try {
			atts.addAttributes(DemographicAttributeFactory.getFactory()
					.createAttribute("iris", GSEnumDataType.Nominal, Arrays.asList("765400102", "765400101")));
		} catch (GSIllegalRangedData e1) {
			e1.printStackTrace();
		}
		
		GSUtilGenerator ug = new GSUtilGenerator(atts);
		pop = ug.generate(50);
	}
	
}