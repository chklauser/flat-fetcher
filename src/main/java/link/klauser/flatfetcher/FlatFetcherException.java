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
import javax.persistence.metamodel.Attribute;

/**
 * Usually indicates an error during the introspection/reflection phase of the {@link FlatFetcher}.
 */
public class FlatFetcherException extends RuntimeException implements Serializable {

	public FlatFetcherException(String message) {
		super(message);
	}

	public FlatFetcherException(String message, Throwable cause) {
		super(message, cause);
	}

	protected FlatFetcherException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public static FlatFetcherException onAttr(String msg, Attribute<?, ?> metaAttr) {
		return onAttr(msg, metaAttr, null);
	}

	public static FlatFetcherException onAttr(String msg, Attribute<?, ?> metaAttr, Throwable cause) {
		return new FlatFetcherException(msg +
				PlanUtils.shortAttrDescription(metaAttr), cause);
	}

}
