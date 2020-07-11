package link.klauser.flatfetcher;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.persistence.EntityManager;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.internal.SessionImpl;
import org.hibernate.persister.entity.UniqueKeyLoadable;
import org.springframework.lang.Nullable;

@Slf4j
abstract class Accessor<X, A> {

	@Nullable
	abstract A get(X owner);

	void set(EntityManager entityManager, X owner, @Nullable A value) {
		set(owner, value);
		setLoadedStatus(entityManager, owner, value);
	}

	protected abstract void set(X owner, @Nullable A value);

	abstract Attribute<X, A> attr();

	public SingularAttribute<X, A> singularAttr() {
		// This is type-safe because IF `attr` is a `SingularAttribute`, then the only `SingularAttribute` it can be, is a
		// `SingularAttribute<X, A>`.
		return (SingularAttribute<X, A>) attr();
	}
	public PluralAttribute<X, A, ?> pluralAttr() {
		// This is type-safe because IF `attr` is a `PluralAttribute`, then the only `PluralAttribute` it can be, is a
		// `PluralAttribute<X, A, ?>`.
		return (PluralAttribute<X, A, ?>) attr();
	}

	@SuppressWarnings("unchecked")
	static <C, K extends Serializable> Accessor<? super C, K> forIdOf(Attribute<? super C, ?> associationAttr) {
		Attribute<? super C, ?> idAttribute;
		var inferredAttributeName = associationAttr.getName() + "Id";
		try {
			idAttribute = associationAttr.getDeclaringType().getAttribute(inferredAttributeName);
		}
		catch (IllegalArgumentException e) {
			throw FlatFetcherException
					.onAttr("Expected id attribute with name `" + inferredAttributeName + "` to exist on " + associationAttr
					.getDeclaringType() + ". Used to fetch ", associationAttr, e);
		}
		return of((Attribute<C, K>) idAttribute);
	}

	static <C> Accessor<? super C, ?> forPrimaryKeyOf(EntityType<? super C> entity) {
		var idAttr = entity.getId(entity.getIdType().getJavaType());
		return of(idAttr);
	}

	@SneakyThrows
	static <C, T> Accessor<? super C, T> of(Attribute<? super C, T> attr) {
		var m = attr.getJavaMember();
		if (m instanceof Field) {
			var field = (Field) m;
			field.setAccessible(true);
			return new Accessor<>() {
				@SuppressWarnings("unchecked")
				@Override
				@SneakyThrows
				public T get(C owner) {
					return (T) field.get(owner);
				}

				@Override
				@SneakyThrows
				public void set(C owner, T value) {
					field.set(owner, value);
				}

				@Override
				public String toString() {
					return "accessor(" + field.getDeclaringClass().getSimpleName() + "#" + field.getName() + ")";
				}

				@SuppressWarnings({ "unchecked", "rawtypes" })
				@Override
				public Attribute<C, T> attr() {
					// This is safe because we have the same bounds when we return the Accessor;
					// We just can't _instantiate_ the the Accessor with the wildcard type. Because Java.
					return (Attribute)attr;
				}
			};
		}
		if (m instanceof Method) {
			var getter = (Method) m;
			var setterName = "s" + getter.getName().substring(1);
			var setter = getter.getDeclaringClass().getMethod(setterName, getter.getReturnType());
			getter.setAccessible(true);
			setter.setAccessible(true);
			return new Accessor<>() {
				@SuppressWarnings("unchecked")
				@SneakyThrows
				@Override
				public T get(C owner) {
					return (T) getter.invoke(owner);
				}

				@SneakyThrows
				@Override
				public void set(C owner, T value) {
					setter.invoke(owner, value);
				}

				@Override
				public String toString() {
					return "accessor(" + getter.getDeclaringClass().getSimpleName() + "#" + getter.getName() + ","
							+ setter.getDeclaringClass().getSimpleName() + "#" + setter.getName() + ")";
				}

				@SuppressWarnings({ "unchecked", "rawtypes" })
				@Override
				public Attribute<C, T> attr() {
					// This is safe because we have the same bounds when we return the Accessor;
					// We just can't _instantiate_ the the Accessor with the wildcard type. Because Java.
					return (Attribute) attr;
				}
			};
		}
		throw new FlatFetcherException("Members of type " + m.getClass().getSimpleName() + " are not supported.");
	}

	void setLoadedStatus(EntityManager em, X owner, @Nullable A value) {
		var session = (SessionImpl) em.getDelegate();
		if(!session.contains(owner)){
			return;
		}
		var entityEntry = session.getPersistenceContext().getEntry(owner);
		if (entityEntry == null) {
			if(log.isTraceEnabled()) {
				log.trace("Skip {}.setLoadedStatus because the entity manager didn't return an entry for this entity.", this);
			}
		}
		else {
			var propIndex = ((UniqueKeyLoadable) entityEntry.getPersister()).getPropertyIndex(attr().getName());
			var loadedState = entityEntry.getLoadedState();
			if(loadedState != null) {
				loadedState[propIndex] = value;
			}
		}
	}
}
