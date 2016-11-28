package spll.datamapper.matcher;

import core.io.geo.entity.GSFeature;
import spll.datamapper.variable.ISPLVariable;

public interface ISPLMatcher<V extends ISPLVariable, T> {

	public String getName();
	
	public T getValue();
	
	public boolean expandValue(T expand);
	
	public V getVariable();

	public GSFeature getFeature();
	
	public String toString();

}