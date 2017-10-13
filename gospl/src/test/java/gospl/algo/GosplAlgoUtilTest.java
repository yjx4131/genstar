package gospl.algo;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import core.metamodel.IPopulation;
import core.metamodel.pop.DemographicAttribute;
import core.metamodel.pop.ADemoEntity;
import core.metamodel.pop.IValue;
import core.util.data.GSEnumDataType;
import core.util.excpetion.GSIllegalRangedData;
import core.util.random.GenstarRandom;
import gospl.distribution.GosplNDimensionalMatrixFactory;
import gospl.distribution.exception.IllegalDistributionCreation;
import gospl.distribution.matrix.AFullNDimensionalMatrix;
import gospl.distribution.matrix.ASegmentedNDimensionalMatrix;
import gospl.entity.attribute.GSEnumAttributeType;
import gospl.entity.attribute.GosplAttributeFactory;
import gospl.generator.ISyntheticGosplPopGenerator;
import gospl.generator.UtilGenerator;

public class GosplAlgoUtilTest {

	private Logger log = LogManager.getLogger();
	
	private GosplAttributeFactory gaf = new GosplAttributeFactory();
	private ISyntheticGosplPopGenerator generator;
	private Set<DemographicAttribute> attributes;
	
	private IPopulation<ADemoEntity, DemographicAttribute, IValue> population = null;

	public GosplAlgoUtilTest(Set<DemographicAttribute> attributes, 
			ISyntheticGosplPopGenerator generator){
		this.attributes = attributes;
		this.generator = generator;
	}
	
	public GosplAlgoUtilTest(Set<DemographicAttribute> attributes){
		this(attributes, new UtilGenerator(attributes));
	}
	
	public GosplAlgoUtilTest() throws GSIllegalRangedData{
		this.attributes = new HashSet<>();
		this.attributes.add(gaf.createAttribute("Genre", GSEnumDataType.String, 
				Arrays.asList("Homme", "Femme"), GSEnumAttributeType.unique));
		this.attributes.add(gaf.createAttribute("Age", GSEnumDataType.Integer, 
				Arrays.asList("0-5", "6-15", "16-25", "26-40", "40-55", "55 et plus"), GSEnumAttributeType.range));
		this.attributes.add(gaf.createAttribute("Couple", GSEnumDataType.Boolean, 
				Arrays.asList("oui", "non"), GSEnumAttributeType.unique));
		this.attributes.add(gaf.createAttribute("Education", GSEnumDataType.String, 
				Arrays.asList("pre-bac", "bac", "licence", "master et plus"), GSEnumAttributeType.unique));
		this.attributes.add(gaf.createAttribute("Activité", GSEnumDataType.String, 
				Arrays.asList("inactif", "chomage", "employé", "fonctionnaire", "indépendant", "retraité"), GSEnumAttributeType.unique));
		this.generator = new UtilGenerator(attributes);
	}
	
	/**
	 * Create a population with random component and given attributes
	 * 
	 * @param size
	 * @return 
	 * @return
	 */
	public IPopulation<ADemoEntity,DemographicAttribute,IValue> buildPopulation(int size){
		this.population = generator.generate(size);
		return this.population;
	}
	
	// ---------------------------------------------------- //


	/**
	 * Get a contingency based on a created population using {@link #getPopulation(int)}
	 * 
	 * @param size
	 * @return
	 */
	public AFullNDimensionalMatrix<Integer> getContingency(){
		if(this.population == null)
			throw new NullPointerException("No population have been generated - see #buildPopulation");
		return new GosplNDimensionalMatrixFactory().createContingency(this.population);
	}

	/**
	 * Get a frequency based on a created population using {@link #getPopulation(int)}
	 * 
	 * @param size
	 * @return
	 */
	public AFullNDimensionalMatrix<Double> getFrequency(){
		if(this.population == null)
			throw new NullPointerException("No population have been generated - see #buildPopulation");
		return new GosplNDimensionalMatrixFactory().createDistribution(this.population);
	}

	/**
	 * Get a segmented frequency based on several created population using {@link #getPopulation(int)}
	 * 
	 * @param segmentSize
	 * @return
	 * @throws IllegalDistributionCreation
	 */
	public ASegmentedNDimensionalMatrix<Double> getSegmentedFrequency(int segmentSize) 
			throws IllegalDistributionCreation {
		if(this.population == null)
			this.buildPopulation(segmentSize);
		log.debug("Try to build segmented matrix with {} dimensions", this.attributes.size());
		Map<DemographicAttribute, Double> attributesProb = this.attributes.stream().collect(
				Collectors.toMap(Function.identity(), att -> new Double(0.5)));

		Collection<Set<DemographicAttribute>> segmentedAttribute = new HashSet<>();
		while(!segmentedAttribute.stream().flatMap(set -> set.stream())
				.collect(Collectors.toSet()).containsAll(this.attributes)){
			Set<DemographicAttribute> atts = new HashSet<>();
			for(DemographicAttribute attribute : attributesProb.keySet()){
				if(GenstarRandom.getInstance().nextDouble() < attributesProb.get(attribute)){
					atts.add(attribute);
					attributesProb.put(attribute, attributesProb.get(attribute) * 0.5); 
				} else {
					attributesProb.put(attribute, Math.tanh(attributesProb.get(attribute) + 0.5));
				}
			}
			if(atts.size() < 2)
				continue;
			log.debug("Build a new full inner matrix with {} attributes", 
					atts.stream().map(a -> a.getAttributeName()).collect(Collectors.joining(", ")));
			segmentedAttribute.add(atts);
		}
		log.debug("Build the segmented matrix with {} inner full matrix", segmentedAttribute.size());
		GosplNDimensionalMatrixFactory factory = new GosplNDimensionalMatrixFactory();
		return factory.createDistributionFromDistributions(segmentedAttribute.stream()
				.map(sa -> factory.createDistribution(sa, this.population))
				.collect(Collectors.toSet()));
	}

}
