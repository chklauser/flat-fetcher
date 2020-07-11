package link.klauser.flatfetcher;

import static java.util.stream.Collectors.toList;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.JoinColumn;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManyToOnePlan<X, A, K extends Serializable> implements FetchPlan<X, A> {

	final EntityType<A> targetType;

	private final Accessor<? super X, A> attrAccessor;
	private final Accessor<? super X, K> attrIdAccessor;

	private final Accessor<? super A, K> targetIdAccessor;

	public ManyToOnePlan(SingularAttribute<X, A> fetchAttr) {
		targetType = (EntityType<A>) fetchAttr.getType();
		attrAccessor = Accessor.of(fetchAttr);
		attrIdAccessor = Accessor.forIdOf(fetchAttr);

		// Cannot use PlanUtils.referencedColumnAccessor here because the opposite attribute is
		// a collection.
		var joinColumnAnnotation = PlanUtils.findAnnotation(fetchAttr, JoinColumn.class);
		if(!joinColumnAnnotation.referencedColumnName().isBlank()) {
			var referencedColumn = targetType.getSingularAttribute(
					joinColumnAnnotation.referencedColumnName(),
					attrIdAccessor.singularAttr().getJavaType());
			targetIdAccessor = Accessor.of(referencedColumn);
		}
		else {
			@SuppressWarnings("unchecked")
			var genericTargetIdAccess = (Accessor<? super A, K>) Accessor.forPrimaryKeyOf(targetType);
			this.targetIdAccessor = genericTargetIdAccess;
		}
	}

	@Override
	public Collection<A> fetch(EntityManager em, Collection<? extends X> roots) {
		Map<K, A> byId = new HashMap<>();
		var cb = em.getCriteriaBuilder();
		var assocQ = cb.createQuery(targetType.getJavaType());
		var fromTarget = assocQ.from(targetType.getJavaType());
		var targetIds = roots.stream().map(attrIdAccessor::get).collect(toList());
		assocQ.where(fromTarget.get(targetIdAccessor.singularAttr()).in(targetIds));
		em.createQuery(assocQ).getResultStream().forEach(associated -> {
			var id = targetIdAccessor.get(associated);
			var previous = byId.put(id, associated);
			if (previous != null && previous != associated) {
				log.warn("Query for {} by {} resulted in two different objects that map to the same FK {}.",
						targetType.getName(), targetIdAccessor.singularAttr().getName(), id);
			}
		});

		for (var root : roots) {
			var fkId = attrIdAccessor.get(root);
			var associatedEntity = byId.get(fkId);
			attrAccessor.set(em, root, associatedEntity);
			// will not touch opposite because it is a collection from which we only have 1 element. There are no
			// "partially lazy" collections in Hibernate.
		}
		return byId.values();
	}
}
