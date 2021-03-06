package spin.algo.factory;

import core.metamodel.IPopulation;
import core.metamodel.attribute.Attribute;
import core.metamodel.entity.ADemoEntity;
import core.metamodel.value.IValue;
import spin.SpinNetwork;
import spin.algo.generator.ISpinNetworkGenerator;
import spin.algo.generator.SpinCompleteNetworkGenerator;
import spin.algo.generator.SpinRandomNetworkGenerator;
import spin.algo.generator.SpinRegularNetworkGenerator;
import spin.algo.generator.SpinSFNetworkGenerator;
import spin.algo.generator.SpinSWNetworkGenerator;
import spin.algo.generator.SpinSpatialNetworkGenerator;

/** Propose de generer des reseaux 
 * Si le reseau est non oriente, chaque edges n'est mis qu'une fois, donc pas d'aller retour implicite. 
 */
public class SpinNetworkFactory {
	
	public final static String COMPLETE_NETWORK = "complete";
	public final static String RANDOM_NETWORK = "random";
	public final static String REGULAR_NETWORK = "regular";
	public final static String SPATIAL_NETWORK = "spatial";
	public final static String SCALEFREE_NETWORK = "scale_free";
	public final static String SMALLWORLD_NETWORK = "small_world";
	
	// Singleton
	private static SpinNetworkFactory INSTANCE;
	
	public static SpinNetworkFactory getInstance(){
		if(INSTANCE == null)
			INSTANCE = new SpinNetworkFactory();
		return INSTANCE;
	}

	/** Création d'un SpinNetwork correspondant à la population passée en paramètre
	 * @param population
	 * @return SpinNetwork. 
	 */
	public static SpinNetwork loadPopulation(IPopulation<? extends ADemoEntity, Attribute<? extends IValue>> population){
		// Create a SpinNetwork with nodes linked to population entities
		// The SpinNetwork has all the needed nodes and no links
		SpinNetwork myNetwork = new SpinNetwork();
		int i = 0;		
		
		// create all the nodes 
		for (ADemoEntity entity : population) {
			myNetwork.putNode(String.valueOf(i), entity);
			i++;
		}
		
		return myNetwork;
	}
	
	/** Création d'une SpinPopulation dont le réseau correspond à la population passée en paramètre.
	 * Le réseau généré est du type passé en paramètre.
	 * 
	 * @param typeGenerator Type du reseau genere
	 * @param population Population en parametre. 
	 * @return SpinPopulation
	 */
	// FIXME : à remettre 
/*	public <E extends ADemoEntity> SpinPopulation<E> generateNetwork(ENetworkGenerator typeGenerator, 
			IPopulation<E, Attribute<? extends IValue>> population){
		SpinNetwork baseNetwork = loadPopulation(population);
		if(typeGenerator.equals(ENetworkGenerator.SmallWorld))
			network = new SWNetworkGenerator().generateNetwork(baseNetwork, 5, .1); 
		if(typeGenerator.equals(ENetworkGenerator.Random))	
			network = new RandomNetworkGenerator().generateNetwork(baseNetwork, .0005);
		if(typeGenerator.equals(ENetworkGenerator.Regular))	
			network = new RegularNetworkGenerator().generateNetwork(baseNetwork, 5);
		if(typeGenerator.equals(ENetworkGenerator.ScaleFree))	
			network = new SFNetworkGenerator().generateNetwork(baseNetwork);
		if(typeGenerator.equals(ENetworkGenerator.Spatial))	
			network = new SpatialNetworkGenerator().generateNetwork(baseNetwork,800,600,3);
		
		// Create the SpinPopulation
		return new SpinPopulation<>(population, network);
	}
	*/
	
	// TODO : add tests sur les paramètres
	public ISpinNetworkGenerator<? extends ADemoEntity> getSpinPopulationGenerator(String networkName, String graphGenerator, double probaDistance, int k) {
		if(COMPLETE_NETWORK.equals(graphGenerator)) {
			return new SpinCompleteNetworkGenerator<>(networkName);
		}
		if(RANDOM_NETWORK.equals(graphGenerator)) {
			return new SpinRandomNetworkGenerator<>(networkName, probaDistance);
		}
		if(SCALEFREE_NETWORK.equals(graphGenerator)) {
			return new SpinSFNetworkGenerator<>(networkName);
		}
		if(SMALLWORLD_NETWORK.equals(graphGenerator)) {
			return new SpinSWNetworkGenerator<>(networkName, k, probaDistance);
		}
		if(REGULAR_NETWORK.equals(graphGenerator)) {
			return new SpinRegularNetworkGenerator<>(networkName, k);
		}
		if(SPATIAL_NETWORK.equals(graphGenerator)) {
			return new SpinSpatialNetworkGenerator<>(networkName, probaDistance);
		}
		
		return null;
	}	
	
}
