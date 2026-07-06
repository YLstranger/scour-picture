package org.apache.shardingsphere.infra.util.yaml.representer;

import org.apache.shardingsphere.infra.spi.ShardingSphereServiceLoader;
import org.apache.shardingsphere.infra.util.yaml.representer.processor.DefaultYamlTupleProcessor;
import org.apache.shardingsphere.infra.util.yaml.representer.processor.ShardingSphereYamlTupleProcessor;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compatibility override for ShardingSphere 5.4.1 on Spring Boot 3.5 / SnakeYAML 2.x.
 */
public final class ShardingSphereYamlRepresenter extends Representer {

    public ShardingSphereYamlRepresenter() {
        super(new DumperOptions());
    }

    @Override
    protected NodeTuple representJavaBeanProperty(final Object javaBean, final Property property,
                                                  final Object propertyValue, final Tag customTag) {
        NodeTuple tuple = super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
        for (ShardingSphereYamlTupleProcessor processor
                : ShardingSphereServiceLoader.getServiceInstances(ShardingSphereYamlTupleProcessor.class)) {
            if (property.getName().equals(processor.getTupleName())) {
                return processor.process(tuple);
            }
        }
        return new DefaultYamlTupleProcessor().process(tuple);
    }

    @Override
    protected Node representMapping(final Tag tag, final Map<?, ?> mapping, final DumperOptions.FlowStyle flowStyle) {
        Map<Object, Object> filteredMapping = new LinkedHashMap<>(mapping.size(), 1F);
        for (Map.Entry<?, ?> entry : mapping.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Collection && ((Collection<?>) value).isEmpty()) {
                continue;
            }
            if (value instanceof Map && ((Map<?, ?>) value).isEmpty()) {
                continue;
            }
            filteredMapping.put(entry.getKey(), value);
        }
        return super.representMapping(tag, filteredMapping, flowStyle);
    }
}
