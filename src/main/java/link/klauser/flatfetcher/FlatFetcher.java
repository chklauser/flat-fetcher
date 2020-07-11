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
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.AttributeNode;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.LazyToOne;
import org.hibernate.annotations.LazyToOneOption;

/**
 * <p>
 * Efficiently fetches named entity graphs in bulk. Avoids both N+1 and accidental cartesian products.
 * </p>
 * <p>
 *     There are a number of pre-requisites for flat fetcher to work as expected:
 * </p>
 * <ol>
 *     <li>The persistence provider has to be Hibernate. Other JPA implementations are not supported.</li>
 *     <li>For {@code @XxxToOne} mappings, lazy initialization byte code enhancement needs to be applied to your entity classes.
 *     See <a href="https://docs.jboss.org/hibernate/orm/5.4/userguide/html_single/Hibernate_User_Guide.html#BytecodeEnhancement">
 *         Section 5.2 Bytecode Enhancement in the Hibernate documentation</a>.</li>
 *     <li>Your associations need to be marked as {@link FetchType#LAZY}.</li>
 *     <li>Each {@code XxxToOne} association needs to be marked with {@code @}{@link LazyToOne}{@code (}{@link LazyToOneOption#NO_PROXY}{@code )}.</li>
 *     <li>Access to associations needs to happen through properties:
 *     {@code @}{@link Access}{@code (}{@link AccessType#PROPERTY}{@code )}. This restriction only applies to associations.
 *     Ordinary columns can use {@link AccessType#FIELD}.</li>
 *     <li>Each {@code XxxToOne} association with a {@code @}{@link javax.persistence.JoinColumn} needs to be accompanied by a
 *     second mapping of the same underlying DB column to access the join column value (the FK ID) directly. The ID-attribute has
 *     to have the same name as the association but with the suffix {@code "Id"}. Depending on the configured naming strategy, you
 *     might have to explicitly specify the name of the join column.
 *     </li>
 *     <li>You need to have a {@code @}{@link javax.persistence.NamedEntityGraph} on your entity that includes all of the
 *     attributes that you want to fetch. {@link FetchType#EAGER} attributes will not be fetched unless they are explicitly
 *     included in the entity graph.</li>
 * </ol>
 * <p>Example:</p>
 * <pre>{@code
 *   @JoinColumn(name = "fooId")
 *   @ManyToOne(fetch = FetchType.LAZY, optional = false)
 *   @LazyToOne(LazyToOneOption.NO_PROXY)
 *   @Access(AccessType.PROPERTY)
 *   Foo foo;
 *
 *   @Column(name = "fooId", insertable = false, updatable = false)
 *   UUID fooId;
 * }</pre>
 * <p>
 *     {@link FlatFetcher} needs to perform some reflection to figure out, how to perform the desired queries. These "query plans"
 *     are cached in a {@link FlatFetcher} instance, but not shared between flat fetcher instances. It is intended to be used
 *     with a proxied {@link EntityManager} and shared between threads.
 * </p>
 * <p>
 *     While concurrent use of {@link FlatFetcher} may result in some duplicate work when determining query plans,
 *     {@link FlatFetcher} is thread-safe.
 * </p>
 */
@RequiredArgsConstructor
@Slf4j
public class FlatFetcher {

	@lombok.NonNull
	final EntityManager em;

	/**
	 * <p>Upper limit on how many rows to request in a single query.</p>
	 * <p>The {@link FlatFetcher} mostly produces
	 * {@code where ... in (...)} queries, which Hibernate translates as {@code where ... in (?, ?, ..., ?)}. Some RDBMSs,
	 * such as Oracle DB, have limits on how long an SQL query can be and how long the {@code in} list can be.</p>
	 */
	@Getter
	@Setter
	volatile int batchSize = 500;

	/**
	 * A tuple of a collection of {@link #roots()} and the union of a {@link javax.persistence.EntityGraph}
	 * and {@link javax.persistence.Subgraph}.
	 * @param <X> The type of entity that the graph object describes fetching for.
	 */
	interface FetchNode<X> {

		String name();

		Class<X> tag();

		List<AttributeNode<?>> attributeNodes();

		Collection<X> roots();
	}

	@Value
	static class PlanKey {
		@lombok.NonNull

		EntityType<?> entityType;
		@lombok.NonNull
		String attributeName;
	}

	@SuppressWarnings("rawtypes")
	final ConcurrentHashMap<PlanKey, FetchPlan> attributePlanCache = new ConcurrentHashMap<>();

	/**
	 * <p>
	 * Fetches associated entities for the associations included in the {@code @}{@link javax.persistence.NamedEntityGraph} with
	 * the supplied {@code entityGraphName} for all of the {@code roots}.
	 * </p>
	 * <p>
	 *     Fills forward and, where possible, backwards (mappedBy) relations.
	 * </p>
	 * @param tag Entity type on which entity graph attributes are looked up.
	 * @param roots The entities for which to fetch the associations listed in the entity graph
	 * @param entityGraphName The name of the entity graph that indicates <em>which</em> associations to fetch for {@code roots}.
	 * @param <X> The type of entities to fetch associations for.
	 * @see #setBatchSize(int)
	 */
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
		var subRoots = planForNode.fetch(em, fetchNode.roots(), getBatchSize());
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

