package link.klauser.flatfetcher;

import java.util.Collection;
import javax.persistence.EntityManager;

@FunctionalInterface
interface FetchPlan<X, A> {
	Collection<A> fetch(EntityManager em, Collection<? extends X> roots);
}
