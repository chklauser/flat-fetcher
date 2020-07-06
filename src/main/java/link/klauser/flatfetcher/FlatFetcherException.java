package link.klauser.flatfetcher;

import java.io.Serializable;
import javax.persistence.metamodel.Attribute;

import org.springframework.lang.Nullable;

public class FlatFetcherException extends RuntimeException implements Serializable {

	FlatFetcherException(String message) {
		super(message);
	}

	FlatFetcherException(String message, @Nullable Throwable cause) {
		super(message, cause);
	}

	FlatFetcherException(String message, @Nullable Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	static FlatFetcherException onAttr(String msg, Attribute<?, ?> metaAttr) {
		return onAttr(msg, metaAttr, null);
	}

	static FlatFetcherException onAttr(String msg, Attribute<?, ?> metaAttr, @Nullable Throwable cause) {
		return new FlatFetcherException(msg +
				metaAttr.getDeclaringType().getJavaType().getSimpleName() + "#" + metaAttr.getJavaMember().getName(), cause);
	}
}
