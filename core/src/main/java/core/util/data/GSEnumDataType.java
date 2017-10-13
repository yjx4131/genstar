package core.util.data;

/**
 * 
 * 
 * @author kevinchapuis
 * @author Vo Duc An
 *
 */
public enum GSEnumDataType {

	Continue (Double.class),
	Integer (Integer.class),
	Range (String.class),
	Boolean (Boolean.class),
	Order (String.class),
	Nominal (String.class);

	private Class<?> wrapperClass;

	private GSEnumDataType(Class<?> wrapperClass){
		this.wrapperClass = wrapperClass;
	}
	
	/**
	 * Whether this {@link GSEnumDataType} is numerical or not
	 * 
	 * @return
	 */
	public boolean isNumericValue() {
		return wrapperClass.getSuperclass().equals(Number.class);
	}
	
}
