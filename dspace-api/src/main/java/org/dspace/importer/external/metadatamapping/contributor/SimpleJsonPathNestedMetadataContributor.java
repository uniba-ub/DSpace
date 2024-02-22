/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.importer.external.metadatamapping.contributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.core.CrisConstants;
import org.dspace.importer.external.metadatamapping.MetadataFieldConfig;
import org.dspace.importer.external.metadatamapping.MetadataFieldMapping;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;

/**
 * A simple JsonPath Metadata processor
 * that allow extract value from json array with objects into metadata groups.
 * by configuring the path in the query variable via the bean and the path to additional nested fields.
 * For missing elements placeholder are generated
 * moreover this can also perform more compact extractions
 * by configuring specific json processor in "metadataProcessor"
 *
 * Given Json:
 * { Affiliation: [{role:"tester",start:"2024"},{end:"2023",organisation:"ABC-Universität Delangen"}]}
 *
 *     <entry key-ref="person.affiliation" value-ref="testAffiliation"/>
 *        ...
 * <bean id="testAffiliation"
 * class="org.dspace.importer.external.metadatamapping.contributor.SimpleJsonPathNestedMetadataContributor">
 *     <property name="query" value="/Affiliation"/>
 *     <property name="nestedfields">
 *         <map>
 *             <entry key="/role" value-ref="testperson.affiliation.role" />
 *             <entry key="/organisation" value-ref="testperson.affiliation.orgunit" />
 *             <entry key="/start" value-ref="testperson.affiliation.startdate" />
 *             <entry key="/end" value-ref="testperson.affiliation.enddate" />
 *         </map>
 *     </property>
 * </bean>
 *     value-ref to the according beans with the metadatafields, e.g. oairecerif
 *
 * <bean id="person.affiliation" class="org.dspace.importer.external.metadatamapping.MetadataFieldConfig">
 *     <constructor-arg value="oairecerif.affiliation.startDate"/>
 * </bean>
 * <bean id="testperson.affiliation.startdate" class="org.dspace.importer.external.metadatamapping.MetadataFieldConfig">
 *     <constructor-arg value="oairecerif.affiliation.startDate"/>
 * </bean>
 * <bean id="testperson.affiliation.enddate" class="org.dspace.importer.external.metadatamapping.MetadataFieldConfig">
 *     <constructor-arg value="oairecerif.affiliation.endDate"/>
 * </bean>
 * <bean id="testperson.affiliation.role" class="org.dspace.importer.external.metadatamapping.MetadataFieldConfig">
 *     <constructor-arg value="oairecerif.affiliation.role"/>
 * </bean>
 * <bean id="testperson.affiliation.orgunit" class="org.dspace.importer.external.metadatamapping.MetadataFieldConfig">
 *     <constructor-arg value="oairecerif.affiliation.orgunit"/>
 * </bean>
 * should return the metadatavalues:
 * oairecerif.affiliation.startDate: 2024
 * oairecerif.affiliation.endDate: "#PLACEHOLDER_PARENT_METADATA_VALUE#"
 * oairecerif.affiliation.role: "tester"
 * oairecerif.affiliation.orgunit: "#PLACEHOLDER_PARENT_METADATA_VALUE#"
 * oairecerif.affiliation.startDate: "#PLACEHOLDER_PARENT_METADATA_VALUE#"
 * oairecerif.affiliation.endDate: "2023"
 * oairecerif.affiliation.role: "#PLACEHOLDER_PARENT_METADATA_VALUE#"
 * oairecerif.affiliation.orgunit: "ABC-Universität Delangen"
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk@4science.com)
 * @author Florian Gantner (florian.gantner@uni-bamberg.de)
 */
public class SimpleJsonPathNestedMetadataContributor implements MetadataContributor<String> {

    private final static Logger log = LogManager.getLogger();

    private MetadataFieldConfig field;

    private String query;
    private Map<String,MetadataFieldConfig> nestedfields;

    protected JsonPathMetadataProcessor metadataProcessor;

    /**
     * Initialize SimpleJsonPathMetadataContributor with a query, prefixToNamespaceMapping and MetadataFieldConfig
     *
     * @param query The JSonPath query
     * @param field the matadata field to map the result of the Json path query
     * <a href="https://github.com/DSpace/DSpace/tree/master/dspace-api/src/main/java/org/dspace/importer/external#metadata-mapping-">MetadataFieldConfig</a>
     */
    public SimpleJsonPathNestedMetadataContributor(String query, MetadataFieldConfig field) {
        this.query = query;
        this.field = field;
        this.nestedfields = new HashMap<String, MetadataFieldConfig>();
    }


    /**
     * Unused by this implementation
     */
    @Override
    public void setMetadataFieldMapping(MetadataFieldMapping<String, MetadataContributor<String>> rt) {

    }

    /**
     * Empty constructor for SimpleJsonPathMetadataContributor
     */
    public SimpleJsonPathNestedMetadataContributor() {

    }

    /**
     * Return the MetadataFieldConfig used while retrieving MetadatumDTO
     *
     * @return MetadataFieldConfig
     */
    public MetadataFieldConfig getField() {
        return field;
    }

    /**
     * Setting the MetadataFieldConfig
     *
     * @param field MetadataFieldConfig used while retrieving MetadatumDTO
     */
    public void setField(MetadataFieldConfig field) {
        this.field = field;
    }

    /**
     * Return query used to create the JSonPath
     *
     * @return the query this instance is based on
     */
    public String getQuery() {
        return query;
    }

    /**
     * Return query used to create the JSonPath
     *
     */
    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * Return query used to create the JSonPath for nested Entities
     *
     * @return Map with query and the field mapping
     */
    public Map<String,MetadataFieldConfig> getNestedfields() {
        return nestedfields;
    }

    /**
     * Set query/field used to create the JSonPath for nested entities
     *
     */
    public void setNestedfields(Map<String,MetadataFieldConfig> query) {
        this.nestedfields = query;
    }


    /**
     * Used to process data got by jsonpath expression, like arrays to stringify, change date format or else
     * If it is null, toString will be used.
     *
     * @param metadataProcessor
     */
    public void setMetadataProcessor(JsonPathMetadataProcessor metadataProcessor) {
        this.metadataProcessor = metadataProcessor;
    }

    /**
     * Retrieve the metadata associated with the given object.
     * The toString() of the resulting object will be used.
     *
     * @param fullJson A class to retrieve metadata from.
     * @return a collection of import records. Only the identifier of the found records may be put in the record.
     */
    @Override
    public Collection<MetadatumDTO> contributeMetadata(String fullJson) {
        Collection<MetadatumDTO> metadata = new ArrayList<>();
        Collection<String> metadataValue = new ArrayList<>();
        if (Objects.nonNull(metadataProcessor)) {
            metadataValue = metadataProcessor.processMetadata(fullJson);
        } else {
            JsonNode jsonNode = convertStringJsonToJsonNode(fullJson);
            JsonNode node = jsonNode.at(query);
            if (node.isArray()) {
                ArrayNode results = (ArrayNode) node;
                for (int i = 0; i < results.size(); i++) {
                    /* Use subqueries inside this node
                     *   For Missing/not existing attributes placeholders values are generated.
                     *    [{a:"",b:""},{b:"",c:""}]
                     *    .> 1st object: generate placeholder for c
                     *    -> 2nd object: generate placeholder for b
                     * */
                    for (Map.Entry<String, MetadataFieldConfig> entry : getNestedfields().entrySet()) {
                        MetadatumDTO metadatumDtonested = new MetadatumDTO();
                        metadatumDtonested.setElement(entry.getValue().getElement());
                        metadatumDtonested.setQualifier(entry.getValue().getQualifier());
                        metadatumDtonested.setSchema(entry.getValue().getSchema());
                        try {
                            JsonNode nestednode = results.get(i).at(entry.getKey());
                            String nestedValue = getStringValue(nestednode);
                            if (StringUtils.isNotBlank(nestedValue)) {
                                metadatumDtonested.setValue(nestedValue);
                            } else {
                                metadatumDtonested.setValue(CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE);
                            }
                        } catch (Exception ee) {
                            // no path, set placeholder
                            metadatumDtonested.setValue(CrisConstants.PLACEHOLDER_PARENT_METADATA_VALUE);
                        }

                        metadata.add(metadatumDtonested);
                    }


                }
            } else {
                String nodeValue = getStringValue(node);
                if (StringUtils.isNotBlank(nodeValue)) {
                    metadataValue.add(nodeValue);
                }
            }
        }
        for (String value : metadataValue) {
            MetadatumDTO metadatumDto = new MetadatumDTO();
            metadatumDto.setValue(value);
            metadatumDto.setElement(field.getElement());
            metadatumDto.setQualifier(field.getQualifier());
            metadatumDto.setSchema(field.getSchema());
            metadata.add(metadatumDto);
        }
        return metadata;
    }

    private String getStringValue(JsonNode node) {
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isNumber()) {
            return node.numberValue().toString();
        }
        log.error("It wasn't possible to convert the value of the following JsonNode:" + node.asText());
        return StringUtils.EMPTY;
    }

    private JsonNode convertStringJsonToJsonNode(String json) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode body = null;
        try {
            body = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.error("Unable to process json response.", e);
        }
        return body;
    }
}
