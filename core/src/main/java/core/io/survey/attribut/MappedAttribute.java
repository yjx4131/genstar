package core.io.survey.attribut;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import core.io.survey.attribut.value.AValue;
import core.util.data.GSEnumDataType;

/**
 * TODO: javadoc
 * 
 * @author kevinchapuis
 *
 */
public class MappedAttribute extends ASurveyAttribute {

	/*
	 * Keys refer to this attribute values, and values refer to referent attribute values
	 */
	private Map<Set<String>, Set<String>> mapper;

	public MappedAttribute(String name, GSEnumDataType dataType, ASurveyAttribute referentAttribute,
			Map<Set<String>, Set<String>> mapper) {
		super(name, dataType, referentAttribute);
		this.mapper = mapper;
	}

	@Override
	public boolean isRecordAttribute() {
		return false;
	}

	public Map<Set<String>, Set<String>> getMapper(){
		return Collections.unmodifiableMap(mapper);
	}

	@Override
	public Set<AValue> findMappedAttributeValues(AValue val) {
		Optional<Entry<Set<String>, Set<String>>> optMap = mapper.entrySet().stream()
				.filter(e -> e.getKey().contains(val.getInputStringValue())).findFirst();
		if(optMap.isPresent())
			return this.getReferentAttribute().getValues().stream()
					.filter(refVal -> optMap.get().getValue().contains(refVal.getInputStringValue()))
					.collect(Collectors.toSet());
		return Stream.of(this.getEmptyValue()).collect(Collectors.toSet());
	}

}