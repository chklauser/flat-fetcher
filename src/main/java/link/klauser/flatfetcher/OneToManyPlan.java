package link.klauser.flatfetcher;

import static java.util.stream.Collectors.groupingBy;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.persistence.EntityManager;
import javax.persistence.OneToMany;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.Type;

class OneToManyPlan<X extends BaseEntity, C extends Collection<A>, A, K extends Serializable> implements FetchPlan<X> {

	final PluralAttribute<? super X, C, A> fetchAttr;
	final Accessor<? super X, C> rootField;
	final Type<A> targetType;
	final Accessor<? super A, X> mappedByAccessor;
	final Accessor<? super A, K> mappedByIdAccessor;

	final Supplier<C> emptyCollectionSupplier;

	final Accessor<? super X, K> rootIdAccessor;

	OneToManyPlan(EntityType<X> rootType, PluralAttribute<? super X, C, A> fetchAttr) {
		this.fetchAttr = fetchAttr;
		rootField = Accessor.of(fetchAttr);
		targetType = fetchAttr.getElementType();
		var oneToManyAnnotation = PlanUtils.findAnnotation(fetchAttr, OneToMany.class);
		var mappedByAttrName = oneToManyAnnotation.mappedBy();
		if (mappedByAttrName.isBlank()) {
			throw FlatFetcherException.onAttr("FlatFetcher does not support unidirectional @OneToMany. "
					+ "`mappedBy` is required.", fetchAttr);
		}
		@SuppressWarnings("unchecked")
		var mappedByAttr = (SingularAttribute<A, X>)((EntityType<A>) targetType).getSingularAttribute(mappedByAttrName);
		if(!rootType.getJavaType().isAssignableFrom(mappedByAttr.getJavaType())) {
			throw FlatFetcherException.onAttr("Expected attribute to be assignable to " + targetType.getJavaType()
					+ ", but found " + mappedByAttr.getJavaType() + " as the type of ", mappedByAttr);
		}
		mappedByAccessor = Accessor.of(mappedByAttr);
		mappedByIdAccessor = Accessor.forIdOf(mappedByAttr);
		emptyCollectionSupplier = PlanUtils.emptyCollectionSupplierFor(fetchAttr);
		this.rootIdAccessor = PlanUtils.referencedColumnAccessor(rootType, mappedByAttr);
		if(!mappedByIdAccessor.attr().getJavaType().isAssignableFrom(rootIdAccessor.attr().getJavaType())) {
			throw FlatFetcherException.onAttr("Key type on root (" + rootIdAccessor.attr().getJavaType() + " "
					+ rootIdAccessor.attr()+ ") does not match type on mappedBy FK ("
					+ mappedByIdAccessor.attr().getJavaType() + ") ", mappedByAttr);
		}
	}

	@Override
	public void fetch(EntityManager em, Collection<? extends X> roots) {
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<A> assocQ = cb.createQuery(targetType.getJavaType());
		Root<A> fromTarget = assocQ.from(targetType.getJavaType());
		assocQ.where(fromTarget.get(mappedByAccessor.singularAttr().getName()).in(roots));

		Map<K, List<A>> byRootId = em.createQuery(assocQ).getResultStream()
				.collect(groupingBy(mappedByIdAccessor::get));
		for (X root : roots) {
			var rootCollection = emptyCollectionSupplier.get();
			rootField.set(em, root, rootCollection);
			var children = byRootId.getOrDefault(rootIdAccessor.get(root), Collections.emptyList());
			rootCollection.addAll(children);
			for (var child : children) {
				mappedByAccessor.set(em, child, root);
			}
		}
	}
}