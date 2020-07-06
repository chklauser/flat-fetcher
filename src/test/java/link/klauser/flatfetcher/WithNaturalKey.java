package link.klauser.flatfetcher;

import java.io.Serializable;

public interface WithNaturalKey<T extends Serializable> {
    T getNaturalKey();
    void setNaturalKey(T newNaturalKey);
}
