package gospl.distribution;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import core.metamodel.pop.APopulationAttribute;
import core.metamodel.pop.APopulationValue;
import core.util.data.GSDataParser;
import core.util.data.GSEnumDataType;
import gospl.distribution.exception.IllegalDistributionCreation;
import gospl.distribution.matrix.AFullNDimensionalMatrix;
import gospl.distribution.matrix.ASegmentedNDimensionalMatrix;
import gospl.distribution.matrix.control.AControl;
import gospl.distribution.matrix.control.ControlFrequency;
import gospl.distribution.matrix.coordinate.ACoordinate;

/**
 * A set of joint distributions with links of dependancy between them. 
 * 
 * @author Kevin Chapuis
 */
public class GosplConditionalDistribution extends ASegmentedNDimensionalMatrix<Double> {

	Logger logger = LogManager.getLogger();

	protected GosplConditionalDistribution(Set<AFullNDimensionalMatrix<Double>> jointDistributionSet) throws IllegalDistributionCreation {
		super(jointDistributionSet);
	}

	// --------------- Main contract --------------- //

	/**
	 * {@inheritDoc}
	 * <p>
	 * Provide the most informed control associated with the given set of aspects
	 * <br>
	 * TODO: describe the whole process
	 */
	@Override
	public AControl<Double> getVal(Collection<APopulationValue> aspects) {
		// Setup output with identity product value
		AControl<Double> conditionalProba = this.getIdentityProductVal();
		// Setup a record of visited dimension to avoid duplicated probabilities
		Set<APopulationAttribute> remainingDimension = aspects.stream()
				.map(aspect -> aspect.getAttribute()).collect(Collectors.toSet());

		// Select matrices that contains at least one concerned dimension and ordered them
		// in decreasing order of the number of matches
		List<AFullNDimensionalMatrix<Double>> concernedMatrices = jointDistributionSet.stream()
				.filter(matrix -> matrix.getDimensions().stream().anyMatch(dimension -> remainingDimension.contains(dimension)))
				.sorted((m1, m2) -> m1.getDimensions().stream().filter(dim -> remainingDimension.contains(dim)).count() >=
				m2.getDimensions().stream().filter(dim -> remainingDimension.contains(dim)).count() ? -1 : 1)
				.collect(Collectors.toList());

		// Store visited dimension to compute conditional probabilities
		Set<APopulationAttribute> assignedDimension = new HashSet<>();

		for(AFullNDimensionalMatrix<Double> mat : concernedMatrices){
			if(mat.getDimensions().stream()
					.noneMatch(dimension -> remainingDimension.contains(dimension)))
				continue;

			// Setup concerned values
			Set<APopulationValue> concernedValues = aspects.stream()
					.filter(a -> mat.getDimensions().contains(a.getAttribute()))
					.collect(Collectors.toSet());


			// Setup conditional values (known probability to compute conditional probability)
			Set<APopulationValue> conditionalValues = concernedValues.stream()
					.filter(val -> assignedDimension.stream().anyMatch(dim -> dim.getValues().contains(val)))
					.collect(Collectors.toSet());

			AControl<Double> conditionalReferent = mat.getIdentityProductVal();

			// Setup conditional bottom up values: value for which this matrix has partial 
			// bottom-up information, i.e. one attribute of this matrix has for referent
			// one value attribute for which probability has already be defined 
			Map<APopulationAttribute, APopulationAttribute> refAttributeToBottomup = mat.getDimensions().stream()
					.filter(att -> !att.getReferentAttribute().equals(att) && !att.isRecordAttribute()
							&& assignedDimension.contains(att.getReferentAttribute()))
					.collect(Collectors.toMap(att -> att.getReferentAttribute(), Function.identity()));
			if(!refAttributeToBottomup.isEmpty()){
				// Map of referent attribute and their already defined value
				Map<APopulationAttribute, Set<APopulationValue>> refAttributeToAssignedValues = 
						aspects.stream().filter(as -> assignedDimension.contains(as.getAttribute())
								&& refAttributeToBottomup.keySet().contains(as.getAttribute()))
						.collect(Collectors.groupingBy(as -> refAttributeToBottomup.get(as.getAttribute()), 
								Collectors.mapping(Function.identity(), Collectors.toSet())));
				// Map of already assigned value to conditional bottom op values
				Map<Set<APopulationValue>, Set<APopulationValue>> assignedValueToBottomup = refAttributeToAssignedValues
						.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), 
								entry -> entry.getValue().stream().flatMap(val -> entry.getKey()
										.findMappedAttributeValues(val).stream()).collect(Collectors.toSet())));
				// ADD BOTTOM UP CONDITIONAL VALUES
				conditionalValues.addAll(assignedValueToBottomup.values().stream()
						.flatMap(set -> set.stream()).collect(Collectors.toSet()));
				// WARNING: this is the independent hypothesis, another way to do can be to randomly pick
				// referent attribute value = i.e. randomly select 'x' value in entry.getKey()
				conditionalReferent.multiply(new ControlFrequency(assignedValueToBottomup.keySet()
						.stream().flatMap(set -> set.stream()).collect(Collectors.toSet()).size() * 1d / 
						assignedValueToBottomup.values().stream().flatMap(set -> set.stream())
						.flatMap(bUpAspect -> bUpAspect.getAttribute().findMappedAttributeValues(bUpAspect).stream())
						.collect(Collectors.toSet()).size()));
			}

			// Setup conditional top down values: value for which this matrix has partial 
			// top down information, i.e. one attribute of this matrix is the referent of
			// one value attribute for which probability has already be defined
			Map<APopulationAttribute, APopulationAttribute> assignedAttributeToTopdown = assignedDimension.stream()
					.filter(att -> !att.getReferentAttribute().equals(att) && !att.isRecordAttribute()
							&& mat.getDimensions().contains(att))
					.collect(Collectors.toMap(Function.identity(), att -> att.getReferentAttribute()));
			if(!assignedAttributeToTopdown.isEmpty()){
				// Map of top down attribute and bottom up already defined value
				Map<APopulationAttribute, Set<APopulationValue>> topdownToAssignedValues = 
						aspects.stream().filter(as -> assignedDimension.contains(as.getAttribute())
								&& assignedAttributeToTopdown.keySet().contains(as.getAttribute()))
						.collect(Collectors.groupingBy(as -> assignedAttributeToTopdown.get(as.getAttribute()), 
								Collectors.mapping(Function.identity(), Collectors.toSet())));
				// Map of already assigned value to conditional topdown values
				Map<Set<APopulationValue>, Set<APopulationValue>> assignedValueToTopdown = topdownToAssignedValues
						.entrySet().stream().collect(Collectors.toMap(entry -> entry.getValue(), 
								entry -> entry.getValue().stream().flatMap(val -> val.getAttribute().getReferentAttribute()
										.findMappedAttributeValues(val).stream()).collect(Collectors.toSet())));
				// ADD BOTTOM UP CONDITIONAL VALUES
				conditionalValues.addAll(assignedValueToTopdown.values().stream()
						.flatMap(set -> set.stream()).collect(Collectors.toSet()));
				// WARNING: this is the independent hypothesis, another way to do can be to randomly pick
				// referent attribute value = i.e. randomly select 'x' value in entry.getKey()
				int assignedAttributeFullSize = assignedValueToTopdown.keySet().stream()
						.map(set -> set.stream().findAny().get()).flatMap(as -> as.getAttribute().findMappedAttributeValues(as).stream())
						.collect(Collectors.toSet()).size();
				conditionalReferent.multiply(assignedValueToTopdown.keySet().stream()
						.flatMap(set -> set.stream()).collect(Collectors.toSet()).size() * 1d / assignedAttributeFullSize);
			}


			// COMPUTE BRUT PROBABILITY
			AControl<Double> brutProbability = mat.getVal(concernedValues);

			// COMPUTE CONDITIONAL PROBABILITY
			AControl<Double> conditionalUpdate = conditionalValues.isEmpty() ? 
					this.getIdentityProductVal() : mat.getVal(conditionalValues);
					conditionalUpdate.multiply(conditionalReferent);

					// Update conditional probability
			conditionalProba.multiply(brutProbability.multiply(1d / conditionalUpdate.getValue().doubleValue()));

					// Update visited probability
			Set<APopulationAttribute> updateDimension = concernedValues
					.stream().map(a -> a.getAttribute()).collect(Collectors.toSet()); 
			assignedDimension.addAll(updateDimension);
			remainingDimension.removeAll(updateDimension);
		}
		return conditionalProba;
	}

	// ------------------ Setters ------------------ //

	@Override
	public boolean addValue(ACoordinate<APopulationAttribute, APopulationValue> coordinates, AControl<? extends Number> value) {
		Set<AFullNDimensionalMatrix<Double>> jds = jointDistributionSet
				.stream().filter(jd -> jd.getDimensions().equals(coordinates.getDimensions())).collect(Collectors.toSet());
		return jds.iterator().next().addValue(coordinates, value);
	}


	@Override
	public boolean setValue(ACoordinate<APopulationAttribute, APopulationValue> coordinates, AControl<? extends Number> value) {
		Set<AFullNDimensionalMatrix<Double>> jds = jointDistributionSet
				.stream().filter(jd -> jd.getDimensions().equals(coordinates.getDimensions())).collect(Collectors.toSet());
		if(jds.size() != 1)
			return false;
		return jds.iterator().next().setValue(coordinates, value);
	}

	// ------------------ Side contract ------------------ //  

	@Override
	public AControl<Double> getNulVal() {
		return new ControlFrequency(0d);
	}

	@Override
	public AControl<Double> getIdentityProductVal() {
		return new ControlFrequency(1d);
	}

	// -------------------- Utilities -------------------- //

	@Override
	public boolean isCoordinateCompliant(ACoordinate<APopulationAttribute, APopulationValue> coordinate) {
		return jointDistributionSet.stream().anyMatch(jd -> jd.isCoordinateCompliant(coordinate));
	}

	@Override
	public AControl<Double> parseVal(GSDataParser parser, String val) {
		if(parser.getValueType(val).equals(GSEnumDataType.String) || parser.getValueType(val).equals(GSEnumDataType.Boolean))
			return getNulVal();
		return new ControlFrequency(Double.valueOf(val));
	}


}
