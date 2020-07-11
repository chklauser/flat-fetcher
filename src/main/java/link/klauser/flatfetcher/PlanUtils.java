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
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.persistence.JoinColumn;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

final class PlanUtils {

	private PlanUtils() {
		throw new IllegalStateException("Cannot construct instance of utility class");
	}

	@SuppressWarnings("unchecked")
	static <C> Supplier<C> emptyCollectionSupplierFor(PluralAttribute<?, C, ?> attribute) {
		switch (attribute.getCollectionType()) {
			case COLLECTION:
			case SET:
				return () -> (C) new HashSet<>();
			case LIST:
				return () -> (C) new ArrayList<>();
			default:
				throw FlatFetcherException.onAttr("Collection type " + attribute.getCollectionType() + " not supported for ",
						attribute);
		}
	}

	static <A extends Annotation> A findAnnotation(Attribute<?, ?> metaAttr, Class<A> annotationClass) {
		return findAnnotationOpt(metaAttr, annotationClass)
				.orElseThrow(() -> FlatFetcherException.onAttr(
						"Expected @" + annotationClass.getSimpleName() + " annotation on ", metaAttr));
	}

	static <A extends Annotation> Optional<A> findAnnotationOpt(Attribute<?, ?> metaAttr, Class<A> annotationClass) {
		var member = (AnnotatedElement & Member) metaAttr.getJavaMember();
		return Optional.ofNullable(member.getAnnotation(annotationClass))
				.or(() -> {
					String associatedMemberName = "<unknown>";
					var clazz = member.getDeclaringClass();
					try {
						if (member instanceof Field) {
							var fieldName = member.getName();
							associatedMemberName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
							var getter = clazz.getDeclaredMethod(associatedMemberName);
							return Optional.ofNullable(getter.getAnnotation(annotationClass));
						}
						if (member instanceof Method) {
							var capitalizedName = member.getName().substring(3);
							associatedMemberName = capitalizedName.substring(0, 1).toLowerCase() + capitalizedName.substring(1);
							return Optional.ofNullable(clazz.getDeclaredField(associatedMemberName).getAnnotation(annotationClass));
						}
						return Optional.empty();
					}
					catch (NoSuchFieldException | NoSuchMethodException e) {
						throw FlatFetcherException.onAttr(
								"While looking for @" + annotationClass.getSimpleName() + ": Could not find associated member "
										+ clazz.getSimpleName() + "#" + associatedMemberName + " of ",
								metaAttr, e);
					}
				});
	}

	static <X, A, K extends Serializable> Accessor<? super X, K> referencedColumnAccessor(
			EntityType<? super X> rootType,
			Attribute<? super A, X> mappedByAttr) {
		Accessor<? super X, K> referencedColumnAccessor;
		var mappedByJoinColumnAnnotation = findAnnotation(mappedByAttr, JoinColumn.class);
		if(mappedByJoinColumnAnnotation.referencedColumnName().isBlank()){
			@SuppressWarnings("unchecked")
			var genericRootIdAccess = (Accessor<? super X, K>) Accessor.forPrimaryKeyOf(rootType);
			referencedColumnAccessor = genericRootIdAccess;
		}
		else {
			@SuppressWarnings("unchecked")
			SingularAttribute<X, K> referencedRootAttr = (SingularAttribute<X, K>) rootType
					.getSingularAttribute(mappedByJoinColumnAnnotation.referencedColumnName());
			referencedColumnAccessor = Accessor.of(referencedRootAttr);
		}
		return referencedColumnAccessor;
	}

	static String shortAttrDescription(Attribute<?, ?> metaAttr) {
		return metaAttr.getDeclaringType().getJavaType().getSimpleName() + "#" + metaAttr.getJavaMember().getName();
	}

	static <T> Stream<List<T>> chunks(Stream<T> sourceStream, int size) {
		var source = sourceStream.spliterator();
		return StreamSupport.stream(new ChunksSpliterator<T>(source, size), false);
	}

	private static class ChunksSpliterator<T> implements Spliterator<List<T>> {

		final List<T> buf;

		private final Spliterator<T> source;

		private final int size;

		public ChunksSpliterator(Spliterator<T> source, int size) {
			if(size <= 0){
				throw new IllegalArgumentException("Chunk size must be strictly positive.");
			}
			this.source = source;
			this.size = size;
			buf = new ArrayList<>(size);
		}

		@Override
		public boolean tryAdvance(Consumer<? super List<T>> action) {
			while (buf.size() < size) {
				if (!source.tryAdvance(buf::add)) {
					if (!buf.isEmpty()) {
						action.accept(List.copyOf(buf));
						buf.clear();
						return true;
					} else {
						return false;
					}
				}
			}
			action.accept(List.copyOf(buf));
			buf.clear();
			return true;
		}

		@Override
		public Spliterator<List<T>> trySplit() {
			var innerSize = source.estimateSize();
			if(innerSize == Long.MAX_VALUE || innerSize < size) {
				return null;
			}
			var prefix = source.trySplit();
			if(prefix == null){
				return null;
			}
			return new ChunksSpliterator<>(prefix, size);
		}

		@Override
		public long estimateSize() {
			var sourceSize = source.estimateSize();
			if(sourceSize == Long.MAX_VALUE){
				return Long.MAX_VALUE;
			}
			return sourceSize / size + (sourceSize % size != 0 ? 1 : 0);
		}

		@Override
		public int characteristics() {
			var sourceCharacteristics = source.characteristics();
			return NONNULL | ((ORDERED  | SIZED | SUBSIZED | DISTINCT | SORTED | IMMUTABLE | CONCURRENT) & sourceCharacteristics);
		}
	}
}
