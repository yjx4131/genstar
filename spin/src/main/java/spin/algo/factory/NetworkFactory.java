package spin.algo.factory;

import java.util.Hashtable;

import org.graphstream.graph.Graph;

import core.metamodel.IPopulation;
import core.metamodel.pop.APopulationAttribute;
import core.metamodel.pop.APopulationEntity;
import core.metamodel.pop.APopulationValue;
import spin.algo.generator.SWGenerator;
import spin.interfaces.EGraphStreamNetworkType;
import spin.interfaces.ENetworkEnumGenerator;
import spin.objects.SpinNetwork;

/** Propose de générer des réseaux 
 *
 */
public class NetworkFactory {
	
	private SpinNetwork network;
	
	// Singleton
	private static NetworkFactory INSTANCE;
	
	public static NetworkFactory getIntance(){
		if(INSTANCE == null)
			INSTANCE = new NetworkFactory();
		return INSTANCE;
	}
	
	private NetworkFactory(){
	}
	
	/** Renvoi un spinNetwork sur une population passé en paramètre, en prenant une population
	 * en entrée.
	 * 
	 * @param typeGenerator Type du réseau généré
	 * @param population Population en parametre. 
	 * @return
	 */
	public SpinNetwork generateNetwork(ENetworkEnumGenerator typeGenerator, IPopulation<APopulationEntity, APopulationAttribute, APopulationValue> population){
		if(typeGenerator.equals(ENetworkEnumGenerator.SmallWorld))
			network = new SWGenerator().generateNetwork(population); 
			
//		if(typeGenerator.equals(NetworkEnumGenerator.ScaleFree))
//			return new SFGenerator().generateNetwork(population);
		
		return network;
	}
	
	public SpinNetwork getSpinNetwork(){
		return this.network;
	}
 
	
}
