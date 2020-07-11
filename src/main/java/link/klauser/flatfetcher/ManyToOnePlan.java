// Copyright 2020 Christian Klauser
//
// Licensed under the Apache License,Version2.0(the"License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,software
// distributed under the License is distributed on an"AS IS"BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package link.klauser.flatfetcher;

import static link.klauser.flatfetcher.PlanUtils.chunks;

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
class ManyToOnePlan<X, A, K extends Serializable> implements FetchPlan<X, A> {

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
	public Collection<A> fetch(EntityManager em, Collection<? extends X> roots, int batchSize) {
		Map<K, A> byId = new HashMap<>();
		var cb = em.getCriteriaBuilder();
		chunks(roots.stream().map(attrIdAccessor::get), batchSize).flatMap(targetIds ->{
			var assocQ = cb.createQuery(targetType.getJavaType());
			var fromTarget = assocQ.from(targetType.getJavaType());
			assocQ.where(fromTarget.get(targetIdAccessor.singularAttr()).in(targetIds));
			return em.createQuery(assocQ).getResultStream();
		}).forEach(associated -> {
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
