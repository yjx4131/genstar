package gospl.algo.ipf;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import core.metamodel.IPopulation;
import core.metamodel.attribute.demographic.DemographicAttribute;
import core.metamodel.entity.ADemoEntity;
import core.metamodel.value.IValue;
import core.util.GSPerformanceUtil;
import gospl.algo.ipf.margin.AMargin;
import gospl.algo.ipf.margin.IMarginalsIPFBuilder;
import gospl.algo.ipf.margin.MarginalsIPFBuilder;
import gospl.distribution.matrix.AFullNDimensionalMatrix;
import gospl.distribution.matrix.INDimensionalMatrix;
import gospl.distribution.matrix.control.AControl;
import gospl.distribution.matrix.control.ControlFrequency;
import gospl.distribution.matrix.coordinate.ACoordinate;
import gospl.sampler.IDistributionSampler;
import gospl.sampler.IEntitySampler;

/**
 * 
 * Higher order abstraction that contains basics principle calculation for IPF. <br>
 * Concrete classes should provide two type of outcomes:
 * <p>
 * <ul>
 * <li> one to draw from a distribution using a {@link IDistributionSampler}
 * <li> ont to draw from a samble using a {@link IEntitySampler}
 * </ul>
 * <p>
 * Two concerns must be cleared up for {@link AGosplIPF} to be fully and explicitly setup: 
 * <p>
 * <ul>
 * <li> Convergence criteria: could be a number of maximum iteration {@link AGosplIPF#step},
 * a maximal error for any objective {@link AGosplIPF#delta} or an increase in error fitting lower that delta itself
 * <li> zero-cell problem: As it is impossible to distinguish between structural 0 cell - i.e. impossible
 * set of value, like being age under 5 and retired - and conjonctural 0 cell - i.e. a set of value for
 * which we do not have any record  
 * <li> zero-margin problem: As we use sparse collection to store marginal records hence 0 margin is not
 * a problem at all
 * </ul>
 * <p>
 * Usefull information could be found at {@link http://u.demog.berkeley.edu/~eddieh/datafitting.html}
 * <p>
 * 
 * TODO: make it possible to choose the function to establish criteria - here is AAPD but could have been SRMSE
 * 
 * @author kevinchapuis
 *
 */
public abstract class AGosplIPF<T extends Number> {

	private int step = 100;
	private double delta = Math.pow(10, -4);
	private double aapd = Double.MAX_VALUE;

	private Logger logger = LogManager.getLogger();

	protected IPopulation<ADemoEntity, DemographicAttribute<? extends IValue>> sampleSeed;
	protected INDimensionalMatrix<DemographicAttribute<? extends IValue>, IValue, T> marginals;
	protected IMarginalsIPFBuilder<T> marginalProcessor;

	protected AGosplIPF(IPopulation<ADemoEntity, DemographicAttribute<? extends IValue>> sampleSeed,
			IMarginalsIPFBuilder<T> marginalProcessor, int step, double delta){
		this.sampleSeed = sampleSeed;
		this.marginalProcessor = marginalProcessor;
		this.step = step;
		this.delta = delta;
	}

	protected AGosplIPF(IPopulation<ADemoEntity, DemographicAttribute<? extends IValue>> sampleSeed,
			int step, double delta){
		this(sampleSeed, new MarginalsIPFBuilder<T>(), step, delta);
	}

	protected AGosplIPF(IPopulation<ADemoEntity, DemographicAttribute<? extends IValue>> sampleSeed,
			IMarginalsIPFBuilder<T> marginalProcessor){
		this.sampleSeed = sampleSeed;
		this.marginalProcessor = marginalProcessor;
	}

	protected AGosplIPF(IPopulation<ADemoEntity, DemographicAttribute<? extends IValue>> sampleSeed){
		this(sampleSeed, new MarginalsIPFBuilder<T>());
	}

	/**
	 * Setup the matrix that define marginal control. May be a full or segmented matrix: the first one
	 * will give actual marginal, while the second one will give estimate marginal
	 * 
	 * @see INDimensionalMatrix#getVal(Collection)
	 * @param marginals
	 */
	protected void setMarginalMatrix(INDimensionalMatrix<DemographicAttribute<? extends IValue>, IValue, T> marginals){
		this.marginals = marginals;
	}

	/**
	 * Setup iteration number stop criteria
	 * 
	 * @param maxStep
	 */
	protected void setMaxStep(int maxStep){
		this.step = maxStep;
	}

	/**
	 * Setup maximum delta (i.e. the relative absolute difference between actual and expected marginals)
	 * stop criteria 
	 * 
	 * @param delta
	 */
	protected void setMaxDelta(double delta) {
		this.delta = delta;
	}

	//////////////////////////////////////////////////////////////
	// ------------------------- ALGO ------------------------- //
	//////////////////////////////////////////////////////////////

	/**
	 * Main estimation method: iteratively fit the distribution to marginal
	 * constraint using odd ratio procedure
	 * 
	 * @return
	 */
	public abstract AFullNDimensionalMatrix<T> process();

	/**
	 * Main estimation method using parametrized delta threshold and maximum step iteration
	 * 
	 * @see AGosplIPF#process()
	 * 
	 * @param delta
	 * @param step
	 * @return
	 */
	public AFullNDimensionalMatrix<T> process(double delta, int step){
		this.delta = delta;
		this.step = step;
		return process();
	}

	// ------------------------- GENERIC IPF ------------------------- //

	/**
	 * Describe the <i>estimation factor</i> IPF algorithm:
	 * <p>
	 * <ul>
	 * <li> Setup convergence criteria and iterate while criteria is not fulfill
	 * <li> Compute the factor to fit control
	 * <li> Adjust dimensional values to fit control
	 * <li> Update convergence criteria
	 * </ul>
	 * <p>
	 * There is other algorithm for IPF. This one is the most simple one and also the more
	 * adaptable to a n-dimensional matrix, because it does not include any matrix calculation
	 * 
	 * @param seed
	 * @return
	 */
	protected AFullNDimensionalMatrix<T> process(AFullNDimensionalMatrix<T> seed) {
		if(seed.getDimensions().stream().noneMatch(dim -> marginals.getDimensions().contains(dim) || 
				marginals.getDimensions().contains(dim.getReferentAttribute())))
			throw new IllegalArgumentException("Output distribution and sample seed does not have any matching dimensions\n"
					+ "Distribution: "+Arrays.toString(marginals.getDimensions().toArray()) +"\n"
					+ "Sample seed: :"+Arrays.toString(seed.getDimensions().toArray()));
		
		List<DemographicAttribute<? extends IValue>> unmatchSeedAttribute = seed.getDimensions().stream()
				.filter(dim -> marginals.getDimensions().contains(dim) 
						|| marginals.getDimensions().contains(dim.getReferentAttribute()))
				.collect(Collectors.toList());

		GSPerformanceUtil gspu = new GSPerformanceUtil("*** IPF PROCEDURE ***", logger, Level.DEBUG);

		logger.debug(unmatchSeedAttribute.size() / (double) seed.getDimensions().size() * 100d
				+"% of samples dimensions will be estimate with output controls");
		
		logger.debug("Sample seed controls' dimension: "+seed.getDimensions()
			.stream().map(d -> d.getAttributeName()+" = "+d.getValueSpace().getValues().size())
			.collect(Collectors.joining(";")));

		Collection<AMargin<T>> marginals = marginalProcessor.buildCompliantMarginals(this.marginals, seed, true);

		int stepIter = step;
		int totalNumberOfMargins = marginals.stream().mapToInt(AMargin::size).sum();
		gspu.sysoStempMessage("Convergence criterias are: step = "+step+" | delta = "+delta);
				
		double total = this.marginals.getVal().getValue().doubleValue();
		aapd = marginals.stream().mapToDouble(m -> m.getSeedMarginalDescriptors()
				.stream().mapToDouble(sd -> Math.abs(seed.getVal(sd).getDiff(m.getControl(sd))
						.doubleValue()) / total).sum()).sum() / totalNumberOfMargins;
		gspu.sysoStempMessage("Start fitting iterations with AAPD = "+aapd);

		double relativeIncrease = Double.MAX_VALUE;
		
		while(stepIter-- > 0 ? aapd > delta || relativeIncrease < delta : false){
			if(stepIter % (int) (step * 0.1) == 0d)
				gspu.sysoStempMessage("Step = "+(step - stepIter)+" | average error = "+aapd);
			for(AMargin<T> margin : marginals){
				for(Set<IValue> seedMarginalDescriptor : margin.getSeedMarginalDescriptors()){
					double marginValue = margin.getControl(seedMarginalDescriptor).getValue().doubleValue();
					double actualValue = seed.getVal(seedMarginalDescriptor).getValue().doubleValue();
					AControl<Double> factor = new ControlFrequency(marginValue/actualValue);
					Collection<ACoordinate<DemographicAttribute<? extends IValue>, IValue>> relatedCoordinates = 
							seed.getCoordinates(seedMarginalDescriptor); 
					for(ACoordinate<DemographicAttribute<? extends IValue>, IValue> coord : relatedCoordinates) {
						AControl<T> av = seed.getVal(coord);
						logger.trace("Coord {}: AV = {} and UpdatedV = {}",
							coord, av.getValue().doubleValue(), av.multiply(factor).getValue().doubleValue());
					}
				}
			}

			double cachedAapd = marginals.stream().mapToDouble(m -> m.getSeedMarginalDescriptors()
					.stream().mapToDouble(sd -> Math.abs(seed.getVal(sd).getDiff(m.getControl(sd))
							.doubleValue()) / total).sum()).sum() / totalNumberOfMargins;
			relativeIncrease = Math.abs(aapd - cachedAapd);
			aapd = cachedAapd;
		}
		gspu.sysoStempMessage("IPF fitting ends with final "+aapd+" AAPD value");
		return seed;
	}

}
