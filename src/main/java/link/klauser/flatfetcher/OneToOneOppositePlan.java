package link.klauser.flatfetcher;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.persistence.EntityManager;
import javax.persistence.OneToOne;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OneToOneOppositePlan<X extends BaseEntity, A extends BaseEntity, K extends Serializable> implements FetchPlan<X> {
	final EntityType<A> targetType;
	final Accessor<? super X, A> rootField;
	final Accessor<? super A, X> mappedByAccessor;
	final Accessor<? super A, K> mappedByIdAccessor;

	OneToOneOppositePlan(EntityType<X> rootType, SingularAttribute<X, A> fetchAttr, OneToOne oneToOneAnnotation) {
		targetType = (EntityType<A>) fetchAttr.getType();
		rootField = Accessor.of(fetchAttr);
		@SuppressWarnings("unchecked")
		Attribute<A, X> mappedByAttr = (Attribute<A, X>) targetType.getAttribute(oneToOneAnnotation.mappedBy());
		if(!mappedByAttr.getJavaType().isAssignableFrom(rootType.getJavaType())) {
			throw FlatFetcherException.onAttr("Type " + mappedByAttr.getJavaType() + " is not compatible with "
					+ rootType.getJavaType() + ". Attribute: ", mappedByAttr);
		}
		mappedByAccessor = Accessor.of(mappedByAttr);
		mappedByIdAccessor = Accessor.forIdOf(mappedByAttr);
	}

	@Override
	public void fetch(EntityManager em, Collection<? extends X> roots) {
		// select t from Target t where t.mappedBy in (:roots)
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<A> assocQ = cb.createQuery(targetType.getJavaType());
		Root<A> fromTarget = assocQ.from(targetType.getJavaType());
		assocQ.where(fromTarget.get(mappedByAccessor.singularAttr()).in(roots));
		Map<UUID, A> byMappedById = new HashMap<>();
		em.createQuery(assocQ).getResultStream().forEach(associated -> {
			var id = (UUID) mappedByIdAccessor.get(associated);
			var previous = byMappedById.put(id, associated);
			if (previous != null && previous != associated) {
				log.warn("Query for {} by {} resulted in two different objects that map to the same FK {}.",
						targetType.getName(), mappedByAccessor.attr().getName(), id);
			}
		});

		for(var root : roots) {
			var fkId = root.getId();
			var associatedEntity = byMappedById.get(fkId);
			rootField.set(em, root, associatedEntity);
			if (associatedEntity != null) {
				mappedByAccessor.set(em, associatedEntity, root);
			}
		}
	}
}
