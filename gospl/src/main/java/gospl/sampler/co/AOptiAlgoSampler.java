package gospl.sampler.co;

import java.util.Collection;

import core.metamodel.pop.APopulationEntity;
import core.util.random.GenstarRandom;
import gospl.algo.co.metamodel.IGSOptimizationAlgorithm;
import gospl.algo.co.metamodel.IGSSampleBasedCOSolution;
import gospl.distribution.matrix.AFullNDimensionalMatrix;
import gospl.sampler.IEntitySampler;

public abstract class AOptiAlgoSampler<A extends IGSOptimizationAlgorithm> implements IEntitySampler {

	protected Collection<APopulationEntity> sample;
	protected RandomSampler basicSampler;
	protected A algorithm;
	
	private IGSSampleBasedCOSolution solution;

	@Override
	public APopulationEntity draw() {
		if(solution == null)
			throw new RuntimeException("You cannot draw a unique entity before "
					+ "drawing an entire collection of entities, i.e. a solution to tabu search");
		return (APopulationEntity) this.solution.getSolution().toArray()[GenstarRandom.getInstance().nextInt(sample.size())];
	}
	
	@Override
	public void setSample(Collection<APopulationEntity> sample) {
		this.sample = sample;
		this.basicSampler = new RandomSampler();
		basicSampler.setSample(sample);
	}

	@Override
	public void addObjectives(AFullNDimensionalMatrix<Integer> objectives) {
		this.algorithm.addObjectives(objectives);
	}
	
	@Override
	public String toCsv(String csvSeparator) {
		// TODO Auto-generated method stub
		return null;
	}

}