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

import java.util.Collection;
import javax.persistence.EntityManager;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
@NotNull
interface FetchPlan<X, A> {
	@NotNull
	Collection<A> fetch(@NotNull EntityManager em, @NotNull Collection<? extends X> roots);
}
