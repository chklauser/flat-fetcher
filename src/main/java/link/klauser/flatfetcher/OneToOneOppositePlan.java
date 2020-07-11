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

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.OneToOne;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@NotNull
class OneToOneOppositePlan<X, A, K extends Serializable> implements FetchPlan<X, A> {
	final EntityType<A> targetType;
	final Accessor<? super X, A> rootField;
	final Accessor<? super A, X> mappedByAccessor;
	final Accessor<? super A, K> mappedByIdAccessor;

	final Accessor<? super X, K> rootIdAccessor;

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
		this.rootIdAccessor = rootIdAccessorChecked(rootField, mappedByIdAccessor);
	}

	@SuppressWarnings("unchecked")
	private static <X, A, K extends Serializable> Accessor<? super X, K> rootIdAccessorChecked(Accessor<? super X, A> rootField,
			Accessor<? super A, K> mappedByIdAccessor) {
		Accessor<? super X, ?> idAccessor = Accessor.forPrimaryKeyOf((EntityType<X>) rootField.attr().getDeclaringType());
		if(!idAccessor.singularAttr().getJavaType().isAssignableFrom(mappedByIdAccessor.singularAttr().getJavaType())){
			throw FlatFetcherException.onAttr("Type " + idAccessor.singularAttr().getJavaType() + " is not compatible with " + mappedByIdAccessor
					.singularAttr().getJavaType() + ". Attribute: ", idAccessor.attr());
		}
		return (Accessor<? super X, K>) idAccessor;
	}

	@Override
	public @NotNull Collection<A> fetch(@NotNull EntityManager em, @NotNull Collection<? extends X> roots) {
		// select t from Target t where t.mappedBy in (:roots)
		var cb = em.getCriteriaBuilder();
		CriteriaQuery<A> assocQ = cb.createQuery(targetType.getJavaType());
		Root<A> fromTarget = assocQ.from(targetType.getJavaType());
		assocQ.where(fromTarget.get(mappedByAccessor.singularAttr()).in(roots));
		Map<K, A> byMappedById = new HashMap<>();
		em.createQuery(assocQ).getResultStream().forEach(associated -> {
			var id = mappedByIdAccessor.get(associated);
			var previous = byMappedById.put(id, associated);
			if (previous != null && previous != associated) {
				log.warn("Query for {} by {} resulted in two different objects that map to the same FK {}.",
						targetType.getName(), mappedByAccessor.attr().getName(), id);
			}
		});

		for(var root : roots) {
			var fkId = rootIdAccessor.get(root);
			var associatedEntity = byMappedById.get(fkId);
			rootField.set(em, root, associatedEntity);
			if (associatedEntity != null) {
				mappedByAccessor.set(em, associatedEntity, root);
			}
		}
		return byMappedById.values();
	}
}
