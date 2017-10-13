package gospl.algo.is;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import core.metamodel.IPopulation;
import core.metamodel.pop.DemographicAttribute;
import core.metamodel.pop.ADemoEntity;
import core.metamodel.pop.IValue;
import gospl.algo.GosplAlgoUtilTest;
import gospl.algo.sr.ISyntheticReconstructionAlgo;
import gospl.algo.sr.is.IndependantHypothesisAlgo;
import gospl.distribution.exception.IllegalDistributionCreation;
import gospl.distribution.matrix.ASegmentedNDimensionalMatrix;
import gospl.generator.DistributionBasedGenerator;
import gospl.generator.ISyntheticGosplPopGenerator;
import gospl.sampler.IDistributionSampler;
import gospl.sampler.sr.GosplBasicSampler;

public class IndependentHypothesisAlgoTest {
	
	public static ASegmentedNDimensionalMatrix<Double> partialDistribution; 
	
	public static int SEGMENT_SIZE = 100000;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		partialDistribution = new GosplAlgoUtilTest().getSegmentedFrequency(SEGMENT_SIZE);
	}

	@Test
	public void test() {
		assertTrue(partialDistribution.isSegmented());
		assertTrue(!partialDistribution.getMatrix().isEmpty());
		
		System.out.println(partialDistribution.toString());
		
		IDistributionSampler sampler = new GosplBasicSampler();
		ISyntheticGosplPopGenerator generator = new DistributionBasedGenerator(sampler);
		ISyntheticReconstructionAlgo<IDistributionSampler> isAlgo = new IndependantHypothesisAlgo();
		
		try {
			isAlgo.inferSRSampler(partialDistribution, sampler);
		} catch (IllegalDistributionCreation e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		IPopulation<ADemoEntity, DemographicAttribute, IValue> pop = generator.generate(SEGMENT_SIZE);

		assertEquals(pop.size(), SEGMENT_SIZE, 0.01);
		
	}

}
