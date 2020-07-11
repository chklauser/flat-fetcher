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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.persistence.AttributeNode;
import javax.persistence.EntityManager;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class FlatFetcher {

	@lombok.NonNull
	final EntityManager em;

	@Value
	static class PlanKey {
		@lombok.NonNull

		EntityType<?> entityType;
		@lombok.NonNull
		String attributeName;
	}

	interface FetchNode<X> {

		String name();

		Class<X> tag();

		List<AttributeNode<?>> attributeNodes();

		Collection<X> roots();
	}

	@SuppressWarnings("rawtypes")
	final ConcurrentHashMap<PlanKey, FetchPlan> attributePlanCache = new ConcurrentHashMap<>();

	public <X> void fetch(Class<X> tag, Collection<X> roots, String entityGraphName) {
		if (roots.isEmpty()) {
			return;
		}
		if (log.isDebugEnabled()) {
			log.debug("Begin flat fetch([{}...; {}], {}) cached plans: {}", tag.getSimpleName(), roots.size(), entityGraphName,
					attributePlanCache.size());
		}
		var graph = em.getEntityGraph(entityGraphName);

		List<FetchNode<?>> fetchQueue = new ArrayList<>();
		fetchQueue.add(new FetchNode<X>() {
			@Override
			public String name() {
				return entityGraphName;
			}

			@Override
			public Class<X> tag() {
				return tag;
			}

			@Override
			public List<AttributeNode<?>> attributeNodes() {
				return graph.getAttributeNodes();
			}

			@Override
			public Collection<X> roots() {
				return roots;
			}
		});
		fetchRecursively(fetchQueue);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void fetchRecursively(List<FetchNode<?>> fetchQueue) {
		var fetchNodeIndex = 0;
		while(fetchNodeIndex < fetchQueue.size()){
			var fetchNode = fetchQueue.get(fetchNodeIndex);
			fetchNodeIndex += 1;
			if (log.isDebugEnabled()) {
				log.debug("Flat fetch([{}...; {}], {}) step {}/{}",
						fetchNode.tag().getSimpleName(), fetchNode.roots().size(), fetchNode.name(),
						fetchNodeIndex, fetchQueue.size());
			}
			var currentRootType = em.getMetamodel().entity(fetchNode.tag());
			for (AttributeNode<?> attributeNode : fetchNode.attributeNodes()) {
				// cast via raw FetchNode is necessary because Java doesn't figure out that the existentials on fetchNode and
				// currentRootType originate from the same object (fetchNode).
				fetchAttribute(fetchQueue, (FetchNode) fetchNode, currentRootType, attributeNode);
			}
		}
	}

	private <X, A> void fetchAttribute(List<FetchNode<?>> fetchQueue, FetchNode<X> fetchNode,
			EntityType<X> currentRootType, AttributeNode<A> attributeNode) {
		FetchPlan<X, A> planForNode = fetchPlanFor(currentRootType, attributeNode);
		var subRoots = planForNode.fetch(em, fetchNode.roots());
		if(!subRoots.isEmpty()) {
			for (var subgraphEntry : attributeNode.getSubgraphs().entrySet()) {
				var subgraphName = fetchNode.name() + "." + attributeNode.getAttributeName()
						+ "<" + subgraphEntry.getKey().getSimpleName() + ">";
				// This is probably closer to `? super A`, but that's not something that we can instantiate directly.
				// As we'll throw away the type information the moment, we add the FetchNode to the fetchQueue, the imprecision
				// doesn't have any impact.
				fetchQueue.add(new FetchNode<A>() {
					@Override
					public String name() {
						return subgraphName;
					}

					@SuppressWarnings("unchecked")
					@Override
					public Class<A> tag() {
						return subgraphEntry.getKey();
					}

					@SuppressWarnings("unchecked")
					@Override
					public List<AttributeNode<?>> attributeNodes() {
						return subgraphEntry.getValue().getAttributeNodes();
					}

					@Override
					public Collection<A> roots() {
						return subRoots;
					}
				});
			}
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private <X, A> FetchPlan<X, A> fetchPlanFor(EntityType<X> rootType, AttributeNode<A> attributeNode) {
		return attributePlanCache.computeIfAbsent(new PlanKey(rootType, attributeNode.getAttributeName()), k -> {
			if(log.isDebugEnabled()) {
				log.debug("Preparing fetch plan for JPA attribute {}#{}", rootType.getName(), attributeNode.getAttributeName());
			}
			FetchPlan<X, A> planForNode;
			var fetchAttr = k.entityType.getAttribute(k.getAttributeName());
			if (fetchAttr instanceof PluralAttribute) {
				planForNode = new OneToManyPlan<>(rootType, (PluralAttribute) fetchAttr);
			}
			else if (fetchAttr instanceof SingularAttribute) {
				planForNode = PlanUtils.findAnnotationOpt(fetchAttr, ManyToOne.class)
						.map(manyToOne -> (FetchPlan<X, A>) new ManyToOnePlan<>((SingularAttribute) fetchAttr))
						.orElseGet(() -> planForOneToOneAttr(rootType, (SingularAttribute) fetchAttr));
			}
			else {
				throw FlatFetcherException.onAttr("Attribute type not supported by flat fetcher: ", fetchAttr);
			}
			return planForNode;
		});
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private <X, A> FetchPlan<X, A> planForOneToOneAttr(EntityType<X> rootType, SingularAttribute<? super X, A> fetchAttr) {
		var oneToOneAnnotation = PlanUtils.findAnnotation(fetchAttr, OneToOne.class);
		if (!oneToOneAnnotation.mappedBy().isBlank()) {
			return new OneToOneOppositePlan(rootType, fetchAttr, oneToOneAnnotation);

		}
		else {
			return new OneToOneOwningPlan(rootType, fetchAttr);
		}
	}

}

