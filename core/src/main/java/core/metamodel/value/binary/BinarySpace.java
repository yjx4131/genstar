package core.metamodel.value.binary;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import core.metamodel.attribute.IAttribute;
import core.metamodel.attribute.IValueSpace;
import core.util.data.GSEnumDataType;

/**
 * Value space of boolean value
 * 
 * @author kevinchapuis
 *
 */
public class BinarySpace implements IValueSpace<BooleanValue> {
	
	private Set<BooleanValue> values;
	private BooleanValue emptyValue;

	private IAttribute<BooleanValue> attribute;
	
	/**
	 * Constraint bianry space constructor define with values: {@link Boolean#TRUE}, {@link Boolean#FALSE}
	 * and a null {@link Boolean} as empty value
	 * 
	 * @param attribute
	 */
	public BinarySpace(IAttribute<BooleanValue> attribute){
		this.attribute = attribute;
		this.values = Stream.of(new BooleanValue(this, true), new BooleanValue(this, false))
				.collect(Collectors.toSet());
		this.emptyValue = new BooleanValue(this, null);
	}
	
	// ---------------------------------------------------------------------- //

	@Override
	public BooleanValue getInstanceValue(String value) {
		return this.getValue(value);
	}
	
	@Override
	public BooleanValue proposeValue(String value) {
		return this.getValue(value);
	}
	
	@Override
	public BooleanValue addValue(String value) throws IllegalArgumentException {
		return this.getValue(value);
	}

	@Override
	public BooleanValue getValue(String value) throws NullPointerException {
		if(!isValidCandidate(value))
			throw new NullPointerException("The string value "+value
					+" cannot be resolve to boolean as defined by "+this.getClass().getSimpleName());
		return values.stream().filter(val -> val.getStringValue().equalsIgnoreCase(value)).findFirst().get();
	}
	
	@Override
	public Set<BooleanValue> getValues(){
		return Collections.unmodifiableSet(values);
	}

	@Override
	public IAttribute<BooleanValue> getAttribute() {
		return attribute;
	}
	
	@Override
	public GSEnumDataType getType() {
		return GSEnumDataType.Boolean;
	}
	
	@Override
	public BooleanValue getEmptyValue() {
		return emptyValue;
	}
	
	@Override
	public void setEmptyValue(String value){
		// JUST DONT DO THAT
	}
	
	@Override
	public boolean isValidCandidate(String value) {
		if(!value.equalsIgnoreCase(Boolean.TRUE.toString()) 
				|| !value.equalsIgnoreCase(Boolean.FALSE.toString())
				|| emptyValue.getStringValue().equalsIgnoreCase(value))
			return true;
		return false;
	}
	
	// ---------------------------------------------- //
	
	@Override
	public int hashCode() {
		return this.getHashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		return this.isEqual(obj);
	}
	
	@Override
	public String toString() {
		return this.toPrettyString();
	}
	
}
