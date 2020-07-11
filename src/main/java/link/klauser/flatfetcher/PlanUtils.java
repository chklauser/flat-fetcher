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
import java.util.Optional;
import java.util.function.Supplier;
import javax.persistence.JoinColumn;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

import org.jetbrains.annotations.NotNull;

@NotNull
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
}
