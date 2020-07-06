package link.klauser.flatfetcher;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import javax.persistence.AttributeNode;
import javax.persistence.EntityManager;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class FlatFetcher {

	final EntityManager em;

	@Value
	static class PlanKey {
		@lombok.NonNull
		EntityType<?> entityType;
		AttributeNode<?> graphNode;
	}

	@SuppressWarnings("rawtypes")
	final ConcurrentHashMap<PlanKey, FetchPlan> attributePlanCache = new ConcurrentHashMap<>();

	@SneakyThrows
	public <X extends BaseEntity> void fetch(Class<X> tag, Collection<X> roots, String entityGraphName) {
		if (roots.isEmpty()) {
			return;
		}

		log.debug("Flat fetch([{}...; {}], {})", tag.getSimpleName(), roots.size(), entityGraphName);
		var graph = em.getEntityGraph(entityGraphName);
		var rootType = em.getMetamodel().entity(tag);


		for (AttributeNode<?> attributeNode : graph.getAttributeNodes()) {
			FetchPlan<X> planForNode;
			planForNode = fetchPlanFor(rootType, attributeNode);
			planForNode.fetch(em, roots);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private <X extends BaseEntity> FetchPlan<X> fetchPlanFor(EntityType<X> rootType, AttributeNode<?> attributeNode) {
		return attributePlanCache.computeIfAbsent(new PlanKey(rootType, attributeNode), k -> {
			FetchPlan<X> planForNode;
			var fetchAttr = k.entityType.getAttribute(k.graphNode.getAttributeName());
			if (fetchAttr instanceof PluralAttribute) {
				planForNode = new OneToManyPlan<>(rootType, (PluralAttribute) fetchAttr);
			}
			else if (fetchAttr instanceof SingularAttribute) {
				planForNode = PlanUtils.findAnnotationOpt(fetchAttr, ManyToOne.class)
						.map(manyToOne -> (FetchPlan<X>) new ManyToOnePlan<>((SingularAttribute) fetchAttr))
						.orElseGet(() -> planForOneToOneAttr(rootType, (SingularAttribute) fetchAttr));
			}
			else {
				throw FlatFetcherException.onAttr("Attribute type not supported by flat fetcher: ", fetchAttr);
			}
			return planForNode;
		});
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private <X, A> FetchPlan<X> planForOneToOneAttr(EntityType<X> rootType, SingularAttribute<? super X, A> fetchAttr) {
		var oneToOneAnnotation = PlanUtils.findAnnotation(fetchAttr, OneToOne.class);
		if (!oneToOneAnnotation.mappedBy().isBlank()) {
			return new OneToOneOppositePlan(rootType, fetchAttr, oneToOneAnnotation);

		}
		else {
			return new OneToOneOwningPlan(rootType, fetchAttr);
		}
	}

}

