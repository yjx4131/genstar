package core.metamodel.pop;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.vividsolutions.jts.geom.Point;

import core.metamodel.IEntity;
import core.metamodel.geo.AGeoEntity;

public abstract class APopulationEntity implements IEntity<APopulationAttribute, APopulationValue> {

	private Map<APopulationAttribute, APopulationValue> attributes;
	
	/**
	 * the type of this entity (such as "household" or "individual" or "building")
	 * null if not declared.
	 */
	private String entityType = null;
	
	/**
	 * Creates a population entity by defining directly the attribute values
	 * @param attributes
	 */
	public APopulationEntity(Map<APopulationAttribute, APopulationValue> attributes) {
		this.attributes = attributes;
	}
	
	/**
	 * creates a population entity without defining its population attributes
	 * @param attributes
	 */
	public APopulationEntity() {
		this.attributes = new HashMap<APopulationAttribute, APopulationValue>();
	}
	

	/**
	 * creates a population entity by defining the attributes it will contain without attributing any value
	 * @param attributes
	 */
	public APopulationEntity(Collection<APopulationAttribute> attributes) {
		this.attributes = new HashMap<APopulationAttribute, APopulationValue>();
		for (APopulationAttribute a: attributes) {
			this.attributes.put(a, null);
		}
	}
	
	@Override
	/**
	 * Clone returns a similar population entity whose values might be modified without modifying the 
	 * parent one.
	 */
	public abstract Object clone();
	
	/** 
	 * sets the value for the attribute or updates this value
	 * @param attribute
	 * @param value
	 */
	public void setAttributeValue(APopulationAttribute attribute, APopulationValue value) {
		this.attributes.put(attribute, value);
	}
	
	
	protected APopulationAttribute getAttributeNamed(String attributeName) throws IllegalArgumentException {
		
		if (attributes.isEmpty())
			throw new IllegalArgumentException("there is no attribute defined for this entity");
		
		for (APopulationAttribute a: attributes.keySet()) {
			if (a.getAttributeName().equals(attributeName)) {
				return a;
			}
		}
		throw new IllegalArgumentException("there is no attribute named "+attributeName+" defined for this entity");
	}


	/** 
	 * sets the value for the attribute or updates this value
	 * @param attributeName
	 * @param value
	 */
	public void setAttributeValue(String attributeName, APopulationValue value) {
		
		APopulationAttribute attribute = getAttributeNamed(attributeName);
		
		this.attributes.put(attribute, value);
	}
	
	
	/** 
	 * sets the value for the attribute or updates this value
	 * @param attributeName
	 * @param value
	 */
	public void setAttributeValue(String attributeName, String valueString) {
		
		APopulationAttribute attribute = getAttributeNamed(attributeName);
		
		this.attributes.put(attribute, attribute.getValue(valueString));
	}
	

	public Map<APopulationAttribute, APopulationValue> getAttributesMap() {
		return Collections.unmodifiableMap(attributes);
	}
	
	@Override
	public Collection<APopulationAttribute> getAttributes() {
		return attributes.keySet();
	}
	
	@Override
	public final boolean hasAttribute(APopulationAttribute a) {
		return attributes.containsKey(a);
	}

	@Override
	public Collection<APopulationValue> getValues() {
		return Collections.unmodifiableCollection(attributes.values());
	}

	@Override
	public APopulationValue getValueForAttribute(APopulationAttribute attribute) {
		return attributes.get(attribute);
	}
	
	@Override
	public APopulationValue getValueForAttribute(String property){
		Optional<APopulationAttribute> opAtt = attributes.keySet()
				.stream().filter(att -> att.getAttributeName().equals(property)).findFirst();
		if(opAtt.isPresent())
			return attributes.get(opAtt.get());
		throw new NullPointerException("Attribute "+property+" does not exist in "+this.getClass().getSimpleName());
	}

	/**
	 * Retrieve the localtion of the agent as a point
	 * 
	 * @return a point of type {@link Point}
	 */
	public abstract Point getLocation();

	/**
	 * Retrieve the most significant enclosing geographical entity this
	 * entity is situated. It represents 'home's entity 
	 * 
	 * @return
	 */
	public abstract AGeoEntity getNest();

	/**
	 * Change the location of the entity
	 * 
	 * @param location
	 */
	public abstract void setLocation(Point location);

	/**
	 * Change the nest of the entity
	 * 
	 * @param entity
	 */
	public abstract void setNest(AGeoEntity entity);

	
	public String toString() {
		return attributes.entrySet().stream().map(e -> e.getKey().getAttributeName()+":"+e.getValue().getStringValue()).collect(Collectors.joining(",\t"));
	}
	

	@Override
	public final boolean hasEntityType() {
		return entityType != null;
	}

	@Override
	public final String getEntityType() {
		return entityType;
	}

	@Override
	public final void setEntityType(String type) {
		this.entityType = type;
	}
	
}
