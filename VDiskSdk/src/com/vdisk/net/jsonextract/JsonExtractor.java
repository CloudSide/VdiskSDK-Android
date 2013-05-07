package com.vdisk.net.jsonextract;

public abstract class JsonExtractor<T> {
    public abstract T extract(JsonThing jt) throws JsonExtractionException;
}
