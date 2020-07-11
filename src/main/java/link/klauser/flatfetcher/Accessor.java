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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
@NotNull
abstract class Accessor<X, A> {

	@Nullable
	abstract A get(@NotNull X owner);

	void set(@NotNull EntityManager entityManager, @NotNull X owner, @Nullable A value) {
		set(owner, value);
		setLoadedStatus(entityManager, owner, value);
	}

	protected abstract void set(X owner, @Nullable A value);

	abstract Attribute<X, A> attr();

	@NotNull
	public SingularAttribute<X, A> singularAttr() {
		// This is type-safe because IF `attr` is a `SingularAttribute`, then the only `SingularAttribute` it can be, is a
		// `SingularAttribute<X, A>`.
		return (SingularAttribute<X, A>) attr();
	}

	@NotNull
	public PluralAttribute<X, A, ?> pluralAttr() {
		// This is type-safe because IF `attr` is a `PluralAttribute`, then the only `PluralAttribute` it can be, is a
		// `PluralAttribute<X, A, ?>`.
		return (PluralAttribute<X, A, ?>) attr();
	}

	@SuppressWarnings("unchecked")
	static <C, K extends Serializable> @NotNull Accessor<? super C, K> forIdOf(@NotNull Attribute<? super C, ?> associationAttr) {
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

	static <C> @NotNull Accessor<? super C, ?> forPrimaryKeyOf(@NotNull EntityType<? super C> entity) {
		var idAttr = entity.getId(entity.getIdType().getJavaType());
		return of(idAttr);
	}

	@SneakyThrows
	static <C, T> @NotNull Accessor<? super C, T> of(@NotNull Attribute<? super C, T> attr) {
		var m = attr.getJavaMember();
		if (m instanceof Field) {
			var field = (Field) m;
			field.setAccessible(true);
			return new Accessor<>() {
				@SuppressWarnings("unchecked")
				@Override
				@SneakyThrows
				public T get(@NotNull C owner) {
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
				public T get(@NotNull C owner) {
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

	void setLoadedStatus(@NotNull EntityManager em, X owner, @Nullable A value) {
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
