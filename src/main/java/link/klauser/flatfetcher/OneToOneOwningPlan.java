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

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static link.klauser.flatfetcher.PlanUtils.chunks;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.OneToOne;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class OneToOneOwningPlan<X, A, K extends Serializable> implements FetchPlan<X, A> {
	final EntityType<A> targetType;
	final Accessor<? super X, A> rootField;
	final Accessor<? super A, X> mappedByAccessor;

	final Accessor<? super A, K> targetIdAccessor;

	final Accessor<? super X, K> attrIdAccessor;

	OneToOneOwningPlan(EntityType<X> rootType, SingularAttribute<X, A> fetchAttr) {
		targetType = (EntityType<A>) fetchAttr.getType();
		rootField = Accessor.of(fetchAttr);
		var mappedByCandidates = targetType.getSingularAttributes()
				.stream()
				.filter(mappedByAttr -> PlanUtils.findAnnotationOpt(mappedByAttr, OneToOne.class)
						.map(annot -> fetchAttr.getName().equals(annot.mappedBy()))
						.orElse(false))
				.limit(2)
				.collect(toList());
		if(mappedByCandidates.size() > 1) {
			var candidateList = mappedByCandidates
					.stream()
					.map(x -> x.getName() + "." + x.getName())
					.collect(joining());
			throw new FlatFetcherException("Found more than one @OneToOne(mappedBy=\""
					+ fetchAttr.getName() + "\") on " + targetType.getName() + ": " + candidateList);
		}
		if(mappedByCandidates.size() == 1) {
			@SuppressWarnings("unchecked")
			var mappedByCandidate = (SingularAttribute<A, X>) mappedByCandidates.get(0);
			if(log.isDebugEnabled()) {
				log.debug("Inferring mappedBy of {}.{} to be {}.{} (singular).",
						rootType, fetchAttr,
						mappedByCandidate.getDeclaringType(), mappedByCandidate);
			}
			if(!mappedByCandidate.getJavaType().isAssignableFrom(rootType.getJavaType())){
				throw FlatFetcherException.onAttr("Inferred mappedBy attribute type ("
						+ mappedByCandidate.getJavaType() + ") is expected to be compatible with "
						+ targetType.getJavaType() + ". Attribute: ", mappedByCandidate);
			}
			mappedByAccessor = Accessor.of(mappedByCandidate);
		}
		else {
			if(log.isDebugEnabled()){
				log.debug("Cannot find mappedBy of {}.{} (singular). This is fine if this is a unidirectional relation.",
						rootType, fetchAttr);
			}
			mappedByAccessor = null;
		}

		attrIdAccessor = Accessor.forIdOf(fetchAttr);
		this.targetIdAccessor = PlanUtils.referencedColumnAccessor(targetType, rootField.attr());

	}

	@Override
	public Collection<A> fetch(EntityManager em, Collection<? extends X> roots, int batchSize) {
		Map<K, A> byId = new HashMap<>();
		var cb = em.getCriteriaBuilder();
		chunks(roots.stream().map(attrIdAccessor::get), batchSize).flatMap(targetIds -> {
			CriteriaQuery<A> assocQ = cb.createQuery(targetType.getJavaType());
			Root<A> fromTarget = assocQ.from(targetType.getJavaType());
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
			rootField.set(em, root, associatedEntity);
			if (mappedByAccessor != null && associatedEntity != null) {
				mappedByAccessor.set(em, associatedEntity, root);
			}
		}
		return byId.values();
	}
}
