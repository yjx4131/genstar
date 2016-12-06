package spll.example;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.geotools.feature.SchemaException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.referencing.operation.TransformException;

import core.metamodel.IPopulation;
import core.metamodel.geo.AGeoEntity;
import core.metamodel.geo.io.GeoGSFileType;
import core.metamodel.geo.io.IGSGeofile;
import core.metamodel.pop.APopulationAttribute;
import core.metamodel.pop.APopulationEntity;
import core.metamodel.pop.APopulationValue;
import core.util.GSBasicStats;
import core.util.GSPerformanceUtil;
import gospl.algo.IDistributionInferenceAlgo;
import gospl.algo.IndependantHypothesisAlgo;
import gospl.algo.sampler.IDistributionSampler;
import gospl.algo.sampler.ISampler;
import gospl.algo.sampler.sr.GosplBasicSampler;
import gospl.distribution.GosplDistributionBuilder;
import gospl.distribution.exception.IllegalControlTotalException;
import gospl.distribution.exception.IllegalDistributionCreation;
import gospl.distribution.matrix.INDimensionalMatrix;
import gospl.distribution.matrix.coordinate.ACoordinate;
import gospl.example.GosplSPTemplate;
import gospl.generator.DistributionBasedGenerator;
import gospl.generator.ISyntheticGosplPopGenerator;
import gospl.io.exception.InvalidSurveyFormatException;
import spll.algo.ISPLRegressionAlgo;
import spll.algo.LMRegressionOLS;
import spll.algo.exception.IllegalRegressionException;
import spll.datamapper.ASPLMapperBuilder;
import spll.datamapper.SPLAreaMapperBuilder;
import spll.datamapper.SPLMapper;
import spll.datamapper.exception.GSMapperException;
import spll.datamapper.variable.SPLVariable;
import spll.entity.GeoEntityFactory;
import spll.io.GeofileFactory;
import spll.io.RasterFile;
import spll.io.ShapeFile;
import spll.io.exception.InvalidGeoFormatException;
import spll.popmapper.SPUniformLocalizer;
import spll.popmapper.normalizer.SPLUniformNormalizer;
import spll.util.SpllUtil;

public class TestLocalisation {

	
	public static void main(String[] args) {
		
		
		///////////////////////
		// PARAMETERS
		///////////////////////
		
		//target number of entities to create
		int targetPopulation = 100000; 
		
		//path to the configuration file for the population generation (GOSPL)
		String stringPathToXMLConfFile = "sample/Rouen/GSC_Rouen.xml";
		
		//path to the main census shapefile - the entities are generated at this level
		String stringPathToCensusShapefile = "sample/Rouen/Rouen_shp/Rouen_iris.shp";
		
		//path to the shapefile that define the geographical objects on which the entities should be located
		String stringPathToNestShapefile = "sample/Rouen/Rouen_shp/buildings.shp";
		
		//path to the file that will be used as support for the spatial regression (bring additional spatial data)
		String stringPathToLandUseGrid = "sample/Rouen/Rouen_raster/CLC12_D076_RGF_S.tif";
		
		//path to the csv file that contains data (population number) that should be added to the census shapefile
		String stringPathAdditionaryDataToCensusFile = "sample/Rouen/Rouen_insee_indiv/Rouen_iris.csv";
				
		//path to the result files
		@SuppressWarnings("unused")
		String stringPathToRegressionGrid = "sample/Rouen/regression_grid.tif";
		String stringPathToPopulationShapefile = "sample/Rouen/population.shp";
		
		//name of the property that define the number of entities per census spatial areas.
		String stringOfNumberProperty = "P13_POP";
		
		//name of the property that contains the id of the census spatial areas in the shapefile
		String stringOfCensusIdInShapefile = "CODE_IRIS";
		
		//name of the property that contains the id of the census spatial areas in the csv file (and population)
		String stringOfCensusIdInCSVfile = "IRIS";
		
		//name of the property that will by generated by the regression and that specifies the number of entities per regression areas
		String stringOfNumberAttribute = GeoEntityFactory.ATTRIBUTE_PIXEL_BAND+0;
		
		/////////////////////
		// GENERATE THE POPULATION (GOSPL)
		/////////////////////
		
		IPopulation<APopulationEntity, APopulationAttribute, APopulationValue> population = 
				generatePopulation(targetPopulation,stringPathToXMLConfFile);
				
		/////////////////////
		// IMPORT DATA FILES
		/////////////////////
		
		GeofileFactory gf = new GeofileFactory();
		
		List<String> atts_buildings = Arrays.asList();
		core.util.GSPerformanceUtil gspu = new GSPerformanceUtil("Localisation of people in Rouen based on Iris population");
		ShapeFile sfAdmin = null;
		ShapeFile sfBuildings = null;
		
		try {
			//building shapefile
			sfBuildings = gf.getShapeFile(new File(stringPathToNestShapefile), atts_buildings);
			
			//Iris shapefile
			sfAdmin = gf.getShapeFile(new File(stringPathToCensusShapefile));
			
			//add from the csv file, the population attribute to the Iris shapefile
			List<String> att = new ArrayList<String>();
			att.add(stringOfNumberProperty);
			sfAdmin.addAttributes(new File(stringPathAdditionaryDataToCensusFile), ',', stringOfCensusIdInShapefile, stringOfCensusIdInCSVfile, att);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidGeoFormatException e) {
			e.printStackTrace();
		}
		
		
		//import the land-use file
		Collection<String> stringPathToAncilaryGeofiles = new ArrayList<>();
		stringPathToAncilaryGeofiles.add(stringPathToLandUseGrid);

		List<IGSGeofile<? extends AGeoEntity>> endogeneousVarFile = new ArrayList<>();
		for(String path : stringPathToAncilaryGeofiles){
			try {
				endogeneousVarFile.add(gf.getGeofile(new File(path)));
			} catch (IllegalArgumentException | TransformException | IOException | InvalidGeoFormatException e2) {
				e2.printStackTrace();
			}
		}
		gspu.sysoStempPerformance("Input files data import: done\n", "Main");

		
		//////////////////////////////////
		// SETUP MAIN CLASS FOR REGRESSION
		//////////////////////////////////
		
		// Choice have been made to regress from areal data count
		ISPLRegressionAlgo<SPLVariable, Double> regressionAlgo = new LMRegressionOLS();
		
		ASPLMapperBuilder<SPLVariable, Double> spllBuilder = new SPLAreaMapperBuilder(
				sfAdmin, stringOfNumberProperty, endogeneousVarFile, new ArrayList<>(),
				regressionAlgo);
		gspu.sysoStempPerformance("Setup MapperBuilder to proceed regression: done\n", "Main");
 
		// Setup main regressor class: SPLMapper
		SPLMapper<SPLVariable,Double> spl = null;
		boolean syso = false;
		try {
			spl = spllBuilder.buildMapper();
			if(syso){
				Map<SPLVariable, Double> regMap = spl.getRegression();
				gspu.sysoStempMessage("Regression parameter: \n"+Arrays.toString(regMap.entrySet().stream().map(e -> e.getKey()+" = "+e.getValue()+"\n").toArray()));
				gspu.sysoStempMessage("Intersect = "+spl.getIntercept());
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (TransformException e1) {
			e1.printStackTrace();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		} catch (ExecutionException e1) {
			e1.printStackTrace();
		} catch (IllegalRegressionException e) {
			e.printStackTrace();
		}

		// ---------------------------------
		// Apply regression function to output
		// ---------------------------------
		
		// WARNING: not generic at all - or define 1st ancillary data file to be the one for output format
		RasterFile outputFormat = (RasterFile) endogeneousVarFile
				.stream().filter(file -> file.getGeoGSFileType().equals(GeoGSFileType.RASTER))
				.findFirst().get();
		spllBuilder.setNormalizer(new SPLUniformNormalizer(0, RasterFile.DEF_NODATA));
		float[][] pixelOutput = null;
		try { 
			pixelOutput = spllBuilder.buildOutput(outputFormat, false, true, new Double(targetPopulation));
		} catch (IOException e) {
			e.printStackTrace();
		} catch (IllegalRegressionException e) {
			e.printStackTrace();
		} catch (IndexOutOfBoundsException e1) {
			e1.printStackTrace();
		} catch (TransformException e1) {
			e1.printStackTrace();
		} catch (GSMapperException e) {
			e.printStackTrace();
		}
		
		List<Double> outList = GSBasicStats.transpose(pixelOutput);
		GSBasicStats<Double> bs = new GSBasicStats<>(outList, Arrays.asList(RasterFile.DEF_NODATA.doubleValue()));
		gspu.sysoStempMessage("\nStatistics on output:\n"+bs.getStatReport());
		
		IGSGeofile<? extends AGeoEntity> outputFile = null;
		try {
			ReferencedEnvelope env = new ReferencedEnvelope(endogeneousVarFile.get(0).getEnvelope(),
					SpllUtil.getCRSfromWKT(outputFormat.getWKTCoordinateReferentSystem()));
			
			outputFile = gf.createRasterfile(new File("sample/Rouen/result.tif"), pixelOutput, 
					RasterFile.DEF_NODATA.floatValue(), env);
		} catch (IllegalArgumentException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (TransformException e1) {
			e1.printStackTrace();
		}
		 
		
		///////////////////////
		// MATCH TO POPULATION
		///////////////////////
		
 		SPUniformLocalizer localizer = new SPUniformLocalizer(sfBuildings);
 		
 		// use of the regression grid
 		localizer.setEntityNbAreas(outputFile, stringOfNumberAttribute);
		
 		// use of the IRIS attribute of the population
 		localizer.setMatch(sfAdmin, stringOfCensusIdInCSVfile, stringOfCensusIdInShapefile);
 		
 		//localize the population
 		localizer.localisePopulation(population);
 		
		/////////////////////////////////////////
		// SAVE THE POPULATION INTO A SHAPEFILE
		////////////////////////////////////////

		try {
			gf.createShapeFile(new File(stringPathToPopulationShapefile), population, 
					SpllUtil.getCRSfromWKT(outputFormat.getWKTCoordinateReferentSystem()));
		} catch (IOException | SchemaException e) {
			e.printStackTrace();
		}
	}
	
	private static IPopulation<APopulationEntity, APopulationAttribute, APopulationValue> generatePopulation(int targetPopulation, String xmlFilePath ) {
		// INPUT ARGS
		
		Path confFile = Paths.get(xmlFilePath);
		
		// THE POPULATION TO BE GENERATED
		IPopulation<APopulationEntity, APopulationAttribute, APopulationValue> population = null;

		// INSTANCIATE FACTORY
		GosplDistributionBuilder df = null; 
		try {
			df = new GosplDistributionBuilder(confFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		// RETRIEV INFORMATION FROM DATA IN FORM OF A SET OF JOINT DISTRIBUTIONS 
		try {
			df.buildDistributions();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidSurveyFormatException e) {
			e.printStackTrace();
		} catch (InvalidFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
		// TRANSPOSE SAMPLES INTO IPOPULATION
		try {
			df.buildSamples();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidSurveyFormatException e) {
			e.printStackTrace();
		} catch (InvalidFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// HERE IS A CHOICE TO MAKE BASED ON THE TYPE OF GENERATOR WE WANT:
		// Choice is made here to use distribution based generator
		
		// so we collapse all distribution build from the data
		INDimensionalMatrix<APopulationAttribute, APopulationValue, Double> distribution = null;
		try {
			distribution = df.collapseDistributions();
		} catch (IllegalDistributionCreation e1) {
			e1.printStackTrace();
		} catch (IllegalControlTotalException e1) {
			e1.printStackTrace();
		}
		
		// BUILD THE SAMPLER WITH THE INFERENCE ALGORITHM
		IDistributionInferenceAlgo<IDistributionSampler> distributionInfAlgo = new IndependantHypothesisAlgo();
		ISampler<ACoordinate<APopulationAttribute, APopulationValue>> sampler = null;
		try {
			sampler = distributionInfAlgo.inferDistributionSampler(distribution, new GosplBasicSampler());
		} catch (IllegalDistributionCreation e1) {
			e1.printStackTrace();
		}
		
		
		GSPerformanceUtil gspu = new GSPerformanceUtil("Start generating synthetic population of size "+targetPopulation);
		
		// BUILD THE GENERATOR
		ISyntheticGosplPopGenerator ispGenerator = new DistributionBasedGenerator(sampler);
		
		// BUILD THE POPULATION
		try {
			population = ispGenerator.generate(targetPopulation);
			gspu.sysoStempPerformance("End generating synthetic population: elapse time", GosplSPTemplate.class.getName());
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}
		
		return population;
	}
	

}
