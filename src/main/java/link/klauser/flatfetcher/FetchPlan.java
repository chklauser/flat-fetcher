package link.klauser.flatfetcher;

import java.util.Collection;
import javax.persistence.EntityManager;

@FunctionalInterface
interface FetchPlan<C> {
	void fetch(EntityManager em, Collection<? extends C> roots);
}
