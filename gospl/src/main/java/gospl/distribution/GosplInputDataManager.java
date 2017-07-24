/*********************************************************************************************
 *
 * 'GosplDistributionFactory.java, in plugin gospl, is part of the source code of the GAMA modeling and simulation
 * platform. (c) 2007-2016 UMI 209 UMMISCO IRD/UPMC & Partners
 *
 * Visit https://github.com/gama-platform/gama for license information and developers contact.
 * 
 *
 **********************************************************************************************/
package gospl.distribution;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import core.configuration.GenstarConfigurationFile;
import core.configuration.GenstarXmlSerializer;
import core.metamodel.IEntity;
import core.metamodel.IPopulation;
import core.metamodel.pop.APopulationAttribute;
import core.metamodel.pop.APopulationEntity;
import core.metamodel.pop.APopulationValue;
import core.metamodel.pop.io.GSSurveyType;
import core.metamodel.pop.io.GSSurveyWrapper;
import core.metamodel.pop.io.IGSSurvey;
import core.util.GSPerformanceUtil;
import core.util.data.GSDataParser;
import core.util.data.GSEnumDataType;
import gospl.GosplPopulation;
import gospl.distribution.exception.IllegalControlTotalException;
import gospl.distribution.exception.IllegalDistributionCreation;
import gospl.distribution.matrix.AFullNDimensionalMatrix;
import gospl.distribution.matrix.INDimensionalMatrix;
import gospl.distribution.matrix.control.AControl;
import gospl.distribution.matrix.control.ControlFrequency;
import gospl.distribution.matrix.coordinate.ACoordinate;
import gospl.distribution.matrix.coordinate.GosplCoordinate;
import gospl.entity.GosplEntity;
import gospl.io.GosplSurveyFactory;
import gospl.io.exception.InvalidSurveyFormatException;

/**
 * Main class to setup and harmonize input data. Can handle:
 * <p><ul>
 * <li>Contingency or frequency table => collapse into one distribution of attribute, i.e. {@link INDimensionalMatrix}
 * <li>Sample => convert to population, i.e. {@link IPopulation}
 * </ul><p>
 * TODO: the ability to input statistical moment or custom distribution
 * 
 * @author kevinchapuis
 *
 */
public class GosplInputDataManager {

	private static Logger logger = LogManager.getLogger();
	
	private final double EPSILON = Math.pow(10d, -3);

	private final GenstarConfigurationFile configuration;
	private final GSDataParser dataParser;

	private Set<AFullNDimensionalMatrix<? extends Number>> inputData;
	private Set<GosplPopulation> samples;

	public GosplInputDataManager(final Path configurationFilePath) throws FileNotFoundException {
		this.configuration = new GenstarXmlSerializer().deserializeGSConfig(configurationFilePath);
		this.configuration.setBaseDirectory(configurationFilePath.toFile());
		this.dataParser = new GSDataParser();
	}
	
	public GosplInputDataManager(final GenstarConfigurationFile configurationFile) {
		this.configuration = configurationFile;
		this.dataParser = new GSDataParser();
	}

	/**
	 * 
	 * Main methods to parse and get control totals from a {@link GSDataFile} file and with the help of a specified set
	 * of {@link APopulationAttribute}
	 * <p>
	 * Method gets all data file from the builder and harmonizes them to one another using line identifier attributes
	 * 
	 * @return A {@link Set} of {@link INDimensionalMatrix}
	 * @throws InputFileNotSupportedException
	 * @throws IOException
	 * @throws InvalidFormatException
	 * @throws MatrixCoordinateException
	 * @throws InvalidFileTypeException
	 */
	public void buildDataTables() throws IOException, InvalidSurveyFormatException, InvalidFormatException {
		GosplSurveyFactory sf = new GosplSurveyFactory();
		this.inputData = new HashSet<>();
		for (final GSSurveyWrapper wrapper : this.configuration.getSurveyWrappers())
			if (!wrapper.getSurveyType().equals(GSSurveyType.Sample))
				this.inputData.addAll(getDataTables(sf.getSurvey(wrapper, this.configuration.getBaseDirectory() == null ? 
						null : this.configuration.getBaseDirectory()), 
						this.configuration.getAttributes()));
	}

	/**
	 * Main methods to parse and get samples cast into population of according type in Gospl. More precisely, each
	 * sample is transposed where each individual in the survey is a {@link IEntity} in a synthetic {@link IPopulation}
	 * 
	 * @return
	 * @throws IOException
	 * @throws InvalidFormatException
	 * @throws InvalidFileTypeException
	 * 
	 */
	public void buildSamples() throws IOException, InvalidSurveyFormatException, InvalidFormatException {
		GosplSurveyFactory sf = new GosplSurveyFactory();
		samples = new HashSet<>();
		for (final GSSurveyWrapper wrapper : this.configuration.getSurveyWrappers())
			if (wrapper.getSurveyType().equals(GSSurveyType.Sample))
				samples.add(getSample(sf.getSurvey(wrapper, this.configuration.getBaseDirectory()), 
						this.configuration.getAttributes()));
	}

	/////////////////////////////////////////////////////////////////////////////////
	// -------------------------------- ACCESSORS -------------------------------- //
	/////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Returns an unmodifiable view of input data tables, as a raw set of matrices
	 * @return
	 */
	public Set<INDimensionalMatrix<APopulationAttribute, APopulationValue, ? extends Number>> getRawDataTables() {
		return Collections.unmodifiableSet(this.inputData);
	}
	
	/**
	 * Returns an unmodifiable view of input contingency tables. If there is not any
	 * contingency data in input tables, then return an empty set
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Set<AFullNDimensionalMatrix<Integer>> getContingencyTables(){
		return this.inputData.stream().filter(matrix -> matrix.getMetaDataType().equals(GSSurveyType.ContingencyTable))
				.map(matrix -> (AFullNDimensionalMatrix<Integer>) matrix).collect(Collectors.toSet());
	}
	
	/**
	 * Returns an unmodifiable view of input samples 
	 * @return
	 */
	public Set<IPopulation<APopulationEntity, APopulationAttribute, APopulationValue>> getRawSamples(){
		return Collections.unmodifiableSet(this.samples);
	}
	
	/**
	 * 
	 * Create a frequency matrix from all input data tables
	 * 
	 * @return
	 * @throws IllegalDistributionCreation
	 * @throws IllegalControlTotalException
	 * @throws MatrixCoordinateException
	 *
	 */
	public INDimensionalMatrix<APopulationAttribute, APopulationValue, Double> collapseDataTablesIntoDistributions()
			throws IllegalDistributionCreation, IllegalControlTotalException {
		if (inputData.isEmpty())
			throw new IllegalArgumentException(
					"To collapse matrices you must build at least one first: see buildDistributions method");
		if (inputData.size() == 1)
			return getFrequency(inputData.iterator().next());
		final Set<AFullNDimensionalMatrix<Double>> fullMatrices = new HashSet<>();
		
		GSPerformanceUtil gspu = new GSPerformanceUtil("Proceed to distribution collapse", logger);
		gspu.sysoStempPerformance(0, this);
		
		// Matrices that contain a record attribute
		for (AFullNDimensionalMatrix<? extends Number> recordMatrices : inputData.stream()
				.filter(mat -> mat.getDimensions().stream().anyMatch(d -> d.isRecordAttribute()))
				.collect(Collectors.toSet())){
			if(recordMatrices.getDimensions().stream().filter(d -> !d.isRecordAttribute())
					.allMatch(d -> fullMatrices.stream().allMatch(matOther -> !matOther.getDimensions().contains(d))))
				fullMatrices.add(getTransposedRecord(recordMatrices));
		}
		
		gspu.sysoStempPerformance(1, this);
		gspu.sysoStempMessage("Collapse record attribute: done");
		
		// Matrices that do not contain any record attribute
		for (final AFullNDimensionalMatrix<? extends Number> mat : inputData.stream()
				.filter(mat -> mat.getDimensions().stream().allMatch(d -> !d.isRecordAttribute()))
				.collect(Collectors.toSet()))
			fullMatrices.add(getFrequency(mat));
		
		gspu.sysoStempPerformance(2, this);
		gspu.sysoStempMessage("Transpose to frequency: done");
				
		return new GosplConditionalDistribution(fullMatrices);
	}
	
	/////////////////////////////////////////////////////////////////////////////////
	// -------------------------- inner utility methods -------------------------- //
	/////////////////////////////////////////////////////////////////////////////////

	/*
	 * Get the distribution matrix from data files
	 */
	private Set<AFullNDimensionalMatrix<? extends Number>> getDataTables(final IGSSurvey survey,
			final Set<APopulationAttribute> attributes) throws IOException, InvalidSurveyFormatException {
		final Set<AFullNDimensionalMatrix<? extends Number>> cTableSet = new HashSet<>();
		
		// Read headers and store possible variables by line index
		final Map<Integer, Set<APopulationValue>> rowHeaders = survey.getRowHeaders(attributes);
		// Read headers and store possible variables by column index
		final Map<Integer, Set<APopulationValue>> columnHeaders = survey.getColumnHeaders(attributes);

		// Store column related attributes while keeping unrelated attributes separated
		final Set<Set<APopulationAttribute>> columnSchemas = columnHeaders.values().stream()
				.map(head -> head.stream().map(v -> v.getAttribute()).collect(Collectors.toSet()))
				.collect(Collectors.toSet());
		// Remove lower generality schema: e.g. if we have scheam [A,B] then [A] or [B] will be skiped
		columnSchemas.removeAll(columnSchemas.stream().filter(schema -> 
			columnSchemas.stream().anyMatch(higherSchema -> schema.stream()
					.allMatch(att -> higherSchema.contains(att)) && higherSchema.size() > schema.size()))
				.collect(Collectors.toSet()));
		// Store line related attributes while keeping unrelated attributes separated
		final Set<Set<APopulationAttribute>> rowSchemas = rowHeaders.values().stream()
				.map(line -> line.stream().map(v -> v.getAttribute()).collect(Collectors.toSet()))
				.collect(Collectors.toSet());
		rowSchemas.removeAll(rowSchemas.stream().filter(schema -> 
			rowSchemas.stream().anyMatch(higherSchema -> schema.stream()
				.allMatch(att -> higherSchema.contains(att)) && higherSchema.size() > schema.size()))
				.collect(Collectors.toSet()));

		// Start iterating over each related set of attribute
		for (final Set<APopulationAttribute> rSchema : rowSchemas) {
			for (final Set<APopulationAttribute> cSchema : columnSchemas) {
				// Create a matrix for each set of related attribute
				AFullNDimensionalMatrix<? extends Number> jDistribution;
				// Matrix 'dimension / aspect' map
				final Map<APopulationAttribute, Set<APopulationValue>> dimTable = Stream.concat(rSchema.stream(), cSchema.stream())
						.collect(Collectors.toMap(a -> a, a -> a.getValues()));
				// Instantiate either contingency (int and global frame of reference) or frequency (double and either
				// global or local frame of reference) matrix
				if (survey.getDataFileType().equals(GSSurveyType.ContingencyTable))
					jDistribution = new GosplContingencyTable(dimTable);
				else
					jDistribution = new GosplJointDistribution(dimTable, survey.getDataFileType());
				jDistribution.setLabel(survey.getName());
				jDistribution.addGenesis("from file "+survey.getName());
				// Fill in the matrix through line & column
				for (final Integer row : rowHeaders.entrySet().stream()
						.filter(e -> rSchema.stream().allMatch(att -> e.getValue().stream()
								.anyMatch(val -> val.getAttribute().equals(att))))
						.map(e -> e.getKey()).collect(Collectors.toSet())) {
					for (final Integer col : columnHeaders.entrySet().stream()
							.filter(e -> cSchema.stream().allMatch(att -> e.getValue().stream()
									.anyMatch(val -> val.getAttribute().equals(att))))
							.map(e -> e.getKey()).collect(Collectors.toSet())) {
						// The value
						final String stringVal = survey.read(row, col);
						// Value type
						final GSEnumDataType dt = dataParser.getValueType(stringVal);
						// Store coordinate for the value. It is made of all line & column attribute's aspects
						final Set<APopulationValue> coordSet =
								Stream.concat(rowHeaders.get(row).stream(), columnHeaders.get(col).stream())
										.collect(Collectors.toSet());
						final ACoordinate<APopulationAttribute, APopulationValue> coord = new GosplCoordinate(coordSet);
						// Add the coordinate / parsed value pair into the matrix
						if (dt == GSEnumDataType.Integer || dt == GSEnumDataType.Double)
							if (!jDistribution.addValue(coord, jDistribution.parseVal(dataParser, stringVal)))
								jDistribution.getVal(coord).add(jDistribution.parseVal(dataParser, stringVal));
					}
				}
				cTableSet.add(jDistribution);
			}
		}
		return cTableSet;
	}

	/*
	 * Transpose any matrix to a frequency based matrix
	 */
	private AFullNDimensionalMatrix<Double> getFrequency(final AFullNDimensionalMatrix<? extends Number> matrix)
			throws IllegalControlTotalException {
		// returned matrix
		AFullNDimensionalMatrix<Double> freqMatrix = null;
		
		if (matrix.getMetaDataType().equals(GSSurveyType.LocalFrequencyTable)) {
			// Identify local referent dimension
			final Map<APopulationAttribute, List<AControl<? extends Number>>> mappedControls =
					matrix.getDimensions().stream().collect(Collectors.toMap(d -> d, d -> d.getValues().parallelStream()
							.map(a -> matrix.getVal(a)).collect(Collectors.toList())));
			final APopulationAttribute localReferentDimension =
					mappedControls.entrySet().stream()
							.filter(e -> e.getValue().stream()
									.allMatch(ac -> ac.equalsCastedVal(e.getValue().get(0), EPSILON)))
							.map(e -> e.getKey()).findFirst().get();
			final AControl<? extends Number> localReferentControl =
					mappedControls.get(localReferentDimension).iterator().next();

			// The most appropriate align referent matrix (the one that have most information about matrix to align,
			// i.e. the highest number of shared dimensions)
			final Optional<AFullNDimensionalMatrix<? extends Number>> optionalRef = inputData.stream()
					.filter(ctFitter -> !ctFitter.getMetaDataType().equals(GSSurveyType.LocalFrequencyTable)
							&& ctFitter.getDimensions().contains(localReferentDimension))
					.sorted((jd1,
							jd2) -> (int) jd2.getDimensions().stream().filter(d -> matrix.getDimensions().contains(d))
									.count()
									- (int) jd1.getDimensions().stream().filter(d -> matrix.getDimensions().contains(d))
											.count())
					.findFirst();
			if (optionalRef.isPresent()) {
				freqMatrix = new GosplJointDistribution(
						matrix.getDimensions().stream().collect(Collectors.toMap(d -> d, d -> d.getValues())),
						GSSurveyType.GlobalFrequencyTable);
				final AFullNDimensionalMatrix<? extends Number> matrixOfReference = optionalRef.get();
				final double totalControl =
						matrixOfReference.getVal(localReferentDimension.getValues()).getValue().doubleValue();
				final Map<APopulationValue, Double> freqControls =
						localReferentDimension.getValues().stream().collect(Collectors.toMap(lrv -> lrv,
								lrv -> matrixOfReference.getVal(lrv).getValue().doubleValue() / totalControl));

				for (final ACoordinate<APopulationAttribute, APopulationValue> controlKey : matrix.getMatrix().keySet()) {
					freqMatrix.addValue(controlKey,
							new ControlFrequency(matrix.getVal(controlKey).getValue().doubleValue()
									/ localReferentControl.getValue().doubleValue()
									* freqControls.get(controlKey.getMap().get(localReferentDimension))));
				}
			} else
				throw new IllegalControlTotalException("The matrix (" + matrix.getLabel()
						+ ") must be align to globale frequency table but lack of a referent matrix", matrix);
		} else {
			// Init output matrix
			freqMatrix = new GosplJointDistribution(
					matrix.getDimensions().stream().collect(Collectors.toMap(d -> d, d -> d.getValues())),
					GSSurveyType.GlobalFrequencyTable);
			freqMatrix.setLabel((matrix.getLabel()==null?"?/joint":matrix.getLabel()+"/joint"));

			if (matrix.getMetaDataType().equals(GSSurveyType.GlobalFrequencyTable)) {
				for (final ACoordinate<APopulationAttribute, APopulationValue> coord : matrix.getMatrix().keySet())
					freqMatrix.addValue(coord, new ControlFrequency(matrix.getVal(coord).getValue().doubleValue()));
			} else {
				final AControl<? extends Number> total = matrix.getVal();
				for (final APopulationAttribute attribut : matrix.getDimensions()) {
					final AControl<? extends Number> controlAtt = matrix.getVal(attribut.getValues());
					if (Math.abs(controlAtt.getValue().doubleValue() - total.getValue().doubleValue())
							/ controlAtt.getValue().doubleValue() > this.EPSILON)
						throw new IllegalControlTotalException(total, controlAtt);
				}
				for (final ACoordinate<APopulationAttribute, APopulationValue> coord : matrix.getMatrix().keySet())
					freqMatrix.addValue(coord, new ControlFrequency(
							matrix.getVal(coord).getValue().doubleValue() / total.getValue().doubleValue()));
			}
		}
		
		freqMatrix.inheritGenesis(matrix);
		freqMatrix.addGenesis("converted to frequency GosplDistributionBuilder@@getFrequency");

		return freqMatrix;
	}

	/**
	 * Based on a survey wrapping data, and for a given set of expected attributes, 
	 * creates a GoSPl population.
	 */
	public static GosplPopulation getSample(final IGSSurvey survey, 
			final Set<APopulationAttribute> attributes)
			throws IOException, InvalidSurveyFormatException {
		return getSample(survey, attributes, null, Collections.emptyMap());
	}
	
	/**
	 * Based on a survey wrapping data, and for a given set of expected attributes, 
	 * creates a GoSPl population.
	 */
	public static GosplPopulation getSample(final IGSSurvey survey, 
			final Set<APopulationAttribute> attributes, 
			Integer maxIndividuals,
			Map<String,String> keepOnlyEqual
			)
			throws IOException, InvalidSurveyFormatException {
		
		final GosplPopulation sampleSet = new GosplPopulation();
		
		// Read headers and store possible variables by column index
		final Map<Integer, APopulationAttribute> columnHeaders = survey.getColumnSample(attributes);

		if (columnHeaders.isEmpty()) 
			throw new RuntimeException("no column header was found in survey "+survey);
		
		int unmatchSize = 0;
		int maxIndivSize = columnHeaders.keySet().stream().max((i1, i2) -> i1.compareTo(i2)).get();
		
		loopLines: for (int i = survey.getFirstRowIndex(); i <= survey.getLastRowIndex(); i++) {
			
			// too much ?
			if (maxIndividuals != null && sampleSet.size() >= maxIndivSize)
				break;
			
			final Map<APopulationAttribute, APopulationValue> entityAttributes = new HashMap<>();
			final List<String> indiVals = survey.readLine(i);
			//System.err.println(i+" "+indiVals);

			if(indiVals.size() <= maxIndivSize){
				logger.warn("One individual does not fit required number of attributes: \n"
						+ Arrays.toString(indiVals.toArray()));
						
				unmatchSize++;
				continue;
			}
			for (final Integer idx : columnHeaders.keySet()){
				
				APopulationAttribute att = columnHeaders.get(idx);
				APopulationValue val = att.getValue(indiVals.get(idx));
				
				// filter
				if (val != null) {
					String expected = keepOnlyEqual.get(att.getAttributeName());
					if (expected != null && !val.getStringValue().equals(expected))
						// skip
						continue loopLines;
				}
				
				if (val!=null)
					entityAttributes.put(att, val);
				else if(att.getEmptyValue().getInputStringValue().equals(indiVals.get(idx)))
					entityAttributes.put(att, att.getEmptyValue());
				else{
					logger.warn("Data modality "+indiVals.get(idx)+" does not match any value for attribute "
							+att.getAttributeName());
					unmatchSize++;
				}
			}
			if(entityAttributes.size() == entityAttributes.size())
				sampleSet.add(new GosplEntity(entityAttributes));
		}
		if (unmatchSize > 0) {
			logger.debug("Input sample has bypass "+new DecimalFormat("#.##").format(unmatchSize/(double)sampleSet.size()*100)
				+"% ("+unmatchSize+") of entities due to unmatching attribute's value");
		}
		return sampleSet;
	}
	
	/*
	 * Result in the same matrix without any record attribute
	 */
	private AFullNDimensionalMatrix<Double> getTransposedRecord(
			AFullNDimensionalMatrix<? extends Number> recordMatrices) {
		
		Set<APopulationAttribute> dims = recordMatrices.getDimensions().stream().filter(d -> !d.isRecordAttribute())
				.collect(Collectors.toSet());
		
		GSPerformanceUtil gspu = new GSPerformanceUtil("Transpose process of matrix "
				+Arrays.toString(recordMatrices.getDimensions().toArray()), logger, Level.TRACE);
		gspu.sysoStempPerformance(0, this);
		gspu.setObjectif(recordMatrices.getMatrix().size());
		
		AFullNDimensionalMatrix<Double> freqMatrix = new GosplJointDistribution(dims.stream()
				.collect(Collectors.toMap(d -> d, d -> d.getValues())), GSSurveyType.GlobalFrequencyTable);
		freqMatrix.inheritGenesis(recordMatrices);
		freqMatrix.addGenesis("transposted by GosplDistributionBuilder@getTransposedRecord");

		AControl<? extends Number> recordMatrixControl = recordMatrices.getVal();
		
		int iter = 1;
		for(ACoordinate<APopulationAttribute, APopulationValue> oldCoord : recordMatrices.getMatrix().keySet()){
			if(iter % (gspu.getObjectif()/10) == 0)
				gspu.sysoStempPerformance(0.1, this);
			Set<APopulationValue> newCoord = new HashSet<>(oldCoord.values());
			newCoord.retainAll(dims.stream().flatMap(dim -> dim.getValues().stream()).collect(Collectors.toSet()));
			freqMatrix.addValue(new GosplCoordinate(newCoord), 
					new ControlFrequency(recordMatrices.getVal(oldCoord).getValue().doubleValue() 
							/ recordMatrixControl.getValue().doubleValue()));
		}
		
		return freqMatrix;
	}


}
