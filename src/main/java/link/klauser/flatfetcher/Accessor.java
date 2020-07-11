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

/**
 * <p>
 * Cached implementation of a getter ({@link #get(Object)}) and setter ({@link #set(EntityManager, Object, Object)}) on an
 * attribute ({@link #attr()}).
 * </p>
 * <p>
 *     The setter is aware of the persistence context and will set values in such a way that the persistence provider does not
 *     provide the assignment as a change to the entity.
 * </p>
 * @param <X> The type of object to invoke the getter/setter on (the {@code this} pointer)
 * @param <A> The type of value to get/set (the return type/argument type)
 */
@Slf4j
abstract class Accessor<X, A> {

	public abstract A get(X owner);

	public void set(EntityManager entityManager, X owner, A value) {
		set(owner, value);
		setLoadedStatus(entityManager, owner, value);
	}

	protected abstract void set(X owner, A value);

	/**
	 * The underlying attribute. This value is immutable. Never {@code null}.
	 */
	public abstract Attribute<X, A> attr();

	/**
	 * The {@link #attr()} as a {@link SingularAttribute}.
	 * @throws ClassCastException if the {@link #attr()} is not a {@link SingularAttribute}.
	 */
	public SingularAttribute<X, A> singularAttr() {
		// This is type-safe because IF `attr` is a `SingularAttribute`, then the only `SingularAttribute` it can be, is a
		// `SingularAttribute<X, A>`.
		return (SingularAttribute<X, A>) attr();
	}

	/**
	 * The {@link #attr()} as a {@link PluralAttribute}.
	 * @throws ClassCastException if the {@link #attr()} is not a {@link PluralAttribute}.
	 */
	public PluralAttribute<X, A, ?> pluralAttr() {
		// This is type-safe because IF `attr` is a `PluralAttribute`, then the only `PluralAttribute` it can be, is a
		// `PluralAttribute<X, A, ?>`.
		return (PluralAttribute<X, A, ?>) attr();
	}

	/**
	 * <p>
	 * Constructs a fresh accessor for the id "companion" attribute of the supplied association attribute.
	 * </p>
	 * <p>
	 *  Searches for an attribute with the suffix `{@code Id}` added to the end. For example, if the attribute is called
	 *  `{@code foo}`, this factory method will search for an attribute `{@code fooId}`.
	 * </p>
	 *
	 * @param associationAttr The association attribute to derive the association id attribute from.
	 * @param <C> The type of entity to search for attributes.
	 * @param <K> The type of the id attribute (e.g., {@code long}, {@code UUID} or {@code String})
	 * @return a fresh accessor for the id "companion" attribute of the supplied association attribute. Never {@code null}.
	 */
	@SuppressWarnings("unchecked")
	public static <C, K extends Serializable> Accessor<? super C, K> forIdOf(Attribute<? super C, ?> associationAttr) {
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

	/**
	 * Constructs a fresh accessor for the primary key of the supplied entity type.
	 * @param entity The entity type to create a primary key accessor for.
	 * @param <C> The type of entity to create a primary key accessor for.
	 * @return a fresh accessor for the primary key of the supplied entity type.
	 */
	public static <C> Accessor<? super C, ?> forPrimaryKeyOf(EntityType<? super C> entity) {
		var idAttr = entity.getId(entity.getIdType().getJavaType());
		return of(idAttr);
	}

	/**
	 * Constructs a fresh accessor for the supplied attribute.
	 * @param attr The attribute to create an accessor for.
	 * @param <C> The class to call the accessor on (the {@code this} pointer)
	 * @param <T> The type of the value to get set (the return type/argument type)
	 * @return a fresh accessor for the supplied attribute. Never {@code null}.
	 */
	public static <C, T> Accessor<? super C, T> of(Attribute<? super C, T> attr) {
		var m = attr.getJavaMember();
		if (m instanceof Field) {
			var field = (Field) m;
			return fieldAccessor(attr, field);
		}
		if (m instanceof Method) {
			var getter = (Method) m;
			return getterSetterAccessor(attr, getter);
		}
		throw new FlatFetcherException("Members of type " + m.getClass().getSimpleName() + " are not supported.");
	}

	private static <C, T> Accessor<? super C, T> getterSetterAccessor(Attribute<? super C, T> attr, Method getter) {
		var setterName = "s" + getter.getName().substring(1);
		Method setter;
		try {
			setter = getter.getDeclaringClass().getMethod(setterName, getter.getReturnType());
		}
		catch (NoSuchMethodException e) {
			throw FlatFetcherException.onAttr("Cannot find setter " + setterName + "(" + getter.getReturnType() +
					") on " + getter.getDeclaringClass() + ". Needed to access attribute ", attr);
		}
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

	private static <C, T> Accessor<? super C, T> fieldAccessor(Attribute<? super C, T> attr, Field field) {
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

			@SuppressWarnings({ "unchecked" })
			@Override
			public Attribute<C, T> attr() {
				// This is safe because we have the same bounds when we return the Accessor;
				// We just can't _instantiate_ the the Accessor with the wildcard type. Because Java.
				return (Attribute<C, T>) attr;
			}
		};
	}

	void setLoadedStatus(EntityManager em, X owner, A value) {
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
